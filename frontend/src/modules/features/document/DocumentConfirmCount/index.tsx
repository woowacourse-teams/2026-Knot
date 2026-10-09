import useDocumentQuery from "@api/queries/useDocumentQuery";
import Popover from "@composites/Popover";
import styled from "@emotion/styled";
import { useParams } from "react-router";

import ChevronDownIcon from "@/assets/icons/chevronDown.svg";

import { useDocumentConfirmationMembers } from "./model/useDocumentConfirmationMembers";
import ConfirmedPeopleCard from "./ui/ConfirmedPeopleCard";

interface DocumentConfirmCountProps {
  /**
   * 확인 현황을 보여줄 문서의 ID.
   * 같은 녹음의 문서를 넘겨 보는 녹음 직후 확인 화면에서는 지금 보는 문서의 ID가 주소에 없을 수 있어요.
   * 그 화면에도 놓을 수 있게 주소에서 읽지 않고 받아요. 워크스페이스 ID는 워크스페이스 아래 모든 화면의 주소에 있어 주소에서 읽어요
   */
  documentId: number;
}

/**
 * 문서를 확인한 사람 수. 포인터를 올리면 확인한 사람 팝오버가 떠요(CONF-R5).
 *
 * 수는 문서 상세의 집계를 써요. 문서 보기 위젯과 같은 키로 조회해서 요청이 더 나가지 않고 캐시를 나눠 써요.
 * 문서를 아직 받지 못했으면 아무것도 그리지 않아요.
 */
export default function DocumentConfirmCount({
  documentId,
}: DocumentConfirmCountProps) {
  const params = useParams();
  const workspaceId = Number(params.workspaceId);
  const { data: documentDetail } = useDocumentQuery({
    workspaceId,
    documentId,
  });
  const { status, confirmedMembers, pendingMembers, retryIfFailed } =
    useDocumentConfirmationMembers({ workspaceId, documentId });

  if (documentDetail === undefined) return null;

  return (
    <Popover
      placement="bottom-end"
      content={
        <ConfirmedPeopleCard
          status={status}
          confirmedMembers={confirmedMembers}
          pendingMembers={pendingMembers}
        />
      }
    >
      {(triggerProps) => (
        <Trigger
          {...triggerProps}
          onPointerEnter={() => {
            triggerProps.onPointerEnter();
            // 앞선 조회가 실패했으면 팝오버를 여는 김에 다시 조회해요
            retryIfFailed();
          }}
        >
          {`${documentDetail.confirmationSummary.confirmedCount}명 확인했어요`}
          <ChevronDownIcon size={12} />
        </Trigger>
      )}
    </Popover>
  );
}

/** 피그마 Confirm/확인 수: 여백 2/4/2/6 · 글자와 화살표 사이 4px · 포인터를 올리면 배경 */
const Trigger = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.25rem; /* 4px */
  padding: 0.125rem 0.25rem 0.125rem 0.375rem; /* 2px 4px 2px 6px */
  border-radius: 0.375rem; /* 6px */
  color: ${({ theme }) => theme.neutral[800]};
  white-space: nowrap;
  cursor: default;

  ${({ theme }) => theme.text.caption02};

  &:hover {
    background-color: ${({ theme }) => theme.neutral[100]};
  }

  & > svg {
    color: ${({ theme }) => theme.neutral[400]};
  }
`;
