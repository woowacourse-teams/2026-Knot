/** 꾸밈 없는 글 조각. 정한 문법 밖의 기호는 글자 그대로 남아요 */
export interface MarkdownTextInline {
  type: "text";
  value: string;
}

/** `**굵게**`로 감싼 글 조각 */
export interface MarkdownBoldInline {
  type: "bold";
  value: string;
}

/** 굵게만 구분하는 글 조각 */
export type MarkdownInline = MarkdownTextInline | MarkdownBoldInline;

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

/** `1. `처럼 숫자와 점으로 시작하는 줄이 이어진 번호 목록 */
export interface MarkdownOrderedListBlock {
  type: "orderedList";
  /** 첫 항목의 번호. 뒤 항목은 적힌 숫자와 상관없이 1씩 늘려 그려요 */
  start: number;
  items: MarkdownInline[][];
}

export type MarkdownBlock =
  | MarkdownHeadingBlock
  | MarkdownParagraphBlock
  | MarkdownListBlock
  | MarkdownOrderedListBlock;
