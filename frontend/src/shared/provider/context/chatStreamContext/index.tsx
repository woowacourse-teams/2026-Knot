import useChatStream from "@hooks/domain/chat/useChatStream";
import { createContext, useContext, type ReactNode } from "react";

type ChatStream = ReturnType<typeof useChatStream>;

const ChatStreamContext = createContext<ChatStream | null>(null);

/**
 * 진행 중인 대화 상태를 화면 여러 구획이 함께 보게 해 주는 프로바이더.
 *
 * 질문은 화면 아래 독에서 적고 답변은 탐색 화면의 패널에 쌓이는데, 둘은 서로 다른 구획이라
 * 한쪽이 다른 쪽 안에 들어 있지 않습니다. 그래서 두 곳을 모두 감싸는 이 자리에서 상태를 한 번만 만들어
 * 나눠 줍니다. 워크스페이스 레이아웃이 감싸므로 홈과 탐색을 오가도 같은 대화가 이어집니다.
 */
export function ChatStreamProvider({ children }: { children: ReactNode }) {
  const chatStream = useChatStream();

  return (
    <ChatStreamContext.Provider value={chatStream}>
      {children}
    </ChatStreamContext.Provider>
  );
}

/** 진행 중인 대화 상태를 읽습니다. `ChatStreamProvider` 안에서만 부를 수 있어요. */
export const useChatStreamContext = () => {
  const chatStream = useContext(ChatStreamContext);

  if (chatStream === null) {
    throw new Error(
      "useChatStreamContext는 ChatStreamProvider 안에서만 쓸 수 있어요",
    );
  }

  return chatStream;
};
