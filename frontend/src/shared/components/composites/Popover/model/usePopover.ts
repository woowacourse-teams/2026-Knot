import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";

import useTimeout from "@hooks/common/useTimeout";

import type { PopoverPlacement, PopoverTriggerProps } from "../types/popover";
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
 * 그래서 트리거(`triggerProps`)와 카드(`cardProps`) 양쪽에 같은 여닫기 함수를 붙이고, 벗어나면 바로 닫지 않고
 * {@link CLOSE_DELAY_MS}만큼 기다려요. 그 사이 다른 쪽에 들어오면 닫기를 취소해요.
 *
 * 위치는 팝오버를 그린 뒤 크기를 재서 정해요. 크기를 알아야 위로 뒤집을지 정할 수 있어서예요.
 * 열려 있는 동안 트리거를 품은 영역이 스크롤되거나 창 크기가 바뀌면 트리거와 자리가 어긋나므로 닫아요.
 * 카드 안이나 트리거와 관계없는 영역이 스크롤될 때는 닫지 않아요.
 */
const usePopover = ({ placement }: UsePopoverParams) => {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0 });

  const triggerRef = useRef<HTMLElement | null>(null);
  const popoverRef = useRef<HTMLDivElement>(null);

  const { start: startCloseTimer, clear: clearCloseTimer } = useTimeout({
    timeout: CLOSE_DELAY_MS,
    callback: () => setIsOpen(false),
  });

  // 렌더링마다 새 함수가 되면 React가 ref를 떼었다 다시 붙이므로 한 번만 만들어요
  const setTriggerRef = useCallback((node: HTMLElement | null) => {
    triggerRef.current = node;
  }, []);

  const close = () => {
    clearCloseTimer();
    setIsOpen(false);
  };

  const handlePointerEnter = () => {
    clearCloseTimer();
    setIsOpen(true);
  };

  const handlePointerLeave = () => {
    startCloseTimer();
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

    const handleScroll = (event: Event) => {
      // 트리거를 품은 영역이 스크롤될 때만 트리거가 움직여요. 페이지 스크롤이면 대상이 document예요
      if (!(event.target instanceof Node)) return;
      if (!event.target.contains(triggerRef.current)) return;

      close();
    };

    // 캡처 단계에서 들어야 페이지뿐 아니라 안쪽 스크롤 영역의 스크롤도 잡혀요
    window.addEventListener("scroll", handleScroll, true);
    window.addEventListener("resize", close);

    return () => {
      window.removeEventListener("scroll", handleScroll, true);
      window.removeEventListener("resize", close);
    };
  }, [isOpen]);

  /** 트리거와 카드 양쪽에 붙여요. 둘 사이를 오가는 동안에는 닫히지 않아요 */
  const hoverProps = {
    onPointerEnter: handlePointerEnter,
    onPointerLeave: handlePointerLeave,
  };

  const triggerProps: PopoverTriggerProps = { ref: setTriggerRef, ...hoverProps };
  const cardProps = { ref: popoverRef, ...hoverProps };

  return { isOpen, position, triggerProps, cardProps };
};

export default usePopover;
