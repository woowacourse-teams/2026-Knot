import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import DocumentFailedIllustration from "@/assets/illustrations/documentFailed.svg";

import DocumentStateMessage from "./DocumentStateMessage";

interface DocumentLoadFailedProps {
  /** `다시 시도`를 눌렀을 때 실행할 동작 */
  onRetry: () => void;
}

/**
 * 문서를 불러오지 못했을 때(네트워크 · 서버 문제) 보여주는 임시 안내.
 *
 * 이 상황의 시안이 아직 없어, 같은 문서 흐름에서 다시 시도하는 상태 화면(피그마 「문서/정리 실패」)의 배치를 빌렸어요.
 * 기획(ERR-R3 · R4)의 공통 오류 화면이나 이 상황의 시안이 생기면 그것으로 바꿔요.
 */
export default function DocumentLoadFailed({
  onRetry,
}: DocumentLoadFailedProps) {
  return (
    <DocumentStateMessage
      illustration={<Illustration aria-hidden="true" />}
      title="문서를 불러오지 못했어요"
      description="잠시 후 다시 시도해 주세요."
      button={
        <Button size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      }
    />
  );
}

const Illustration = styled(DocumentFailedIllustration)`
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.primary};
`;
