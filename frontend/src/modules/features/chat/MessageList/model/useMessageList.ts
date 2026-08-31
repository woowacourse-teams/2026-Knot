import { useState } from "react";
import { mockMessages, mockSourceCounts } from "../mock";
import type { ChatTurnView } from "../types/chatTurn";
import { formatSourceLabel } from "../utils/formatSourceLabel";
import { toChatTurns } from "../utils/toChatTurns";

/**
 * 대화를 화면에 그릴 모양으로 만듭니다.
 *
 * 메시지에 근거 문구를 붙인 뒤 질문·답변 한 쌍씩 턴으로 접습니다.
 * 어느 턴의 근거 문서를 열어 두었는지는 화면에만 있는 상태라 여기서 들고 있습니다.
 */
export const useMessageList = () => {
  const [openedTurnId, setOpenedTurnId] = useState<number | null>(null);

  // TODO: mock 데이터 교체 필요
  const turns: ChatTurnView[] = toChatTurns(mockMessages).map((turn) => {
    const sourceCount = mockSourceCounts[turn.id];
    const sourceLabel = sourceCount ? formatSourceLabel(sourceCount) : null;

    return { ...turn, sourceLabel: sourceLabel ?? undefined };
  });

  const hasAnsweredTurn = turns.some(({ status }) => status === "done");

  const handleOpenSource = (turnId: number) => setOpenedTurnId(turnId);

  return { turns, openedTurnId, hasAnsweredTurn, handleOpenSource };
};
