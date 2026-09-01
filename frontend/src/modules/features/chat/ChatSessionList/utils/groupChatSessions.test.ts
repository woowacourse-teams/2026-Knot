import { describe, it, expect } from "vitest";

import { groupChatSessions } from "./groupChatSessions";
import type { ChatSession } from "../types/chatSession";

/** "오늘"은 달력상 같은 날인지로 갈리므로 기준 시각을 로컬 시각으로 잡습니다. */
const now = new Date(2026, 8, 1, 12, 0);

const createSession = (id: number, lastMessageAt: Date): ChatSession => ({
  id,
  title: `대화 ${id}`,
  createdAt: lastMessageAt.toISOString(),
  lastMessageAt: lastMessageAt.toISOString(),
});

describe("groupChatSessions", () => {
  it("오늘·이번 주·지난 30일·이전 순으로 묶는다", () => {
    const sessions = [
      createSession(1, new Date(2026, 8, 1, 9, 0)),
      createSession(2, new Date(2026, 7, 30, 9, 0)),
      createSession(3, new Date(2026, 7, 20, 9, 0)),
      createSession(4, new Date(2026, 0, 1, 9, 0)),
    ];

    expect(groupChatSessions({ sessions, now })).toEqual([
      { label: "오늘", sessions: [sessions[0]] },
      { label: "이번 주", sessions: [sessions[1]] },
      { label: "지난 30일", sessions: [sessions[2]] },
      { label: "이전", sessions: [sessions[3]] },
    ]);
  });

  it("같은 기간의 대화는 최근에 오간 순으로 둔다", () => {
    const older = createSession(1, new Date(2026, 8, 1, 1, 0));
    const newer = createSession(2, new Date(2026, 8, 1, 9, 0));

    expect(groupChatSessions({ sessions: [older, newer], now })).toEqual([
      { label: "오늘", sessions: [newer, older] },
    ]);
  });

  it("빈 기간은 목록에 두지 않는다", () => {
    const sessions = [createSession(1, new Date(2026, 7, 20, 9, 0))];

    expect(groupChatSessions({ sessions, now })).toEqual([
      { label: "지난 30일", sessions: [sessions[0]] },
    ]);
  });

  it("자정을 막 넘긴 어제 대화는 오늘로 묶지 않는다", () => {
    const sessions = [createSession(1, new Date(2026, 7, 31, 23, 59))];

    expect(groupChatSessions({ sessions, now })).toEqual([
      { label: "이번 주", sessions: [sessions[0]] },
    ]);
  });

  it("대화가 없으면 빈 목록을 만든다", () => {
    expect(groupChatSessions({ sessions: [], now })).toEqual([]);
  });
});
