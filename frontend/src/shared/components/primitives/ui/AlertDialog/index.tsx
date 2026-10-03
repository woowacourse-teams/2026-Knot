import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";
import type { HTMLAttributes, ReactNode } from "react";

interface AlertDialogProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "title"
> {
  /** 제목 앞에 붙는 아이콘. 넘기지 않으면 제목만 그려요. 색은 넘기는 쪽에서 정해요 */
  icon?: ReactNode;
  /** 한 줄 제목 */
  title: ReactNode;
  /** 제목 아래 안내. 한 줄이 기본이고 최대 두 줄이에요. 줄바꿈은 의미 단위로 직접 넣어요 */
  description: ReactNode;
  /** 버튼 문구. 예: `확인` */
  confirmLabel: string;
  /** 버튼을 눌렀을 때 할 일 */
  onConfirm: () => void;
}

/**
 * 일어난 일을 알리는 알림 모달 카드.
 *
 * 제목·안내와 가로를 채운 확인 버튼 하나를 그려요. 아이콘을 넘기면 제목 앞에 붙여요.
 * 화면 가운데 띄우기·ESC·포커스 가두기 같은 동작은 없어서,
 * 쓰는 쪽이 `Dim` 위에 올리고 `role`·`aria-*`도 함께 넘겨요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-5058 Dialog 유형=알림}
 */
export default function AlertDialog({
  icon,
  title,
  description,
  confirmLabel,
  onConfirm,
  ...rest
}: AlertDialogProps) {
  return (
    <Container {...rest}>
      <Head>
        <TitleRow>
          {icon && <IconWrapper aria-hidden>{icon}</IconWrapper>}
          <Title>{title}</Title>
        </TitleRow>
        <Description>{description}</Description>
      </Head>
      <Button size="md" variant="filled" isFullWidth onClick={onConfirm}>
        {confirmLabel}
      </Button>
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
  align-items: center;
  gap: 0.5rem; /* 8px */
  text-align: center;
  overflow-wrap: break-word;
`;

const TitleRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.625rem; /* 10px */
`;

const IconWrapper = styled.span`
  display: flex;
  flex-shrink: 0;
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
