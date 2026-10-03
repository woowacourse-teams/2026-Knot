import { ComponentProps } from "react";
import styled from "@emotion/styled";
import ChevronRight from "@/assets/icons/chevronRight.svg";
import File from "@/assets/icons/file.svg";

interface SourceButtonProps extends ComponentProps<"button"> {
  isSelected?: boolean;
}

/**
 * 답변의 근거 문서를 여는 버튼.
 *
 * 동작 규칙은 스토리북 `Shared/SourceButton`에서 확인해요.
 */
export default function SourceButton({
  isSelected = false,
  type = "button",
  children,
  ...props
}: SourceButtonProps) {
  return (
    <Container
      type={type}
      aria-pressed={isSelected}
      $isSelected={isSelected}
      {...props}
    >
      <File />
      <Label>{children}</Label>
      <ChevronRight />
    </Container>
  );
}

const Container = styled.button<{ $isSelected: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  height: 2rem;
  padding: 0 0.5rem;
  border: 1px solid ${({ theme }) => theme.neutral[300]};
  border-radius: 0.5rem;
  transition:
    background-color 0.3s ease-in,
    color 0.3s ease-in;

  background-color: ${({ theme, $isSelected }) =>
    $isSelected ? theme.neutral[100] : "transparent"};
  color: ${({ theme, $isSelected }) =>
    $isSelected ? theme.neutral[900] : theme.neutral[600]};

  & > svg {
    flex-shrink: 0;
  }

  & > svg:first-of-type {
    width: 1rem;
    height: 1rem;
  }

  & > svg:last-of-type {
    width: 0.75rem;
    height: 0.75rem;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const Label = styled.span`
  white-space: nowrap;
  ${({ theme }) => theme.text.caption02};
`;
