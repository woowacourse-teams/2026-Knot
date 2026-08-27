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
 * `display: flex`와 `flex-direction: column`을 매번 적는 대신 씁니다.
 * 가로로 나열할 때는 `Row`를 쓰세요.
 *
 * ## `gap`과 `Spacing` 중 무엇을 쓸까
 *
 * - `gap`: 자식들이 **모두 같은 간격**으로 나열될 때. 리스트, 카드 목록, 폼 필드 여러 개.
 *   자식이 조건부로 사라지면 간격도 함께 사라져요.
 * - `Spacing`: 간격이 **자리마다 다를 때**. 여러 개의 `gap`을 만들려고 그룹 `div`를
 *   겹겹이 만드는 상황이면 `Spacing`이 읽기 쉬워요.
 *
 * @example 같은 간격 — gap
 * <Stack gap={1}>
 *   {todos.map((todo) => <TodoItem key={todo.id} {...todo} />)}
 * </Stack>
 *
 * @example 자리마다 다른 간격 — Spacing
 * <Stack align="center">
 *   <Logo />
 *   <Spacing size={2.5} />
 *   <Title />
 *   <Spacing size={4.5} />
 *   <Button />
 * </Stack>
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
