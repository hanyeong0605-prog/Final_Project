param(
    [string]$Mysql = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe',
    [int]$Port = 33379,
    [string]$ExpectedDataDirectory = 'C:/Final_Project/tmp/review-schema-mysql-0829'
)
$ErrorActionPreference = 'Stop'
# This test intentionally requires a disposable, separately initialized MySQL instance.
# Never load application-local.yml or use the project's shared development database.
function Query([string]$Sql, [string]$ExpectedError = '') {
    $result = & $Mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch --skip-column-names --default-character-set=utf8mb4 --execute=$Sql 2>&1
    $code = $LASTEXITCODE
    if ($ExpectedError) {
        if ($code -eq 0 -or "$result" -notmatch $ExpectedError) { throw "Expected MySQL error $ExpectedError; received: $result" }
        return
    }
    if ($code -ne 0) { throw "MySQL verification failed: $result" }
    return $result
}
$actual = ("$(Query 'SELECT @@datadir')" -replace '\\+', '/').TrimEnd('/')
$expected = ($ExpectedDataDirectory -replace '\\+', '/').TrimEnd('/')
if ($actual -ine $expected -or $actual -notlike 'C:/Final_Project/tmp/*') {
    throw "Refusing non-test MySQL data directory: $actual"
}
$database = 'review_schema_' + [Guid]::NewGuid().ToString('N')
Query "CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
$prefix = "USE $database; "
Query ($prefix + @'
CREATE TABLE members (id BIGINT NOT NULL PRIMARY KEY,nickname VARCHAR(80) NOT NULL);
CREATE TABLE employer_accounts (id BIGINT NOT NULL PRIMARY KEY,status VARCHAR(20) NOT NULL);
CREATE TABLE job_postings (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,external_job_id VARCHAR(150) NOT NULL,source_provider VARCHAR(30) NOT NULL,
 source_company_id VARCHAR(150),employer_account_id BIGINT,title VARCHAR(500),company_name VARCHAR(255),description MEDIUMTEXT,source_url VARCHAR(1500) NOT NULL,
 location VARCHAR(255),employment_type VARCHAR(50),experience_type VARCHAR(50),industry_name VARCHAR(255),job_name VARCHAR(1000),salary VARCHAR(255),keywords TEXT,
 is_rolling_deadline BOOLEAN NOT NULL DEFAULT FALSE,status VARCHAR(30) NOT NULL,crawl_status VARCHAR(30) NOT NULL,raw_payload JSON,
 UNIQUE KEY uq_demo_post(source_provider,external_job_id));
INSERT INTO members VALUES (1,'Member 1'), (2,'Member 2');
INSERT INTO job_postings(id,external_job_id,source_provider,title,source_url,status,crawl_status) VALUES
 (10,'P10','TEST','Test 10','https://example.invalid/10','ACTIVE','NOT_REQUESTED'),(20,'P20','TEST','Test 20','https://example.invalid/20','ACTIVE','NOT_REQUESTED');
'@)
$migration = Get-Content -LiteralPath "$PSScriptRoot/../src/main/resources/db/migration/V43__fictional_company_reviews.sql" -Raw
Query ($prefix + $migration)
$communityMigration = Get-Content -LiteralPath "$PSScriptRoot/../src/main/resources/db/migration/V44__community_forum_sentiment.sql" -Raw
Query ($prefix + $communityMigration)
Query ($prefix + @'
INSERT INTO review_companies(id, seed_key, name, description) VALUES
 (1,'DEMO-1','Company 1 (fictional)','Demo'), (2,'DEMO-2','Company 2 (fictional)','Demo');
INSERT INTO review_company_postings VALUES (10,1), (20,2);
INSERT INTO company_reviews(company_id,job_posting_id,author_member_id,source_type,display_author,rating,title,pros,cons,body,content_hash)
VALUES (1,10,1,'USER','Demo user',4,'Title','Pros','Cons','Body',REPEAT('a',64));
'@)
$insert = "INSERT INTO company_reviews(company_id,job_posting_id,author_member_id,source_type,display_author,rating,title,pros,cons,body,content_hash) VALUES "
Query ($prefix + $insert + "(1,20,2,'USER','Demo',4,'T','P','C','B',REPEAT('a',64))") '1452'
Query ($prefix + $insert + "(1,10,2,'USER','Demo',6,'T','P','C','B',REPEAT('a',64))") '3819'
Query ($prefix + $insert + "(1,10,NULL,'USER','Demo',4,'T','P','C','B',REPEAT('a',64))") '3819'
Query ($prefix + $insert + "(1,10,1,'USER','Demo',4,'T','P','C','B',REPEAT('a',64))") '1062'
Query ($prefix + "UPDATE company_reviews SET visibility='HIDDEN' WHERE id=1")
Query ($prefix + $insert + "(1,10,1,'USER','Demo',4,'T','P','C','B',REPEAT('a',64))") '1062'
Query ($prefix + "UPDATE company_reviews SET visibility='DELETED' WHERE id=1")
Query ($prefix + $insert + "(1,NULL,1,'USER','Demo',4,'T','P','C','B',REPEAT('a',64))")
Query ($prefix + @'
INSERT INTO company_reviews(company_id,seed_key,source_type,display_author,rating,title,pros,cons,body,content_hash)
VALUES (2,'DEMO-R-1','SYNTHETIC_DEMO','Demo reviewer 1',3,'T','P','C','B',REPEAT('a',64)),
       (2,'DEMO-R-2','SYNTHETIC_DEMO','Demo reviewer 2',4,'T','P','C','B',REPEAT('a',64));
'@)
Write-Output 'PASS: migration, cross-company FK, rating, provenance, duplicate/hidden author, deletion/recreation, company-only review, synthetic authors.'
Write-Output "Temporary test database retained in isolated instance: $database"
