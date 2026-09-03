import styled from "@emotion/styled";
import Spinner from "@primitives/ui/Spinner";

import { useWorkspaceInviteLinkGate } from "./model/useWorkspaceInviteLinkGate";

/**
 * 초대 링크 진입 게이트.
 *
 * `/invite/:token`으로 들어온 사용자를 붙잡아 두고 토큰을 판정하는 동안 스피너만 보여줘요.
 * 자기 화면은 없고, 판정이 끝나면 입장 확인(`/workspace/:workspaceId/join`)이나
 * 초대 링크 오류(`/join-error`)로 `replace` 이동해 뒤로 가기 때 이 라우트로 되돌아오지 않게 합니다.
 *
 * 마운트되어 있는 동안은 곧 판정 중이므로 스피너를 조건 없이 그리고,
 * 스크린리더에는 `role="status"` 안의 숨긴 문구로 확인 중임을 알려요.
 *
 * 판정은 미리보기 조회(`GET /invitations/{token}`)로 해요. 통과하면 응답의 workspaceId로 가면서
 * 토큰과 워크스페이스 이름을 라우터 state로 넘기고, 어떤 이유로든 실패하면 오류 화면으로 보내요.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 위젯은 스피너 자리만 잡아요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10180 초대 링크로 워크스페이스 입장}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10148 올바르지 않은 초대 링크 접근}
 */
export default function WorkspaceInviteLinkGate() {
  useWorkspaceInviteLinkGate();

  return (
    <Container role="status">
      <Spinner />
      <StatusText>초대 링크를 확인하고 있어요</StatusText>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  justify-content: center;
  color: ${({ theme }) => theme.neutral[800]};
`;

/** 화면에서는 감추고 보조기기에만 읽히는 문구 */
const StatusText = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
`;
