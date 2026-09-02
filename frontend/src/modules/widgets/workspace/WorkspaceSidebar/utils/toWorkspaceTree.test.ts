import { describe, expect, it } from "vitest";

import type { WorkspacePage } from "../types/workspaceTree";

import { toWorkspaceTree } from "./toWorkspaceTree";

const page = (
  id: number,
  parentPageId: number | null,
  title: string,
  position = 0,
): WorkspacePage => ({ id, parentPageId, title, position });

describe("toWorkspaceTree", () => {
  it("부모가 없는 페이지를 최상위로 둔다", () => {
    const tree = toWorkspaceTree([page(1, null, "제품")]);

    expect(tree).toEqual([
      { id: 1, name: "제품", documentCount: 0, children: [] },
    ]);
  });

  it("부모 아래로 자식을 묶는다", () => {
    const tree = toWorkspaceTree([page(1, null, "제품"), page(2, 1, "로드맵")]);

    expect(tree[0].children).toEqual([
      { id: 2, name: "로드맵", documentCount: 0, children: [] },
    ]);
  });

  it("같은 부모 아래에서는 순서대로 늘어놓는다", () => {
    const tree = toWorkspaceTree([
      page(2, null, "리서치", 1),
      page(1, null, "제품", 0),
    ]);

    expect(tree.map(({ name }) => name)).toEqual(["제품", "리서치"]);
  });

  it("문서 수는 깊이에 상관없이 아래 페이지를 모두 센다", () => {
    const tree = toWorkspaceTree([
      page(1, null, "제품"),
      page(2, 1, "로드맵"),
      page(3, 2, "2026 H2 로드맵"),
      page(4, 1, "스펙", 1),
    ]);

    expect(tree[0].documentCount).toBe(3);
    expect(tree[0].children[0].documentCount).toBe(1);
  });

  it("부모가 목록에 없는 페이지는 그리지 않는다", () => {
    const tree = toWorkspaceTree([page(2, 99, "고아 문서")]);

    expect(tree).toEqual([]);
  });

  it("페이지가 없으면 빈 트리를 준다", () => {
    expect(toWorkspaceTree([])).toEqual([]);
  });
});
