import type { NotionPageTreeItem } from "@api/mock/types/notionPage";

const notionUrl = (slug: string) => `https://www.notion.so/${slug}`;

/** Figma 사이드바 예시(제품 > 로드맵·스펙, 리서치, 회의록, 초안)를 서버 응답 모양으로 옮긴 트리예요 */
export const notionPageTreeResponse = [
  {
    id: 1,
    parentPageId: null,
    title: "제품",
    position: 0,
    notionUrl: notionUrl("product"),
  },
  {
    id: 2,
    parentPageId: 1,
    title: "로드맵",
    position: 0,
    notionUrl: notionUrl("roadmap"),
  },
  {
    id: 3,
    parentPageId: 2,
    title: "2026 H2 로드맵",
    position: 0,
    notionUrl: notionUrl("roadmap-2026-h2"),
  },
  {
    id: 4,
    parentPageId: 1,
    title: "스펙",
    position: 1,
    notionUrl: notionUrl("spec"),
  },
  {
    id: 5,
    parentPageId: 4,
    title: "탐색 스펙",
    position: 0,
    notionUrl: notionUrl("search-spec"),
  },
  {
    id: 6,
    parentPageId: null,
    title: "리서치",
    position: 1,
    notionUrl: notionUrl("research"),
  },
  {
    id: 7,
    parentPageId: 6,
    title: "사용자 인터뷰 정리",
    position: 0,
    notionUrl: notionUrl("user-interview"),
  },
  {
    id: 8,
    parentPageId: null,
    title: "회의록",
    position: 2,
    notionUrl: notionUrl("meeting-notes"),
  },
  {
    id: 9,
    parentPageId: 8,
    title: "DB 기술 선정 회의록",
    position: 0,
    notionUrl: notionUrl("db-decision"),
  },
  {
    id: 10,
    parentPageId: null,
    title: "초안",
    position: 3,
    notionUrl: notionUrl("drafts"),
  },
] satisfies NotionPageTreeItem[];
