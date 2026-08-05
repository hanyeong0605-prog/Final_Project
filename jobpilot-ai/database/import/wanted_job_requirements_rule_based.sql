-- Rule-based Wanted requirement seed. Run this AFTER:
--   1) Flyway V7__wanted_job_posting_sync.sql
--   2) wanted_jobs_merge_20260804_normal.sql
--
-- This is intentionally API-free. It extracts named technical skills from
-- job_postings.title / description / keywords, then populates job_requirements
-- and job_skills. Results are marked RULE_BASED_WANTED + UNVERIFIED so they can
-- later be reviewed or replaced by a higher-quality AI extraction.

USE jobpilot;
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO skills (name, category) VALUES
  ('Java', 'TECHNICAL'),
  ('Kotlin', 'TECHNICAL'),
  ('Python', 'TECHNICAL'),
  ('JavaScript', 'TECHNICAL'),
  ('TypeScript', 'TECHNICAL'),
  ('C', 'TECHNICAL'),
  ('C++', 'TECHNICAL'),
  ('C#', 'TECHNICAL'),
  ('Go', 'TECHNICAL'),
  ('PHP', 'TECHNICAL'),
  ('Ruby', 'TECHNICAL'),
  ('Scala', 'TECHNICAL'),
  ('Swift', 'TECHNICAL'),
  ('Dart', 'TECHNICAL'),
  ('SQL', 'TECHNICAL'),
  ('Spring Framework', 'TECHNICAL'),
  ('Spring Boot', 'TECHNICAL'),
  ('Spring WebFlux', 'TECHNICAL'),
  ('JPA', 'TECHNICAL'),
  ('Hibernate', 'TECHNICAL'),
  ('MyBatis', 'TECHNICAL'),
  ('Node.js', 'TECHNICAL'),
  ('NestJS', 'TECHNICAL'),
  ('Express.js', 'TECHNICAL'),
  ('React', 'TECHNICAL'),
  ('Next.js', 'TECHNICAL'),
  ('Vue.js', 'TECHNICAL'),
  ('Angular', 'TECHNICAL'),
  ('React Native', 'TECHNICAL'),
  ('Flutter', 'TECHNICAL'),
  ('Android', 'TECHNICAL'),
  ('iOS', 'TECHNICAL'),
  ('MySQL', 'TECHNICAL'),
  ('PostgreSQL', 'TECHNICAL'),
  ('MariaDB', 'TECHNICAL'),
  ('Oracle Database', 'TECHNICAL'),
  ('MongoDB', 'TECHNICAL'),
  ('Redis', 'TECHNICAL'),
  ('Elasticsearch', 'TECHNICAL'),
  ('DynamoDB', 'TECHNICAL'),
  ('AWS', 'TECHNICAL'),
  ('GCP', 'TECHNICAL'),
  ('Azure', 'TECHNICAL'),
  ('Docker', 'TECHNICAL'),
  ('Kubernetes', 'TECHNICAL'),
  ('Terraform', 'TECHNICAL'),
  ('Jenkins', 'TECHNICAL'),
  ('Nginx', 'TECHNICAL'),
  ('Linux', 'TECHNICAL'),
  ('Kafka', 'TECHNICAL'),
  ('RabbitMQ', 'TECHNICAL'),
  ('GraphQL', 'TECHNICAL'),
  ('gRPC', 'TECHNICAL'),
  ('REST API', 'TECHNICAL'),
  ('Git', 'TECHNICAL'),
  ('GitHub Actions', 'TECHNICAL'),
  ('Jira', 'TECHNICAL'),
  ('Confluence', 'TECHNICAL'),
  ('TensorFlow', 'TECHNICAL'),
  ('PyTorch', 'TECHNICAL'),
  ('LangChain', 'TECHNICAL'),
  ('LLM', 'TECHNICAL'),
  ('OpenAI API', 'TECHNICAL')
ON DUPLICATE KEY UPDATE category = VALUES(category);

CREATE TEMPORARY TABLE wanted_skill_rules (
  skill_name VARCHAR(100) NOT NULL PRIMARY KEY,
  skill_pattern VARCHAR(500) NOT NULL
) ENGINE=MEMORY;

