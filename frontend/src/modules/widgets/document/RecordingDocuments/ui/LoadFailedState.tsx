import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import DocumentFailedIllustration from "@/assets/illustrations/documentFailed.svg";

import StateMessage from "./StateMessage";

interface LoadFailedStateProps {
  /** 「다시 시도」를 눌렀을 때 실행할 동작 */
  onRetry: () => void;
}

/**
 * 녹음 상태를 불러오지 못해 정리 화면을 보여 주지 못할 때의 화면.
 *
 * 문서 보기의 불러오기 실패 화면(피그마 「문서/불러오기 실패」)과 같은 그림과 문구를 써요.
 * 잘못된 주소 · 없는 녹음 · 볼 수 없는 녹음 · 네트워크와 서버 문제가 모두 이 화면이에요.
 */
export default function LoadFailedState({ onRetry }: LoadFailedStateProps) {
  return (
    <StateMessage
      illustration={<Illustration size={48} />}
      title="문서를 불러오지 못했어요"
      descriptionLines={["잠시 후 다시 시도해 주세요."]}
      button={
        <Button size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      }
    />
  );
}

const Illustration = styled(DocumentFailedIllustration)`
  color: ${({ theme }) => theme.primary};
`;
