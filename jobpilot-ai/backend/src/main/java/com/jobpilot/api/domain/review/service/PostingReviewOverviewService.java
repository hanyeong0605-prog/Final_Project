package com.jobpilot.api.domain.review.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.api.domain.sentiment.client.SentimentAiClient.Emotion;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Read model for the review section embedded in a normal job-posting detail page. */
@Service
public class PostingReviewOverviewService {
    private final JdbcTemplate db; private final ObjectMapper mapper;
    public PostingReviewOverviewService(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
    public record Summary(long reviewCount,double averageRating,long analyzedCount,double positive,double neutral,double negative,String dominantPolarity,List<Emotion> topEmotions){}
    public record Review(long id,String displayAuthor,String department,String employmentStatus,Integer tenureMonths,int rating,String title,String pros,String cons,String body,String managementMessage,long likeCount,boolean mine,LocalDateTime createdAt){}
    public record Overview(long companyId,String companyName,String sourceType,Summary summary,List<Review> reviews){}

    public Overview find(long postingId,Long viewer){
        var links=db.query("""
          SELECT c.id,c.name,c.source_type FROM review_company_postings l JOIN review_companies c ON c.id=l.company_id
          JOIN job_postings p ON p.id=l.job_posting_id WHERE l.job_posting_id=? AND p.status='ACTIVE' AND c.reviews_enabled=TRUE
          """,(rs,n)->Map.of("id",rs.getLong("id"),"name",rs.getString("name"),"source",rs.getString("source_type")),postingId);
        if(links.isEmpty())throw new ResponseStatusException(NOT_FOUND,"이 공고에는 연결된 회사 리뷰가 없습니다.");
        var company=links.getFirst();long companyId=(long)company.get("id");
        List<Review> reviews=db.query("""
          SELECT r.*,(SELECT COUNT(*) FROM company_review_likes l WHERE l.review_id=r.id) like_count
          FROM company_reviews r WHERE r.job_posting_id=? AND r.visibility='PUBLIC' ORDER BY r.created_at DESC,r.id DESC
          """,(rs,n)->new Review(rs.getLong("id"),rs.getString("display_author"),rs.getString("department"),rs.getString("employment_status"),
            (Integer)rs.getObject("tenure_months"),rs.getInt("rating"),rs.getString("title"),rs.getString("pros"),rs.getString("cons"),rs.getString("body"),
            rs.getString("management_message"),rs.getLong("like_count"),viewer!=null&&viewer==rs.getLong("author_member_id"),rs.getTimestamp("created_at").toLocalDateTime()),postingId);
        return new Overview(companyId,(String)company.get("name"),(String)company.get("source"),summary(postingId,reviews),reviews);
    }
    private Summary summary(long postingId,List<Review> reviews){
        if(reviews.isEmpty())return new Summary(0,0,0,0,0,0,"UNAVAILABLE",List.of());
        var rows=db.queryForList("""
          SELECT a.polarity,a.positive_score,a.neutral_score,a.negative_score,a.emotions
          FROM company_reviews r JOIN company_review_analyses a ON a.id=(SELECT a2.id FROM company_review_analyses a2
            WHERE a2.review_id=r.id AND a2.content_hash=r.content_hash ORDER BY a2.analyzed_at DESC,a2.id DESC LIMIT 1)
          WHERE r.job_posting_id=? AND r.visibility='PUBLIC'
          """,postingId);
        double avg=reviews.stream().mapToInt(Review::rating).average().orElse(0),positive=0,neutral=0,negative=0;Map<String,Double> emotions=new HashMap<>();
        for(var row:rows){positive+=number(row.get("positive_score"));neutral+=number(row.get("neutral_score"));negative+=number(row.get("negative_score"));
          try{for(var e:mapper.readValue((String)row.get("emotions"),new TypeReference<List<Emotion>>(){}))emotions.merge(e.label(),e.score(),Double::sum);}catch(Exception ignored){}
        }
        int n=rows.size();if(n>0){positive/=n;neutral/=n;negative/=n;}String polarity=n==0?"PENDING":positive>=negative&&positive>=neutral?"POSITIVE":negative>=neutral?"NEGATIVE":"NEUTRAL";
        List<Emotion> top=emotions.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(5).map(e->new Emotion(e.getKey(),e.getValue()/Math.max(n,1))).toList();
        return new Summary(reviews.size(),avg,n,positive,neutral,negative,polarity,top);
    }
    private double number(Object value){return value instanceof Number n?n.doubleValue():0;}
}
