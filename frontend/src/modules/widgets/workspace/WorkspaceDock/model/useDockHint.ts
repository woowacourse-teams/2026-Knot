import { useEffect, useRef, useState } from "react";

import {
  DOCK_HINT_MAX_SEEN_COUNT,
  DOCK_HINT_SEEN_COUNT_KEY,
  DOCK_HINT_VISIT_KEY,
} from "../constants/dockHint";

interface UseDockHintParams {
  /** 독이 이미 펼쳐져 있는지. 펼쳐져 있으면 안내할 게 없어요 */
  isDockExpanded: boolean;
}

/** 이번 방문에서 안내를 띄우기로 했는지. 아직 정하지 않았으면 `null` */
type VisitDecision = "shown" | "hidden" | null;

const readVisitDecision = (): VisitDecision => {
  try {
    const decision = sessionStorage.getItem(DOCK_HINT_VISIT_KEY);

    return decision === "shown" || decision === "hidden" ? decision : null;
  } catch {
    return null;
  }
};

const writeVisitDecision = (decision: Exclude<VisitDecision, null>) => {
  try {
    sessionStorage.setItem(DOCK_HINT_VISIT_KEY, decision);
  } catch {
    // 남길 수 없으면 이번 방문만 판단하고 넘어가요
  }
};

const readSeenCount = () => {
  try {
    return Number(localStorage.getItem(DOCK_HINT_SEEN_COUNT_KEY)) || 0;
  } catch {
    // 몇 번 봤는지 알 수 없으면 이미 다 본 것으로 쳐요
    return DOCK_HINT_MAX_SEEN_COUNT;
  }
};

const writeSeenCount = (count: number) => {
  try {
    localStorage.setItem(DOCK_HINT_SEEN_COUNT_KEY, String(count));
  } catch {
    // 남길 수 없으면 이번 방문만 보여주고 넘어가요
  }
};

/**
 * 독 안내를 띄울지 정하고, 보여준 방문 횟수를 브라우저에 남깁니다.
 *
 * 처음 몇 번만 알려주면 되는 안내라 {@link DOCK_HINT_MAX_SEEN_COUNT}번의 방문까지만 띄웁니다.
 *
 * 세는 단위는 화면을 그린 횟수가 아니라 **방문**입니다. 한 방문 안에서 새로고침하거나 홈과 탐색을
 * 오가는 것은 여전히 같은 방문이라, 그때마다 세면 사용자가 안내를 읽기도 전에 세 번이 다 닳습니다.
 * 그래서 방문 단위 판단은 탭을 닫으면 지워지는 `sessionStorage`에 남기고,
 * 방문 횟수만 `localStorage`에 쌓아 다음에 다시 와도 이어서 셉니다.
 *
 * 저장소를 못 쓰는 브라우저(시크릿 모드 등)에서는 아예 띄우지 않습니다. 몇 번 봤는지 알 수 없어
 * 매번 띄우게 되는데, 그러면 안내가 아니라 방해가 되기 때문입니다.
 *
 * 독이 펼쳐진 채로 들어온 화면(탐색)에서는 세지도 띄우지도 않습니다.
 */
export const useDockHint = ({ isDockExpanded }: UseDockHintParams) => {
  const [isHintVisible, setIsHintVisible] = useState(false);

  // StrictMode에서 효과가 두 번 실행돼도 판단은 한 번만 해요
  const hasDecidedRef = useRef(false);

  useEffect(() => {
    if (hasDecidedRef.current || isDockExpanded) return;

    hasDecidedRef.current = true;

    const visitDecision = readVisitDecision();

    // 이번 방문에서 이미 정해 뒀으면 그 판단을 그대로 따라요
    if (visitDecision !== null) {
      setIsHintVisible(visitDecision === "shown");
      return;
    }

    const seenCount = readSeenCount();

    if (seenCount >= DOCK_HINT_MAX_SEEN_COUNT) {
      writeVisitDecision("hidden");
      return;
    }

    writeSeenCount(seenCount + 1);
    writeVisitDecision("shown");
    setIsHintVisible(true);
  }, [isDockExpanded]);

  // 안내대로 독을 열었으면 할 일을 다 한 안내라 이번 방문에는 다시 띄우지 않아요
  useEffect(() => {
    if (!isDockExpanded) return;

    setIsHintVisible(false);
  }, [isDockExpanded]);

  return { isHintVisible };
};
