import styled from "@emotion/styled";

import Block from "./ui/Block";
import { groupBlocksBySection } from "./utils/groupBlocksBySection";
import { parseMarkdownBlocks } from "./utils/parseMarkdownBlocks";

interface DocumentBodyProps {
  /** 그릴 Markdown 본문 */
  content: string;
  /** 문단 전체가 이 문장 중 하나와 같으면 흐리게 그려요. 어떤 문장인지는 쓰는 쪽이 정해요 */
  mutedLines?: string[];
}

/**
 * 문서 본문 Markdown을 읽기 전용으로 그리는 본문.
 *
 * 정한 문법(`#` · `##` · `###` · `-` · `**굵게**`)만 제목 · 목록 · 굵게로 그리고, 나머지는 글자 그대로 보여줘요.
 * HTML 문자열로 바꾸지 않고 React 요소로 그리므로 본문에 HTML이 섞여 와도 실행되지 않고 글자로 보여요.
 */
export default function DocumentBody({
  content,
  mutedLines,
}: DocumentBodyProps) {
  const sections = groupBlocksBySection(
    parseMarkdownBlocks({ content, mutedLines }),
  );

  return (
    <Container>
      {sections.map((section, sectionIndex) => (
        // 본문은 읽기 전용이라 렌더 중에 구역 · 블록 순서가 바뀌지 않아 위치를 key로 써요
        <Section key={sectionIndex}>
          {section.map((block, blockIndex) => (
            <Block key={blockIndex} block={block} />
          ))}
        </Section>
      ))}
    </Container>
  );
}

/** 피그마 `Sections`: 구역 사이 24px */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
  overflow-wrap: break-word;
`;

/** 피그마 `Section`: 구역 제목과 그 아래 내용 사이 8px */
const Section = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
`;
