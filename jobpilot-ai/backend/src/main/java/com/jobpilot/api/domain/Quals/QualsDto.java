package com.jobpilot.api.domain.Quals;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QualsDto {

   private String Qualifications; // 자격종목 - 필요한거
   private String Rating; // 등급 - 필요한거
   private String Manager; // 자격관리자 - 필요한거

// csv 파일 컬럼정의표 기준 필요한 거.
// 공인민간자격증 관련



}
