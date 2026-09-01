/** `https:`, `mailto:`처럼 스킴으로 시작하거나 `//`로 시작하는 주소 */
const EXTERNAL_HREF_PATTERN = /^([a-z][a-z0-9+\-.]*:|\/\/)/i;

/**
 * 앱 밖으로 나가는 주소인지 판단합니다.
 * 스킴(`https:`, `mailto:` 등)이나 `//`로 시작하면 외부, 그 외(`/chat`, `chat/1`, `?q=1`, `#top`)는 내부로 봅니다.
 *
 * @param href - 판단할 주소
 * @returns 외부 링크 여부
 *
 * @example
 * isExternalHref("https://www.notion.so/page"); // true
 * isExternalHref("/workspace/1/chat"); // false
 */
export const isExternalHref = (href: string) =>
  EXTERNAL_HREF_PATTERN.test(href.trim());
