import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import DocumentFailedIllustration from "@/assets/illustrations/documentFailed.svg";

import StateMessage from "./StateMessage";

interface DocumentFailedStateProps {
  /** 「다시 시도」를 눌렀을 때. 다시 시도할 수 없는 실패면 넘기지 않아요 */
  onRetry?: () => void;
  /** 다시 시도 요청의 응답을 기다리는 중인지. 그동안 버튼을 누를 수 없어요 */
  isRetrying?: boolean;
  /** 「홈으로」를 눌렀을 때 */
  onGoHome: () => void;
}

/**
 * 문서를 만들지 못했을 때의 화면(STT-R22).
 *
 * 문서 만들기 단계의 실패는 녹음과 원문이 보관되어 있어, 그 사실을 알리고 「다시 시도」를 둬요.
 * 다시 시도할 수 없는 실패(`onRetry`가 없음)는 설명 없이 「홈으로」만 둬요.
 * 이 경우의 문구는 기획에 아직 없고, 녹음이 보관되어 있는지도 경우마다 달라서예요.
 */
export default function DocumentFailedState({
  onRetry,
  isRetrying = false,
  onGoHome,
}: DocumentFailedStateProps) {
  const illustration = <Illustration size={48} />;
  const title = "문서를 만들지 못했어요";

  if (onRetry === undefined) {
    return (
      <StateMessage
        illustration={illustration}
        title={title}
        button={
          <Button size="sm" onClick={onGoHome}>
            홈으로
          </Button>
        }
      />
    );
  }

  return (
    <StateMessage
      illustration={illustration}
      title={title}
      descriptionLines={[
        "녹음은 보관해 두었어요.",
        "다시 시도하거나, 홈의 진행 중인 녹음에서 나중에 다시 시도할 수 있어요.",
      ]}
      button={
        <Button size="sm" isLoading={isRetrying} onClick={onRetry}>
          다시 시도
        </Button>
      }
    />
  );
}

const Illustration = styled(DocumentFailedIllustration)`
  color: ${({ theme }) => theme.primary};
`;
