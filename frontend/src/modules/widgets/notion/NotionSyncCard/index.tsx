import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import CheckIcon from "@/assets/icons/check.svg";
import NotionIcon from "@/assets/icons/notion.svg";
import SyncIcon from "@/assets/icons/sync.svg";

import { LAST_SYNCED_AT_LABEL } from "./constants/notionSync";
import { useNotionSync } from "./model/useNotionSync";

/**
 * 홈의 Notion 동기화 카드.
 *
 * 마지막 동기화 시각을 보여주고, `지금 동기화`를 누르면 임시 지연 동안 스피너를 돌린 뒤
 * 새로 들어온 문서 수 안내와 비활성 `완료` 버튼으로 바뀌어요.
 * 동기화 API가 아직 없어 지연·문서 수는 `constants/notionSync`의 임시 값이에요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10086 Card/NotionImport status=기본}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10101 홈 화면/노션 연동 완료}
 */
export default function NotionSyncCard() {
  const { isSyncing, isSynced, syncedDocumentCount, startSync } =
    useNotionSync();

  return (
    <Container>
      <Content>
        <Head>
          <IconChip>
            <NotionIcon size={24} />
          </IconChip>
          <Title>Notion 동기화</Title>
        </Head>
        <Description>
          {isSynced
            ? `문서 ${syncedDocumentCount}개가 새로 들어왔어요`
            : LAST_SYNCED_AT_LABEL}
        </Description>
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
