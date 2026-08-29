export function BrandLogo({ compact = false }: { compact?: boolean }) {
  return (
    <span className={`brand-identity${compact ? " compact" : ""}`}>
      <span className="brand-name brand-name-en">
        <img className="brand-wordmark-image" src="/brand/job-a-dream-wordmark-blue.png" alt="Job A Dream" />
      </span>
      <span className="brand-name brand-name-ko" aria-hidden="true">
        <img className="brand-wordmark-image" src="/brand/job-a-dream-wordmark-ko.png" alt="" />
      </span>
    </span>
  );
}
