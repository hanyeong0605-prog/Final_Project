package com.jobpilot.api.domain.community;
import com.jobpilot.api.global.security.AuthenticatedMember;import java.util.*;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/community") public class CommunityController{
 private final CommunityService s;public CommunityController(CommunityService s){this.s=s;}private Long me(Authentication a){return AuthenticatedMember.id(a);}
 @GetMapping("/posts")public List<CommunityService.Post>list(@RequestParam String boardType,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="")String query,@RequestParam(defaultValue="RECENT")String sort,Authentication a){return s.list(boardType,me(a),page,size,query,sort);}
 @GetMapping("/posts/{id}")public CommunityService.Post detail(@PathVariable long id,Authentication a){return s.detail(id,me(a));}
 @PostMapping("/posts")public CommunityService.Post create(@RequestBody CommunityService.Input i,Authentication a){return s.create(me(a),i);}
 @PutMapping("/posts/{id}")public CommunityService.Post update(@PathVariable long id,@RequestBody CommunityService.Input i,Authentication a){return s.update(id,me(a),i);}
 @DeleteMapping("/posts/{id}")public void delete(@PathVariable long id,Authentication a){s.delete(id,me(a));}
 @PostMapping("/posts/{id}/like")public Map<String,Boolean>like(@PathVariable long id,Authentication a){return Map.of("liked",s.like(id,me(a)));}
 @GetMapping("/posts/{id}/comments")public List<CommunityService.Comment>comments(@PathVariable long id,Authentication a){return s.comments(id,me(a));}
 public record CommentInput(Long parentId,String body){} @PostMapping("/posts/{id}/comments")public CommunityService.Comment comment(@PathVariable long id,@RequestBody CommentInput i,Authentication a){return s.comment(id,me(a),i.parentId(),i.body());}
 @DeleteMapping("/comments/{id}")public void deleteComment(@PathVariable long id,Authentication a){s.deleteComment(id,me(a));}
 @PutMapping("/comments/{id}")public CommunityService.Comment updateComment(@PathVariable long id,@RequestBody CommentInput i,Authentication a){return s.updateComment(id,me(a),i.body());}
 @PostMapping("/comments/{id}/like")public Map<String,Boolean>likeComment(@PathVariable long id,Authentication a){return Map.of("liked",s.likeComment(id,me(a)));}
 public record ReportInput(String targetType,long targetId,String reason){}@PostMapping("/reports")public void report(@RequestBody ReportInput i,Authentication a){s.report(i.targetType(),i.targetId(),me(a),i.reason());}
}
