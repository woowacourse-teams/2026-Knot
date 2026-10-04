/**
 * 말소리의 RMS는 0.2 안팎이라 그대로 쓰면 막대가 낮게만 움직여요.
 * 보통 크기로 말할 때 막대가 꽉 차도록 이만큼 키워요.
 */
const LEVEL_GAIN = 5;

/**
 * 마이크 샘플(-1~1 진폭) 묶음의 소리 크기를 0~1로 돌려줘요.
 *
 * 진폭의 제곱 평균의 제곱근(RMS)을 써서 순간적인 튐보다 묶음 전체의 크기를 봐요.
 */
export const measureLevel = (samples: Float32Array) => {
  if (samples.length === 0) return 0;

  let sumOfSquares = 0;
  for (const sample of samples) sumOfSquares += sample * sample;

  const rms = Math.sqrt(sumOfSquares / samples.length);

  return Math.min(1, rms * LEVEL_GAIN);
};
