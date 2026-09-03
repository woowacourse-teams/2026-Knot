import styled from "@emotion/styled";
import { useState } from "react";

import AvatarGlyphIcon from "@/assets/icons/avatarGlyph.svg";

interface AvatarProps {
  /** 접근성 이름. 누구의 아바타인지 알려줘요 */
  label: string;
  /** 프로필 이미지 URL. 없거나 불러오지 못하면 이름 첫 글자로 대신해요 */
  src?: string;
  /** 이미지를 못 쓸 때 첫 글자를 딸 이름. 이것도 없으면 기본 글리프를 그려요 */
  name?: string;
  /** 지름(px). 사이드바 워크스페이스는 24, GNB는 32 */
  size?: number;
  className?: string;
}

/**
 * 원형 아바타.
 *
 * 프로필 이미지 → 이름 첫 글자 → 기본 글리프 순으로 그릴 수 있는 것을 그려요.
 * 이미지 주소를 받았더라도 불러오기에 실패하면 첫 글자로 내려가므로,
 * GitHub 프로필 이미지가 없거나 주소가 깨져도 빈 원이 남지 않아요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516 Avatar}
 */
export default function Avatar({
  label,
  src,
  name,
  size = 32,
  className,
}: AvatarProps) {
  const [isImageBroken, setIsImageBroken] = useState(false);

  const initial = name?.trim().charAt(0) ?? "";
  const canShowImage = Boolean(src) && !isImageBroken;

  return (
    <Root
      className={className}
      role="img"
      aria-label={label}
      $size={size}
      $hasImage={canShowImage}
    >
      {canShowImage ? (
        <Image src={src} alt="" onError={() => setIsImageBroken(true)} />
      ) : initial ? (
        <Initial $size={size}>{initial}</Initial>
      ) : (
        <AvatarGlyphIcon size={Math.round(size * 0.7)} />
      )}
    </Root>
  );
}

const Root = styled.span<{ $size: number; $hasImage: boolean }>`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: ${({ $size }) => $size / 16}rem;
  height: ${({ $size }) => $size / 16}rem;
  border-radius: 50%;
  overflow: hidden;
  background-color: ${({ theme, $hasImage }) =>
    $hasImage ? theme.neutral[200] : theme.neutral[800]};
  color: ${({ theme }) => theme.neutral[0]};
`;

const Image = styled.img`
  width: 100%;
  height: 100%;
  object-fit: cover;
`;

/** 24px 아바타는 caption01, 32px 아바타는 caption02 크기를 써요 */
const Initial = styled.span<{ $size: number }>`
  ${({ theme, $size }) =>
    $size >= 32 ? theme.text.caption02 : theme.text.caption01};
`;
