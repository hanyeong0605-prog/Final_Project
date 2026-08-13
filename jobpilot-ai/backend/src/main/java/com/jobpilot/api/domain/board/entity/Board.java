package com.jobpilot.api.domain.board.entity;

/*import com.fasterxml.jackson.annotation.JsonIgnore;
import com.jobpilot.api.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "BOARD")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class) // 1. Auditing 기능 활성화 필수!
public class Board {} /* {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id")
    private Board board;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private int commentCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    // 2. 수동 초기화 제거하고 JPA Auditing 어노테이션 적용
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 3. 수정 시간 컬럼 추가
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    /*@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BoardPlace> places = new ArrayList<>();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<BoardComment> comments = new ArrayList<>();

    /*@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BoardLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BoardView> views = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private BoardCategory category;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BoardImage> images = new ArrayList<>();


    /* 🔗 연관관계 편의 메서드
    public void addPlace(BoardPlace boardPlace) {
        this.places.add(boardPlace);
        boardPlace.setBoard(this);
    }

    // 🛠 비즈니스 로직
    public void update(String title, String content, boolean isPublic) {
        this.title = title;
        this.content = content;
        this.isPublic = isPublic;
        this.modifiedAt = LocalDateTime.now(); // 수정 시간 업데이트
    }

    // 좋아요 로직
    public void increaseLike() { this.likeCount++; }
    public void decreaseLike() { if (this.likeCount > 0) this.likeCount--; }

    // 댓글 로직
    public void increaseComment() { this.commentCount++; }
    public void decreaseComment() { if (this.commentCount > 0) this.commentCount--; }

    // 💡 조회수 증가 로직
    public void increaseViewCount() {
        this.viewCount++;
    }
}

*/

// 백엔드는 프론트 화면 만들어가면서 다  수정하기....