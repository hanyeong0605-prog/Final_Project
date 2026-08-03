// ai-server(app/domain/interview/audio_analysis.py)의 VoiceMetrics.to_dict()와
// 그대로 대응되는 필드명(snake_case)을 유지한다 - Python 응답을 그대로 받아쓰는
// 유일한 API라 굳이 camelCase로 변환하는 계층을 하나 더 두지 않았다.
export interface VoiceMetrics {
  duration_sec: number;
  speaking_rate_chars_per_min: number | null;
  pitch_mean_hz: number | null;
  pitch_variation_hz: number | null;
  silence_ratio: number;
  long_pause_count: number;
  volume_mean_rms: number;
  volume_variation_rms: number;
}

export interface AnswerAnalysis {
  transcript: string;
  metrics: VoiceMetrics;
}
