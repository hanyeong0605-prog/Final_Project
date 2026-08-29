package com.jobpilot.api.domain.review.entity;

import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** 리뷰 원문 수명주기. 소유권/가상기업/공고 귀속 확인은 서비스에서 추가 검증한다. */
@Entity
@Table(name = "company_reviews")
public class CompanyReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private Long companyId;
    @Column(name = "job_posting_id") private Long jobPostingId;
    @Column(name = "author_member_id") private Long authorMemberId;
    @Column(name = "seed_key", length = 80) private String seedKey;
    @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
    @Column(name = "display_author", nullable = false, length = 100) private String displayAuthor;
    @Column(nullable = false) private int rating;
    @Column(nullable = false, length = 200) private String title;
    @Column(nullable = false, length = 1500) private String pros;
    @Column(nullable = false, length = 1500) private String cons;
    @Column(nullable = false, length = 5000) private String body;
    @Column(nullable = false, length = 20) private String visibility;
    @Column(name = "content_hash", nullable = false, columnDefinition = "CHAR(64)") private String contentHash;
    @Column(name = "analysis_state", nullable = false, length = 20) private String analysisState;
    @Column(name = "analysis_attempts", nullable = false) private int analysisAttempts;
    @Column(name = "next_analysis_at") private LocalDateTime nextAnalysisAt;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected CompanyReview() {}

    public static CompanyReview byMember(Long companyId, Long postingId, Long authorId,
                                          String displayAuthor, int rating, String title,
                                          String pros, String cons, String body) {
        if (companyId == null || authorId == null) throw new IllegalArgumentException("회사와 작성자가 필요합니다.");
        var r = new CompanyReview();
        r.companyId = companyId;
        r.jobPostingId = postingId;
        r.authorMemberId = authorId;
        r.sourceType = "USER";
        r.displayAuthor = text(displayAuthor, 100);
        r.visibility = "PUBLIC";
        r.createdAt = LocalDateTime.now();
        r.replaceContent(rating, title, pros, cons, body);
        return r;
    }

    public void edit(Long memberId, int rating, String title, String pros, String cons, String body) {
        requireOwner(memberId);
        if (!"PUBLIC".equals(visibility)) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "숨김/삭제 리뷰는 수정할 수 없습니다.");
        replaceContent(rating, title, pros, cons, body);
    }

    private void replaceContent(int rating, String title, String pros, String cons, String body) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("별점은 1~5점입니다.");
        // Validate everything before changing the managed entity, including the combined ML input.
        String nextTitle = text(title, 200), nextPros = text(pros, 1500);
        String nextCons = text(cons, 1500), nextBody = text(body, 5000);
        String nextText = text(String.join("\n", nextTitle, nextPros, nextCons, nextBody), 5000);
        String nextHash = SentimentAiClient.contentHash(nextText);
        this.rating = rating;
        this.title = nextTitle;
        this.pros = nextPros;
        this.cons = nextCons;
        this.body = nextBody;
        if (!nextHash.equals(contentHash)) {
            contentHash = nextHash;
            analysisState = "PENDING";
            analysisAttempts = 0;
            nextAnalysisAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    public String analysisText() { return String.join("\n", title, pros, cons, body); }

    public void deleteByMember(Long memberId) {
        requireOwner(memberId);
        visibility = "DELETED";
        nextAnalysisAt = null;
        updatedAt = LocalDateTime.now();
    }

    private void requireOwner(Long memberId) {
        if (memberId == null || !Objects.equals(authorMemberId, memberId))
            throw new org.springframework.security.access.AccessDeniedException("본인 리뷰만 변경할 수 있습니다.");
    }

    /** The worker retains this hash; late results cannot overwrite an edited/deleted review. */
    public boolean completeAnalysis(String expectedHash) {
        if (!"PUBLIC".equals(visibility) || !Objects.equals(contentHash, expectedHash)) return false;
        analysisState = "COMPLETED";
        nextAnalysisAt = null;
        return true;
    }

    public static String text(String value, int limit) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("필수 내용을 입력하세요.");
        String stripped = value.strip();
        if (stripped.codePointCount(0, stripped.length()) > limit)
            throw new IllegalArgumentException("입력 가능한 글자 수를 초과했습니다.");
        return stripped;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public Long getJobPostingId() { return jobPostingId; }
    public Long getAuthorMemberId() { return authorMemberId; }
    public String getDisplayAuthor() { return displayAuthor; }
    public String getSourceType() { return sourceType; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getPros() { return pros; }
    public String getCons() { return cons; }
    public String getBody() { return body; }
    public String getVisibility() { return visibility; }
    public String getContentHash() { return contentHash; }
    public String getAnalysisState() { return analysisState; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
