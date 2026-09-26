import styled from "@emotion/styled";

import writingGif from "@/assets/illustrations/writing.gif";
import WritingIllustration from "@/assets/illustrations/writing.svg";

/**
 * 펜이 문서에 글줄을 한 줄씩 써 내려가는 48×48 그림.
 *
 * 디자이너가 만든 GIF(3.6초 반복)를 그대로 보여줘요. GIF는 CSS로 멈출 수 없어서,
 * 움직임 줄이기(`prefers-reduced-motion: reduce`)를 켠 사용자에게는 같은 그림의 멈춘 SVG를 대신 보여줘요.
 * 둘 다 그려 두고 미디어 쿼리로 하나만 보이게 하므로, 숨긴 쪽의 GIF도 내려받아요.
 *
 * SVG는 `<img>`로 넣으면 `currentColor`가 검정이 되므로 svgr 컴포넌트로 넣어요.
 * 꾸밈용 그림이라 낭독기에서 읽지 않아요.
 */
export default function WritingAnimation() {
  return (
    <>
      <MovingImage src={writingGif} alt="" />
      <StillImage />
    </>
  );
}

const MovingImage = styled.img`
  display: block;
  width: 3rem; /* 48px */
  height: 3rem;

  @media (prefers-reduced-motion: reduce) {
    display: none;
  }
`;

const StillImage = styled(WritingIllustration)`
  display: none;
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.primary};

  @media (prefers-reduced-motion: reduce) {
    display: block;
  }
`;
