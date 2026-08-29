package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Installs the versioned fictional portfolio dataset in one transaction.
 * Existing IDs are retained so bookmarks/events remain valid, but every V1 synthetic review and
 * low-quality posting field is replaced. Crawled, employer-created, and member-authored rows are
 * outside every mutation predicate.
 */
@Component
@ConditionalOnProperty(name="app.review-demo.seed-enabled",havingValue="true")
public class FictionalDemoSeeder implements ApplicationRunner {
    private static final String DATASET="FICTIONAL_RECRUITING";
    private final JdbcTemplate jdbc; private final String publicUrl; private final TransactionTemplate tx; private final Long demoEmployerId;

    public FictionalDemoSeeder(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
            @Value("${app.public-base-url:https://job-a-dream.site}") String publicUrl,
            @Value("${app.review-demo.employer-id:0}") Long demoEmployerId) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.publicUrl=publicUrl.replaceAll("/$","");
        this.demoEmployerId=demoEmployerId>0?demoEmployerId:null;
    }
    @Override public void run(ApplicationArguments args){seed();}
    public void seed(){tx.executeWithoutResult(ignored->seedInTransaction());}

    private void seedInTransaction() {
        Integer installed=jdbc.query("SELECT dataset_version FROM portfolio_demo_dataset_versions WHERE dataset_name=?",
            rs->rs.next()?rs.getInt(1):null,DATASET);
        if(installed!=null&&installed==FictionalDemoDataset.VERSION&&validCounts()) return;

        removeOldSyntheticReviews();
        for(int i=0;i<100;i++) installCompany(i);
        linkDemoEmployerIfConfigured();
        if(!validCounts()) throw new IllegalStateException("fictional dataset cardinality mismatch");
        jdbc.update("""
            INSERT INTO portfolio_demo_dataset_versions(dataset_name,dataset_version,description)
            VALUES (?,?,?) ON DUPLICATE KEY UPDATE dataset_version=VALUES(dataset_version),installed_at=CURRENT_TIMESTAMP,description=VALUES(description)
            """,DATASET,FictionalDemoDataset.VERSION,"브랜드·근무조건·공고·리뷰 표현을 분산한 가상회사 100곳과 리뷰 500건");
    }

    private void removeOldSyntheticReviews() {
        String scope="SELECT id FROM company_reviews WHERE source_type='SYNTHETIC_DEMO'";
        jdbc.update("DELETE FROM company_review_moderation_events WHERE review_id IN ("+scope+")");
        jdbc.update("DELETE FROM company_review_reports WHERE review_id IN ("+scope+")");
        jdbc.update("DELETE FROM company_review_likes WHERE review_id IN ("+scope+")");
        jdbc.update("DELETE FROM company_review_analyses WHERE review_id IN ("+scope+")");
        jdbc.update("DELETE FROM company_reviews WHERE source_type='SYNTHETIC_DEMO'");
    }

    private void installCompany(int index) {
        int number=index+1; var sector=FictionalDemoDataset.SECTORS.get(index/10); var role=FictionalDemoDataset.ROLES.get(index%10);
        String name=FictionalDemoDataset.NAMES.get(index)+" (가상기업)";
        String companyKey="DEMO-COMPANY-%03d".formatted(number), postingKey="DEMO-JOB-%03d".formatted(number);
        String companyDescription=sector.product()+"을(를) 만드는 포트폴리오용 가상기업입니다. 실제 기업 및 채용과 무관합니다.";
        jdbc.update("""
            INSERT INTO review_companies(seed_key,name,source_type,description,industry,location,reviews_enabled)
            VALUES (?,?,'FICTIONAL_DEMO',?,?,?,TRUE)
            ON DUPLICATE KEY UPDATE name=VALUES(name),description=VALUES(description),industry=VALUES(industry),location=VALUES(location),reviews_enabled=TRUE
            """,companyKey,name,companyDescription,sector.industry(),sector.location());
        long companyId=jdbc.queryForObject("SELECT id FROM review_companies WHERE seed_key=? AND source_type='FICTIONAL_DEMO'",Long.class,companyKey);
        String description=FictionalDemoDataset.description(index,name,sector,role), skills=String.join(",",role.skills());
        LocalDateTime published=LocalDateTime.now().minusDays(3L+(index%21)),deadline=LocalDateTime.now().plusDays(25L+(index%45));
        String raw="{\"fictional\":true,\"datasetVersion\":2,\"notice\":\"포트폴리오용 완전 창작 공고\"}";
        jdbc.update("""
            INSERT INTO job_postings(external_job_id,source_provider,source_company_id,title,company_name,description,source_url,
              location,employment_type,experience_type,is_entry_level,industry_name,job_mid_name,job_name,salary,keywords,
              published_at,deadline_at,is_rolling_deadline,status,fetched_at,source_updated_at,crawl_status,crawled_at,raw_payload)
            VALUES (?,'FICTIONAL_DEMO',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FALSE,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'COMPLETED',CURRENT_TIMESTAMP,CAST(? AS JSON))
            ON DUPLICATE KEY UPDATE source_company_id=VALUES(source_company_id),title=VALUES(title),company_name=VALUES(company_name),
              description=VALUES(description),location=VALUES(location),employment_type=VALUES(employment_type),experience_type=VALUES(experience_type),
              is_entry_level=VALUES(is_entry_level),industry_name=VALUES(industry_name),job_mid_name=VALUES(job_mid_name),job_name=VALUES(job_name),
              salary=VALUES(salary),keywords=VALUES(keywords),published_at=VALUES(published_at),deadline_at=VALUES(deadline_at),
              is_rolling_deadline=FALSE,status='ACTIVE',fetched_at=CURRENT_TIMESTAMP,source_updated_at=CURRENT_TIMESTAMP,
              crawl_status='COMPLETED',crawled_at=CURRENT_TIMESTAMP,raw_payload=VALUES(raw_payload)
            """,postingKey,companyKey,role.title(),name,description,"PENDING",sector.location(),"정규직",role.experience(),false,
            sector.industry(),role.department(),role.jobName(),FictionalDemoDataset.salary(index,role),skills,published,deadline,raw);
        long postingId=jdbc.queryForObject("SELECT id FROM job_postings WHERE source_provider='FICTIONAL_DEMO' AND external_job_id=?",Long.class,postingKey);
        jdbc.update("UPDATE job_postings SET source_url=? WHERE id=?",publicUrl+"/job-postings/"+postingId,postingId);
        jdbc.update("INSERT INTO review_company_postings(job_posting_id,company_id) VALUES (?,?) ON DUPLICATE KEY UPDATE company_id=VALUES(company_id)",postingId,companyId);
        installLocation(postingId,index,sector); installRequirements(postingId,role); installSkills(postingId,role); installReviews(postingId,companyId,index,name,role);
    }

    private void installLocation(long postingId,int index,FictionalDemoDataset.Sector sector) {
        jdbc.update("DELETE FROM job_posting_locations WHERE job_posting_id=? AND source_provider='FICTIONAL_DEMO'",postingId);
        jdbc.update("""
            INSERT INTO job_posting_locations(job_posting_id,source_provider,source_location_id,location_text,sido,sigungu,detailed_address,latitude,longitude,is_primary)
            VALUES (?,'FICTIONAL_DEMO',?,?,?,?,?,CAST(? AS DECIMAL(10,7)),CAST(? AS DECIMAL(10,7)),TRUE)
            """,postingId,"DEMO-LOCATION-%03d".formatted(index+1),sector.location(),sido(sector.location()),sigungu(sector.location()),sector.address(),sector.latitude(),sector.longitude());
    }

    private void installRequirements(long postingId,FictionalDemoDataset.Role role) {
        Integer evidence=jdbc.queryForObject("SELECT COUNT(*) FROM job_match_evidences e JOIN job_requirements r ON r.id=e.job_requirement_id WHERE r.job_posting_id=?",Integer.class,postingId);
        if(evidence!=null&&evidence>0) jdbc.update("DELETE e FROM job_match_evidences e JOIN job_requirements r ON r.id=e.job_requirement_id WHERE r.job_posting_id=?",postingId);
        jdbc.update("DELETE FROM job_requirements WHERE job_posting_id=?",postingId);
        addRequirement(postingId,"EXPERIENCE",role.experienceRequirement(),"REQUIRED");
        addRequirement(postingId,"EDUCATION","학력보다 역할에 필요한 문제 해결 경험과 결과를 우선합니다.","PREFERRED");
        for(int i=0;i<role.skills().size();i++) addRequirement(postingId,"SKILL",role.skills().get(i)+" 실무 활용 경험",i<3?"REQUIRED":"PREFERRED");
        addRequirement(postingId,"OTHER","Git 기반 협업, 코드 리뷰와 기술 문서 작성 경험","REQUIRED");
    }
    private void addRequirement(long posting,String type,String content,String importance){
        jdbc.update("INSERT INTO job_requirements(job_posting_id,type,content,source_excerpt,importance,extraction_source,verification_status) VALUES (?,?,?,?,?,'FICTIONAL_CURATED','VERIFIED')",posting,type,content,content,importance);
    }
    private void installSkills(long postingId,FictionalDemoDataset.Role role){
        jdbc.update("DELETE FROM job_skills WHERE job_posting_id=?",postingId);
        for(int i=0;i<role.skills().size();i++){
            Long skill=jdbc.queryForObject("SELECT id FROM skills WHERE name=? AND catalog_status='CANONICAL' LIMIT 1",Long.class,role.skills().get(i));
            jdbc.update("INSERT INTO job_skills(job_posting_id,skill_id,canonical_skill_id,requirement_type,source_excerpt) VALUES (?,?,?,?,?)",postingId,skill,skill,i<3?"REQUIRED":"PREFERRED",role.skills().get(i)+" 활용 경험");
        }
    }
    private void installReviews(long postingId,long companyId,int index,String company,FictionalDemoDataset.Role role){
        for(int ordinal=0;ordinal<5;ordinal++){
            var review=FictionalDemoDataset.review(index,ordinal,company,FictionalDemoDataset.SECTORS.get(index/10),role);
            String hash=SentimentAiClient.contentHash(String.join("\n",review.title(),review.pros(),review.cons(),review.body(),review.managementMessage()));
            jdbc.update("""
                INSERT INTO company_reviews(company_id,job_posting_id,seed_key,source_type,display_author,department,employment_status,tenure_months,
                  rating,title,pros,cons,body,management_message,content_hash,analysis_state,next_analysis_at,created_at,updated_at)
                VALUES (?,? ,?,'SYNTHETIC_DEMO',?,?,?,?,?,?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP,DATE_SUB(CURRENT_TIMESTAMP,INTERVAL ? DAY),CURRENT_TIMESTAMP)
                """,companyId,postingId,"DEMO-REVIEW-V2-%03d-%d".formatted(index+1,ordinal+1),"가상 리뷰어 %03d-%d".formatted(index+1,ordinal+1),
                review.department(),review.status(),review.tenureMonths(),review.rating(),review.title(),review.pros(),review.cons(),review.body(),review.managementMessage(),hash,ordinal*23+index%17);
        }
    }

    private void linkDemoEmployerIfConfigured(){
        if(demoEmployerId==null)return;
        Integer approved=jdbc.queryForObject("SELECT COUNT(*) FROM employer_accounts WHERE id=? AND status='APPROVED'",Integer.class,demoEmployerId);
        if(approved==null||approved!=1)throw new IllegalStateException("configured demo employer is not approved");
        jdbc.update("UPDATE review_companies SET employer_account_id=? WHERE source_type='FICTIONAL_DEMO'",demoEmployerId);
        jdbc.update("UPDATE job_postings SET employer_account_id=? WHERE source_provider='FICTIONAL_DEMO'",demoEmployerId);
    }
    private boolean validCounts(){
        long companies=count("SELECT COUNT(*) FROM review_companies WHERE source_type='FICTIONAL_DEMO'");
        long postings=count("SELECT COUNT(*) FROM job_postings WHERE source_provider='FICTIONAL_DEMO'");
        long reviews=count("SELECT COUNT(*) FROM company_reviews WHERE source_type='SYNTHETIC_DEMO'");
        long requirements=count("SELECT COUNT(*) FROM job_requirements r JOIN job_postings p ON p.id=r.job_posting_id WHERE p.source_provider='FICTIONAL_DEMO'");
        return companies==100&&postings==100&&reviews==500&&requirements>=700;
    }
    private long count(String sql){Long value=jdbc.queryForObject(sql,Long.class);return value==null?0:value;}
    private String sido(String location){return location.substring(0,location.indexOf(' '));}
    private String sigungu(String location){return location.substring(location.indexOf(' ')+1);}
}
