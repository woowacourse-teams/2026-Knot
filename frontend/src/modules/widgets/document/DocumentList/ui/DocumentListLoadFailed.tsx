import styled from "@emotion/styled";
import InlineError from "@primitives/ui/InlineError";

interface DocumentListLoadFailedProps {
  /** `다시 시도`를 눌렀을 때 실행할 동작 */
  onRetry: () => void;
}

/**
 * 문서 목록을 불러오지 못했을 때 목록 자리에 보여 주는 안내. 피그마 「문서 목록/불러오기 실패」예요(ERR-09).
 *
 * 화면 전체가 아니라 목록만 실패한 것이라, 제목과 설명은 그대로 두고 목록이 있어야 할 자리에만 안내를 남겨요.
 */
export default function DocumentListLoadFailed({
  onRetry,
}: DocumentListLoadFailedProps) {
  return (
    <Root>
      <InlineError
        message="목록을 불러오지 못했어요. 다시 시도해 주세요."
        onRetry={onRetry}
      />
    </Root>
  );
}

/** 빈 상태 안내와 같은 자리(높이 320px의 가운데)에 놓아요 */
const Root = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 20rem; /* 320px */
`;
