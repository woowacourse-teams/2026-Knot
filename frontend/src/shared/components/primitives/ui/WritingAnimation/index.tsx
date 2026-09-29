import styled from "@emotion/styled";

// 48px로 보여주므로 3배 밀도 화면까지 선명한 144×144로 줄여 두었어요
import writingGif from "@/assets/illustrations/writing.gif";
import WritingIllustration from "@/assets/illustrations/writing.svg";

/**
 * 펜이 문서에 글줄을 한 줄씩 써 내려가는 48×48 그림.
 *
 * 디자이너가 만든 GIF(3.6초 반복)를 보여주고,
 * 움직임 줄이기(`prefers-reduced-motion: reduce`)를 켠 사용자에게는 같은 그림의 멈춘 판을 보여줘요.
 * 꾸밈용 그림이라 낭독기에서 읽지 않아요.
 *
 * @example
 * <IllustratedMessageLayout illustration={<WritingAnimation />} title={…} description={…} />
 */
export default function WritingAnimation() {
  return (
    <>
      <MovingImage src={writingGif} alt="" />
      <StillImage />
    </>
  );
}

// GIF는 CSS로 멈출 수 없어서, 두 그림을 모두 그려 두고 미디어 쿼리로 하나만 보여줘요.
// 그래서 움직임 줄이기를 켜도 숨긴 GIF는 내려받아요.
const MovingImage = styled.img`
  display: block;
  width: 3rem; /* 48px */
  height: 3rem;

  @media (prefers-reduced-motion: reduce) {
    display: none;
  }
`;

// <img>로 넣으면 SVG 안의 currentColor가 바깥 color를 받지 못해 검정이 되므로 svgr 컴포넌트로 넣어요
const StillImage = styled(WritingIllustration)`
  display: none;
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.primary};

  @media (prefers-reduced-motion: reduce) {
    display: block;
  }
`;
