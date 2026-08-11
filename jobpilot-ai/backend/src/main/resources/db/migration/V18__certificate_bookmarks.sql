-- 2026-08-11: "성장 기회 추천" 페이지의 자격증 섹션 - 회원이 Q-Net 종목을 찜해두면
-- 나중에 다시 검색하지 않고도 상단에 모아볼 수 있게 한다. Q-Net 카탈로그 자체는 DB에
-- 저장하지 않고(QnetQualificationService가 12시간 캐시로 들고 있음) 그때그때 실시간
-- 조회한 값이라, 여기엔 종목코드(jmcd)와 화면 표시에 필요한 값만 스냅샷으로 저장한다.
CREATE TABLE certificate_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    jmcd VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    qualification_type VARCHAR(100) NULL,
    field VARCHAR(200) NULL,
    sub_field VARCHAR(200) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_certificate_bookmarks_member_jmcd (member_id, jmcd),
    CONSTRAINT fk_certificate_bookmarks_member FOREIGN KEY (member_id) REFERENCES members (id)
);
