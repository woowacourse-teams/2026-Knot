import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import Divider from "@primitives/ui/Divider";
import Input from "@primitives/ui/Input";
import Spinner from "@primitives/ui/Spinner";

import CheckIcon from "@/assets/icons/check.svg";
import CopyIcon from "@/assets/icons/copy.svg";

import { useWorkspaceInvite } from "./model/useWorkspaceInvite";

/**
 * 팀원 초대 카드.
 *
 * 워크스페이스를 만든 직후 참여 코드와 초대 링크를 보여주고 각각 클립보드로 복사하게 해요.
 * 코드 상자는 아이콘이 check로, 링크 버튼은 `복사됨`으로 잠시 바뀌어 결과를 알리고,
 * 클립보드에 쓰지 못하면 화면 변화 없이 조용히 넘어갑니다.
 *
 * 코드와 링크는 현재 `:workspaceId`의 활성 초대 조회 응답에서 오고, 응답 전에는 복사를 막아요.
 * 조회가 401이면 로그인으로, 403·404면 워크스페이스 선택 화면으로 돌려보냅니다.
 * `다음`은 현재 `:workspaceId`로 노션 연동(`/workspace/:workspaceId/notion-connection`)으로 이어져요.
 *
 * 로고와 중앙 배치는 `CenteredLayout`이 맡으므로 이 카드는 자기 모양만 그려요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1868 새 워크스페이스 생성/참여 코드 및 링크 공유}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=679-3120 새 워크스페이스 생성/참여 코드 복사}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=704-3182 새 워크스페이스 생성/참여 코드 복사 완료}
 */
export default function WorkspaceInviteCard() {
  const {
    inviteCode,
    isLoading,
    isCodeCopied,
    isLinkCopied,
    displayInviteLink,
    handleCopyLink,
    handleNext,
    handleCopyCode,
  } = useWorkspaceInvite();

  const isInviteReady = inviteCode !== undefined;

  return (
    <Container>
      <Content>
        <Header>
          <Title>팀원을 초대하세요</Title>
          <Description>참여 코드나 링크를 팀원에게 공유하세요</Description>
        </Header>

        <ShareOptions>
          <CodeBox
            type="button"
            aria-label={
              isInviteReady ? `참여 코드 ${inviteCode} 복사` : "참여 코드 복사"
            }
            aria-busy={isLoading}
            disabled={!isInviteReady}
            onClick={handleCopyCode}
          >
            {isLoading ? <Spinner /> : <Code>{inviteCode}</Code>}
            {!isLoading && (isCodeCopied ? <CheckIcon /> : <CopyIcon />)}
          </CodeBox>

          <Divider label="또는" />

          <LinkField>
            <LinkInput
              variant="copy"
              status="filled"
              value={displayInviteLink ?? ""}
              readOnly
              aria-label="초대 링크"
            />
            <CopyButton
              size="sm"
              variant={isLinkCopied ? "accent" : "filled"}
              isLoading={isLoading}
              disabled={!isInviteReady}
              onClick={handleCopyLink}
            >
              {isLinkCopied ? (
                <>
                  <CheckIcon />
                  복사됨
                </>
              ) : (
                "복사"
              )}
            </CopyButton>
          </LinkField>
        </ShareOptions>
      </Content>

      <Button size="lg" isFullWidth onClick={handleNext}>
        다음
      </Button>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3rem; /* 48px */
  width: 100%;
  max-width: 28.75rem; /* 460px */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.75rem; /* 28px */
  width: 100%;
`;

const Header = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem; /* 12px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h1`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.heading02};
`;

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[600]};
  ${({ theme }) => theme.text.body01};
`;

const ShareOptions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem; /* 12px */
  width: 100%;
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=691-1746 Card/CodeBox} */
const CodeBox = styled.button`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 5rem; /* 80px */
  border: 1.5px dashed ${({ theme }) => theme.sub.accent[500]};
  border-radius: 0.875rem; /* 14px */
  background-color: ${({ theme }) => theme.sub.accent[100]};
  color: ${({ theme }) => theme.sub.accent[500]};

  & > svg {
    position: absolute;
    top: 50%;
    right: 1.25rem; /* 20px */
    width: 1.5rem; /* 24px */
    height: 1.5rem;
    transform: translateY(-50%);
  }

  &:disabled {
    cursor: default;
  }

  &[aria-busy="true"] {
    cursor: progress;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const Code = styled.span`
  padding-left: 0.3125em;
  font-size: 2rem; /* 32px */
  font-weight: 700;
  line-height: normal;
  letter-spacing: 0.3125em; /* 10px */
  white-space: nowrap;
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4925 Field/Copy} */
const LinkField = styled.div`
  position: relative;
  display: flex;
  width: 100%;
`;

const LinkInput = styled(Input)`
  flex: 1;
  min-width: 0;
  padding-right: 7.5rem; /* 120px */
  text-overflow: ellipsis;
`;

const CopyButton = styled(Button)`
  position: absolute;
  top: 50%;
  right: 0.75rem; /* 12px */
  transform: translateY(-50%);
`;
