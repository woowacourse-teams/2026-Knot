import { useState } from "react";
import styled from "@emotion/styled";
import ThumbDown from "@/assets/icons/thumbDown.svg";
import ThumbDownActive from "@/assets/icons/thumbDownActive.svg";
import ThumbUp from "@/assets/icons/thumbUp.svg";
import ThumbUpActive from "@/assets/icons/thumbUpActive.svg";

/** 답변에 남긴 평가 */
type FeedbackValue = "up" | "down";

const QUESTION_MESSAGE = "답변이 도움이 됐나요?";
const THANKS_MESSAGE = "피드백 감사합니다.";

/**
 * 마지막 답변에 좋아요·싫어요를 남기는 행.
 *
 * 하나를 고르면 문구가 감사 인사로 바뀌고 고른 아이콘이 강조됩니다.
 * 마음이 바뀌면 다른 쪽을 다시 고를 수 있습니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=715-584
 */
export default function AnswerFeedback() {
  // TODO: 피드백 API가 나오면 mutation으로 교체
  const [value, setValue] = useState<FeedbackValue | null>(null);

  const isUp = value === "up";
  const isDown = value === "down";


  // TODO: 버튼을 누른 후 N초 뒤에 사라지는 로직 추가
  return (
    <Container>
      <Message>{value ? THANKS_MESSAGE : QUESTION_MESSAGE}</Message>

      <ThumbUpButton
        type="button"
        aria-label="도움이 됐어요"
        aria-pressed={isUp}
        $isActive={isUp}
        onClick={() => setValue("up")}
      >
        {isUp ? <ThumbUpActive /> : <ThumbUp />}
      </ThumbUpButton>

      <FeedbackButton
        type="button"
        aria-label="도움이 되지 않았어요"
        aria-pressed={isDown}
        $isActive={isDown}
        onClick={() => setValue("down")}
      >
        {isDown ? <ThumbDownActive /> : <ThumbDown />}
      </FeedbackButton>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  row-gap: 0.5rem;
  column-gap: 0.25rem;
  width: 100%;
`;

const Message = styled.p`
  white-space: nowrap;
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[600]};
`;

const FeedbackButton = styled.button<{ $isActive: boolean }>`
  display: inline-flex;
  justify-content: center;
  align-items: center;
  padding: 0.25rem;
  border-radius: 0.5rem;
  transition: color 0.3s ease-in;

  color: ${({ theme, $isActive }) =>
    $isActive ? theme.sub.accent[500] : theme.neutral[600]};

  & > svg {
    flex-shrink: 0;
    width: 1.125rem;
    height: 1.125rem;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const ThumbUpButton = styled(FeedbackButton)`
  padding-left: 0.75rem;
`;
