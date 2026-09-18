import { useMemo } from "react";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";
import { mock } from "../mock";
import { getReferenceSourceIcon } from "../utils/getReferenceSourceIcon";

/**
 * 근거 버튼으로 펼쳐 둔 답변의 문서 목록을 만듭니다.
 *
 * 이 목록은 열려 있을 때만 화면에 놓이므로 여는 판단은 하지 않고, 닫는 것만 맡습니다.
 */
export const useSearchReferenceList = () => {
  const { openedMessageId, closeSourceMessage } = useOpenedSourceMessage();

  // TODO: openedMessageId로 답변 출처 문서 조회 쿼리 호출하도록 교체
  const references = useMemo(
    () =>
      mock.map((data) => ({
        id: data.id,
        title: data.notionPage.title,
        documentPath: data.notionPage.path,
        href: data.notionPage.notionUrl,
        SourceIcon: getReferenceSourceIcon(data.referenceSource),
      })),
    [],
  );

  return { references, handleClose: closeSourceMessage };
};
