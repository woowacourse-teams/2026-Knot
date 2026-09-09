import useChatMessageSourcesQuery from "@api/queries/useChatMessageSourcesQuery";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";

import { formatReferenceLocation } from "../utils/formatReferenceLocation";
import { getReferenceSourceIcon } from "../utils/getReferenceSourceIcon";
import { groupReferencesByPage } from "../utils/groupReferencesByPage";
import { toReferenceSource } from "../utils/toReferenceSource";

/**
 * 근거 버튼으로 펼쳐 둔 답변의 문서 목록을 만듭니다.
 *
 * 펼쳐 둔 답변(`?messageId=`)의 검색 출처를 조회해 청크 8건을 페이지로 묶고,
 * 페이지의 대표 점수(가장 높은 청크 점수) 순으로 정렬해요.
 * 이 목록은 열려 있을 때만 화면에 놓이므로 여는 판단은 하지 않고, 닫는 것만 맡습니다.
 */
export const useSearchReferenceList = () => {
  const { openedMessageId, closeSourceMessage } = useOpenedSourceMessage();
  const { data, isPending, isError, refetch } = useChatMessageSourcesQuery({
    messageId: openedMessageId,
  });

  // 최대 8건이라 매 렌더에 다시 묶어도 부담이 없어요. DTO 인스턴스는 refetch마다 참조가 바뀌어 memo 대상이 아니에요
  const references = groupReferencesByPage(data?.searchReferences ?? []).map(
    (page) => ({
      id: page.id,
      title: page.title,
      href: page.href,
      documentPath: formatReferenceLocation(page),
      SourceIcon: getReferenceSourceIcon(toReferenceSource(page.source)),
    }),
  );

  return {
    references,
    isLoading: isPending,
    isError,
    handleRetry: () => void refetch(),
    handleClose: closeSourceMessage,
  };
};
