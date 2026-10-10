import styled from "@emotion/styled";
import { Fragment } from "react";

import type { MarkdownInline } from "../types/markdownBlock";

interface InlinesProps {
  /** 한 줄을 이루는 글 조각 */
  inlines: MarkdownInline[];
}

/**
 * 글 조각을 차례로 그려요. 굵게 조각만 `strong`으로 감싸고, 글자 조각은 그대로 출력해요.
 */
export default function Inlines({ inlines }: InlinesProps) {
  return (
    <>
      {inlines.map((inline, index) =>
        // 본문은 읽기 전용이라 렌더 중에 조각 순서가 바뀌지 않아 위치를 key로 써요
        inline.type === "bold" ? (
          <Bold key={index}>{inline.value}</Bold>
        ) : (
          <Fragment key={index}>{inline.value}</Fragment>
        ),
      )}
    </>
  );
}

// 피그마의 굵은 글은 Label01(600)이에요. 제목 안의 굵게는 제목 크기를 유지해야 해서 글자 토큰 대신 굵기만 바꿔요
const Bold = styled.strong`
  font-weight: 600;
`;
