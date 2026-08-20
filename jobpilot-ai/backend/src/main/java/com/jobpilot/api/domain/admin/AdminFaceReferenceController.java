package com.jobpilot.api.domain.admin;

import com.jobpilot.api.domain.member.entity.Member;
import com.jobpilot.api.domain.member.repository.MemberRepository;
import com.jobpilot.api.global.exception.ResourceNotFoundException;
import com.jobpilot.api.global.security.AuthenticatedMember;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin-managed biometric references stored only on the protected host volume. */
@RestController
@RequestMapping("/api/v1/admin/face-references")
public class AdminFaceReferenceController {
    private final AdminAccessService adminAccess;
    private final MemberRepository members;
    private final Path referenceDir;

    public AdminFaceReferenceController(AdminAccessService adminAccess, MemberRepository members,
                                       @Value("${app.face-reference.directory:/app/admin_photos}") String referenceDir) {
        this.adminAccess = adminAccess;
        this.members = members;
        this.referenceDir = Path.of(referenceDir).toAbsolutePath().normalize();
    }

    @GetMapping
    public List<AdminReferenceResponse> list(Authentication authentication) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        return members.findAll().stream().filter(Member::isAdmin).sorted(Comparator.comparing(Member::getLoginId))
                .map(member -> new AdminReferenceResponse(member.getLoginId(), member.getNickname(), Files.isRegularFile(referencePath(member.getLoginId()))))
                .toList();
    }

    @PostMapping(value = "/{loginId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminReferenceResponse upload(Authentication authentication, @PathVariable String loginId, @RequestParam("photo") MultipartFile photo) {
        adminAccess.requireAdmin(AuthenticatedMember.id(authentication));
        Member target = members.findByLoginId(loginId).filter(Member::isAdmin)
                .orElseThrow(() -> new ResourceNotFoundException("등록할 관리자 계정을 찾을 수 없습니다."));
        if (photo.isEmpty()) throw new IllegalArgumentException("기준 얼굴 사진을 선택해 주세요.");
        try {
            BufferedImage image = ImageIO.read(photo.getInputStream());
            if (image == null || image.getWidth() < 80 || image.getHeight() < 80) throw new IllegalArgumentException("얼굴이 보이는 80px 이상의 이미지 파일을 올려 주세요.");
            Files.createDirectories(referenceDir);
            Path targetPath = referencePath(target.getLoginId());
            Path temporary = Files.createTempFile(referenceDir, "face-reference-", ".jpg");
            try {
                if (!ImageIO.write(image, "jpg", temporary.toFile())) throw new IOException("JPEG 인코더를 찾을 수 없습니다.");
                try { Files.move(temporary, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, targetPath, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException error) { throw new IllegalArgumentException("기준 얼굴 사진을 저장하지 못했습니다."); }
        return new AdminReferenceResponse(target.getLoginId(), target.getNickname(), true);
    }

    private Path referencePath(String loginId) { return referenceDir.resolve(loginId + ".jpg").normalize(); }
    public record AdminReferenceResponse(String loginId, String nickname, boolean registered) {}
}
