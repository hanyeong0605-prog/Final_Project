package com.jobpilot.api.domain.review.service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Versioned v1 ranking. Only fictional demos with five or more public reviews qualify. */
@Service
public class ReviewRankingService {
    private final JdbcTemplate jdbc;
    public ReviewRankingService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record Ranking(long id,Long jobPostingId,String name,String title,long reviewCount,double averageRating,
                          Double positiveSentiment,double score,String sourceType) {}
    public List<Ranking> companies(){return jdbc.query("""
      SELECT c.id,MIN(l.job_posting_id) job_posting_id,c.name,NULL title,COUNT(*) n,AVG(r.rating) avg_rating,
       AVG(CASE WHEN a.id IS NOT NULL THEN a.positive_score END) positive_score,
       (((AVG(r.rating)*COUNT(*)+3.5*10)/(COUNT(*)+10))/5)*.60
        +COALESCE(AVG(CASE WHEN a.id IS NOT NULL THEN a.positive_score END),0)*.25
        +LEAST(LOG10(COUNT(*)+1)/2,1)*.10
        +EXP(-TIMESTAMPDIFF(DAY,MAX(r.created_at),CURRENT_TIMESTAMP)/90)*.05 ranking_score
      FROM review_companies c JOIN company_reviews r ON r.company_id=c.id AND r.visibility='PUBLIC'
      JOIN review_company_postings l ON l.company_id=c.id
      LEFT JOIN company_review_analyses a ON a.id=(SELECT a2.id FROM company_review_analyses a2 WHERE a2.review_id=r.id AND a2.content_hash=r.content_hash ORDER BY a2.analyzed_at DESC,a2.id DESC LIMIT 1)
      WHERE c.source_type='FICTIONAL_DEMO' GROUP BY c.id,c.name HAVING COUNT(*)>=5
      ORDER BY ranking_score DESC,n DESC,MAX(r.created_at) DESC LIMIT 10
      """,(rs,n)->row(rs));}
    public List<Ranking> postings(){return jdbc.query("""
      SELECT p.id,p.id job_posting_id,c.name,p.title,COUNT(*) n,AVG(r.rating) avg_rating,
       AVG(CASE WHEN a.id IS NOT NULL THEN a.positive_score END) positive_score,
       (((AVG(r.rating)*COUNT(*)+3.5*10)/(COUNT(*)+10))/5)*.60
        +COALESCE(AVG(CASE WHEN a.id IS NOT NULL THEN a.positive_score END),0)*.25
        +LEAST(LOG10(COUNT(*)+1)/2,1)*.10
        +EXP(-TIMESTAMPDIFF(DAY,MAX(r.created_at),CURRENT_TIMESTAMP)/90)*.05 ranking_score
      FROM review_company_postings l JOIN job_postings p ON p.id=l.job_posting_id
      JOIN review_companies c ON c.id=l.company_id JOIN company_reviews r ON r.job_posting_id=p.id AND r.visibility='PUBLIC'
      LEFT JOIN company_review_analyses a ON a.id=(SELECT a2.id FROM company_review_analyses a2 WHERE a2.review_id=r.id AND a2.content_hash=r.content_hash ORDER BY a2.analyzed_at DESC,a2.id DESC LIMIT 1)
      WHERE c.source_type='FICTIONAL_DEMO' AND p.source_provider='FICTIONAL_DEMO'
      GROUP BY p.id,c.name,p.title HAVING COUNT(*)>=5
      ORDER BY ranking_score DESC,n DESC,MAX(r.created_at) DESC LIMIT 10
      """,(rs,n)->row(rs));}
    private Ranking row(java.sql.ResultSet rs)throws java.sql.SQLException{
      Double sentiment=(Double)rs.getObject("positive_score");
      return new Ranking(rs.getLong("id"),rs.getLong("job_posting_id"),rs.getString("name"),rs.getString("title"),rs.getLong("n"),
        rs.getDouble("avg_rating"),sentiment,rs.getDouble("ranking_score"),"FICTIONAL_DEMO");
    }
}
