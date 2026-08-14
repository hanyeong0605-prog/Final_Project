package com.jobpilot.api.domain.portfolio.service;

import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioNarrative;
import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioSlide;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.CoreFile;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.TechnologyFact;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

// PptxRenderer와 같은 슬라이드 구조를 받아 별도 PDF 레이아웃으로 그린다. pptx를 pdf로
// 변환하는 방식이 아니라 PDFBox로 직접 그려서 LibreOffice 같은 외부 변환 프로세스에
// 의존하지 않는다.
//
// PDFBox 표준 14 폰트(Helvetica 등)는 한글 글리프가 없어 Gemini/정적 폴백이 만드는 한글
// 텍스트를 그대로 그리면 showText()에서 예외가 난다 - 반드시 한글 폰트를 임베드해야 한다.
// 처음엔 Noto Sans KR(OTF/CFF 윤곽선)을 썼는데, PDType0Font.load(document, InputStream)이
// 쓰는 TTFParser가 기본적으로 CFF 윤곽선을 허용하지 않아 "True Type fonts using CFF
// outlines are not supported" 예외가 났다(실제로 겪은 문제) - TrueType(glyf) 윤곽선인
// resources/fonts/NanumGothic-Regular.ttf(네이버, OFL-1.1 라이선스)로 교체해 해결했다.
//
// 2026-08-14: 첫 버전은 파일 경로만 텍스트로 나열해서 "코드가 안 보인다"는 피드백을 받았다 -
// 실제 근거 코드 스니펫(analysis.coreFiles의 excerpt)을 페이지에 직접 넣고, 별도 "프로젝트
// 개요" 페이지를 추가하고, 템플릿(색상 테마) 선택을 지원하도록 다시 만들었다.
@Component
class PdfRenderer {
    private static final String FONT_RESOURCE = "/fonts/NanumGothic-Regular.ttf";
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 56f;
    private static final int MAX_EXCERPT_CHARS = 700;

