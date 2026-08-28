import styled from "@emotion/styled";
import type { HTMLAttributes } from "react";

interface DividerProps extends HTMLAttributes<HTMLDivElement> {
  /** 선 가운데에 넣을 짧은 글자. `또는`처럼 두 선택지를 나눌 때 써요. */
  label?: string;
}

/**
 * 콘텐츠를 가로로 나누는 구분선.
 *
 * `label`을 넘기면 선이 양쪽으로 갈라지고 그 사이에 글자가 들어가요.
 * 부모 너비를 꽉 채우므로 폭은 쓰는 쪽이 정합니다.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=432-1738 Divider}
 */
export default function Divider({ label, ...props }: DividerProps) {
  return (
    <Container role="separator" {...props}>
      <Line />
      {label && (
        <>
          <Label>{label}</Label>
          <Line />
        </>
      )}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  align-items: center;
  gap: 0.75rem; /* 12px */
  width: 100%;
`;

const Line = styled.span`
  flex: 1 0 0;
  min-width: 1px;
  height: 1px;
  background-color: ${({ theme }) => theme.neutral[200]};
`;

const Label = styled.span`
  flex-shrink: 0;
  color: ${({ theme }) => theme.neutral[400]};
  white-space: nowrap;
  ${({ theme }) => theme.text.caption01};
`;
