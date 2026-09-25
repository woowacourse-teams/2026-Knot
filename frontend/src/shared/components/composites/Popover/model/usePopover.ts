import { useEffect, useLayoutEffect, useRef, useState } from "react";

import type { PopoverPlacement } from "../types/popover";
import { getPopoverPosition } from "../utils/getPopoverPosition";

/** 포인터가 트리거와 팝오버 사이 8px 틈을 지나는 동안 닫지 않고 기다리는 시간 */
const CLOSE_DELAY_MS = 150;

interface UsePopoverParams {
  /** 트리거의 어느 가장자리에 맞출지 */
  placement: PopoverPlacement;
}

/**
 * 트리거에 포인터를 올리면 팝오버를 열고, 트리거와 팝오버 밖으로 나가면 닫는 상태.
 *
 * 팝오버는 `body`에 따로 그려져서 DOM으로는 트리거 밖에 있어요.
 * 그래서 트리거와 팝오버 양쪽에 같은 `hoverProps`를 붙이고, 벗어나면 바로 닫지 않고
 * {@link CLOSE_DELAY_MS}만큼 기다려요. 그 사이 다른 쪽에 들어오면 닫기를 취소해요.
 *
 * 위치는 팝오버를 그린 뒤 크기를 재서 정해요. 크기를 알아야 위로 뒤집을지 정할 수 있어서예요.
 * 열려 있는 동안 스크롤하거나 창 크기가 바뀌면 트리거와 자리가 어긋나므로 닫아요.
 */
const usePopover = ({ placement }: UsePopoverParams) => {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0 });

  const triggerRef = useRef<HTMLSpanElement>(null);
  const popoverRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearCloseTimer = () => {
    if (closeTimerRef.current === null) return;

    clearTimeout(closeTimerRef.current);
    closeTimerRef.current = null;
  };

  // 사라질 때 걸려 있는 닫기 타이머를 지워요
  useEffect(() => clearCloseTimer, []);

  const close = () => {
    clearCloseTimer();
    setIsOpen(false);
  };

  const handlePointerEnter = () => {
    clearCloseTimer();
    setIsOpen(true);
  };

  const handlePointerLeave = () => {
    clearCloseTimer();
    closeTimerRef.current = setTimeout(close, CLOSE_DELAY_MS);
  };

  // 여기서 바꾼 좌표는 브라우저가 그리기 전에 반영돼요. 그래서 이전 좌표나 (0, 0)에 잠깐 보이지 않아요
  useLayoutEffect(() => {
    if (!isOpen || triggerRef.current === null || popoverRef.current === null) {
      return;
    }

    const { clientWidth, clientHeight } = document.documentElement;

    setPosition(
      getPopoverPosition({
        triggerRect: triggerRef.current.getBoundingClientRect(),
        popoverSize: popoverRef.current.getBoundingClientRect(),
        // 스크롤바를 뺀, 실제로 보이는 영역 기준으로 가장자리를 재요
        viewport: { width: clientWidth, height: clientHeight },
        placement,
      }),
    );
  }, [isOpen, placement]);

  useEffect(() => {
    if (!isOpen) return;

    // 캡처 단계에서 들어야 페이지뿐 아니라 안쪽 스크롤 영역의 스크롤도 잡혀요
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);

    return () => {
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [isOpen]);

  /** 트리거와 팝오버 양쪽에 붙여요. 둘 사이를 오가는 동안에는 닫히지 않아요 */
  const hoverProps = {
    onPointerEnter: handlePointerEnter,
    onPointerLeave: handlePointerLeave,
  };

  return { isOpen, position, triggerRef, popoverRef, hoverProps };
};

export default usePopover;
