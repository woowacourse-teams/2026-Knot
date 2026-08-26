import { css, type SerializedStyles, type Theme } from "@emotion/react";
import styled from "@emotion/styled";
import type { ButtonHTMLAttributes } from "react";

import Spinner from "@/shared/components/primitives/ui/Spinner";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  size?: ButtonSize;
  variant?: ButtonVariant;
  isLoading?: boolean;
  isFullWidth?: boolean;
}

/** 버튼 크기. 피그마 Button/CTA의 L·M·S에 대응해요. */
export type ButtonSize = "lg" | "md" | "sm";
type ButtonSizeToken =
  | "paddingX"
  | "paddingY"
  | "borderRadius"
  | "gap"
  | "iconSize"
  | "spinnerSize";

/**
 * 버튼의 겉모양.
 *
 * - `filled` : 배경이 채워진 기본 버튼
 * - `outline` : 흰 배경에 테두리만 있는 버튼
 */
export type ButtonVariant = "filled" | "outline";

/**
 * 버튼이 그려야 할 상태. prop이 아니라 `isLoading`·`disabled`로 계산해요.
 *
 * - `active` : 누를 수 있음
 * - `loading` : 처리 중. 스피너가 뜨고 누를 수 없음
 * - `inactive` : 비활성
 *
 * 둘 다 참이면 `loading`이 이깁니다.
 * 처리 중인 버튼을 회색으로 그리면 아무 일도 일어나지 않는 것처럼 보이기 때문이에요.
 */
type ButtonStatus = "active" | "loading" | "inactive";

/**
 * 사이즈별 치수. 주석의 px이 피그마 원본 값이에요.
 *
 * height를 지정하지 않고 paddingY로 높이를 만듭니다.
 * 사용자가 글꼴 크기를 키워도 버튼이 함께 커져서 글자가 잘리지 않아요.
 */
const BUTTON_SIZE = {
  lg: {
    paddingX: "1.25rem" /* 20px */,
    paddingY: "1rem" /* 16px */,
    borderRadius: "0.875rem" /* 14px */,
    gap: "0.5rem" /* 8px */,
    iconSize: "1.5rem" /* 24px */,
    spinnerSize: "1.5rem" /* 24px */,
  },
  md: {
    paddingX: "1.125rem" /* 18px */,
    paddingY: "0.75rem" /* 12px */,
    borderRadius: "0.8125rem" /* 13px */,
    gap: "0.4375rem" /* 7px */,
    iconSize: "1.125rem" /* 18px */,
    spinnerSize: "1.375rem" /* 22px */,
  },
  sm: {
    paddingX: "1rem" /* 16px */,
    paddingY: "0.5rem" /* 8px */,
    borderRadius: "0.75rem" /* 12px */,
    gap: "0.375rem" /* 6px */,
    iconSize: "1rem" /* 16px */,
    spinnerSize: "1.25rem" /* 20px */,
  },
} as const satisfies Record<ButtonSize, Record<ButtonSizeToken, string>>;

/**
 * variant × status 조합별 색상.
 *
 * `loading`은 `active`와 같은 색입니다.
 */
const buttonAppearance = (theme: Theme) => {
  return {
    filled: {
      active: css`
        background-color: ${theme.primary};
        color: ${theme.neutral[0]};
      `,
      loading: css`
        background-color: ${theme.primary};
        color: ${theme.neutral[0]};
      `,
      inactive: css`
        background-color: ${theme.neutral[200]};
        color: ${theme.neutral[400]};
      `,
    },
    outline: {
      active: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[700]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
      loading: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[700]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
      inactive: css`
        background-color: ${theme.neutral[0]};
        color: ${theme.neutral[400]};
        box-shadow: inset 0 0 0 1px ${theme.neutral[200]};
      `,
    },
  } satisfies Record<ButtonVariant, Record<ButtonStatus, SerializedStyles>>;
};

/**
 * 액션을 실행하는 버튼.
 *
 * 로딩 중에도 라벨이 자리를 지켜 너비가 변하지 않아요.
 * 라벨은 `opacity: 0`으로 감추므로 스크린리더는 계속 버튼 이름을 읽습니다.
 *
 * `isLoading`이면 `disabled`도 함께 걸리고, 두 값을 모두 넘기면 로딩이 우선합니다.
 *
 * 크기는 height가 아니라 padding으로 정의해요. 글꼴이 커져도 잘리지 않습니다.
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=422-440 Button/CTA/L}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=511-284 Button/CTA/M}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=484-4907 Button/CTA/S}
 */
export default function Button({
  size = "md",
  variant = "filled",
  isLoading = false,
  isFullWidth = false,
  disabled = false,
  type = "button",
  children,
  ...props
}: ButtonProps) {
  const status: ButtonStatus = isLoading
    ? "loading"
    : disabled
      ? "inactive"
      : "active";

  return (
    <Root
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading}
      $size={size}
      $variant={variant}
      $status={status}
      $isFullWidth={isFullWidth}
      {...props}
    >
      <Content $isHidden={isLoading} $size={size}>
        {children}
      </Content>

      {isLoading && (
        <SpinnerWrapper>
          <Spinner size={BUTTON_SIZE[size].spinnerSize} />
        </SpinnerWrapper>
      )}
    </Root>
  );
}

const Root = styled.button<{
  $size: ButtonSize;
  $variant: ButtonVariant;
  $status: ButtonStatus;
  $isFullWidth: boolean;
}>`
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
  transition:
    background-color 0.3s ease-in,
    color 0.3s ease-in,
    box-shadow 0.3s ease-in;

  ${({ theme }) => theme.text.label01};

  width: ${({ $isFullWidth }) => ($isFullWidth ? "100%" : "auto")};
  padding: ${({ $size }) =>
    `${BUTTON_SIZE[$size].paddingY} ${BUTTON_SIZE[$size].paddingX}`};
  border-radius: ${({ $size }) => BUTTON_SIZE[$size].borderRadius};

  ${({ theme, $variant, $status }) =>
    buttonAppearance(theme)[$variant][$status]};

  &[aria-busy="true"] {
    cursor: progress;
  }

  /* 포커스 링 아웃라인 논의 필요 */
  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const Content = styled.span<{ $isHidden: boolean; $size: ButtonSize }>`
  display: inline-flex;
  align-items: center;
  gap: ${({ $size }) => BUTTON_SIZE[$size].gap};
  opacity: ${({ $isHidden }) => ($isHidden ? 0 : 1)};

  & > svg {
    flex-shrink: 0;
    width: ${({ $size }) => BUTTON_SIZE[$size].iconSize};
    height: ${({ $size }) => BUTTON_SIZE[$size].iconSize};
  }
`;

const SpinnerWrapper = styled.span`
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
`;
