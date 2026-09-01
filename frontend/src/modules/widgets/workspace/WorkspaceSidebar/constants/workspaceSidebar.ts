import type { WorkspaceTreeNode } from "../types/workspaceTree";

// TODO(#266): 워크스페이스 Notion Page Tree 조회 API 연결 후 응답으로 교체
/** Figma 사이드바 예시(제품 > 로드맵 > 2026 H2 로드맵 · 스펙, 리서치, 회의록, 초안)를 그대로 옮긴 임시 트리예요. */
export const WORKSPACE_TREE: WorkspaceTreeNode[] = [
  {
    type: "folder",
    id: "product",
    name: "제품",
    documentCount: 24,
    children: [
      {
        type: "folder",
        id: "roadmap",
        name: "로드맵",
        documentCount: 7,
        children: [
          { type: "file", id: "roadmap-2026-h2", name: "2026 H2 로드맵" },
        ],
      },
      {
        type: "folder",
        id: "spec",
        name: "스펙",
        documentCount: 5,
        children: [],
      },
    ],
  },
  {
    type: "folder",
    id: "research",
    name: "리서치",
    documentCount: 18,
    children: [],
  },
  {
    type: "folder",
    id: "meeting-notes",
    name: "회의록",
    documentCount: 41,
    children: [],
  },
  {
    type: "folder",
    id: "drafts",
    name: "초안",
    documentCount: 6,
    children: [],
  },
];

// TODO(#266): 트리 응답을 받으면 처음 펼쳐 둘 폴더 기준을 다시 정해요
/** Figma 예시에서 처음부터 펼쳐져 있는 폴더예요. */
export const INITIAL_EXPANDED_FOLDER_IDS = ["product", "roadmap"];

// TODO: 워크스페이스 조회 API 연결 후 응답의 이름으로 교체
export const WORKSPACE_NAME = "팀 노트";

// TODO(#266): 동기화 API 연결 후 마지막 동기화 시각으로 교체
export const LAST_SYNCED_LABEL = "2분 전";
