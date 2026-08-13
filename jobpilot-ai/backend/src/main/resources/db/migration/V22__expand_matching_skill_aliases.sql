-- Korean and commonly abbreviated names used in job requirement text.
-- INSERT IGNORE keeps the migration safe on environments where an alias already exists.
INSERT IGNORE INTO skill_aliases (skill_id, alias, normalized_alias)
SELECT id, '리액트', '리액트' FROM skills WHERE name = 'React'
UNION ALL SELECT id, '자바스크립트', '자바스크립트' FROM skills WHERE name = 'JavaScript'
UNION ALL SELECT id, '타입스크립트', '타입스크립트' FROM skills WHERE name = 'TypeScript'
UNION ALL SELECT id, '스프링부트', '스프링부트' FROM skills WHERE name = 'Spring Boot'
UNION ALL SELECT id, '스프링', '스프링' FROM skills WHERE name = 'Spring Framework'
UNION ALL SELECT id, '파이썬', '파이썬' FROM skills WHERE name = 'Python'
UNION ALL SELECT id, '자바', '자바' FROM skills WHERE name = 'Java'
UNION ALL SELECT id, '도커', '도커' FROM skills WHERE name = 'Docker'
UNION ALL SELECT id, '쿠버네티스', '쿠버네티스' FROM skills WHERE name = 'Kubernetes'
UNION ALL SELECT id, '깃허브액션', '깃허브액션' FROM skills WHERE name = 'GitHub Actions';
