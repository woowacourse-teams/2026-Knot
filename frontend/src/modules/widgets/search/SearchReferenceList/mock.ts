import type { ReferenceSource } from "./types/searchReference";

export type SearchReferenceCardListResponse = Array<{
  id: number;
  messageId: number;
  rank: number;
  relevanceScore: number;
  notionPage: {
    id: number;
    title: string;
    path: string;
    notionUrl: string;
    createdAt: string;
    updatedAt: string;
  };
  referenceSource: ReferenceSource;
}>;

export const mock = [
  {
    id: 1,
    messageId: 45,
    rank: 1,
    relevanceScore: 0.95,
    notionPage: {
      id: 1,
      title:
        "2026 H2 제품 로드맵 확정 및 DB 기술 선정 관련 논의 회의록 관련 논의 회의록 관련 논의 회의록논의 회의록",
      path: " 제품/스펙",
      notionUrl:
        "https://www.notion.com/ko/product?utm_source=google&utm_medium=cpc&utm_campaign=brand%5Fkeyword%5Fgroup&utm_term=notion&utm_content=All&gad_source=1&gad_campaignid=23666398709&gbraid=0AAAABDF1p-CmDI-VeNVbM8Ni57dxCa6gH&gclid=Cj0KCQjwnbrUBhDOARIsAKKhPpcyI5xl0BexEJ4Vr0ms_pVFeza_QzYauv1eb1ByQ-MKqwEH5Dqop7QaAj8cEALw_wcB",
      createdAt: "2024-01-01T00:00:00Z",
      updatedAt: "2024-01-01T00:00:00Z",
    },
    referenceSource: "notion",
  },
  {
    id: 2,
    messageId: 45,
    rank: 2,
    relevanceScore: 0.85,
    notionPage: {
      id: 2,
      title: "2026 H2 제품 로드맵 확정",
      path: " 제품/스펙",
      notionUrl:
        "https://www.notion.com/ko/product?utm_source=google&utm_medium=cpc&utm_campaign=brand%5Fkeyword%5Fgroup&utm_term=notion&utm_content=All&gad_source=1&gad_campaignid=23666398709&gbraid=0AAAABDF1p-CmDI-VeNVbM8Ni57dxCa6gH&gclid=Cj0KCQjwnbrUBhDOARIsAKKhPpcyI5xl0BexEJ4Vr0ms_pVFeza_QzYauv1eb1ByQ-MKqwEH5Dqop7QaAj8cEALw_wcB",
      createdAt: "2024-01-02T00:00:00Z",
      updatedAt: "2024-01-02T00:00:00Z",
    },
    referenceSource: "notion",
  },
] satisfies SearchReferenceCardListResponse;
