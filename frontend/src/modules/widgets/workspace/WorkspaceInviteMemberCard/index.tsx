import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import Input from "@primitives/ui/Input";

import CheckIcon from "@/assets/icons/check.svg";
import TeammateIcon from "@/assets/icons/teammate.svg";

import { useWorkspaceInviteMemberCard } from "./model/useWorkspaceInviteMemberCard";

/**
 * 홈의 팀원 초대 카드.
 *
 * 초대 링크를 보여주고 `복사`를 누르면 2초 동안 강조색 `복사됨`으로, `초대 코드 복사`를 누르면
 * 6자 참여 코드를 복사하고 글자가 2초 동안 `복사됨`으로 바뀌어요.
 * alert는 쓰지 않고, 클립보드에 쓰지 못하면 화면 변화 없이 넘어갑니다.
 *
 * 코드·링크·복사 로직은 팀원 초대 화면 카드와 같은 `useCopyWorkspaceInvite`를 써요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10087 Card/InviteMember}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10089 홈 화면/초대 링크 복사}
 */
export default function WorkspaceInviteMemberCard() {
  const {
    displayInviteLink,
    isLinkCopied,
    isCodeCopied,
    handleCopyLink,
    handleCopyCode,
  } = useWorkspaceInviteMemberCard();

  return (
    <Container>
      <Head>
        <IconChip>
          <TeammateIcon size={24} />
        </IconChip>
        <Title>팀원 초대</Title>
      </Head>

      <Content>
        <LinkField>
          <LinkInput
            variant="copy"
            status="filled"
            value={displayInviteLink}
            readOnly
            aria-label="초대 링크"
          />
          <CopyButton
            size="sm"
            variant={isLinkCopied ? "accent" : "filled"}
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

        <CopyCodeButton type="button" onClick={handleCopyCode}>
          {isCodeCopied ? "복사됨" : "초대 코드 복사"}
        </CopyCodeButton>
      </Content>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 2rem; /* 32px */
  width: 25rem; /* 400px */
  max-width: 100%;
  min-height: 12.5rem; /* 200px */
  padding: 1.25rem; /* 20px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Head = styled.div`
  display: flex;
  align-items: center;
  gap: 0.75rem; /* 12px */
`;

const IconChip = styled.span`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2.5rem; /* 40px */
  height: 2.5rem;
  border-radius: 0.875rem; /* 14px */
  background-color: ${({ theme }) => theme.neutral[200]};
  color: ${({ theme }) => theme.neutral[600]};
`;

const Title = styled.h2`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.label01};
`;

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
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

const CopyCodeButton = styled.button`
  width: 100%;
  border-radius: 0.5rem;
  color: ${({ theme }) => theme.neutral[500]};
  text-align: center;
  ${({ theme }) => theme.text.body01};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
