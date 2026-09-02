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
 * 지금 열려 있는 버튼은 `isSelected`로 채워진 모양이 되며, 이 상태를 `aria-pressed`로도 알립니다.
 * 라벨 문구는 도메인이 정하므로 `children`으로 받습니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1170-697
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
  border: 1px solid
    ${({ theme, $isSelected }) =>
      $isSelected ? theme.neutral[300] : theme.neutral[600]};
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
