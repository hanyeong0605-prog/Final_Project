package com.jobpilot.api.domain.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.portfolio.dto.PortfolioDocumentSummaryResponse;
import com.jobpilot.api.domain.portfolio.dto.PortfolioGenerateRequest;
import com.jobpilot.api.domain.portfolio.entity.PortfolioDocument;
import com.jobpilot.api.domain.portfolio.repository.PortfolioDocumentRepository;
import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.NarrativeResult;
import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioNarrative;
import com.jobpilot.api.domain.portfolio.service.PortfolioNarrativeGeminiClient.PortfolioSlide;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse.ImplementationStory;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

// GitHub 코드 분석 미리보기(RepositoryAnalysisPage)에서 사용자가 고른 구현들을 근거로
// pptx/pdf 포트폴리오를 만들고 저장한다. 파이프라인: Gemini 슬라이드 구조 생성(실패 시
// 정적 폴백, fail-open) -> PptxRenderer/PdfRenderer로 렌더링 -> DB 저장.
@Service
@Transactional
public class PortfolioDocumentService {
    private final PortfolioDocumentRepository repository;
    private final PortfolioNarrativeGeminiClient geminiClient;
    private final PptxRenderer pptxRenderer;
    private final PdfRenderer pdfRenderer;
    private final ObjectMapper objectMapper;

    public PortfolioDocumentService(
            PortfolioDocumentRepository repository,
            PortfolioNarrativeGeminiClient geminiClient,
            PptxRenderer pptxRenderer,
            PdfRenderer pdfRenderer,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.geminiClient = geminiClient;
        this.pptxRenderer = pptxRenderer;
        this.pdfRenderer = pdfRenderer;
        this.objectMapper = objectMapper;
    }

    public PortfolioDocumentSummaryResponse generate(Long memberId, PortfolioGenerateRequest request) {
        GitHubProjectAnalysisResponse analysis = request.analysis();
        List<ImplementationStory> selected = analysis.implementations().stream()
                .filter(implementation -> request.selectedImplementationIds().contains(implementation.id()))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("포트폴리오에 포함할 구현을 하나 이상 선택해 주세요.");
        }
        PortfolioTemplate template = PortfolioTemplate.fromCode(request.template());

        NarrativeResult result = geminiClient.generate(analysis, selected);
        PortfolioNarrative narrative = result.narrative().orElseGet(() -> staticFallback(analysis, selected));
        String narrativeSource = result.narrative().isPresent() ? "GEMINI" : "STATIC_FALLBACK";

        byte[] pptxData = pptxRenderer.render(analysis, narrative, template);
        byte[] pdfData = pdfRenderer.render(analysis, narrative, template);

        PortfolioDocument saved = repository.save(new PortfolioDocument(
                memberId,
                analysis.repository().fullName(),
                analysis.repository().htmlUrl(),
                narrative.title(),
                objectMapper.valueToTree(narrative),
                objectMapper.valueToTree(analysis),
                pptxData,
                pdfData,
                narrativeSource,
                template.name()
        ));
        return summary(saved);
    }

    public List<PortfolioDocumentSummaryResponse> list(Long memberId) {
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId).stream().map(this::summary).toList();
    }

    public byte[] pptx(Long memberId, Long id) {
        return owned(memberId, id).getPptxData();
    }

    public byte[] pdf(Long memberId, Long id) {
        return owned(memberId, id).getPdfData();
    }

    public String title(Long memberId, Long id) {
        return owned(memberId, id).getTitle();
    }

    private PortfolioDocument owned(Long memberId, Long id) {
        return repository.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("포트폴리오 문서를 찾을 수 없습니다."));
    }

    // Gemini가 비활성/실패 상태여도 포트폴리오 자체는 만들어져야 하므로(fail-open), 선택된
    // 구현의 정적 분석 데이터만으로 최소한의 슬라이드 구조를 만든다. path뿐 아니라 symbol도
    // 그대로 들고 가서(EvidenceRef), 렌더러가 "그 파일의 아무 메서드"가 아니라 이 구현이
    // 실제로 가리키는 메서드를 골라 보여줄 수 있게 한다(2026-08-14).
    private PortfolioNarrative staticFallback(GitHubProjectAnalysisResponse analysis, List<ImplementationStory> selected) {
        String title = analysis.repository().fullName();
        String subtitle = analysis.projectProfile() != null ? analysis.projectProfile().summary() : analysis.overview();
        List<PortfolioSlide> slides = new ArrayList<>();
        for (ImplementationStory implementation : selected) {
            List<String> bullets = new ArrayList<>();
            bullets.add(implementation.description());
            bullets.add(implementation.mechanism());
            List<PortfolioNarrativeGeminiClient.EvidenceRef> evidence = implementation.evidence().stream()
                    .map(item -> new PortfolioNarrativeGeminiClient.EvidenceRef(item.path(), item.symbol()))
                    .distinct().toList();
            slides.add(new PortfolioSlide(implementation.title(), bullets, "", evidence));
        }
        return new PortfolioNarrative(title, subtitle == null ? "" : subtitle, slides);
    }

    private PortfolioDocumentSummaryResponse summary(PortfolioDocument document) {
        return new PortfolioDocumentSummaryResponse(
                document.getId(),
                document.getRepositoryFullName(),
                document.getRepositoryUrl(),
                document.getTitle(),
                document.getNarrativeSource(),
                document.getTemplate(),
                document.getPptxData() != null,
                document.getPdfData() != null,
                document.getCreatedAt()
        );
    }
}
