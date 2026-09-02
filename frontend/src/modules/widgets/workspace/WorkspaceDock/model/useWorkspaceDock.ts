import useNavigateToChat from "@hooks/domain/chat/useNavigateToChat";
import {
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
  type SubmitEvent,
} from "react";
import { useParams } from "react-router";

/**
 * 하단 독의 열림 상태와 질문 입력을 다룹니다.
 *
 * 접혀 있을 때는 동그란 버튼 하나뿐이고, 누르면 입력창이 펼쳐지면서 커서가 바로 입력창에 놓여요.
 * 질문을 보내면 탐색 화면으로 옮겨 가며 그 질문을 들고 가고, 독은 다시 접혀요.
 * 보낸 뒤의 일(세션 생성·답변 스트리밍)은 탐색 화면의 채팅 패널이 맡습니다.
 */
export const useWorkspaceDock = () => {
  const { workspaceId } = useParams();
  const { navigateToChat } = useNavigateToChat();

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [message, setMessage] = useState("");

  const canSubmit = message.trim().length > 0;

  const handleExpand = () => {
    setIsExpanded(true);
    // 펼쳐진 뒤에 그려지는 입력창이라 다음 프레임에 포커스를 옮겨요
    requestAnimationFrame(() => textareaRef.current?.focus());
  };

  const handleCollapse = () => {
    setIsExpanded(false);
    setMessage("");
  };

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) =>
    setMessage(e.target.value);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Escape") {
      handleCollapse();
      return;
    }

    if (e.key !== "Enter" || e.shiftKey) return;
    if (e.nativeEvent.isComposing) return; // 글자 조립 중일 때 폼 제출 막기

    e.preventDefault(); // "Enter" 입력 시 textarea 기본 동작(줄바꿈) 막기
    e.currentTarget.form?.requestSubmit();
  };

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!canSubmit || !workspaceId) return;

    navigateToChat({ workspaceId, question: message.trim() });
    handleCollapse();
  };

  return {
    textareaRef,
    isExpanded,
    message,
    canSubmit,
    handleExpand,
    handleChange,
    handleKeyDown,
    handleSubmit,
  };
};
