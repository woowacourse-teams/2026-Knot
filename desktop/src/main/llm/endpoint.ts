/**
 * 사용자 LLM 엔드포인트 허용 판정과 URL 조립 (로드맵 Q27·Q43).
 *
 * 네비게이션 허용 목록(`shared/env.ts`)과는 별개 목록이다. 로컬 모델 포트가 제각각이라
 * 고정 목록 대신 `https:` 전체와 로컬 `http:`만 연다.
 */

const LOCAL_HTTP_HOSTS: ReadonlySet<string> = new Set(["localhost", "127.0.0.1"]);

/** `https:` 전체 + `http://localhost`·`http://127.0.0.1`(포트 무관). 그 외는 거부 */
export function isAllowedLlmEndpoint(rawUrl: string): boolean {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    return false;
  }
  if (url.protocol === "https:") return true;
  if (url.protocol === "http:") return LOCAL_HTTP_HOSTS.has(url.hostname);
  return false;
}

/** OpenAI 호환: `{baseUrl}/chat/completions`. `baseUrl`은 보통 `/v1`로 끝난다 */
export function chatCompletionsUrl(baseUrl: string): string {
  return `${trimTrailingSlashes(baseUrl)}/chat/completions`;
}

/** Anthropic: `{baseUrl}/v1/messages`. `baseUrl`이 이미 `/v1`로 끝나면 `/messages`만 붙인다 */
export function anthropicMessagesUrl(baseUrl: string): string {
  const base = trimTrailingSlashes(baseUrl);
  return base.endsWith("/v1") ? `${base}/messages` : `${base}/v1/messages`;
}

function trimTrailingSlashes(value: string): string {
  return value.replace(/\/+$/, "");
}
