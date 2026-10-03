import styled from "@emotion/styled";
import Spinner from "@primitives/ui/Spinner";

import { useWorkspaceInviteLinkGate } from "./model/useWorkspaceInviteLinkGate";

/**
 * 초대 링크 진입 게이트. `/invite/:token`의 토큰을 판정하는 동안 스피너만 보여줘요.
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceInviteLinkGate`에서 확인해요.
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
