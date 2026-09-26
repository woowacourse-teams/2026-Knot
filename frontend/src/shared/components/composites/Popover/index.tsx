import styled from "@emotion/styled";
import type { ReactNode } from "react";
import { createPortal } from "react-dom";

import usePopover from "./model/usePopover";
import type { PopoverPlacement, PopoverTriggerProps } from "./types/popover";

interface PopoverProps {
  /** 팝오버 안에 보여줄 내용 */
  content: ReactNode;
  /**
   * 트리거 아래에 띄울 때 어느 가장자리에 맞출지.
   * 왼쪽이나 본문 안의 트리거는 `bottom-start`, 화면이나 줄의 오른쪽 끝에 붙은 트리거는 `bottom-end`로 넘겨요.
   */
  placement: PopoverPlacement;
  /**
   * 트리거를 그리는 함수. 받은 `triggerProps`를 트리거로 쓸 요소에 그대로 펼쳐요.
   * 그 요소가 컴포넌트라면 받은 props를 DOM 요소까지 넘겨야 팝오버가 열려요.
   */
  children: (triggerProps: PopoverTriggerProps) => ReactNode;
}

/**
 * 트리거에 포인터를 올리면 넘겨받은 내용을 카드에 담아 보여주는 팝오버.
 *
 * 무엇을 담을지는 쓰는 쪽이 정하므로 이 컴포넌트는 도메인을 알지 못해요.
 * 확인한 사람 목록이라면 쓰는 쪽이 아바타와 이름 행을 `content`로 넘겨요.
 * 트리거도 쓰는 쪽이 그려요. 팝오버는 트리거를 감싸지 않고 `triggerProps`만 넘기므로,
 * 팝오버를 달아도 트리거의 모양과 배치가 바뀌지 않아요.
 *
 * ```tsx
 * <Popover content={<PeopleList />} placement="bottom-end">
 *   {(triggerProps) => <Chip {...triggerProps}>2/3 확인</Chip>}
 * </Popover>
 * ```
 *
 * 카드(너비·여백·간격·테두리·그림자)는 팝오버가 가져요. 부모에 가려 잘리지 않도록 `body`에 그리고,
 * 트리거 8px 아래에 띄우되 아래 공간이 모자라고 위쪽이 더 넓으면 위로 뒤집고, 가로는 화면 가장자리에서 16px 안쪽에 머물러요.
 * 포인터로만 열려요. 키보드로 여는 동작은 아직 없어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1909-5316 Popover/PeopleList}
 */
export default function Popover({
  content,
  placement,
  children,
}: PopoverProps) {
  const { isOpen, position, popoverRef, triggerProps, hoverProps } = usePopover({
    placement,
  });

  return (
    <>
      {/* 트리거 — 쓰는 쪽이 그리고, 팝오버는 triggerProps만 넘겨요 */}
      {children(triggerProps)}

      {isOpen &&
        createPortal(
          // 좌표는 열 때마다 달라져서 style로 넘겨요. Emotion prop으로 넘기면 좌표마다 CSS 클래스가 새로 쌓여요
          <Card ref={popoverRef} style={position} {...hoverProps}>
            {content}
          </Card>,
          document.body,
        )}
    </>
  );
}

const Card = styled.div`
  position: fixed;
  z-index: 30; /* 화면 위에 떠 있는 패널(20)보다 위에 떠요 */
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem; /* 8px */
  width: 12.5rem; /* 200px */
  padding: 0.625rem 0.75rem; /* 10px 12px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;
