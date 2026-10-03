import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import type { HTMLAttributes, ReactNode } from "react";

interface ConfirmDialogProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "title"
> {
  /** 한 줄 제목 */
  title: ReactNode;
  /** 제목 아래 안내. 한 줄이 기본이고 최대 두 줄이에요. 줄바꿈은 의미 단위로 직접 넣어요 */
  description: ReactNode;
  /** 왼쪽 보조 버튼 문구. 예: `계속 녹음` */
  cancelLabel: string;
  /** 오른쪽 주 버튼 문구. 예: `녹음 끝내기` */
  confirmLabel: string;
  /** 보조 버튼을 눌렀을 때 할 일 */
  onCancel: () => void;
  /** 주 버튼을 눌렀을 때 할 일 */
  onConfirm: () => void;
  /** 되돌릴 수 없는 동작이면 주 버튼을 경고색으로 그려요 */
  isDestructive?: boolean;
}

/**
 * 동작 전에 묻는 확인 모달 카드.
 */
export default function ConfirmDialog({
  title,
  description,
  cancelLabel,
  confirmLabel,
  onCancel,
  onConfirm,
  isDestructive = false,
  ...rest
}: ConfirmDialogProps) {
  return (
    <Container {...rest}>
      <Head>
        <Title>{title}</Title>
        <Description>{description}</Description>
      </Head>
      <Actions>
        <Button size="md" variant="outline" isFullWidth onClick={onCancel}>
          {cancelLabel}
        </Button>
        <Button
          size="md"
          variant={isDestructive ? "danger" : "filled"}
          isFullWidth
          onClick={onConfirm}
        >
          {confirmLabel}
        </Button>
      </Actions>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 2rem; /* 32px */
  width: 100%;
  max-width: 32.5rem; /* 520px */
  padding: 2rem; /* 32px */
  border-radius: 1.25rem; /* 20px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;

const Head = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  text-align: center;
  overflow-wrap: break-word;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[600]};
  white-space: pre-line;
`;

const Actions = styled.div`
  display: flex;
  gap: 0.5rem; /* 8px */
`;
