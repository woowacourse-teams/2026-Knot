import DesktopLoginPrompt from "@features/auth/DesktopLoginPrompt";
import { RouterProvider } from "react-router/dom";
import { router } from "@routes/routes";

/**
 * `DesktopLoginPrompt`는 라우터 밖에 둡니다. 데스크톱 셸이 GitHub 로그인 뷰를 앱 창 안에
 * 붙이는 동안 어느 화면에 있든 위쪽 띠에 "취소"가 보여야 하고, 그 띠는 경로와 무관해요
 * (기획서 5.2, 로드맵 Q68). 브라우저에서는 아무것도 그리지 않습니다.
 */
const App = () => {
  return (
    <>
      <DesktopLoginPrompt />
      <RouterProvider router={router} />
    </>
  );
};

export default App;
