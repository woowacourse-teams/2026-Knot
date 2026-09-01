/**
 * 워크스페이스에 쌓인 대화 한 건.
 * 서버의 채팅 세션 목록 조회 응답과 같은 모양이라, API가 붙으면 응답을 그대로 흘려보낼 수 있습니다.
 */
export interface ChatSession {
  id: number;
  title: string;
  createdAt: string;
  lastMessageAt: string;
}

/**
 * 같은 기간에 묶인 대화 묶음. `label`은 "오늘"처럼 목록 위에 붙는 기간 이름입니다.
 */
export interface ChatSessionGroup {
  label: string;
  sessions: ChatSession[];
}
