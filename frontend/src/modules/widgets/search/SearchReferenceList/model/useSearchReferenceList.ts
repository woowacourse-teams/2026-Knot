import { useMemo } from "react";
import { mock } from "../mock";
import { getReferenceSourceIcon } from "../utils/getReferenceSourceIcon";

// TODO: 이후 useQuery 훅으로 교체
export const useSearchReferenceList = () => {
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

  return { references };
};
