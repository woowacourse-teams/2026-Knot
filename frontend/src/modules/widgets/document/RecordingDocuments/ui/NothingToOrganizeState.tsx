import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import DocumentEmptyIllustration from "@/assets/illustrations/documentEmpty.svg";

import StateMessage from "./StateMessage";

interface NothingToOrganizeStateProps {
  /** 「홈으로」를 눌렀을 때 */
  onGoHome: () => void;
}

/**
 * 녹음에 문서로 만들 내용이 없었을 때의 화면(STT-R16).
 *
 * 오류가 아니라 결과라서 경고색과 「다시 시도」를 두지 않아요. 같은 녹음으로는 결과가 같기 때문이에요.
 */
export default function NothingToOrganizeState({
  onGoHome,
}: NothingToOrganizeStateProps) {
  return (
    <StateMessage
      illustration={<Illustration size={48} />}
      title="문서로 만들 내용이 없었어요"
      descriptionLines={[
        "대화가 너무 짧거나 정리할 논의를 찾지 못했어요.",
        "녹음은 따로 저장하지 않았어요.",
      ]}
      button={
        <Button size="sm" onClick={onGoHome}>
          홈으로
        </Button>
      }
    />
  );
}

const Illustration = styled(DocumentEmptyIllustration)`
  color: ${({ theme }) => theme.primary};
`;
