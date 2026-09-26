import styled from "@emotion/styled";
import RecordingCard from "@features/recording/RecordingCard";
import RecorderBar from "@widgets/recording/RecorderBar";

/**
 * 녹음 화면 (`/workspace/:workspaceId/recording`)
 *
 * 들어오면 바로 녹음이 시작되고, 위에는 녹음 조작 바를, 아래에는 녹음 카드를 둬요.
 * 녹음 상태는 워크스페이스 레이아웃이 들고 있어 다른 화면으로 옮겨 가도 녹음이 이어지고,
 * 그동안 하단 독이 녹음 시간과 이 화면으로 돌아오는 칩을 보여줘요.
 *
 * 마이크 권한·녹음 파일 업로드·문서 정리 요청은 아직 연결하지 않은 UI 단계예요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1562-2595 녹음/진행 중
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1635-2763 녹음/다른 화면 이동
 */
export default function RecordingPage() {
  return (
    <Container>
      <PageColumn>
        <RecorderBar />
        <RecordingCard />
      </PageColumn>
    </Container>
  );
}

/** 좌우 여백과 아래 독 자리를 비워 두고 가운데 열을 놓는 자리예요. */
const Container = styled.div`
  display: flex;
  justify-content: center;
  min-height: 100%;
  padding: 1.75rem 1.5rem 7rem; /* 28px 24px 112px — 위는 GNB 아래 116px 지점, 아래는 하단 Dock 자리 */
`;

const PageColumn = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  width: 100%;
  max-width: 60rem; /* 960px */
`;
