import type {
  MarkdownBlock,
  MarkdownHeadingBlock,
  MarkdownHeadingLevel,
  MarkdownInline,
} from "../types/markdownBlock";

interface ParseMarkdownBlocksParams {
  content: string;
  /** 문단 전체가 이 문장 중 하나와 같으면 그 문단을 흐리게 그려요. 어떤 문장인지는 부르는 쪽이 정해요 */
  mutedLines?: string[];
}

const HEADING_PATTERN = /^(#{1,3}) (.+)$/;
// 들여쓴 `- `(Markdown의 하위 목록)도 깊이를 따지지 않고 같은 목록의 항목으로 받아요
const LIST_ITEM_PATTERN = /^\s*- (.+)$/;

// 패턴은 `#`을 1~3개만 받지만 `length`의 타입은 number라, 단계 값인지 확인해 타입을 좁혀요
const isHeadingLevel = (level: number): level is MarkdownHeadingLevel =>
  level >= 1 && level <= 3;

// `+?`는 가장 가까운 닫는 `**`에서 멈추게 해요. 없으면 `**a** b **c**`가 굵게 하나로 묶여요
const BOLD_PATTERN = /\*\*(.+?)\*\*/g;

/** 한 줄을 `**굵게**` 조각과 글자 조각으로 나눠요. 그 밖의 기호는 글자 조각에 그대로 남아요 */
const parseInlines = (text: string) => {
  const inlines: MarkdownInline[] = [];
  let lastIndex = 0;

  for (const match of text.matchAll(BOLD_PATTERN)) {
    if (match.index > lastIndex) {
      inlines.push({ type: "text", value: text.slice(lastIndex, match.index) });
    }
    inlines.push({ type: "bold", value: match[1] });
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    inlines.push({ type: "text", value: text.slice(lastIndex) });
  }

  return inlines;
};

/** 제목 줄이면 제목 블록을, 아니면 null을 돌려줘요 */
const toHeadingBlock = (line: string) => {
  const heading = HEADING_PATTERN.exec(line);
  const headingLevel = heading?.[1].length ?? 0;

  // 단계 값이 아니면 제목으로 보지 않아 글자 그대로인 문단이 돼요. 한 줄 때문에 문서 전체가 깨지지 않게 던지지 않아요
  if (!heading || !isHeadingLevel(headingLevel)) return null;

  return {
    type: "heading",
    level: headingLevel,
    inlines: parseInlines(heading[2]),
  } satisfies MarkdownHeadingBlock;
};

export const parseMarkdownBlocks = ({
  content,
  mutedLines = [],
}: ParseMarkdownBlocksParams) => {
  const blocks: MarkdownBlock[] = [];
  // 빈 줄이나 다른 블록을 만나기 전까지 이어진 줄을 모아 한 블록으로 내보내요
  let paragraphLines: string[] = [];
  let listItems: string[] = [];

  const flushParagraph = () => {
    if (paragraphLines.length === 0) return;

    blocks.push({
      type: "paragraph",
      lines: paragraphLines.map(parseInlines),
      isMuted: mutedLines.includes(paragraphLines.join("\n").trim()),
    });
    paragraphLines = [];
  };

  const flushList = () => {
    if (listItems.length === 0) return;

    blocks.push({
      type: "list",
      items: listItems.map(parseInlines),
    });
    listItems = [];
  };

  // `\r`이 줄 끝에 남으면 제목 패턴의 `.`이 받지 못해 제목이 문단이 되므로 함께 잘라요
  for (const line of content.split(/\r?\n/)) {
    const headingBlock = toHeadingBlock(line);

    if (headingBlock) {
      flushParagraph();
      flushList();
      blocks.push(headingBlock);
      continue;
    }

    const listItem = LIST_ITEM_PATTERN.exec(line);

    if (listItem) {
      flushParagraph();
      listItems.push(listItem[1]);
      continue;
    }

    if (line.trim() === "") {
      flushParagraph();
      flushList();
      continue;
    }

    flushList();
    paragraphLines.push(line);
  }

  flushParagraph();
  flushList();

  return blocks;
};
