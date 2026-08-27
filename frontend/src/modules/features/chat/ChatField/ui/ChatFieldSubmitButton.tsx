import { ComponentProps } from "react";
import styled from "@emotion/styled";
import { css, type Theme } from "@emotion/react";
import Send from "@/assets/icons/send.svg";
import Spinner from "@/shared/components/primitives/ui/Spinner";

/**
 * 제출 버튼의 상태. 한 번의 전송 사이클에서
 * `inactive → active → loading → stop → inactive` 순으로 전이된다.
 *
 * - `inactive`: 보낼 내용이 없어 전송 불가. 빈 입력, 글자 수 초과 등
 * - `active`: 전송 가능한 기본 대기 상태
 * - `loading`: 요청을 보냈고 응답은 아직 시작 전. 이미 나간 요청이라 취소 불가
 * - `stop`: 답변 생성 중. 누르면 생성을 중단
 */
export type ButtonStatus = "active" | "inactive" | "loading" | "stop";

interface ChatFieldSubmitButtonProps extends ComponentProps<"button"> {
  status: ButtonStatus;
}

const STATUS_STYLE = {
  active: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    color: ${theme.neutral[800]};
  `,
  inactive: (theme: Theme) => css`
    background-color: ${theme.neutral[500]};
    color: ${theme.neutral[700]};
  `,
  loading: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    color: ${theme.neutral[800]};
  `,
  stop: (theme: Theme) => css`
    background-color: ${theme.neutral[0]};
    color: ${theme.neutral[800]};
  `,
} as const satisfies Record<
  ButtonStatus,
  (theme: Theme) => ReturnType<typeof css>
>;

/**
 * 채팅창 제출 버튼
 *
 * `inactive`·`loading`에서는 누를 수 없고, 로딩 중이라는 사실은 `aria-busy`로 알린다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1080-648
 */
export default function ChatFieldSubmitButton({
  status,
  ...props
}: ChatFieldSubmitButtonProps) {
  const isLoading = status === "loading";
  const isStop = status === "stop";
  const isDisabled = status === "inactive" || isLoading;

  return (
    <Root
      {...props}
      type={isStop ? "button" : "submit"}
      $status={status}
      disabled={isDisabled}
      aria-busy={isLoading}
    >
      <IconWrapper>
        {isLoading && <Spinner size="1rem" />}
        {isStop && <StopIcon />}
        {(status === "active" || status === "inactive") && <Send />}
      </IconWrapper>
    </Root>
  );
}

const Root = styled.button<{ $status: ButtonStatus }>`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0;
  border-radius: 999px;
  transition:
    background-color 0.3s ease-in,
    color 0.3s ease-in;

  ${({ theme, $status }) => STATUS_STYLE[$status](theme)};

  &[aria-busy="true"] {
    cursor: progress;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const IconWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 1rem;
  height: 1rem;
`;

const StopIcon = styled.span`
  width: 0.6875rem;
  height: 0.6875rem;
  border-radius: 2.5px;
  background-color: currentColor;
`;
