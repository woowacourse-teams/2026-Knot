import useDesktop from "@hooks/common/useDesktop";
import { useEffect } from "react";
import { useNavigate } from "react-router";

import { resolveDeepLinkPath } from "../../deepLink";

import type { KnotDeepLink } from "@/shared/types/desktop";

/**
 * 데스크톱 셸의 딥링크를 받아 화면을 옮기는 훅. `DeepLinkListener`에만 쓰여요.
 *
 * 두 경로로 와요.
 * - 앱이 켜져 있을 때: 셸이 `onDeepLink`로 보내 줘요. 구독하고, 화면이 사라질 때 해제해요.
 * - 앱이 꺼져 있을 때 눌린 링크: 셸이 보관해 두므로 처음 그릴 때 `getPendingDeepLink`로 한 번 가져와요.
 *
 * 브라우저에서는 `window.knotDesktop`이 없어 아무것도 하지 않아요. 이동은 `replace`로 해서
 * 링크로 열린 화면에서 뒤로 가기가 이전 화면(대개 무관한 화면)으로 튀지 않게 합니다.
 */
const useDesktopDeepLink = () => {
  const { desktopApi } = useDesktop();
  const navigate = useNavigate();

  useEffect(() => {
    if (desktopApi === undefined) return;

    let isActive = true;
    const open = (link: KnotDeepLink) => {
      const path = resolveDeepLinkPath(link);
      if (path === null || !isActive) return;
      navigate(path, { replace: true });
    };

    const unsubscribe = desktopApi.onDeepLink(open);
    desktopApi.getPendingDeepLink().then(
      (pending) => {
        if (pending !== null) open(pending);
      },
      () => {
        // 보류 링크를 못 읽어도 화면은 정상이에요. 셸이 로그에 남깁니다
      },
    );

    return () => {
      isActive = false;
      unsubscribe();
    };
  }, [desktopApi, navigate]);
};

export default useDesktopDeepLink;
