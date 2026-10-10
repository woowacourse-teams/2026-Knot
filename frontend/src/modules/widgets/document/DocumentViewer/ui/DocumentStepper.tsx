import Stepper from "@primitives/ui/Stepper";

import { useDocumentStepper } from "../model/useDocumentStepper";

interface DocumentStepperProps {
  workspaceId: number;
  /** 지금 보고 있는 문서의 ID */
  documentId: number;
  /** 지금 문서가 만들어진 녹음의 ID */
  recordingSessionId: number;
}

/**
 * 같은 녹음에서 나온 문서를 이전 · 다음으로 넘기는 스테퍼(‹ 1 / 3 ›).
 *
 * 한 회의에서 주제마다 문서가 따로 만들어져서, 그 문서들 사이를 옮겨 다녀요. 문서가 하나뿐이어도 1 / 1로 보여줘요.
 * 같은 녹음의 문서 목록은 문서와 다른 API라 여기서 조회해요. 목록을 받기 전이거나 받지 못했으면 아무것도 그리지 않아요.
 */
export default function DocumentStepper({
  workspaceId,
  documentId,
  recordingSessionId,
}: DocumentStepperProps) {
  const { position, goToPrevious, goToNext } = useDocumentStepper({
    workspaceId,
    documentId,
    recordingSessionId,
  });

  if (position === undefined) return null;

  return (
    <div role="group" aria-label="같은 녹음의 문서">
      <Stepper
        current={position.current}
        total={position.total}
        onPrev={goToPrevious}
        onNext={goToNext}
      />
    </div>
  );
}
