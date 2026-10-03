import styled from "@emotion/styled";
import type { ElementType, HTMLAttributes } from "react";

interface StackProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * 실제로 그릴 태그. 기본값은 `div`예요.
   * `main`, `section`, `ul`처럼 의미가 있는 태그가 필요할 때 씁니다.
   */
  as?: ElementType;

  /**
   * 교차축(가로) 정렬.
   * @default "stretch"
   */
  align?: keyof typeof ALIGN;

  /**
   * 주축(세로) 정렬.
   * @default "start"
   */
  justify?: keyof typeof JUSTIFY;

  /**
   * 자식 사이의 간격.
   * 숫자를 넘기면 `rem`으로 붙고, 문자열은 `16px`처럼 단위까지 그대로 적용돼요.
   */
  gap?: number | string;
}

const ALIGN = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  stretch: "stretch",
} as const;

const JUSTIFY = {
  start: "flex-start",
  center: "center",
  end: "flex-end",
  between: "space-between",
} as const;

/**
 * 자식을 세로로 쌓는 레이아웃 프리미티브.
 *
 * 동작 규칙은 스토리북 `Shared/Layout/Stack`에서 확인해요.
 */
export default function Stack({ align, justify, gap, ...props }: StackProps) {
  return <Root $align={align} $justify={justify} $gap={gap} {...props} />;
}

const Root = styled.div<{
  $align?: keyof typeof ALIGN;
  $justify?: keyof typeof JUSTIFY;
  $gap?: number | string;
}>`
  display: flex;
  flex-direction: column;
  ${({ $align }) => $align && `align-items: ${ALIGN[$align]};`}
  ${({ $justify }) => $justify && `justify-content: ${JUSTIFY[$justify]};`}
  ${({ $gap }) =>
    $gap !== undefined &&
    `gap: ${typeof $gap === "number" ? `${$gap}rem` : $gap};`}
`;
