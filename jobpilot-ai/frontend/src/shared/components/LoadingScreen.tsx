interface LoadingScreenProps {
  label?: string;
  compact?: boolean;
}

export function LoadingScreen({ label = "불러오는 중입니다", compact = false }: LoadingScreenProps) {
  return (
    <div className={`site-loading${compact ? " compact" : ""}`} role="status" aria-live="polite">
      <video src="/brand/loading-page.mp4" autoPlay loop muted playsInline preload="auto" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}
