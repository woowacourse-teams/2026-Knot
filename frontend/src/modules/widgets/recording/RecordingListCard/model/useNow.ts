import useInterval from "@hooks/common/useInterval";
import { useEffect, useState } from "react";

/** 화면의 시간 표시가 초 단위라 1초마다 다시 계산해요. */
const TICK_INTERVAL_MS = 1000;

/** 현재 시각. `isTicking`인 동안만 1초마다 갱신해요. */
export const useNow = (isTicking: boolean) => {
  const [now, setNow] = useState(() => Date.now());

  const { start, reset } = useInterval({
    callback: () => setNow(Date.now()),
    delay: TICK_INTERVAL_MS,
  });

  useEffect(() => {
    if (isTicking) start();
    else reset();
  }, [isTicking, reset, start]);

  return now;
};