INSERT INTO wanted_skill_rules (skill_name, skill_pattern) VALUES
  ('Java', 'Java([^A-Za-z]|$)'),
  ('Kotlin', 'Kotlin'),
  ('Python', 'Python'),
  ('JavaScript', 'JavaScript'),
  ('TypeScript', 'TypeScript'),
  ('C++', 'C\\+\\+'),
  ('C#', 'C#'),
  ('Go', '(^|[^A-Za-z])Go([^A-Za-z]|$)|Golang'),
  ('PHP', 'PHP'),
  ('Ruby', 'Ruby'),
  ('Scala', 'Scala'),
  ('Swift', 'Swift'),
  ('Dart', 'Dart'),
  ('SQL', 'SQL'),
  ('Spring Framework', 'Spring'),
  ('Spring Boot', 'Spring[[:space:]]*Boot|SpringBoot'),
  ('Spring WebFlux', 'Spring[[:space:]]*WebFlux|WebFlux'),
  ('JPA', 'JPA'),
  ('Hibernate', 'Hibernate'),
  ('MyBatis', 'MyBatis'),
  ('Node.js', 'Node\\.?js|NodeJS'),
  ('NestJS', 'NestJS|Nest\\.js'),
  ('Express.js', 'Express\\.js|ExpressJS'),
  ('React', 'React'),
  ('Next.js', 'Next\\.js|NextJS'),
  ('Vue.js', 'Vue\\.js|VueJS'),
  ('Angular', 'Angular'),
  ('React Native', 'React[[:space:]]*Native'),
  ('Flutter', 'Flutter'),
  ('Android', 'Android'),
  ('iOS', '(^|[^A-Za-z])iOS([^A-Za-z]|$)'),
  ('MySQL', 'MySQL'),
  ('PostgreSQL', 'PostgreSQL|Postgres'),
  ('MariaDB', 'MariaDB'),
  ('Oracle Database', 'Oracle'),
  ('MongoDB', 'MongoDB|Mongo'),
  ('Redis', 'Redis'),
  ('Elasticsearch', 'Elasticsearch|Elastic[[:space:]]*Search'),
  ('DynamoDB', 'DynamoDB'),
  ('AWS', 'AWS|Amazon[[:space:]]*Web[[:space:]]*Services'),
  ('GCP', 'GCP|Google[[:space:]]*Cloud'),
  ('Azure', 'Azure'),
  ('Docker', 'Docker'),
  ('Kubernetes', 'Kubernetes|K8s'),
  ('Terraform', 'Terraform'),
  ('Jenkins', 'Jenkins'),
  ('Nginx', 'Nginx'),
  ('Linux', 'Linux'),
  ('Kafka', 'Kafka'),
  ('RabbitMQ', 'RabbitMQ'),
  ('GraphQL', 'GraphQL'),
  ('gRPC', 'gRPC'),
  ('REST API', 'REST[[:space:]]*API|RESTful'),
  ('Git', 'Git|Github|GitHub'),
  ('GitHub Actions', 'GitHub[[:space:]]*Actions'),
  ('Jira', 'Jira'),
  ('Confluence', 'Confluence'),
  ('TensorFlow', 'TensorFlow'),
  ('PyTorch', 'PyTorch'),
  ('LangChain', 'LangChain'),
  ('LLM', 'LLM|Large[[:space:]]*Language[[:space:]]*Model'),
  ('OpenAI API', 'OpenAI|ChatGPT');

-- Preserve postings that were already manually or AI-extracted. For all other
-- Wanted postings, add every matched technical skill and the imported experience range.
INSERT INTO job_requirements (
  job_posting_id, type, content, source_excerpt, importance, extraction_source, verification_status
)
SELECT
  posting.id,
  'SKILL',
  rule.skill_name,
  CONCAT('Rule-based keyword match: ', rule.skill_name),
  'REQUIRED',
  'RULE_BASED_WANTED',
  'UNVERIFIED'
FROM job_postings posting
JOIN wanted_skill_rules rule
  ON REGEXP_LIKE(CONCAT_WS('\n', posting.title, posting.description, posting.keywords), rule.skill_pattern, 'i')
WHERE posting.source_provider = 'WANTED'
  AND NOT EXISTS (
    SELECT 1
    FROM job_requirements existing_requirement
    WHERE existing_requirement.job_posting_id = posting.id
  )
UNION ALL
SELECT
  posting.id,
  'EXPERIENCE',
  posting.experience_type,
  CONCAT('Wanted experience_type: ', posting.experience_type),
  'REQUIRED',
  'RULE_BASED_WANTED',
  'UNVERIFIED'
FROM job_postings posting
WHERE posting.source_provider = 'WANTED'
  AND posting.experience_type IS NOT NULL
  AND TRIM(posting.experience_type) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM job_requirements existing_requirement
    WHERE existing_requirement.job_posting_id = posting.id
  );

-- Only SKILL requirements become matchable job_skills rows.
INSERT INTO job_skills (job_posting_id, skill_id, requirement_type, source_excerpt)
SELECT
  requirement.job_posting_id,
  skill.id,
  requirement.importance,
  requirement.source_excerpt
FROM job_requirements requirement
JOIN skills skill ON skill.name = requirement.content
WHERE requirement.type = 'SKILL'
  AND requirement.extraction_source = 'RULE_BASED_WANTED'
ON DUPLICATE KEY UPDATE source_excerpt = VALUES(source_excerpt);

DROP TEMPORARY TABLE wanted_skill_rules;
COMMIT;
