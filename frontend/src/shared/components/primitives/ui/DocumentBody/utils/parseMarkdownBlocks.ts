import type {
  MarkdownBlock,
  MarkdownHeadingLevel,
} from "../types/markdownBlock";

interface ParseMarkdownBlocksParams {
  content: string;
}

const HEADING_PATTERN = /^(#{1,3}) (.+)$/;
const LIST_ITEM_PATTERN = /^- (.+)$/;

// 패턴은 `#`을 1~3개만 받지만 `length`의 타입은 number라, 단계 값인지 확인해 타입을 좁혀요
const isHeadingLevel = (level: number): level is MarkdownHeadingLevel =>
  level >= 1 && level <= 3;

export const parseMarkdownBlocks = ({ content }: ParseMarkdownBlocksParams) => {
  const blocks: MarkdownBlock[] = [];
  // 빈 줄이나 다른 블록을 만나기 전까지 이어진 줄을 모아 한 블록으로 내보내요
  let paragraphLines: string[] = [];
  let listItems: string[] = [];

  const flushParagraph = () => {
    if (paragraphLines.length === 0) return;

    blocks.push({
      type: "paragraph",
      lines: paragraphLines.map((line) => [{ type: "text", value: line }]),
      isMuted: false,
    });
    paragraphLines = [];
  };

  const flushList = () => {
    if (listItems.length === 0) return;

    blocks.push({
      type: "list",
      items: listItems.map((item) => [{ type: "text", value: item }]),
    });
    listItems = [];
  };

  // `\r`이 줄 끝에 남으면 제목 패턴의 `.`이 받지 못해 제목이 문단이 되므로 함께 잘라요
  for (const line of content.split(/\r?\n/)) {
    const heading = HEADING_PATTERN.exec(line);
    const headingLevel = heading?.[1].length ?? 0;

    // 단계 값이 아니면 제목으로 보지 않고 아래에서 글자 그대로인 문단이 돼요. 한 줄 때문에 문서 전체가 깨지지 않게 던지지 않아요
    if (heading && isHeadingLevel(headingLevel)) {
      flushParagraph();
      flushList();
      blocks.push({
        type: "heading",
        level: headingLevel,
        inlines: [{ type: "text", value: heading[2] }],
      });
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
