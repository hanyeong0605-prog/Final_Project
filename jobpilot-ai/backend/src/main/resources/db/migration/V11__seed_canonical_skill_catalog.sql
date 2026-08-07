-- The member-facing catalog starts with common technologies that occur across
-- the imported job postings. Raw requirement text remains outside this catalog.
INSERT INTO skills (name, category, catalog_status, display_order, normalized_name) VALUES
    ('Java', 'LANGUAGE', 'CANONICAL', 10, 'java'),
    ('Kotlin', 'LANGUAGE', 'CANONICAL', 10, 'kotlin'),
    ('Python', 'LANGUAGE', 'CANONICAL', 10, 'python'),
    ('JavaScript', 'LANGUAGE', 'CANONICAL', 10, 'javascript'),
    ('TypeScript', 'LANGUAGE', 'CANONICAL', 10, 'typescript'),
    ('Go', 'LANGUAGE', 'CANONICAL', 10, 'go'),
    ('C', 'LANGUAGE', 'CANONICAL', 10, 'c'),
    ('C++', 'LANGUAGE', 'CANONICAL', 10, 'cplusplus'),
    ('C#', 'LANGUAGE', 'CANONICAL', 10, 'csharp'),
    ('PHP', 'LANGUAGE', 'CANONICAL', 10, 'php'),
    ('Ruby', 'LANGUAGE', 'CANONICAL', 10, 'ruby'),
    ('Swift', 'LANGUAGE', 'CANONICAL', 10, 'swift'),
    ('Dart', 'LANGUAGE', 'CANONICAL', 10, 'dart'),
    ('Scala', 'LANGUAGE', 'CANONICAL', 10, 'scala'),
    ('R', 'LANGUAGE', 'CANONICAL', 10, 'r'),
    ('SQL', 'LANGUAGE', 'CANONICAL', 10, 'sql'),
    ('Spring Framework', 'BACKEND', 'CANONICAL', 20, 'springframework'),
    ('Spring Boot', 'BACKEND', 'CANONICAL', 20, 'springboot'),
    ('Spring Security', 'BACKEND', 'CANONICAL', 20, 'springsecurity'),
    ('Spring Data JPA', 'BACKEND', 'CANONICAL', 20, 'springdatajpa'),
    ('Spring WebFlux', 'BACKEND', 'CANONICAL', 20, 'springwebflux'),
    ('Spring Batch', 'BACKEND', 'CANONICAL', 20, 'springbatch'),
    ('JPA', 'BACKEND', 'CANONICAL', 20, 'jpa'),
    ('Hibernate', 'BACKEND', 'CANONICAL', 20, 'hibernate'),
    ('MyBatis', 'BACKEND', 'CANONICAL', 20, 'mybatis'),
    ('QueryDSL', 'BACKEND', 'CANONICAL', 20, 'querydsl'),
    ('Node.js', 'BACKEND', 'CANONICAL', 20, 'nodejs'),
    ('Express.js', 'BACKEND', 'CANONICAL', 20, 'expressjs'),
    ('NestJS', 'BACKEND', 'CANONICAL', 20, 'nestjs'),
    ('Django', 'BACKEND', 'CANONICAL', 20, 'django'),
    ('FastAPI', 'BACKEND', 'CANONICAL', 20, 'fastapi'),
    ('Flask', 'BACKEND', 'CANONICAL', 20, 'flask'),
    ('.NET', 'BACKEND', 'CANONICAL', 20, 'dotnet'),
    ('ASP.NET', 'BACKEND', 'CANONICAL', 20, 'aspnet'),
    ('REST API', 'BACKEND', 'CANONICAL', 20, 'restapi'),
    ('GraphQL', 'BACKEND', 'CANONICAL', 20, 'graphql'),
    ('gRPC', 'BACKEND', 'CANONICAL', 20, 'grpc'),
    ('React', 'FRONTEND', 'CANONICAL', 30, 'react'),
    ('Next.js', 'FRONTEND', 'CANONICAL', 30, 'nextjs'),
    ('Vue.js', 'FRONTEND', 'CANONICAL', 30, 'vuejs'),
    ('Nuxt.js', 'FRONTEND', 'CANONICAL', 30, 'nuxtjs'),
    ('Angular', 'FRONTEND', 'CANONICAL', 30, 'angular'),
    ('HTML', 'FRONTEND', 'CANONICAL', 30, 'html'),
    ('CSS', 'FRONTEND', 'CANONICAL', 30, 'css'),
    ('Redux', 'FRONTEND', 'CANONICAL', 30, 'redux'),
    ('MySQL', 'DATABASE', 'CANONICAL', 40, 'mysql'),
    ('PostgreSQL', 'DATABASE', 'CANONICAL', 40, 'postgresql'),
    ('MariaDB', 'DATABASE', 'CANONICAL', 40, 'mariadb'),
    ('Oracle', 'DATABASE', 'CANONICAL', 40, 'oracle'),
    ('MongoDB', 'DATABASE', 'CANONICAL', 40, 'mongodb'),
    ('Redis', 'DATABASE', 'CANONICAL', 40, 'redis'),
    ('Elasticsearch', 'DATABASE', 'CANONICAL', 40, 'elasticsearch'),
    ('Kafka', 'DATABASE', 'CANONICAL', 40, 'kafka'),
    ('RabbitMQ', 'DATABASE', 'CANONICAL', 40, 'rabbitmq'),
    ('AWS', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'aws'),
    ('GCP', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'gcp'),
    ('Azure', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'azure'),
    ('Docker', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'docker'),
    ('Kubernetes', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'kubernetes'),
    ('Linux', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'linux'),
    ('Nginx', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'nginx'),
    ('Terraform', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'terraform'),
    ('Jenkins', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'jenkins'),
    ('GitHub Actions', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'githubactions'),
    ('GitLab CI', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'gitlabci'),
    ('CI/CD', 'CLOUD_DEVOPS', 'CANONICAL', 50, 'cicd'),
    ('TensorFlow', 'AI_DATA', 'CANONICAL', 60, 'tensorflow'),
    ('PyTorch', 'AI_DATA', 'CANONICAL', 60, 'pytorch'),
    ('Pandas', 'AI_DATA', 'CANONICAL', 60, 'pandas'),
    ('NumPy', 'AI_DATA', 'CANONICAL', 60, 'numpy'),
    ('scikit-learn', 'AI_DATA', 'CANONICAL', 60, 'scikitlearn'),
    ('OpenAI API', 'AI_DATA', 'CANONICAL', 60, 'openaiapi'),
    ('LLM', 'AI_DATA', 'CANONICAL', 60, 'llm'),
    ('RAG', 'AI_DATA', 'CANONICAL', 60, 'rag'),
    ('LangChain', 'AI_DATA', 'CANONICAL', 60, 'langchain'),
    ('Android', 'MOBILE', 'CANONICAL', 70, 'android'),
    ('iOS', 'MOBILE', 'CANONICAL', 70, 'ios'),
    ('Flutter', 'MOBILE', 'CANONICAL', 70, 'flutter'),
    ('React Native', 'MOBILE', 'CANONICAL', 70, 'reactnative'),
    ('JUnit', 'TEST', 'CANONICAL', 80, 'junit'),
    ('Mockito', 'TEST', 'CANONICAL', 80, 'mockito'),
    ('Pytest', 'TEST', 'CANONICAL', 80, 'pytest'),
    ('Selenium', 'TEST', 'CANONICAL', 80, 'selenium'),
    ('Playwright', 'TEST', 'CANONICAL', 80, 'playwright'),
    ('Git', 'TOOL', 'CANONICAL', 90, 'git'),
    ('GitHub', 'TOOL', 'CANONICAL', 90, 'github'),
    ('GitLab', 'TOOL', 'CANONICAL', 90, 'gitlab'),
    ('Jira', 'TOOL', 'CANONICAL', 90, 'jira'),
    ('Figma', 'TOOL', 'CANONICAL', 90, 'figma'),
    ('Notion', 'TOOL', 'CANONICAL', 90, 'notion'),
    ('Postman', 'TOOL', 'CANONICAL', 90, 'postman')
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    catalog_status = VALUES(catalog_status),
    display_order = VALUES(display_order),
    normalized_name = VALUES(normalized_name);

