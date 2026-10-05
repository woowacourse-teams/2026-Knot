import { useEffect } from "react";

interface UseBeforeUnloadParams {
  /** 켜져 있을 때만 새로고침·탭 닫기 전에 확인 창을 띄워요 */
  isEnabled: boolean;
}

/**
 * 새로고침하거나 탭을 닫기 전에 브라우저 확인 창을 띄웁니다.
 *
 * 창의 문구는 브라우저가 정해서 바꿀 수 없어요. 앱 안에서 화면을 옮기는 것은 막지 않아요.
 */
const useBeforeUnload = ({ isEnabled }: UseBeforeUnloadParams) => {
  // 켜져 있는 동안만 beforeunload를 구독하고, 꺼지거나 사라지면 구독을 풀어요
  useEffect(() => {
    if (!isEnabled) return;

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      // 오래된 브라우저는 returnValue가 있어야 확인 창을 띄워요
      e.returnValue = "";
    };

    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isEnabled]);
};

export default useBeforeUnload;
