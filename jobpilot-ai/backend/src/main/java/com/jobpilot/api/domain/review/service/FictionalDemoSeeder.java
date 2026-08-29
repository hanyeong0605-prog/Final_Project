package com.jobpilot.api.domain.review.service;

import com.jobpilot.api.domain.sentiment.client.SentimentAiClient;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-shot, idempotent demo import. Stable keys are confined to FICTIONAL_DEMO and every existing
 * key is verified before use; it never updates crawled/employer-created postings or real members.
 */
@Component
@ConditionalOnProperty(name="app.review-demo.seed-enabled",havingValue="true")
public class FictionalDemoSeeder implements ApplicationRunner {
    private static final List<String[]> DOMAINS=List.of(
      new String[]{"가온","교육 플랫폼","학습 진도와 강의 추천"},new String[]{"누리","물류 소프트웨어","배송 일정과 재고 추적"},
      new String[]{"다온","환경 데이터","에너지 사용량과 절감 현황"},new String[]{"라온","협업 도구","팀 일정과 문서 공유"},
      new String[]{"마루","여행 서비스","일정 구성과 예약 관리"},new String[]{"바른","콘텐츠 플랫폼","콘텐츠 탐색과 구독 관리"},
      new String[]{"새봄","전자상거래","주문 처리와 상품 검색"},new String[]{"여울","스포츠 데이터","운동 기록과 활동 통계"},
      new String[]{"온빛","업무 자동화","반복 작업과 승인 흐름"},new String[]{"푸른","농업 소프트웨어","작물 생육과 센서 데이터"});
    private static final List<String[]> ROLES=List.of(
      new String[]{"백엔드 개발자","Java, Spring Boot, MySQL","API 설계와 트랜잭션 처리"},new String[]{"프론트엔드 개발자","TypeScript, React, CSS","접근성과 반응형 화면 개선"},
      new String[]{"데이터 엔지니어","Python, SQL, Airflow","수집 파이프라인과 데이터 품질 관리"},new String[]{"머신러닝 엔지니어","Python, PyTorch, scikit-learn","모델 실험과 추론 성능 평가"},
      new String[]{"클라우드 엔지니어","Linux, Docker, AWS","배포 자동화와 서비스 모니터링"},new String[]{"모바일 개발자","Kotlin, Android, Git","모바일 화면과 오프라인 동기화"},
      new String[]{"QA 엔지니어","Python, Playwright, SQL","회귀 테스트와 결함 재현"},new String[]{"데이터 분석가","SQL, Python, Tableau","지표 정의와 사용자 행동 분석"},
      new String[]{"서비스 기획자","Figma, SQL, Jira","요구사항 정리와 사용자 흐름 설계"},new String[]{"보안 엔지니어","Linux, Python, SQL","접근 통제와 취약점 대응"});
    private static final String[] GOOD={"동료들이 질문에 시간을 내어 답해 주었습니다.","업무 우선순위를 함께 정하고 문서로 공유했습니다.","휴가 사용에 눈치를 주지 않았습니다.","코드 리뷰와 회고에서 다른 접근을 배웠습니다.","교육비 지원으로 필요한 기술을 학습했습니다."};
    private static final String[] BAD={"마감 직전에 요구사항이 자주 바뀌었습니다.","보상 기준이 명확하지 않아 아쉬웠습니다.","오래된 문서가 많아 여러 번 확인해야 했습니다.","팀 사이 의사결정이 늦어 작업을 다시 했습니다.","출시 기간에는 야근이 늘었습니다."};
    private static final String[] LOW_GOOD={"뚜렷하게 좋았던 점을 찾기 어려웠습니다.","기대했던 장점을 경험하지 못했습니다.","만족스러운 부분이 거의 없었습니다.","지원 제도의 장점을 체감하기 어려웠습니다.","긍정적으로 평가할 부분이 부족했습니다."};
    private static final String[] MILD_BAD={"일부 문서는 갱신이 필요했지만 큰 지장은 없었습니다.","바쁜 기간에는 회의가 조금 늘었지만 조율할 수 있었습니다.","보상 기준을 더 자세히 공유하면 좋겠습니다.","내부 도구를 익히는 데 시간이 조금 필요했습니다.","팀별 절차가 달랐지만 문의하면 안내받았습니다."};
    private final JdbcTemplate jdbc; private final String publicUrl; private final TransactionTemplate tx;private final Long demoEmployerId;
    public FictionalDemoSeeder(JdbcTemplate jdbc,org.springframework.transaction.PlatformTransactionManager manager,@Value("${app.public-base-url:https://job-a-dream.site}")String publicUrl,@Value("${app.review-demo.employer-id:0}")Long demoEmployerId){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.publicUrl=publicUrl.replaceAll("/$","");this.demoEmployerId=demoEmployerId>0?demoEmployerId:null;}
    @Override public void run(ApplicationArguments args){seed();}

