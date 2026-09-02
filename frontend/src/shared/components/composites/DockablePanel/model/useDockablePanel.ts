import { useEffect, useRef, useState, type FocusEvent, type KeyboardEvent } from "react";

interface UseDockablePanelParams {
  /** 고정했을 때 패널이 옮겨 갈 자리의 DOM id */
  dockTargetId: string;
}

/**
 * 트리거를 스쳐 지나가면 패널을 잠깐 띄우고(peek), 누르면 자리에 고정하는(dock) 상태.
 *
 * 포인터가 트리거에서 패널로 건너가는 동안에는 잠깐 둘 다 벗어나므로,
 * 벗어나자마자 닫지 않고 {@link PEEK_CLOSE_DELAY_MS}만큼 기다렸다 닫아요.
 *
 * 고정된 패널은 트리거 옆이 아니라 다른 자리에 그려야 해서, 그 자리의 DOM 요소를 찾아 함께 돌려줘요.
 * 마운트 뒤에 찾는 이유는 그 자리가 이 컴포넌트보다 늦게 그려질 수 있기 때문이에요.
 */

/** 포인터가 트리거와 패널 사이의 빈 곳을 지나는 동안 패널을 닫지 않고 기다리는 시간 */
const PEEK_CLOSE_DELAY_MS = 150;

const useDockablePanel = ({ dockTargetId }: UseDockablePanelParams) => {
  const [isDocked, setIsDocked] = useState(false);
  const [isPeeking, setIsPeeking] = useState(false);
  const [dockTarget, setDockTarget] = useState<HTMLElement | null>(null);

  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setDockTarget(document.getElementById(dockTargetId));
  }, [dockTargetId]);

  const cancelScheduledClose = () => {
    if (closeTimerRef.current === null) return;

    clearTimeout(closeTimerRef.current);
    closeTimerRef.current = null;
  };

  useEffect(() => cancelScheduledClose, []);

  const openPeek = () => {
    cancelScheduledClose();
    setIsPeeking(true);
  };

  const closePeek = () => {
    cancelScheduledClose();
    setIsPeeking(false);
  };

  const scheduleClosePeek = () => {
    cancelScheduledClose();
    closeTimerRef.current = setTimeout(() => setIsPeeking(false), PEEK_CLOSE_DELAY_MS);
  };

  const handleBlur = (e: FocusEvent<HTMLElement>) => {
    // 패널 안으로 포커스가 옮겨 간 것뿐이라면 닫지 않아요
    if (e.currentTarget.contains(e.relatedTarget)) return;

    closePeek();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLElement>) => {
    if (e.key !== "Escape") return;

    closePeek();
  };

  const handleToggleDock = () => {
    closePeek();
    setIsDocked((prev) => !prev);
  };

  /** 트리거와 패널을 함께 감싸는 요소에 붙여요. 둘 사이를 오갈 때 닫히지 않게 해요 */
  const rootProps = {
    onPointerEnter: openPeek,
    onPointerLeave: scheduleClosePeek,
    onFocus: openPeek,
    onBlur: handleBlur,
    onKeyDown: handleKeyDown,
  };

  const triggerProps = {
    onClick: handleToggleDock,
  };

  return {
    isDocked,
    /** 고정돼 있을 때는 이미 자리를 차지하고 있으므로 따로 띄우지 않아요 */
    isPeeking: isPeeking && !isDocked,
    dockTarget,
    rootProps,
    triggerProps,
  };
};

export default useDockablePanel;
