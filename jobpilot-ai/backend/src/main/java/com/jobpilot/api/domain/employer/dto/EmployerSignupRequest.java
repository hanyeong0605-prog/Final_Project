package com.jobpilot.api.domain.employer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmployerSignupRequest(
        @NotBlank @Size(min = 6, max = 80)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "로그인 아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.")
        String loginId,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{10,72}$", message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해 10자 이상이어야 합니다.")
        String password,
        @NotBlank @Size(min = 2, max = 80) String managerName,
        String managerPhone,
        @NotBlank @Size(min = 1, max = 150) String companyName,
        @NotBlank
        @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자등록번호 10자리를 입력해 주세요.")
        String businessRegistrationNumber,
        @NotBlank @Size(min = 2, max = 80) String representativeName,
        @NotBlank
        @Pattern(regexp = "^\\d{8}$", message = "개업일자는 YYYYMMDD 8자리로 입력해 주세요.")
        String openingDate,
        String companyAddress
) {}
