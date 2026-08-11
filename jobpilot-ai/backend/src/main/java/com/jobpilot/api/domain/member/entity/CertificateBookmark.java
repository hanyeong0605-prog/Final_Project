package com.jobpilot.api.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 2026-08-11: 회원이 찜해 둔 Q-Net 자격 종목. 보유 자격증({@link Certificate}, 실제로
 * 취득해서 등록한 것)과는 별개 - 아직 안 땄지만 "성장 기회 추천" 페이지에서 다시 보고
 * 싶어서 찜해 둔 종목이다. Q-Net 카탈로그 자체는 저장하지 않으므로 화면에 바로 보여줄
 * 만큼의 정보(이름/분류)만 스냅샷으로 들고 있는다.
 */
@Entity
@Table(name = "certificate_bookmarks")
public class CertificateBookmark {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(nullable = false) private String jmcd;
    @Column(nullable = false) private String name;
    @Column(name = "qualification_type") private String qualificationType;
    private String field;
    @Column(name = "sub_field") private String subField;

    protected CertificateBookmark() {}

    public CertificateBookmark(Long memberId, String jmcd, String name, String qualificationType, String field, String subField) {
        this.memberId = memberId;
        this.jmcd = jmcd;
        this.name = name;
        this.qualificationType = qualificationType;
        this.field = field;
        this.subField = subField;
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public String getJmcd() { return jmcd; }
    public String getName() { return name; }
    public String getQualificationType() { return qualificationType; }
    public String getField() { return field; }
    public String getSubField() { return subField; }
}
