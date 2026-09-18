import { describe, expect, it } from "vitest";

import { getEntryWorkspaceId } from ".";

describe("getEntryWorkspaceId", () => {
  it("마지막으로 본 워크스페이스가 목록에 있으면 그 워크스페이스로 보낸다", () => {
    const entryWorkspaceId = getEntryWorkspaceId({
      lastViewedWorkspaceId: 2,
      workspaces: [{ id: 1 }, { id: 2 }],
    });

    expect(entryWorkspaceId).toBe(2);
  });

  it("마지막으로 본 워크스페이스가 없으면 첫 워크스페이스로 보낸다", () => {
    const entryWorkspaceId = getEntryWorkspaceId({
      lastViewedWorkspaceId: null,
      workspaces: [{ id: 7 }, { id: 8 }],
    });

    expect(entryWorkspaceId).toBe(7);
  });

  it("마지막으로 본 워크스페이스가 목록에서 사라졌으면 첫 워크스페이스로 보낸다", () => {
    const entryWorkspaceId = getEntryWorkspaceId({
      lastViewedWorkspaceId: 99,
      workspaces: [{ id: 1 }, { id: 2 }],
    });

    expect(entryWorkspaceId).toBe(1);
  });

  it("속한 워크스페이스가 없으면 보낼 곳이 없다", () => {
    const entryWorkspaceId = getEntryWorkspaceId({
      lastViewedWorkspaceId: null,
      workspaces: [],
    });

    expect(entryWorkspaceId).toBeUndefined();
  });

  it("속한 워크스페이스가 없는데 마지막으로 본 기록만 남아 있어도 보낼 곳이 없다", () => {
    const entryWorkspaceId = getEntryWorkspaceId({
      lastViewedWorkspaceId: 3,
      workspaces: [],
    });

    expect(entryWorkspaceId).toBeUndefined();
  });
});
