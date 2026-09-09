import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * Claude 구독 설정 화면(`/claude-subscription`)으로 이동하는 도메인 훅.
 *
 * 이 화면은 데스크톱 앱(`window.knotDesktop.llm`)에서만 뜻이 있어요. 브라우저에서 열면
 * 화면이 데스크톱 앱에서 이어가라는 안내를 보여 주므로, 진입점을 데스크톱에서만 그리는 건 쓰는 쪽이 맡습니다.
 */
const useNavigateToClaudeSubscription = () => {
  const navigate = useNavigate();

  const navigateToClaudeSubscription = () => {
    navigate(PATH_ROUTE.CLAUDE_SUBSCRIPTION);
  };

  return { navigateToClaudeSubscription };
};

export default useNavigateToClaudeSubscription;
