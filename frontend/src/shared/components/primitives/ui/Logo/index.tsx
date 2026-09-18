import styled from "@emotion/styled";
import type { SVGProps } from "react";

import LogoImage from "@/assets/logos/logo.svg";

interface LogoProps extends Omit<SVGProps<SVGSVGElement>, "width" | "height"> {
  /**
   * 로고의 가로 길이. 세로는 비율에 맞춰 자동으로 정해져요.
   * 숫자를 넘기면 `rem`으로 붙고, 문자열은 `113px`처럼 단위까지 그대로 적용돼요.
   * @default 7.0625 // 113px
   */
  width?: number | string;
}

/** `logo.svg`의 viewBox 비율. 가로 값만 정하면 세로는 여기서 따라옵니다. */
const ASPECT_RATIO = "117.56 / 41.04";

/**
 * knot 워드마크 로고.
 *
 * SVGR이 `<svg>`에 `width`·`height`를 같은 값으로 박아두기 때문에 그대로 쓰면
 * 정사각형으로 찌그러져요. 그래서 CSS로 덮어쓰고 `aspect-ratio`로 비율을 잡습니다.
 * 이 처리를 화면마다 반복하지 않으려고 컴포넌트로 감쌌어요.
 *
 * 색은 `currentColor`가 아니라 `neutral[800]`로 고정돼 있습니다.
 * 다른 색이 필요하면 `color`를 넘기세요.
 *
 * @example
 * <Logo />
 * <Logo width={5} />
 */
export default function Logo({ width = 7.0625, ...props }: LogoProps) {
  return (
    <Root
      $width={typeof width === "number" ? `${width}rem` : width}
      {...props}
    />
  );
}

/**
 * `logo.svg`가 `fill="currentColor"`로 그려져 있어서 `color`가 그대로 로고 색이 돼요.
 * CSS는 SVG 속성보다 세기 때문에, 넘어온 `color`를 여기서 다시 써줘야 반영됩니다.
 */
const Root = styled(LogoImage, {
  shouldForwardProp: (prop) => prop !== "$width",
})<{ $width: string }>`
  width: ${({ $width }) => $width};
  height: auto;
  aspect-ratio: ${ASPECT_RATIO};
  color: ${({ theme, color }) => color ?? theme.neutral[800]};
`;
