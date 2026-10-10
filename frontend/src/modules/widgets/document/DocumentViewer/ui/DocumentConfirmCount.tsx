import Popover from "@composites/Popover";
import styled from "@emotion/styled";

import ChevronDownIcon from "@/assets/icons/chevronDown.svg";

import { useDocumentConfirmationMembers } from "../model/useDocumentConfirmationMembers";

import ConfirmedMembersCard from "./ConfirmedMembersCard";

interface DocumentConfirmCountProps {
  workspaceId: number;
  documentId: number;
  /** 문서를 확인한 사람 수. 문서 보기가 받은 문서의 확인 집계에서 넘겨줘요 */
  confirmedCount: number;
}

/**
 * 문서를 확인한 사람 수. 포인터를 올리면 확인한 사람 팝오버가 떠요(CONF-R5).
 *
 * 수는 문서 보기가 받은 값을 받고, 팝오버에 그릴 확인한 사람 목록만 여기서 조회해요. 목록은 문서와 다른 API예요.
 * 문서를 받은 뒤에만 그려지므로, 문서를 불러오지 못했을 때는 목록도 요청하지 않아요.
 */
export default function DocumentConfirmCount({
  workspaceId,
  documentId,
  confirmedCount,
}: DocumentConfirmCountProps) {
  const { status, confirmedMembers, pendingMembers, retryIfFailed } =
    useDocumentConfirmationMembers({ workspaceId, documentId });

  return (
    <Popover
      placement="bottom-end"
      content={
        <ConfirmedMembersCard
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
          {`${confirmedCount}명 확인했어요`}
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
