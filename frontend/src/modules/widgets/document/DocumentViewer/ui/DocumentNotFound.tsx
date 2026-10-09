import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import DocumentEmptyIllustration from "@/assets/illustrations/documentEmpty.svg";

import DocumentStateMessage from "./DocumentStateMessage";

interface DocumentNotFoundProps {
  /** `홈으로`를 눌렀을 때 실행할 동작 */
  onGoHome: () => void;
}

/**
 * 문서를 열 수 없을 때(없는 문서 · 권한 없음 · 잘못된 주소) 보여주는 임시 안내.
 *
 * 이 상황의 시안이 아직 없어, 같은 문서 흐름에서 홈으로 보내는 상태 화면(피그마 「문서/정리할 내용 없음」)의 배치를 빌렸어요.
 * 기획(ERR-R2)의 공통 "잘못된 요청" 화면이나 이 상황의 시안이 생기면 그것으로 바꿔요.
 */
export default function DocumentNotFound({ onGoHome }: DocumentNotFoundProps) {
  return (
    <DocumentStateMessage
      illustration={<Illustration aria-hidden="true" />}
      title="문서를 찾을 수 없어요"
      description="주소가 잘못됐거나 볼 수 없는 문서예요."
      button={
        <Button size="sm" onClick={onGoHome}>
          홈으로
        </Button>
      }
    />
  );
}

const Illustration = styled(DocumentEmptyIllustration)`
  width: 3rem; /* 48px */
  height: 3rem;
  color: ${({ theme }) => theme.primary};
`;
