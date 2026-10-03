import { keyframes } from "@emotion/react";
import styled from "@emotion/styled";
import Textarea from "@primitives/ui/Textarea";

import GhostIcon from "@/assets/icons/ghost.svg";
import MicIcon from "@/assets/icons/mic.svg";
import SendIcon from "@/assets/icons/send.svg";

import { useDockRecording } from "./model/useDockRecording";
import { useWorkspaceDock } from "./model/useWorkspaceDock";
import DockHintTooltip from "./ui/DockHintTooltip";

/** 폭이 벌어지는 동안 안의 내용이 뒤따라 나타나는 모션 */
const fadeIn = keyframes`
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
`;

/**
 * 화면 하단 가운데에 고정으로 놓이는 독.
 *
 * 접혀 있을 때는 동그란 버튼 하나이고, 누르면 질문 입력창으로 폭이 벌어지며 펼쳐져요.
 * 화면 아무 데서나 글자를 쳐도 같은 자리로 이어지고, 그 방법은 처음 몇 번 말풍선으로 알려줘요.
 * 펼친 뒤 독 바깥을 누르면 다시 접혀요.
 * 채팅이 곧 화면인 탐색에서는 접지 않고 늘 펼쳐 둬요.
 *
 * 질문을 보내면 어느 화면에 있었든 그 질문을 들고 탐색 화면으로 옮겨 가요.
 * 접혀 있든 펼쳐 있든 회의 녹음(마이크) 슬롯을 두고, 누르면 마이크 권한을 받아 녹음을 시작해요.
 * 녹음 화면에서는 마이크를 숨겨요. Figma에서 숨겨져 있는 글 작성 슬롯은 만들지 않아요.
 *
 * 화면 어디에 놓을지는 이 독을 쓰는 레이아웃이 정해요.
 */
export default function WorkspaceDock() {
  const {
    formRef,
    textareaRef,
    isExpanded,
    isHintVisible,
    message,
    canSubmit,
    handleExpand,
    handleChange,
    handleKeyDown,
    handleSubmit,
  } = useWorkspaceDock();
  const { isMicVisible, micLabel, handleMicClick } = useDockRecording();

  return (
    <Bar
      ref={formRef}
      $isExpanded={isExpanded}
      $hasMic={isMicVisible}
      onSubmit={handleSubmit}
    >
      {isHintVisible && <DockHintTooltip />}

      {isExpanded ? (
        <>
          <InputContainer>
            <GhostIcon size={24} />

            <MessageField
              ref={textareaRef}
              rows={1}
              aria-label="무엇이든 요청하세요"
              placeholder="무엇이든 요청하세요"
              value={message}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
            />
          </InputContainer>

          <Controls>
            {isMicVisible && (
              <>
                <ControlDivider aria-hidden="true" />
                <MicButton
                  type="button"
                  aria-label={micLabel}
                  onClick={handleMicClick}
                >
                  <MicIcon size={24} />
                </MicButton>
              </>
            )}

            <SubmitButton
              type="submit"
              aria-label="보내기"
              disabled={!canSubmit}
            >
              <SendIcon size={16} />
            </SubmitButton>
          </Controls>
        </>
      ) : (
        <>
          <SlotButton
            type="button"
            aria-label="무엇이든 요청하기"
            onClick={handleExpand}
          >
            <GhostIcon size={24} />
          </SlotButton>

          {isMicVisible && (
            <MicButton
              type="button"
              aria-label={micLabel}
              onClick={handleMicClick}
            >
              <MicIcon size={24} />
            </MicButton>
          )}
        </>
      )}
    </Bar>
  );
}

/**
 * 접힘·펼침을 오가는 독 껍데기.
 *
 * 두 모양을 다른 요소로 두면 갈아 끼우느라 모션이 끊기므로, 한 요소의 폭만 바꿔 늘어나고 줄어들게 해요.
 * 안의 내용은 그 자리에서 갈리므로 폭이 벌어지는 동안 뒤따라 나타나도록 살짝 흐리게 시작해요.
 */
