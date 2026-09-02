import useNavigateToChat from "@hooks/domain/chat/useNavigateToChat";
import useOutsideClick from "@hooks/common/useOutsideClick";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";
import { useChatStreamContext } from "@provider/context/chatStreamContext";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type KeyboardEvent,
  type SubmitEvent,
} from "react";
import { useParams } from "react-router";

import { useDockHint } from "./useDockHint";
import { useTypeToStartChat } from "./useTypeToStartChat";

/**
 * 하단 독의 열림 상태와 질문 입력을 다룹니다.
 *
 * 접혀 있을 때는 동그란 버튼 하나뿐이고, 누르면 입력창이 펼쳐지면서 커서가 바로 입력창에 놓여요.
 * 화면 아무 데서나 글자를 쳐도 같은 자리로 이어져요(`useTypeToStartChat`).
 * 독 바깥을 누르면 다시 접힙니다. 적던 글은 지우지 않고 들고 있다가 다시 열 때 그대로 돌려줘요.
 * 탐색 화면은 채팅이 곧 화면 자체라 독을 늘 펼쳐 둡니다. 그래서 그 화면에서는 접히지 않고 입력만 비워져요.
 *
 * 이 독이 탐색 화면의 입력창이기도 합니다. 탐색에서 보낸 질문은 보고 있던 대화에 그대로 이어 붙고,
 * 홈에서 보낸 질문은 탐색 화면으로 옮겨 가며 실려 갑니다. 답변이 오는 동안에는 또 보내지 못합니다.
 * 서버가 같은 세션의 동시 스트림을 409로 거절하기 때문입니다.
 */
export const useWorkspaceDock = () => {
  const { workspaceId } = useParams();
  const { navigateToChat } = useNavigateToChat();
  const { isChatActive } = useWorkspaceNav();
  const { isSending, notice, handleSubmitQuestion } = useChatStreamContext();

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [isExpandedByUser, setIsExpandedByUser] = useState(false);
  const [message, setMessage] = useState("");
  /** 커서를 입력창으로 옮겨 달라고 요청한 횟수. 값이 바뀔 때마다 한 번씩 옮겨요 */
  const [focusRequestCount, setFocusRequestCount] = useState(0);

  const isExpanded = isChatActive || isExpandedByUser;
  const canSubmit = message.trim().length > 0 && !isSending;

  const { isHintVisible } = useDockHint({ isDockExpanded: isExpanded });

  // 입력창은 펼쳐진 뒤에야 그려지므로, 그려진 것을 확인하는 효과에서 커서를 옮겨요
  useEffect(() => {
    if (focusRequestCount === 0) return;

    const field = textareaRef.current;
    if (!field) return;

    field.focus();
    // 값을 코드로 채우면 커서가 맨 앞에 남아, 이어 친 글자가 가로챈 글자 앞에 끼어들어요
    field.setSelectionRange(field.value.length, field.value.length);
  }, [focusRequestCount]);

  const requestFocus = () => setFocusRequestCount((prev) => prev + 1);

  const collapse = () => setIsExpandedByUser(false);

  // 늘 펼쳐 두는 탐색 화면에서는 접을 것이 없어 바깥 클릭을 보지 않아요
  const { ref: formRef } = useOutsideClick<HTMLFormElement>({
    isEnabled: isExpandedByUser,
    onOutsideClick: collapse,
  });

  const handleExpand = () => {
    setIsExpandedByUser(true);
    requestFocus();
  };

  const handleReset = () => {
    collapse();
    setMessage("");
  };

  const handleType = useCallback((typedText: string) => {
    setIsExpandedByUser(true);
    setMessage((prev) => prev + typedText);
    setFocusRequestCount((prev) => prev + 1);
  }, []);

  useTypeToStartChat({ onType: handleType });

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) =>
    setMessage(e.target.value);

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Escape") {
      handleReset();
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

    const question = message.trim();

    // 이미 탐색 화면이면 옮겨 갈 곳이 없어요. 보던 대화에 그대로 이어 보내고 입력만 비웁니다
    if (isChatActive) {
      handleSubmitQuestion(question);
      setMessage("");
      // 버튼을 눌러 보냈다면 커서가 버튼에 가 있으므로 이어서 적을 수 있게 되돌려 놔요
      requestFocus();
      return;
    }

    navigateToChat({ workspaceId, question });
    handleReset();
  };

  return {
    formRef,
    textareaRef,
    isExpanded,
    isHintVisible,
    /** 보내지 못했을 때의 안내. 입력한 자리 바로 위에 남겨요 */
    notice,
    message,
    canSubmit,
    handleExpand,
    handleChange,
    handleKeyDown,
    handleSubmit,
  };
};
