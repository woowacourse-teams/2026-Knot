import styled from "@emotion/styled";

interface SpacingProps {
  /** 자식을 받지 않습니다. 간격을 벌릴 형제 요소 사이에 놓아 쓰세요. */
  children?: never;
  /**
   * 간격을 벌릴 방향.
   * `vertical`은 위아래, `horizontal`은 좌우로 벌립니다.
   * @default "vertical"
   */
  direction?: "vertical" | "horizontal";
  /**
   * 벌릴 간격.
   * 숫자를 넘기면 `rem`으로 붙고, 문자열은 `16px`, `8%`처럼 단위까지 그대로 적용돼요.
   */
  size: string | number;
}

/**
 * 형제 요소 사이에 빈 간격을 두는 레이아웃 프리미티브.
 *
 * 자세한 내용은 스토리북 `Shared/Layout/Spacing`에서 확인해요.
 */
export default styled.div<SpacingProps>`
  flex-shrink: 0;
  ${({ direction = "vertical", size }) => {
    const value = typeof size === "number" ? `${size}rem` : size;
    return direction === "vertical" ? `height: ${value};` : `width: ${value};`;
  }}
`;
