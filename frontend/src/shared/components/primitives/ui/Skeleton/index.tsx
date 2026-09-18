import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";

interface SkeletonProps {
  /** 가로 길이. 숫자면 rem, 문자열이면 그대로 씁니다 */
  width?: number | string;
  /** 세로 길이(rem). 글줄 자리는 0.625(10px)가 기본이에요 */
  height?: number;
  /** 모서리 둥글기(rem). 기본은 알약 모양이에요 */
  radius?: number;
  className?: string;
}

/**
 * 아직 오지 않은 내용의 자리를 대신 채워 두는 회색 덩어리.
 *
 * 빈 화면을 보여 주는 대신 곧 들어올 글의 모양을 미리 잡아 둬, 내용이 도착해도 화면이 덜 튑니다.
 * 무엇이 몇 줄 들어올지는 쓰는 쪽이 정하므로 이 컴포넌트는 한 덩어리만 그립니다.
 *
 * 배경이 은은하게 밝아졌다 어두워지기만 하고 번쩍이는 빛줄기는 넣지 않았어요.
 * 종이 같은 배색을 쓰는 화면이라 강한 반짝임은 튀기 때문입니다.
 */
export default function Skeleton({
  width = "100%",
  height = 0.625,
  radius = 62.4375,
  className,
}: SkeletonProps) {
  return (
    <Block
      className={className}
      aria-hidden="true"
      $width={typeof width === "number" ? `${width}rem` : width}
      $height={height}
      $radius={radius}
    />
  );
}

const pulse = keyframes`
  0%, 100% {
    opacity: 1;
  }

  50% {
    opacity: 0.55;
  }
`;

const Block = styled.span<{ $width: string; $height: number; $radius: number }>`
  display: block;
  flex-shrink: 0;
  width: ${({ $width }) => $width};
  height: ${({ $height }) => $height}rem;
  border-radius: ${({ $radius }) => $radius}rem;
  background-color: ${({ theme }) => theme.neutral[200]};
  animation: ${pulse} 1.6s ease-in-out infinite;

  @media (prefers-reduced-motion: reduce) {
    animation: none;
  }
`;
