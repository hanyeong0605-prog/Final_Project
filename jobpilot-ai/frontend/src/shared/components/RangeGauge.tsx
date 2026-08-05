// 2026-08-05: 결과 화면의 "일반적인 범위" 지표(말속도, 침묵 비율)를 숫자+텍스트 힌트만으로
// 보여주던 걸, 실제 분석 도구들처럼 막대 위에 정상 범위(band)와 실제값(marker)을 같이
// 보여주는 게이지로 바꿨다. 새 지표나 백엔드 변경 없이 지금 있는 min/max 기준값만
// 시각화하는 용도라 가볍게 순수 div/style로만 구현했다(차트 라이브러리 추가 안 함).
//
// 주의: 여기서 만드는 건 "정상 범위 대비 지금 값이 어디 있는지" 보여주는 게이지일 뿐이고,
// 0~100 스코어나 등급 같은 걸 새로 만들어내는 게 아니다 - 이미 있던 기준(SPEAKING_RATE_MIN
// 같은)을 그대로 시각화한 것뿐이라 "확신에 찬 판정을 하지 않는다"는 원칙과 충돌하지 않는다.
interface RangeGaugeProps {
  value: number;
  min: number;
  max: number;
  goodMin: number;
  goodMax: number;
}

function clamp(v: number, lo: number, hi: number): number {
  return Math.min(hi, Math.max(lo, v));
}

export function RangeGauge({ value, min, max, goodMin, goodMax }: RangeGaugeProps) {
  const toPct = (v: number) => ((clamp(v, min, max) - min) / (max - min)) * 100;
  const bandLeft = toPct(goodMin);
  const bandWidth = toPct(goodMax) - bandLeft;
  const markerLeft = toPct(value);
  const inRange = value >= goodMin && value <= goodMax;
  // 범위보다 낮으면 파랑(부족), 범위 안이면 초록(양호), 범위보다 높으면 주황(과함) -
  // 기존 카드 힌트 텍스트 색 톤과 맞췄다.
  const markerColor = inRange ? "#2e9e5b" : value < goodMin ? "#596ff3" : "#d98c00";

  return (
    <div style={{ position: "relative", width: "100%", height: 8, marginTop: 8, borderRadius: 4, background: "#eef0f6" }}>
      <div
        style={{
          position: "absolute",
          left: `${bandLeft}%`,
          width: `${bandWidth}%`,
          height: "100%",
          borderRadius: 4,
          background: "rgba(46,158,91,0.18)",
        }}
      />
      <div
        style={{
          position: "absolute",
          left: `calc(${markerLeft}% - 2px)`,
          top: -2,
          width: 4,
          height: 12,
          borderRadius: 2,
          background: markerColor,
        }}
      />
    </div>
  );
}
