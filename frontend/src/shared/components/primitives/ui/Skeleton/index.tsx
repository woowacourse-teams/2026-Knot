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
 * 동작 규칙은 스토리북 `Shared/Skeleton`에서 확인해요.
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
