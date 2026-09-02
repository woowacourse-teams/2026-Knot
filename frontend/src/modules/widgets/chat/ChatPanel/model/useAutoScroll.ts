import { useLayoutEffect, useRef } from "react";

/**
 * 바닥으로 보고 있다고 인정하는 여유 높이(px).
 *
 * 스크롤이 정확히 끝에 닿는 경우는 드물고, 답변이 한 줄 늘어나는 순간에도 어긋나므로
 * 조금의 여유를 둡니다.
 */
const BOTTOM_THRESHOLD = 32;

/**
 * 내용이 길어질 때 대화를 바닥에 붙여 둡니다.
 *
 * 사용자가 위로 올려 지난 대화를 읽는 중이라면 내리지 않습니다. 답변이 도착할 때마다
 * 화면이 끌려 내려가면 읽던 자리를 잃기 때문입니다. 다시 바닥까지 내리면 따라붙습니다.
 *
 * @param content - 바뀔 때마다 바닥으로 따라붙을 값. 보통 지금까지 도착한 답변
 * @returns 스크롤 영역에 달 ref와 스크롤 핸들러
 */
export const useAutoScroll = (content: string) => {
  const containerRef = useRef<HTMLDivElement>(null);
  /** 지금 바닥을 보고 있는지. 렌더와 무관하므로 ref로 둡니다 */
  const isPinnedRef = useRef(true);

  const handleScroll = () => {
    const container = containerRef.current;
    if (!container) return;

    const distanceToBottom =
      container.scrollHeight - container.scrollTop - container.clientHeight;

    isPinnedRef.current = distanceToBottom <= BOTTOM_THRESHOLD;
  };

  // 그려진 뒤 화면에 나가기 전에 내려야 중간 위치가 한 프레임 비치지 않습니다
  useLayoutEffect(() => {
    const container = containerRef.current;
    if (!container || !isPinnedRef.current) return;

    container.scrollTop = container.scrollHeight;
  }, [content]);

  return { containerRef, handleScroll };
};
