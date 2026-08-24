package com.jobpilot.api.global.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.api.domain.jobposting.entity.JobPosting;
import com.jobpilot.api.domain.matching.policy.RecommendationLevel;
import com.jobpilot.api.domain.member.entity.Member;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** Shared Job-A-Dream branded HTML mail. Images are public HTTPS assets so mail
 * clients can render them without exposing a recipient-specific tracking URL. */
@Service
public class JobADreamMailService {
    private final JavaMailSender sender;
    private final String from;
    private final String publicBaseUrl;

    public JobADreamMailService(JavaMailSender sender,
                                @Value("${app.mail.from:}") String from,
                                @Value("${app.public-base-url:https://job-a-dream.site}") String publicBaseUrl) {
        this.sender = sender;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    public void sendVerificationCode(String to, String code, long expiresInMinutes) {
        String body = "<p>안녕하세요. <b>Job-A-Dream</b> 이메일 인증 코드입니다.</p>"
                + "<div style='margin:24px 0;padding:18px;border-radius:12px;background:#e8f5fd;color:#125f91;font-size:28px;font-weight:800;letter-spacing:7px;text-align:center'>"
                + escape(code) + "</div><p>인증 코드는 " + expiresInMinutes
                + "분 동안 유효합니다. 본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>";
        sendHtml(to, "[Job-A-Dream] 이메일 인증 코드", body);
    }

    public void sendRecommendedJobs(Member member, Map<RecommendationLevel, List<JobPosting>> grouped) {
        StringBuilder body = new StringBuilder("<p><b>" + escape(member.getNickname())
                + "</b>님이 관심 있어 할 만한 새 공고를 추천드려요.</p>");
        appendSection(body, "지금 지원 가능", "#5DADEC", grouped.getOrDefault(RecommendationLevel.APPLY_NOW, List.of()));
        appendSection(body, "보완 후 도전", "#d97706", grouped.getOrDefault(RecommendationLevel.CHALLENGE_AFTER_GAPS, List.of()));
        body.append("<p style='margin-top:22px;color:#667085;font-size:12px'>Job-A-Dream에서 내 역량 근거와 함께 공고를 확인해 보세요.</p>");
        sendHtml(member.getEmail(), "[Job-A-Dream] 회원님을 위한 새 맞춤 채용공고", body.toString());
    }

    private void appendSection(StringBuilder html, String title, String color, List<JobPosting> jobs) {
        if (jobs.isEmpty()) return;
        html.append("<h2 style='margin:26px 0 10px;color:").append(color).append(";font-size:18px'>").append(title).append("</h2>");
        for (JobPosting job : jobs) {
            String image = imageUrl(job);
            html.append("<a href='").append(publicBaseUrl).append("/job-postings/").append(job.getId())
                    .append("' style='display:block;margin:10px 0;padding:14px;border:1px solid #dce7f1;border-radius:14px;color:#1f2937;text-decoration:none'>")
                    .append("<table role='presentation' width='100%' cellspacing='0' cellpadding='0'><tr>");
            if (image != null) html.append("<td width='58' valign='top'><img src='").append(escapeAttribute(image)).append("' width='46' height='46' style='object-fit:cover;border-radius:10px' alt='공고 이미지'></td>");
            html.append("<td><strong style='font-size:15px'>").append(escape(job.getTitle()))
                    .append("</strong><br><span style='color:#5b6678;font-size:13px'>")
                    .append(escape(blank(job.getCompanyName(), "채용기업"))).append(" · ")
                    .append(escape(blank(job.getLocation(), "근무지 확인"))).append("</span><br><span style='color:#7b8797;font-size:12px'>")
                    .append(escape(summary(job.getDescription()))).append("</span></td></tr></table></a>");
        }
    }

    private void sendHtml(String to, String subject, String content) {
        if (from.isBlank()) throw new IllegalStateException("MAIL_USERNAME 또는 MAIL_FROM 환경변수를 설정해 주세요.");
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from); helper.setTo(to); helper.setSubject(subject);
            String html = "<div style='max-width:640px;margin:auto;padding:28px;background:#f6f9fc;font-family:Arial,sans-serif;color:#263248'>"
                    + "<div style='padding:18px 20px;background:#fff;border-radius:16px 16px 0 0;border-bottom:3px solid #5DADEC'>"
                    + "<table role='presentation' cellspacing='0' cellpadding='0'><tr>"
                    + "<td style='padding-right:14px;vertical-align:middle'><img src='cid:jobADreamLogo' alt='Job A Dream' width='210' style='display:block;width:210px;max-width:100%;height:auto;border:0'></td>"
                    + "<td style='vertical-align:middle'><img src='cid:jobADreamMascot' alt='Job-A-Dream 마스코트' width='88' style='display:block;width:88px;height:auto;border:0'></td>"
                    + "</tr></table></div>"
                    + "<div style='padding:24px 20px;background:#fff;border-radius:0 0 16px 16px'>" + content + "</div></div>";
            helper.setText(html, true);
            helper.addInline("jobADreamLogo", new ClassPathResource("mail/job-a-dream-logo.png"), "image/png");
            helper.addInline("jobADreamMascot", new ClassPathResource("mail/job-a-dream-cat.png"), "image/png");
            sender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new IllegalStateException("이메일을 보내지 못했습니다.", exception);
        }
    }

    private String imageUrl(JobPosting job) {
        if (job.getCompanyLogoUrl() != null && !job.getCompanyLogoUrl().isBlank()) return job.getCompanyLogoUrl();
        JsonNode payload = job.getRawPayload();
        String candidate = imageCandidate(payload == null ? null : payload.path("imageUrls").path(0));
        if (candidate == null) candidate = imageCandidate(payload == null ? null : payload.path("images").path("job_thumbnail_urls").path(0));
        return candidate != null ? candidate : publicBaseUrl + "/mascot/job-a-dream-email-cat.png";
    }
    private static String imageCandidate(JsonNode node) {
        if (node == null || !node.isTextual()) return null;
        String value = node.asText();
        return value.startsWith("https://") ? value : null;
    }
    private static String blank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String summary(String value) { String text = blank(value, "공고 상세와 지원 요건을 확인해 보세요.").replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim(); return text.length() > 90 ? text.substring(0, 87) + "..." : text; }
    private static String escape(String value) { return blank(value, "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String escapeAttribute(String value) { return escape(value).replace("\"", "&quot;").replace("'", "&#39;"); }
}
