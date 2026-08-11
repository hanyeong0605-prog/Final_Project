package com.jobpilot.api.domain.Qnet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QnetDto {


    private String qualgbcd;    // 자격구분 (T 기술, S 전문)
    private String qualgbnm;   // 자격구분명 국가기술자격,국가전문자격
    private int seriescd; // 계열코드
    private String seriesnm; // 계열명
    private int jmcd; // 종목코드
    private String jmfldnm;    // 종목명
    private int obligfldcd; // 대직무분야코드
    private String obligfldnm; // 대직무분야명
    private int mdobligfldcd; // 중직무분야코드
    private String mdobligfldnm; // 중직무분야명
}