    byte[] render(GitHubProjectAnalysisResponse analysis, PortfolioNarrative narrative, PortfolioTemplate template) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            Map<String, CoreFile> filesByPath = filesByPath(analysis.coreFiles());
            renderTitlePage(document, font, analysis, narrative, template);
            renderOverviewPage(document, font, analysis, template);
            for (PortfolioSlide slide : narrative.slides()) {
                renderContentPage(document, font, slide, filesByPath, template);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Map<String, CoreFile> filesByPath(List<CoreFile> coreFiles) {
        Map<String, CoreFile> result = new LinkedHashMap<>();
        for (CoreFile file : coreFiles) result.put(file.path(), file);
        return result;
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        try (InputStream fontStream = getClass().getResourceAsStream(FONT_RESOURCE)) {
            if (fontStream == null) {
                throw new IOException("Korean PDF font resource is missing: " + FONT_RESOURCE);
            }
            return PDType0Font.load(document, fontStream);
        }
    }

    // 배경 채우기를 먼저 별도 content stream으로 그리고 닫은 뒤, 호출자가 APPEND 모드로 새
    // content stream을 열어 그 위에 텍스트를 그린다 - 그래야 배경이 텍스트를 덮어쓰지 않는다.
    private PDPage newPage(PDDocument document, PortfolioTemplate template) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            int[] bg = template.background;
            stream.setNonStrokingColor(new Color(bg[0], bg[1], bg[2]));
            stream.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            stream.fill();
        }
        return page;
    }

    private void renderTitlePage(
            PDDocument document, PDFont font, GitHubProjectAnalysisResponse analysis, PortfolioNarrative narrative, PortfolioTemplate template
    ) throws IOException {
        PDPage page = newPage(document, template);
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            float y = PAGE_HEIGHT - 220;
            y = writeWrapped(stream, narrative.title(), MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 24, 30, template.ink);
            y -= 20;
            y = writeWrapped(stream, narrative.subtitle(), MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 13, 18, template.muted);
            writeWrapped(stream, analysis.repository().fullName(), MARGIN, y - 40, PAGE_WIDTH - 2 * MARGIN, font, 11, 16, template.accent);
        }
    }

    private void renderOverviewPage(
            PDDocument document, PDFont font, GitHubProjectAnalysisResponse analysis, PortfolioTemplate template
    ) throws IOException {
        PDPage page = newPage(document, template);
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            float y = PAGE_HEIGHT - MARGIN - 20;
            y = writeWrapped(stream, "프로젝트 개요", MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 20, 26, template.ink);
            y -= 10;
            String classification = analysis.projectProfile() != null ? analysis.projectProfile().classification() : "";
            if (!classification.isBlank()) {
                y = writeWrapped(stream, classification, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 14, 20, template.accent);
                y -= 8;
            }
            y = writeWrapped(stream, analysis.overview(), MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 12, 18, template.ink);
            List<TechnologyFact> stack = analysis.technologyStack();
            if (stack != null && !stack.isEmpty()) {
                String techLine = "기술 스택: " + stack.stream().limit(10).map(TechnologyFact::name)
                        .reduce((a, b) -> a + ", " + b).orElse("");
                writeWrapped(stream, techLine, MARGIN, y - 24, PAGE_WIDTH - 2 * MARGIN, font, 10, 15, template.muted);
            }
        }
    }

    private void renderContentPage(
            PDDocument document, PDFont font, PortfolioSlide slide, Map<String, CoreFile> filesByPath, PortfolioTemplate template
    ) throws IOException {
        PDPage page = newPage(document, template);
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
            float y = PAGE_HEIGHT - MARGIN - 20;
            y = writeWrapped(stream, slide.heading(), MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 17, 23, template.ink);
            y -= 12;
            for (String bullet : slide.bullets()) {
                y = writeWrapped(stream, "- " + bullet, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, font, 11, 16, template.ink);
                y -= 5;
            }
            y -= 8;

            String excerpt = firstExcerpt(slide.evidence(), filesByPath);
            if (excerpt != null) {
                List<String> codeLines = new ArrayList<>();
                for (String rawLine : truncate(excerpt, MAX_EXCERPT_CHARS).split("\n")) {
                    codeLines.addAll(wrap(rawLine.isEmpty() ? " " : rawLine, font, 9, PAGE_WIDTH - 2 * MARGIN - 20));
                }
                float blockHeight = codeLines.size() * 13f + 16f;
                float blockTop = y;
                int[] codeBg = template.codeBackground;
                stream.setNonStrokingColor(new Color(codeBg[0], codeBg[1], codeBg[2]));
                stream.addRect(MARGIN, blockTop - blockHeight, PAGE_WIDTH - 2 * MARGIN, blockHeight);
                stream.fill();

                stream.setNonStrokingColor(new Color(template.ink[0], template.ink[1], template.ink[2]));
                stream.beginText();
                stream.setFont(font, 9);
                stream.newLineAtOffset(MARGIN + 10, blockTop - 12);
                for (int i = 0; i < codeLines.size(); i++) {
                    if (i > 0) stream.newLineAtOffset(0, -13);
                    stream.showText(codeLines.get(i));
                }
                stream.endText();
                y = blockTop - blockHeight - 14;
            }

            List<String> evidencePaths = slide.evidence().stream()
                    .map(PortfolioNarrativeGeminiClient.EvidenceRef::path).distinct().toList();
            if (!evidencePaths.isEmpty()) {
                writeWrapped(stream, "근거: " + String.join(", ", evidencePaths), MARGIN, Math.min(y, MARGIN + 30),
                        PAGE_WIDTH - 2 * MARGIN, font, 8, 12, template.muted);
            }
        }
    }

    // evidence 순서대로 파일을 찾아, 그 evidence가 지목한 symbol(메서드 이름)에 정확히
    // 맞는 발췌가 있으면 그걸 쓰고, 없으면(정적 폴백에서 symbol이 클래스 이름일 때 등)
    // 그 파일의 대표 발췌(excerpt)로 대체한다(2026-08-14, 한 파일이 여러 구현의 근거로
    // 쓰일 때 항상 같은 코드만 보이던 문제의 수정).
    private String firstExcerpt(List<PortfolioNarrativeGeminiClient.EvidenceRef> evidence, Map<String, CoreFile> filesByPath) {
        for (PortfolioNarrativeGeminiClient.EvidenceRef ref : evidence) {
            CoreFile file = filesByPath.get(ref.path());
            if (file == null) continue;
            String bySymbol = excerptForSymbol(file, ref.symbol());
            if (bySymbol != null) return bySymbol;
        }
        return null;
    }

    private String excerptForSymbol(CoreFile file, String symbol) {
        if (symbol != null) {
            for (GitHubProjectAnalysisResponse.MethodExcerpt candidate : file.methodExcerpts()) {
                if (candidate.symbol().equals(symbol)) return candidate.excerpt();
            }
        }
        return (file.excerpt() != null && !file.excerpt().isBlank()) ? file.excerpt() : null;
    }

    // 문자수 컷이 코드 줄 중간을 자르면 스니펫이 깨져 보인다(2026-08-14, 발췌 자체는
    // 완결된 줄로 끝나도록 고쳤는데 여기서 또 잘릴 수 있어 같이 고침) - maxChars 이내에서
    // 가장 마지막 개행 지점까지만 자른다.
    private String truncate(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        int cut = text.lastIndexOf('\n', maxChars);
        return (cut > 0 ? text.substring(0, cut) : text.substring(0, maxChars)) + "\n...";
    }

    private float writeWrapped(PDPageContentStream stream, String text, float x, float y, float width,
            PDFont font, float fontSize, float leading, int[] color) throws IOException {
        if (text == null || text.isBlank()) return y;
        List<String> lines = wrap(text, font, fontSize, width);
        stream.setNonStrokingColor(new Color(color[0], color[1], color[2]));
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) stream.newLineAtOffset(0, -leading);
            stream.showText(lines.get(i));
        }
        stream.endText();
        return y - leading * lines.size();
    }

    // PDFBox has no built-in word wrap. This splits on spaces first (handles mixed Korean/English
    // technical text such as "Spring Boot 기반 API"), then hard-wraps any single token that still
    // overflows the page width (e.g. a long file path or unbroken code line) so nothing is clipped.
    private List<String> wrap(String text, PDFont font, float fontSize, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (widthOf(font, candidate, fontSize) > width && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
            while (widthOf(font, current.toString(), fontSize) > width && current.length() > 1) {
                int breakAt = current.length() - 1;
                lines.add(current.substring(0, breakAt));
                current = new StringBuilder(current.substring(breakAt));
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }

    private float widthOf(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }
}
