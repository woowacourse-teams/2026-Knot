/** 저장 뒤 `저장됨` 표시를 유지하는 시간(ms). 초대 카드의 복사 표시와 같아요 */
export const SAVED_DURATION_MS = 2000;

export const SUBSCRIPTION_STATUS_LABEL = {
  loading: "확인하는 중이에요",
  signedIn: "로그인됨",
  signedOut: "로그인 안 됨",
} as const;

/** 마지막 질문이 어느 경로로 답을 받았는지(`lastAnsweredBy`) */
export const ANSWERED_BY_LABEL = {
  subscription: "내 Claude 구독",
  "server-sse": "서버 모델",
  none: "아직 없어요",
} as const;

export const CLAUDE_SUBSCRIPTION_MESSAGE = {
  signInFailed:
    "Claude 로그인을 마치지 못했어요. 브라우저에서 로그인을 끝낸 뒤 다시 시도해 주세요.",
  signOutFailed: "로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.",
  settingsFailed: "설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.",
  noError: "없어요",
  noExpiry: "-",
} as const;

/** extra usage 크레딧 과금 고지(로드맵 R29). 구독 한도가 아니라는 점을 숨기지 않아요 */
export const BILLING_NOTICE =
  "서드파티 앱에서 쓰는 사용량은 구독 한도가 아니라 내 Claude 계정의 extra usage 크레딧에서 토큰 단위로 차감돼요. 크레딧이 없으면 구독으로 답하지 못하고 서버 모델로 답해요.";

/** 정책 리스크 고지(로드맵 R28). 사용자가 감수하기로 한 위험을 화면에 그대로 둬요 */
export const POLICY_NOTICE =
  "이 방식은 Anthropic 약관이 서드파티 앱에 허용하는 형태가 아니라 재량으로 허용되는 구간이에요. 예고 없이 막힐 수 있고, 막히면 서버 모델로 답해요.";

/** 폴백 안내(로드맵 Q22·Q66). 브라우저와 구독 없는 사용자는 서버 모델만 써요 */
export const FALLBACK_NOTICE =
  "구독에 로그인하지 않았거나 호출이 거절되면 Knot 서버 모델로 답하고 화면에 한 줄로 알려요. 브라우저에서는 항상 서버 모델이에요.";
