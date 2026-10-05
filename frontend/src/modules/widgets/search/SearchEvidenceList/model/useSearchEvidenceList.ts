import { useMemo } from "react";
import { mock } from "../mock";
import { getEvidenceSourceIcon } from "../utils/getEvidenceSourceIcon";

// TODO: 이후 useQuery 훅으로 교체
export const useSearchEvidenceList = () => {
  const evidences = useMemo(
    () =>
      mock.map((data) => ({
        id: data.id,
        title: data.notionPage.title,
        documentPath: data.notionPage.path,
        href: data.notionPage.notionUrl,
        EvidenceSourceIcon: getEvidenceSourceIcon(data.evidenceSource),
      })),
    [],
  );

  return { evidences };
};
