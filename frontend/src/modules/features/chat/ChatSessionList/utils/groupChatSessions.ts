import type { ChatSession, ChatSessionGroup } from "../types/chatSession";

interface GroupChatSessionsParams {
  sessions: ChatSession[];
  now?: Date;
}

interface GetGroupIndexParams {
  lastMessageAt: Date;
  now: Date;
}

const DAY = 24 * 60 * 60 * 1000;

/** 목록에 보여줄 기간 묶음. 최근 기간부터 순서대로 씁니다. */
const GROUP_LABELS = ["오늘", "이번 주", "지난 30일", "이전"] as const;

const isSameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() &&
  a.getMonth() === b.getMonth() &&
  a.getDate() === b.getDate();

const getGroupIndex = ({ lastMessageAt, now }: GetGroupIndexParams) => {
  if (isSameDay(lastMessageAt, now)) return 0;

  const elapsed = now.getTime() - lastMessageAt.getTime();

  if (elapsed < 7 * DAY) return 1;
  if (elapsed < 30 * DAY) return 2;

  return 3;
};

/**
 * 대화 목록을 "오늘 / 이번 주 / 지난 30일 / 이전" 기간으로 묶습니다.
 *
 * 오늘은 날짜가 같은지로 가르고, 나머지는 지금으로부터 지난 시간으로 가릅니다.
 * 같은 묶음 안에서는 마지막 메시지가 최근인 대화가 위로 옵니다.
 *
 * @param sessions - 묶을 대화 목록
 * @param now - 비교 기준 시각. 기본값은 현재 시각
 * @returns 대화가 있는 묶음만 최근 기간 순으로 담은 목록
 *
 * @example
 * groupChatSessions({ sessions });
 * // [{ label: "오늘", sessions: [...] }]
 */
export const groupChatSessions = ({
  sessions,
  now = new Date(),
}: GroupChatSessionsParams) => {
  const grouped: ChatSession[][] = GROUP_LABELS.map(() => []);

  for (const session of sessions) {
    const index = getGroupIndex({
      lastMessageAt: new Date(session.lastMessageAt),
      now,
    });

    grouped[index].push(session);
  }

  const groups: ChatSessionGroup[] = [];

  grouped.forEach((groupSessions, index) => {
    if (groupSessions.length === 0) return;

    groups.push({
      label: GROUP_LABELS[index],
      sessions: [...groupSessions].sort(
        (a, b) =>
          new Date(b.lastMessageAt).getTime() -
          new Date(a.lastMessageAt).getTime(),
      ),
    });
  });

  return groups;
};
