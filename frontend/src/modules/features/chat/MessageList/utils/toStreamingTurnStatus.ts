interface ToStreamingTurnStatusParams {
  /** 지금까지 도착한 부분 답변 */
  answer: string;
  /** 답변이 오다가 끊겼는지 여부 */
  isFailed: boolean;
}

/**
 * 진행 중인 턴의 상태를 정합니다.
 *
 * 끊긴 뒤에는 조각이 왔든 안 왔든 실패가 먼저입니다. 아직 한 글자도 오지 않았다면
 * 질문만 보낸 상태이고, 한 글자라도 왔다면 답변이 오는 중입니다.
 *
 * @param params - 지금까지 도착한 부분 답변과 실패 여부
 * @returns 진행 중인 턴에 그릴 상태
 *
 * @example
 * toStreamingTurnStatus({ answer: "", isFailed: false }); // "pending"
 */
export const toStreamingTurnStatus = ({
  answer,
  isFailed,
}: ToStreamingTurnStatusParams) => {
  if (isFailed) return "error";

  return answer.length === 0 ? "pending" : "streaming";
};
