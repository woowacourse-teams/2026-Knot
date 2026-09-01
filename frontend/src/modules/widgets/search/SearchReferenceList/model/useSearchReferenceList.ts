import { useMemo } from "react";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";
import { mock } from "../mock";
import { getReferenceSourceIcon } from "../utils/getReferenceSourceIcon";

/**
 * 근거 버튼으로 펼쳐 둔 답변의 문서 목록을 만듭니다.
 *
 * 펼쳐 둔 답변이 없으면 목록 대신 안내를 보여줘야 하므로 `isOpened`로 알립니다.
 */
export const useSearchReferenceList = () => {
  const { openedMessageId } = useOpenedSourceMessage();

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

  return { references, isOpened: openedMessageId !== null };
};
