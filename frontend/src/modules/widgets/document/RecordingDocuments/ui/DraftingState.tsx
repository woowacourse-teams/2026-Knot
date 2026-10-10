import WritingAnimation from "@primitives/ui/WritingAnimation";

import StateMessage from "./StateMessage";

/**
 * 문서를 정리하는 중일 때의 화면. 펜이 써 내려가는 그림을 보여 주고 버튼은 두지 않아요(STT-R8).
 */
export default function DraftingState() {
  return (
    <StateMessage
      illustration={<WritingAnimation />}
      title="회의 내용을 바탕으로 문서를 정리하고 있어요"
      descriptionLines={[
        "주제별로 문서를 나누는 중이에요.",
        "조금만 기다려 주세요.",
      ]}
    />
  );
}
