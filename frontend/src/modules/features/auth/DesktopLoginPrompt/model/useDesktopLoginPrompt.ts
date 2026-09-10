import useDesktop from "@hooks/common/useDesktop";
import { useEffect, useState } from "react";

import type { LoginPromptState } from "@/shared/types/desktop";

const CLOSED: LoginPromptState = { open: false, headerHeight: 0 };

/**
 * 데스크톱 로그인 뷰의 열림 상태를 듣고 취소 수단을 돌려주는 훅. `DesktopLoginPrompt`에만 쓰여요.
 *
 * 셸은 GitHub 로그인을 **메인 창 안에 뷰로 붙이고** 위쪽 `headerHeight`만 남겨요. 그 띠는
 * 여전히 이 웹 화면이라, 로그인 중이라는 사실과 "취소"를 그리는 것은 웹의 몫입니다.
 *
 * 브라우저나 이 이벤트가 없는 옛 셸에서는 아무 일도 하지 않아요(`open`이 계속 false).
 * 그때도 사용자는 뷰 안에서 `Esc`로 취소할 수 있어요(기획서 5.2, 로드맵 R31).
 */
const useDesktopLoginPrompt = () => {
  const { desktopApi } = useDesktop();
  const [prompt, setPrompt] = useState<LoginPromptState>(CLOSED);

  const subscribe = desktopApi?.auth?.onLoginPromptChanged;
  const cancelLogin = desktopApi?.auth?.cancelLogin;

  useEffect(() => {
    if (subscribe === undefined) return;

    return subscribe(setPrompt);
  }, [subscribe]);

  const cancel = () => {
    if (cancelLogin === undefined) return;

    // 뷰를 떼는 것은 셸이 하고, 그 결과가 이 훅에 `open: false`로 다시 와요
    cancelLogin().catch((error: unknown) => {
      console.error("로그인을 취소하지 못했어요.", error);
    });
  };

  return { prompt, cancel, canCancel: cancelLogin !== undefined };
};

export default useDesktopLoginPrompt;
