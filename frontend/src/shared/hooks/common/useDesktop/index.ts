import type { KnotDesktopApi } from "@/shared/types/desktop";

/**
 * 지금 화면이 데스크톱 앱 안에서 돌고 있는지 알려주는 훅.
 *
 * 웹과 데스크톱이 같은 번들을 쓰기 때문에, 데스크톱에서만 다르게 굴어야 하는 곳
 * (외부 링크 열기·앱 메뉴가 부르는 액션 등)에서 갈래를 나눌 때 씁니다.
 *
 * 셸의 preload는 페이지 스크립트보다 먼저 `window.knotDesktop`을 넣고, 넣은 뒤에는
 * 사라지지 않아요. 그래서 값이 도중에 바뀌지 않고, 상태나 이펙트로 다시 읽을 필요가 없습니다.
 */
const useDesktop = () => {
  const desktopApi: KnotDesktopApi | undefined = window.knotDesktop;

  return { isDesktop: desktopApi !== undefined, desktopApi };
};

export default useDesktop;
