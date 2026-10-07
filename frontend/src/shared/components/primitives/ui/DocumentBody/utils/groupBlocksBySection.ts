import type { MarkdownBlock } from "../types/markdownBlock";

/**
 * 블록을 구역으로 묶어요. 제목 블록마다 새 구역을 시작하고, 첫 제목 앞의 블록은 제목 없는 구역 하나가 돼요.
 *
 * 피그마의 `Sections`(구역 사이) · `Section`(제목과 내용 사이) 간격을 부모의 gap으로 그리려고 나눠요.
 */
export const groupBlocksBySection = (blocks: MarkdownBlock[]) => {
  const sections: MarkdownBlock[][] = [];

  for (const block of blocks) {
    if (block.type === "heading" || sections.length === 0) {
      sections.push([block]);
      continue;
    }

    sections[sections.length - 1].push(block);
  }

  return sections;
};
