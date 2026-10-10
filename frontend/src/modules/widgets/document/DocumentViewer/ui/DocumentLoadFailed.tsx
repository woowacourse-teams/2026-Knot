import styled from "@emotion/styled";
import IllustratedMessageLayout from "@primitives/layout/IllustratedMessageLayout";
import Button from "@primitives/ui/Button";

import DocumentFailedIllustration from "@/assets/illustrations/documentFailed.svg";

interface DocumentLoadFailedProps {
  /** `다시 시도`를 눌렀을 때 실행할 동작 */
  onRetry: () => void;
}

/**
 * 문서를 보여 주지 못할 때의 안내. 피그마 「문서/불러오기 실패」 화면이에요.
 *
 * 네트워크 · 서버 문제뿐 아니라 없는 문서 · 볼 수 없는 문서 · 잘못된 주소에도 이 화면 하나를 써요.
 * 그림 · 제목 · 설명 · 버튼의 배치는 `IllustratedMessageLayout`이 맡고, 여기서는 글꼴과 색, 놓이는 자리만 정해요.
 * 문서 화면에서는 문서 영역의 높이만큼 늘어나므로, 시안처럼 안내를 그 세로 가운데에 놓아요.
 * 낭독기가 바로 읽도록 `role="alert"`를 붙였어요.
 */
export default function DocumentLoadFailed({
  onRetry,
}: DocumentLoadFailedProps) {
  return (
    <Root role="alert">
      <IllustratedMessageLayout
        illustration={<Illustration aria-hidden="true" />}
        title={<Title>문서를 불러오지 못했어요</Title>}
        description={<Description>잠시 후 다시 시도해 주세요.</Description>}
        button={
          <Button size="sm" onClick={onRetry}>
            다시 시도
          </Button>
        }
      />
    </Root>
  );
}

/** 피그마 State/DocNotCreated: 묶음 폭 518px, 화면 세로 가운데 */
const Root = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: center;
  width: 100%;
  max-width: 32.375rem; /* 518px */
  margin: 0 auto;
`;

const Illustration = styled(DocumentFailedIllustration)`
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.primary};
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.primary};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.body02};
  color: ${({ theme }) => theme.neutral[700]};
`;
