/** 파형 막대 수. Figma Recorder/Bar의 Waveform과 같아요. */
export const WAVEFORM_BAR_COUNT = 26;

/** 끝에서 이만큼은 아직 들어오지 않은 소리 자리라 녹음 중에도 흐리게 둬요. */
export const WAVEFORM_PENDING_BAR_COUNT = 3;

/** 막대 높이(px). 소리가 없으면 가장 낮게, 가장 크면 파형 높이를 꽉 채워요. */
export const WAVEFORM_MIN_BAR_HEIGHT = 4;
export const WAVEFORM_MAX_BAR_HEIGHT = 30;

/** 소리 크기를 재는 간격(ms). 막대가 한 칸씩 왼쪽으로 흘러가는 속도예요. */
export const WAVEFORM_SAMPLE_INTERVAL_MS = 120;