UPDATE skills child
JOIN skills parent ON parent.name = 'Spring Framework'
SET child.parent_skill_id = parent.id
WHERE child.name IN ('Spring Boot', 'Spring Security', 'Spring Data JPA', 'Spring WebFlux', 'Spring Batch');

UPDATE skills child
JOIN skills parent ON parent.name = 'React'
SET child.parent_skill_id = parent.id
WHERE child.name IN ('Next.js', 'React Native');

INSERT INTO skill_aliases (skill_id, alias, normalized_alias)
SELECT s.id, a.alias, a.normalized_alias
FROM skills s
JOIN (
    SELECT 'Spring Framework' AS canonical_name, 'spring' AS alias, 'spring' AS normalized_alias
    UNION ALL SELECT 'Spring Boot', 'springboot', 'springboot'
    UNION ALL SELECT 'Spring Security', 'springsecurity', 'springsecurity'
    UNION ALL SELECT 'Spring Data JPA', 'springdatajpa', 'springdatajpa'
    UNION ALL SELECT 'Spring WebFlux', 'springwebflux', 'springwebflux'
    UNION ALL SELECT 'JavaScript', 'js', 'js'
    UNION ALL SELECT 'TypeScript', 'ts', 'ts'
    UNION ALL SELECT 'Node.js', 'node', 'node'
    UNION ALL SELECT 'Node.js', 'nodejs', 'nodejs'
    UNION ALL SELECT 'React', 'reactjs', 'reactjs'
    UNION ALL SELECT 'Next.js', 'nextjs', 'nextjs'
    UNION ALL SELECT 'Vue.js', 'vue', 'vue'
    UNION ALL SELECT 'Vue.js', 'vuejs', 'vuejs'
    UNION ALL SELECT 'Nuxt.js', 'nuxtjs', 'nuxtjs'
    UNION ALL SELECT 'PostgreSQL', 'postgres', 'postgres'
    UNION ALL SELECT 'Kubernetes', 'k8s', 'k8s'
    UNION ALL SELECT 'C#', 'csharp', 'csharp'
    UNION ALL SELECT 'C++', 'cpp', 'cpp'
    UNION ALL SELECT 'C++', 'cplusplus', 'cplusplus'
    UNION ALL SELECT '.NET', 'dotnet', 'dotnet'
    UNION ALL SELECT 'scikit-learn', 'sklearn', 'sklearn'
    UNION ALL SELECT 'scikit-learn', 'scikitlearn', 'scikitlearn'
    UNION ALL SELECT 'OpenAI API', 'openai', 'openai'
    UNION ALL SELECT 'OpenAI API', 'openaiapi', 'openaiapi'
    UNION ALL SELECT 'GitHub Actions', 'githubactions', 'githubactions'
    UNION ALL SELECT 'CI/CD', 'cicd', 'cicd'
) a ON a.canonical_name = s.name
ON DUPLICATE KEY UPDATE
    skill_id = VALUES(skill_id),
    normalized_alias = VALUES(normalized_alias);

UPDATE job_skills js
JOIN skills s ON s.id = js.skill_id
SET js.canonical_skill_id = s.id
WHERE s.catalog_status = 'CANONICAL';
