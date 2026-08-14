package com.jobpilot.api.domain.portfolio.dto;

import com.jobpilot.api.domain.projectanalysis.dto.GitHubProjectAnalysisResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// analysis는 RepositoryAnalysisPage가 이미 화면에 들고 있는 분석 결과를 그대로 다시 보낸다 -
// GitHubProjectAnalysisService는 분석 결과를 서버에 저장하지 않는 stateless 구조라
// (GitHubProjectAnalysisService 참고), 포트폴리오를 만들 때 GitHub을 다시 읽지 않고
// 프론트가 가진 값을 근거로 쓴다. template은 "LIGHT" | "DARK" | "BRAND_BLUE" 중 하나를
// 기대하지만, 알 수 없는 값이 와도 PortfolioTemplate.fromCode()가 LIGHT로 안전하게
// 처리하므로 여기서는 별도 검증을 걸지 않는다.
public record PortfolioGenerateRequest(
        @NotNull GitHubProjectAnalysisResponse analysis,
        @NotEmpty List<String> selectedImplementationIds,
        String template
) {
}
