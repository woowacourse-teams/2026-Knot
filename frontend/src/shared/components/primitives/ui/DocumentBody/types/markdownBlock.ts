/** 굵게만 구분하는 글 조각. 정한 문법 밖의 기호는 `text` 안에 글자 그대로 남아요 */
export type MarkdownInline =
  { type: "text"; value: string } | { type: "bold"; value: string };

/** `#` · `##` · `###`의 개수 */
export type MarkdownHeadingLevel = 1 | 2 | 3;

/** 제목 한 줄 */
export interface MarkdownHeadingBlock {
  type: "heading";
  level: MarkdownHeadingLevel;
  inlines: MarkdownInline[];
}

/** 빈 줄로 나뉜 문단. 문단 안의 줄바꿈은 `lines`로 유지해요 */
export interface MarkdownParagraphBlock {
  type: "paragraph";
  lines: MarkdownInline[][];
  /** 문단 전체가 흐리게 그릴 문장과 같으면 true */
  isMuted: boolean;
}

/** `- `로 시작하는 줄이 이어진 점 목록 */
export interface MarkdownListBlock {
  type: "list";
  items: MarkdownInline[][];
}

export type MarkdownBlock =
  MarkdownHeadingBlock | MarkdownParagraphBlock | MarkdownListBlock;
