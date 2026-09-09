import ClaudeSubscriptionCard from "@widgets/llm/ClaudeSubscriptionCard";

/**
 * Claude 구독 설정 화면 (`/claude-subscription`, 데스크톱 전용)
 *
 * 앱 안 질문에 답하는 데 쓰는 내 Claude 구독의 로그인·로그아웃, 모델·effort 설정,
 * 과금·정책 고지와 마지막 답변 경로를 다루는 화면이다(기획서 6.5, 로드맵 L3).
 *
 * 브라우저처럼 `window.knotDesktop.llm`이 없으면 데스크톱 앱에서 이어가라는 안내만 보여 준다.
 * 로고와 중앙 배치는 `CenteredLayout`이 담당하고, 이 페이지는 카드를 놓기만 한다.
 */
export default function ClaudeSubscriptionPage() {
  return <ClaudeSubscriptionCard />;
}
