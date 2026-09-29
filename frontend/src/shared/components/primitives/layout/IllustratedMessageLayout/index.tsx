import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import type { ReactNode } from "react";

interface IllustratedMessageLayoutProps {
  /** 맨 위에 놓을 그림. 크기와 색은 쓰는 쪽이 정해요 */
  illustration: ReactNode;
  /** 제목. 글꼴 · 색 · 제목 태그는 쓰는 쪽이 정해요 */
  title: ReactNode;
  /** 제목 아래 설명. 줄을 어떻게 나눌지도 쓰는 쪽이 정해요 */
  description: ReactNode;
  /** 설명 아래 놓을 버튼. 문구와 동작은 쓰는 쪽이 정해요 */
  button?: ReactNode;
}

/**
 * 그림 · 제목 · 설명 · 버튼을 세로로 가운데 놓는 배치.
 *
 * 간격(그림과 글 사이 20, 제목과 설명 사이 16, 글과 버튼 사이 20)과 가운데 정렬만 가져요.
 * 글꼴 · 색 · 제목 태그는 쓰는 쪽이 정해서 넘기므로, 같은 배치를 다른 글꼴로도 쓸 수 있어요.
 *
 * @example
 * <IllustratedMessageLayout
 *   illustration={<FailedIllustration />}
 *   title={<Title>문서를 만들지 못했어요</Title>}
 *   description={
 *     <Description>
 *       <p>녹음은 보관해 두었어요.</p>
 *       <p>다시 시도하거나, 홈의 진행 중인 녹음에서 나중에 다시 시도할 수 있어요.</p>
 *     </Description>
 *   }
 *   button={<Button onClick={retry}>다시 시도</Button>}
 * />
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-16290 State/Drafting}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2262-33172 State/DocNotCreated}
 */
export default function IllustratedMessageLayout({
  illustration,
  title,
  description,
  button,
}: IllustratedMessageLayoutProps) {
  // 칸 구성은 같고 간격만 큰 곳(예: 에러 화면)에 쓰게 되면 컴포넌트를 새로 만들지 않고 `size` prop으로 나눠요.
  // 지금 간격을 기본값으로 두면 이미 쓰고 있는 곳은 고치지 않아도 돼요.
  return (
    <Stack align="center" gap={1.25} /* 20px */>
      <IllustrationWrapper>{illustration}</IllustrationWrapper>

      <TextContainer align="center" gap={1} /* 16px */>
        {title}
        {description}
      </TextContainer>

      {button}
    </Stack>
  );
}

// flex로 두어야 그림 아래에 글자 줄 높이만큼 빈틈이 생기지 않아요
const IllustrationWrapper = styled.div`
  display: flex;
`;

const TextContainer = styled(Stack)`
  text-align: center;
  overflow-wrap: break-word;
`;
