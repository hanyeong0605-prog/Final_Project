package com.jobpilot.api.domain.portfolio.service;

import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioNarrative;
import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioSlide;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.CoreFile;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.TechnologyFact;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.springframework.stereotype.Component;

// Gemini(또는 정적 폴백)가 만든 슬라이드 구조(PortfolioNarrative)를 고정 레이아웃에만
// 채운다 - 디자인 판단(색, 배치)은 이 클래스에만 있고 AI가 관여하지 않는다.
//
// 2026-08-14: 첫 버전은 파일 경로만 텍스트로 나열해서 "코드가 안 보인다"는 피드백을 받았다 -
// 실제 근거 코드 스니펫(analysis.coreFiles의 excerpt)을 슬라이드에 직접 넣고, 별도
// "프로젝트 개요" 슬라이드를 추가하고, 템플릿(색상 테마) 선택을 지원하도록 다시 만들었다.
@Component
class PptxRenderer {
    private static final int SLIDE_WIDTH = 960;
    private static final int SLIDE_HEIGHT = 540;
    private static final String FONT_FAMILY = "맑은 고딕";
    private static final String CODE_FONT_FAMILY = "Consolas";
    private static final int MAX_EXCERPT_CHARS = 480;

    byte[] render(GitHubProjectAnalysisResponse analysis, PortfolioNarrative narrative, PortfolioTemplate template) {
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            slideShow.setPageSize(new Dimension(SLIDE_WIDTH, SLIDE_HEIGHT));
            Map<String, CoreFile> filesByPath = filesByPath(analysis.coreFiles());
            renderTitleSlide(slideShow, analysis, narrative, template);
            renderOverviewSlide(slideShow, analysis, template);
            for (PortfolioSlide slide : narrative.slides()) {
                renderContentSlide(slideShow, slide, filesByPath, template);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slideShow.write(out);
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

    private void renderTitleSlide(
            XMLSlideShow slideShow, GitHubProjectAnalysisResponse analysis, PortfolioNarrative narrative, PortfolioTemplate template
    ) {
        XSLFSlide slide = newSlide(slideShow, template);
        addTextBox(slide, new Rectangle2D.Double(60, 190, 840, 90), narrative.title(), 34, true, awt(template.ink));
        addTextBox(slide, new Rectangle2D.Double(60, 280, 840, 80), narrative.subtitle(), 16, false, awt(template.muted));
        addTextBox(slide, new Rectangle2D.Double(60, 470, 840, 30), analysis.repository().fullName(), 12, false, awt(template.accent));
    }

    private void renderOverviewSlide(XMLSlideShow slideShow, GitHubProjectAnalysisResponse analysis, PortfolioTemplate template) {
        XSLFSlide slide = newSlide(slideShow, template);
        addTextBox(slide, new Rectangle2D.Double(50, 40, 860, 50), "프로젝트 개요", 26, true, awt(template.ink));

        String classification = analysis.projectProfile() != null ? analysis.projectProfile().classification() : "";
        double bodyTop = 100;
        if (!classification.isBlank()) {
            addTextBox(slide, new Rectangle2D.Double(50, 100, 860, 34), classification, 15, true, awt(template.accent));
            bodyTop = 140;
        }
        addWrappedParagraph(slide, new Rectangle2D.Double(60, bodyTop, 840, 260), analysis.overview(), 14, awt(template.ink));

        List<TechnologyFact> stack = analysis.technologyStack();
        if (stack != null && !stack.isEmpty()) {
            String techLine = "기술 스택: " + stack.stream().limit(10).map(TechnologyFact::name)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            addTextBox(slide, new Rectangle2D.Double(60, 465, 840, 40), techLine, 12, false, awt(template.muted));
        }
    }

    private void renderContentSlide(
            XMLSlideShow slideShow, PortfolioSlide slide, Map<String, CoreFile> filesByPath, PortfolioTemplate template
    ) {
        XSLFSlide xslfSlide = newSlide(slideShow, template);
        addTextBox(xslfSlide, new Rectangle2D.Double(50, 30, 860, 50), slide.heading(), 24, true, awt(template.ink));

        XSLFTextBox body = xslfSlide.createTextBox();
        body.setAnchor(new Rectangle2D.Double(50, 90, 860, 150));
        boolean first = true;
        for (String bullet : slide.bullets()) {
            XSLFTextParagraph paragraph = first ? body.getTextParagraphs().get(0) : body.addNewTextParagraph();
            first = false;
            paragraph.setBullet(true);
            paragraph.setSpaceAfter(8d);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(bullet);
            run.setFontFamily(FONT_FAMILY);
            run.setFontSize(14d);
            run.setFontColor(awt(template.ink));
        }

        String excerpt = firstExcerpt(slide.evidence(), filesByPath);
        if (excerpt != null) {
            XSLFTextBox codeBox = xslfSlide.createTextBox();
            codeBox.setAnchor(new Rectangle2D.Double(50, 250, 860, 225));
            codeBox.setFillColor(awt(template.codeBackground));
            String[] lines = truncate(excerpt, MAX_EXCERPT_CHARS).split("\n");
            boolean firstLine = true;
            for (String line : lines) {
                XSLFTextParagraph paragraph = firstLine ? codeBox.getTextParagraphs().get(0) : codeBox.addNewTextParagraph();
                firstLine = false;
                XSLFTextRun run = paragraph.addNewTextRun();
                run.setText(line.isEmpty() ? " " : line);
                run.setFontFamily(CODE_FONT_FAMILY);
                run.setFontSize(11d);
                run.setFontColor(awt(template.ink));
            }
        }

        List<String> evidencePaths = slide.evidence().stream()
                .map(PortfolioNarrativeGeminiClient.EvidenceRef::path).distinct().toList();
        if (!evidencePaths.isEmpty()) {
            addTextBox(xslfSlide, new Rectangle2D.Double(50, 495, 860, 30),
                    "근거: " + String.join(", ", evidencePaths), 9, false, awt(template.muted));
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

    private XSLFSlide newSlide(XMLSlideShow slideShow, PortfolioTemplate template) {
        XSLFSlide slide = slideShow.createSlide();
        slide.getBackground().setFillColor(awt(template.background));
        return slide;
    }

    private Color awt(int[] rgb) {
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    private void addTextBox(XSLFSlide slide, Rectangle2D anchor, String text, double fontSize, boolean bold, Color color) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(anchor);
        XSLFTextParagraph paragraph = textBox.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontColor(color);
    }

    private void addWrappedParagraph(XSLFSlide slide, Rectangle2D anchor, String text, double fontSize, Color color) {
        XSLFTextBox textBox = slide.createTextBox();
        textBox.setAnchor(anchor);
        XSLFTextParagraph paragraph = textBox.addNewTextParagraph();
        paragraph.setLineSpacing(130d);
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text == null ? "" : text);
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(fontSize);
        run.setFontColor(color);
    }
}