    public void seed(){tx.executeWithoutResult(ignored->seedInTransaction());}
    private void seedInTransaction(){
      for(int d=0;d<10;d++)for(int r=0;r<10;r++){
        int n=d*10+r+1;String key="DEMO-COMPANY-%03d".formatted(n),postKey="DEMO-JOB-%03d".formatted(n);
        String name=DOMAINS.get(d)[0]+"시연랩%02d (가상기업)".formatted(n),industry=DOMAINS.get(d)[1],product=DOMAINS.get(d)[2];
        jdbc.update("INSERT IGNORE INTO review_companies(seed_key,name,source_type,description,industry,location) VALUES (?,?,'FICTIONAL_DEMO',?,?,?)",
          key,name,product+" 기능을 만드는 포트폴리오 시연용 가상기업입니다.",industry,List.of("서울 강남구","서울 마포구","경기 성남시","부산 해운대구","대전 유성구").get(n%5));
        var company=jdbc.queryForMap("SELECT id,name,source_type FROM review_companies WHERE seed_key=?",key);
        if(!name.equals(company.get("name"))||!"FICTIONAL_DEMO".equals(company.get("source_type")))throw new IllegalStateException("demo company key collision");
        long companyId=((Number)company.get("id")).longValue();String role=ROLES.get(r)[0],skills=ROLES.get(r)[1],work=ROLES.get(r)[2];
        String description="[가상기업 · 시연용 공고]\n주요 업무\n- "+work+"\n- "+product+" 서비스 개선\n\n자격 요건\n- "+skills+" 활용 경험\n\n우대 사항\n- 테스트 자동화 또는 운영 경험\n\n복리후생\n- 유연근무제, 교육비·장비 지원\n\n실제 채용을 진행하지 않는 합성 공고입니다.";
        jdbc.update("""
          INSERT IGNORE INTO job_postings(external_job_id,source_provider,source_company_id,title,company_name,description,source_url,
            location,employment_type,experience_type,industry_name,job_name,salary,keywords,is_rolling_deadline,status,crawl_status,raw_payload)
          VALUES (?,'FICTIONAL_DEMO',?,?,?, ?,?,'서울','정규직','신입·경력',?,?,?, ?,TRUE,'ACTIVE','NOT_REQUESTED',JSON_OBJECT('sourceType','SYNTHETIC_DEMO'))
          """,postKey,key,"[시연] "+role+" 채용",name,description,publicUrl+"/job-postings/demo/"+postKey,industry,role,"연 3,500~4,700만원 (가상 조건)",skills);
        var post=jdbc.queryForMap("SELECT id,company_name FROM job_postings WHERE source_provider='FICTIONAL_DEMO' AND external_job_id=?",postKey);
        if(!name.equals(post.get("company_name")))throw new IllegalStateException("demo posting key collision");long postId=((Number)post.get("id")).longValue();
        jdbc.update("INSERT IGNORE INTO review_company_postings(job_posting_id,company_id) VALUES (?,?)",postId,companyId);
        for(int o=0;o<5;o++){int rating=1+(n+o)%5;String pros=(rating>=3?GOOD:LOW_GOOD)[(r+o)%5],cons=(rating<=3?BAD:MILD_BAD)[(d+o)%5];
          String title=industry+" 팀에서의 가상 근무 후기 "+(o+1),body=role+"로 "+work+"를 담당했다는 시연 설정입니다. "+(rating>=4?"전반적으로 만족했습니다.":rating==3?"좋은 점과 개선점이 함께 있었습니다.":"현재 방식은 개선이 필요합니다.");
          String hash=SentimentAiClient.contentHash(String.join("\n",title,pros,cons,body));
          jdbc.update("""
            INSERT IGNORE INTO company_reviews(company_id,job_posting_id,seed_key,source_type,display_author,rating,title,pros,cons,body,content_hash,next_analysis_at)
            VALUES (?,? ,?,'SYNTHETIC_DEMO',?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            """,companyId,postId,"DEMO-REVIEW-%03d-%d".formatted(n,o+1),"시연 리뷰어 %03d-%d".formatted(n,o+1),rating,title,pros,cons,body,hash);
        }
      }
      if(demoEmployerId!=null){Integer approved=jdbc.queryForObject("SELECT COUNT(*) FROM employer_accounts WHERE id=? AND status='APPROVED'",Integer.class,demoEmployerId);if(approved==null||approved!=1)throw new IllegalStateException("configured demo employer is not approved");jdbc.update("UPDATE review_companies SET employer_account_id=? WHERE seed_key LIKE 'DEMO-COMPANY-%' AND source_type='FICTIONAL_DEMO'",demoEmployerId);jdbc.update("UPDATE job_postings SET employer_account_id=? WHERE external_job_id LIKE 'DEMO-JOB-%' AND source_provider='FICTIONAL_DEMO'",demoEmployerId);}
      long[] counts={jdbc.queryForObject("SELECT COUNT(*) FROM review_companies WHERE source_type='FICTIONAL_DEMO' AND seed_key LIKE 'DEMO-COMPANY-%'",Long.class),jdbc.queryForObject("SELECT COUNT(*) FROM job_postings WHERE source_provider='FICTIONAL_DEMO' AND external_job_id LIKE 'DEMO-JOB-%'",Long.class),jdbc.queryForObject("SELECT COUNT(*) FROM company_reviews WHERE source_type='SYNTHETIC_DEMO' AND seed_key LIKE 'DEMO-REVIEW-%'",Long.class)};
      if(counts[0]!=100||counts[1]!=100||counts[2]!=500)throw new IllegalStateException("demo seed cardinality mismatch");
    }
}
