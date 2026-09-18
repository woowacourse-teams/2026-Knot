import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

interface NavigateToOnboardingCompleteParams {
  /** 완료 화면의 인사말에 넣을 닉네임. */
  nickname: string;
}

/**
 * 가입 완료 화면(`/onboarding/complete`)으로 이동하는 도메인 훅.
 *
 * 방금 등록한 닉네임을 라우터 state로 함께 넘겨요. 서버에 다시 물어볼 필요가 없는 값이라서요.
 * 완료 화면이 이 값을 어떤 이름으로 꺼내는지(`location.state.nickname`)를 여기서만 알면 되도록
 * 이동을 감쌌습니다.
 */
const useNavigateToOnboardingComplete = () => {
  const navigate = useNavigate();

  const navigateToOnboardingComplete = ({
    nickname,
  }: NavigateToOnboardingCompleteParams) => {
    navigate(PATH_ROUTE.ONBOARDING_COMPLETE, { state: { nickname } });
  };

  return { navigateToOnboardingComplete };
};

export default useNavigateToOnboardingComplete;