const Bar = styled.form<{ $isExpanded: boolean; $hasMic: boolean }>`
  position: relative; /* 안내 말풍선이 이 자리를 기준으로 위에 놓여요 */
  display: flex;
  align-items: flex-end; /* 여러 줄로 자라도 보내기 버튼은 아래에 남아요 */
  gap: ${({ $isExpanded }) =>
    $isExpanded ? "0.625rem" : "0.25rem"}; /* 10px : 4px */
  /* 접혔을 때는 슬롯(40px) 개수만큼: 마이크까지 있으면 108px, 없으면 64px */
  width: ${({ $isExpanded, $hasMic }) => {
    if ($isExpanded) return "min(45rem, 100%)"; /* 720px */

    return $hasMic ? "6.75rem" : "4rem";
  }};
  min-height: 3.75rem; /* 60px — 여러 줄이면 이만큼에서부터 늘어나요 */
  padding: ${({ $isExpanded }) =>
    $isExpanded
      ? "0.75rem 0.75rem 0.75rem 1.25rem" /* 12px 12px 12px 20px */
      : "0 0.75rem"}; /* 12px */
  /* 접혔을 때만 동그란 알약이고, 펼친 뒤에는 여러 줄로 자라도 모서리가 30px로 고정돼요 */
  border-radius: ${({ $isExpanded }) =>
    $isExpanded ? "1.875rem" : "6.25rem"}; /* 30px : 100px */
  background-color: ${({ theme }) => theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow03};
  transition:
    width 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    padding 0.28s cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 0.28s cubic-bezier(0.22, 1, 0.36, 1);

  /* 말풍선은 제 모션이 따로 있으므로 한 줄에 놓이는 것들만 뒤따라 나타나게 해요 */
  & > div,
  & > button {
    animation: ${fadeIn} 0.28s ease-out;
  }

  @media (prefers-reduced-motion: reduce) {
    transition: none;

    & > div,
    & > button {
      animation: none;
    }
  }
`;

/** 접힌 독의 탐색·마이크 버튼과 펼친 독의 마이크 버튼. 40px 칸 가운데에 24px 아이콘을 둬요. */
const SlotButton = styled.button`
  display: flex;
  align-self: center;
  align-items: center;
  justify-content: center;
  width: 2.5rem; /* 40px */
  height: 2.5rem;
  flex-shrink: 0;
  border-radius: 1.25rem; /* 20px */
  color: ${({ theme }) => theme.neutral[300]};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/** Figma Icon/Mic는 Neutral/500으로 탐색 아이콘보다 한 단계 옅어요. */
const MicButton = styled(SlotButton)`
  color: ${({ theme }) => theme.neutral[500]};
`;

/**
 * 고스트 아이콘과 입력창을 한 덩어리로 묶는 자리.
 *
 * 한 줄일 때는 덩어리째 가운데에 놓여 36px짜리 보내기 버튼과 눈높이가 맞고,
 * 여러 줄로 자라면 아이콘이 첫 줄에 붙어요. 보내기 버튼만 아래에 남습니다.
 */
const InputContainer = styled.div`
  display: flex;
  flex: 1;
  align-self: center;
  align-items: flex-start;
  gap: 0.625rem; /* 10px */
  min-width: 0;

  & > svg {
    flex-shrink: 0;
    color: ${({ theme }) => theme.neutral[300]};
  }
`;

const MessageField = styled(Textarea)`
  flex: 1;
  min-width: 0;
  max-height: 7.5rem; /* 120px — 5줄까지 자라고 더 길어지면 안에서 스크롤해요 */
  overflow-y: auto;
  field-sizing: content;
  background-color: transparent;
  color: ${({ theme }) => theme.neutral[0]};
  ${({ theme }) => theme.text.body01};

  &::placeholder {
    color: ${({ theme }) => theme.neutral[400]};
  }
`;

/**
 * 펼친 독 오른쪽의 구분선·마이크·보내기 묶음.
 *
 * 마이크(40px)와 보내기(36px)는 높이가 달라 서로 가운데를 맞추고, 여러 줄로 자라면 묶음째 아래에 남아요.
 */
const Controls = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.625rem; /* 10px */
`;

/**
 * 입력창과 마이크 사이의 세로 구분선. 8×28 칸 가운데의 1.5px 선이에요.
 *
 * Figma 값(#474747)과 같은 토큰이 없어 어두운 독 위에서 가장 가까운 Neutral/700을 써요.
 */
const ControlDivider = styled.span`
  width: 0.5rem; /* 8px */
  height: 1.75rem; /* 28px */
  background: linear-gradient(
      ${({ theme }) => theme.neutral[700]},
      ${({ theme }) => theme.neutral[700]}
    )
    center / 1.5px 100% no-repeat;
`;

const SubmitButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2.25rem; /* 36px */
  height: 2.25rem;
  border-radius: 62.4375rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[800]};
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;

  &:disabled {
    background-color: ${({ theme }) => theme.neutral[500]};
    color: ${({ theme }) => theme.neutral[700]};
    cursor: not-allowed;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
