package com.jobpilot.api.domain.portfolio.service;

// 포트폴리오 산출물(PPTX/PDF)의 색상 테마 - PptxRenderer/PdfRenderer가 공통으로 참조한다.
// RGB 원시값(0~255)만 들고, 실제 색상 타입 변환(java.awt.Color / PDFBox의
// setNonStrokingColor(int,int,int))은 각 렌더러가 자신의 라이브러리에 맞게 한다 - 이 enum이
// 특정 렌더링 라이브러리 타입에 묶이지 않게 하기 위함.
enum PortfolioTemplate {
    LIGHT(
            new int[]{255, 255, 255}, new int[]{37, 47, 69}, new int[]{89, 111, 243},
            new int[]{107, 118, 140}, new int[]{243, 245, 250}
    ),
    DARK(
            new int[]{26, 32, 48}, new int[]{240, 243, 250}, new int[]{120, 176, 255},
            new int[]{160, 172, 196}, new int[]{38, 46, 66}
    ),
    BRAND_BLUE(
            new int[]{22, 38, 84}, new int[]{255, 255, 255}, new int[]{255, 202, 87},
            new int[]{190, 205, 240}, new int[]{33, 51, 104}
    );

    final int[] background;
    final int[] ink;
    final int[] accent;
    final int[] muted;
    final int[] codeBackground;

    PortfolioTemplate(int[] background, int[] ink, int[] accent, int[] muted, int[] codeBackground) {
        this.background = background;
        this.ink = ink;
        this.accent = accent;
        this.muted = muted;
        this.codeBackground = codeBackground;
    }

    static PortfolioTemplate fromCode(String code) {
        if (code == null || code.isBlank()) return LIGHT;
        try {
            return PortfolioTemplate.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return LIGHT;
        }
    }
}
