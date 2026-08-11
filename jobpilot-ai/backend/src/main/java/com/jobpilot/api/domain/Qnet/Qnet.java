package com.jobpilot.api.domain.Qnet;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "qnet_qualification")
@Getter
@Setter
@NoArgsConstructor
public class Qnet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String qualgbcd;    // 자격구분  (T 기술, S 전문)
    private String qualgbnm;   // 자격구분명
    private int seriescd;      // 계열코드
    private String seriesnm;   // 계열명
    private int jmcd;          // 종목코드
    private String jmfldnm;    // 종목명
    private int obligfldcd;    // 대직무분야코드
    private String obligfldnm; // 대직무분야명
    private int mdobligfldcd;  // 중직무분야코드
    private String mdobligfldnm; // 중직무분야명
}