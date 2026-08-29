package com.jobpilot.api.domain.community;
import com.jobpilot.api.global.security.AuthenticatedMember;import java.util.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/community")public class AdminCommunityController{private final CommunityService s;public AdminCommunityController(CommunityService s){this.s=s;}
 @GetMapping("/sentiment")public List<Map<String,Object>> sentiment(Authentication a){return s.adminSentiment(AuthenticatedMember.id(a));}
 @GetMapping("/sentiment/summary")public Map<String,Object> summary(Authentication a){return s.adminSummary(AuthenticatedMember.id(a));}
 public record Moderation(String targetType,long targetId,String action,String reason){}@PostMapping("/moderate")public void moderate(@RequestBody Moderation i,Authentication a){s.moderate(AuthenticatedMember.id(a),i.targetType(),i.targetId(),i.action(),i.reason());}}
