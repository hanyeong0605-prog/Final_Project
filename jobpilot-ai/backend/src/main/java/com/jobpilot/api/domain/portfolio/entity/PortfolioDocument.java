package com.jobpilot.api.domain.portfolio.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 포트폴리오 생성 기능(GitHub 코드 분석 미리보기의 다음 단계) - 사용자가 고른 구현 설명을
// 근거로 만든 pptx/pdf 산출물을 저장한다. InterviewSessionRecord와 같은 이유로 "그때 만든
// 결과물"이라 생성만 있고 update()가 없다 - 다시 만들고 싶으면 새 레코드를 추가한다.
// 배포 컨테이너(docker-compose.prod.yml)에 backend용 볼륨이 없어 파일을 디스크에 두면
// 재배포마다 사라지므로, pptx/pdf는 LONGBLOB으로 DB에 직접 저장한다.
@Entity
@Table(name = "portfolio_documents")
public class PortfolioDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "repository_full_name", nullable = false)
    private String repositoryFullName;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(nullable = false)
    private String title;

    @Column(name = "narrative_json", nullable = false, columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode narrativeJson;

    @Column(name = "source_analysis_snapshot", nullable = false, columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode sourceAnalysisSnapshot;

    // columnDefinition을 명시하지 않으면 Hibernate가 length 미지정 시 기본값으로 TINYBLOB을
    // 추론해서, 마이그레이션이 만든 실제 LONGBLOB 컬럼과 스키마 검증(validate) 단계에서
    // 충돌한다(SchemaManagementException: wrong column type ... tinyblob vs longblob) -
    // 실제 겪은 문제라 명시적으로 LONGBLOB을 적어둔다.
    @Lob
    @Column(name = "pptx_data", columnDefinition = "LONGBLOB")
    private byte[] pptxData;

    @Lob
    @Column(name = "pdf_data", columnDefinition = "LONGBLOB")
    private byte[] pdfData;

    @Column(name = "narrative_source", nullable = false)
    private String narrativeSource;

    @Column(nullable = false)
    private String template;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PortfolioDocument() {
    }

    public PortfolioDocument(
            Long memberId,
            String repositoryFullName,
            String repositoryUrl,
            String title,
            JsonNode narrativeJson,
            JsonNode sourceAnalysisSnapshot,
            byte[] pptxData,
            byte[] pdfData,
            String narrativeSource,
            String template
    ) {
        this.memberId = memberId;
        this.repositoryFullName = repositoryFullName;
        this.repositoryUrl = repositoryUrl;
        this.title = title;
        this.narrativeJson = narrativeJson;
        this.sourceAnalysisSnapshot = sourceAnalysisSnapshot;
        this.pptxData = pptxData;
        this.pdfData = pdfData;
        this.narrativeSource = narrativeSource;
        this.template = template;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getRepositoryFullName() { return repositoryFullName; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getTitle() { return title; }
    public JsonNode getNarrativeJson() { return narrativeJson; }
    public JsonNode getSourceAnalysisSnapshot() { return sourceAnalysisSnapshot; }
    public byte[] getPptxData() { return pptxData; }
    public byte[] getPdfData() { return pdfData; }
    public String getNarrativeSource() { return narrativeSource; }
    public String getTemplate() { return template; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
