import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import CheckIcon from "@/assets/icons/check.svg";
import NotionIcon from "@/assets/icons/notion.svg";
import SyncIcon from "@/assets/icons/sync.svg";

import {
  NOTION_CONNECTION_STATUS_LABEL,
  NOTION_CONNECTION_STATUS_UNKNOWN_MESSAGE,
} from "./constants/notionSync";
import { useNotionSync } from "./model/useNotionSync";

/**
 * 홈의 Notion 동기화 카드.
 *
 * Notion 연결 상태(연결 안 됨·연결됨·재인증 필요)를 보여주고, `지금 동기화`를 누르면 동기화 API로
 * Import를 시작해 끝날 때까지 스피너를 돌려요. 완료되면 새로 들어온 문서 수 안내와 비활성 `완료` 버튼으로,
 * 실패하면 실패 안내 문구로 바뀌고, 두 경우 모두 2초 뒤 연결 상태 안내로 돌아와요.
 * 연결 상태를 아직 못 받았으면 안내를 비워 두고, 조회가 실패하면 확인 실패 문구를 보여줘요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10086 Card/NotionImport status=기본}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10101 홈 화면/노션 연동 완료}
 */
export default function NotionSyncCard() {
  const {
    connectionStatus,
    isConnectionStatusError,
    isSyncing,
    isSynced,
    isSyncFailed,
    syncedDocumentCount,
    failureMessage,
    startSync,
  } = useNotionSync();

  const getDescription = () => {
    if (isSynced) return `문서 ${syncedDocumentCount}개가 새로 들어왔어요`;
    if (isSyncFailed) return failureMessage;
    if (isConnectionStatusError)
      return NOTION_CONNECTION_STATUS_UNKNOWN_MESSAGE;
    if (connectionStatus === undefined) return null;
    return NOTION_CONNECTION_STATUS_LABEL[connectionStatus];
  };

  return (
    <Container>
      <Content>
        <Head>
          <IconChip>
            <NotionIcon size={24} />
          </IconChip>
          <Title>Notion 동기화</Title>
        </Head>
        <Description>{getDescription()}</Description>
      </Content>

      {isSynced ? (
        <DoneButton size="md" variant="outline" isFullWidth disabled>
          <CheckIcon />
          완료
        </DoneButton>
      ) : (
        <Button size="md" isFullWidth isLoading={isSyncing} onClick={startSync}>
          <SyncIcon />
          지금 동기화
        </Button>
      )}
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

const Content = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
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

const Description = styled.p`
  color: ${({ theme }) => theme.neutral[700]};
  ${({ theme }) => theme.text.body01};
`;

/** 완료는 비활성이지만 Figma대로 강조색 글자·아이콘으로 방금 끝난 일을 알려요. */
const DoneButton = styled(Button)`
  &:disabled {
    color: ${({ theme }) => theme.sub.accent[500]};
  }
`;
