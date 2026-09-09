import useDesktopLlmSettingsQuery from "@api/queries/useDesktopLlmSettingsQuery";
import useDesktopLlmStatusQuery from "@api/queries/useDesktopLlmStatusQuery";
import { desktopLlmKeys } from "@api/queryKey/desktopLlm";
import useDesktop from "@hooks/common/useDesktop";
import useTimeout from "@hooks/common/useTimeout";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";

import {
  CLAUDE_SUBSCRIPTION_MESSAGE,
  SAVED_DURATION_MS,
} from "../constants/claudeSubscription";

interface SettingsDraft {
  model: string;
  effort: string;
}

/**
 * Claude 구독 카드의 상태 조회·로그인·로그아웃·설정 저장 흐름.
 *
 * 데스크톱 셸의 preload `llm` API(기획서 4.4)만 부르고 서버 API는 쓰지 않아요.
 * `llm`이 없는 브라우저에서는 `isAvailable`이 false이고 나머지 값은 비어 있어요.
 *
 * - 상태는 쿼리(`useDesktopLlmStatusQuery`)가 들어올 때 한 번 읽고, 그 뒤로는 셸이
 *   `onStatusChanged`로 주는 값으로 캐시를 덮어요(로드맵 Q67). 로그인·갱신·로그아웃·질문 결과가
 *   전부 이 이벤트로 오므로 폴링하지 않아요.
 * - 로그인은 셸이 시스템 브라우저를 열고 콜백을 받을 때까지 기다려요. 그동안 버튼은 진행 중이고,
 *   실패·취소·타임아웃은 거절로 와서 안내만 보여 줘요. 토큰은 화면으로 오지 않아요.
 * - 설정은 입력 중인 값(`draft`)이 있으면 그것을, 없으면 셸의 값을 보여 주고, 저장하면 셸이
 *   돌려준 값으로 캐시를 덮은 뒤 2초 동안 `저장됨`을 보여요.
 */
export const useClaudeSubscription = () => {
  const { desktopApi } = useDesktop();
  const llm = desktopApi?.llm;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: status = null, refetch: refetchStatus } =
    useDesktopLlmStatusQuery({ llm });
  const { data: settings = null } = useDesktopLlmSettingsQuery({ llm });

  const [isSigningIn, setIsSigningIn] = useState(false);
  const [isSigningOut, setIsSigningOut] = useState(false);
  const [authError, setAuthError] = useState<string>();

  const [draft, setDraft] = useState<SettingsDraft | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [settingsError, setSettingsError] = useState<string>();
  const { isTimedOut: isSaved, start: startSavedTimeout } = useTimeout({
    timeout: SAVED_DURATION_MS,
  });

  // 셸이 알려 주는 상태 변화로 캐시를 덮어요. 화면을 떠나면 구독을 끊어요
  useEffect(() => {
    if (llm === undefined) return;

    return llm.onStatusChanged((next) => {
      queryClient.setQueryData(desktopLlmKeys.status(), next);
    });
  }, [llm, queryClient]);

  const handleSignIn = async () => {
    if (llm === undefined || isSigningIn) return;

    setIsSigningIn(true);
    try {
      await llm.signIn();
      setAuthError(undefined);
      await refetchStatus();
    } catch {
      setAuthError(CLAUDE_SUBSCRIPTION_MESSAGE.signInFailed);
    } finally {
      setIsSigningIn(false);
    }
  };

  const handleSignOut = async () => {
    if (llm === undefined || isSigningOut) return;

    setIsSigningOut(true);
    try {
      await llm.signOut();
      setAuthError(undefined);
      await refetchStatus();
    } catch {
      setAuthError(CLAUDE_SUBSCRIPTION_MESSAGE.signOutFailed);
    } finally {
      setIsSigningOut(false);
    }
  };

  const modelValue = draft?.model ?? settings?.model ?? "";
  const effortValue = draft?.effort ?? settings?.effort ?? "";
  const isSettingsDirty =
    settings !== null &&
    (modelValue !== settings.model || effortValue !== settings.effort);

  const handleModelChange = (model: string) => {
    setDraft({ model, effort: effortValue });
    setSettingsError(undefined);
  };

  const handleEffortChange = (effort: string) => {
    setDraft({ model: modelValue, effort });
    setSettingsError(undefined);
  };

  const handleSettingsSubmit = async () => {
    if (llm === undefined || isSaving || !isSettingsDirty) return;

    setIsSaving(true);
    try {
      const next = await llm.updateSettings({
        model: modelValue,
        effort: effortValue,
      });
      queryClient.setQueryData(desktopLlmKeys.settings(), next);
      setDraft(null);
      setSettingsError(undefined);
      startSavedTimeout();
    } catch {
      setSettingsError(CLAUDE_SUBSCRIPTION_MESSAGE.settingsFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const handleGoToWorkspace = () => navigate(PATH_ROUTE.HOME);

  return {
    isAvailable: llm !== undefined,
    status,

    isSigningIn,
    isSigningOut,
    authError,
    handleSignIn,
    handleSignOut,

    settings,
    modelValue,
    effortValue,
    isSettingsDirty,
    isSaving,
    isSaved,
    settingsError,
    handleModelChange,
    handleEffortChange,
    handleSettingsSubmit,

    handleGoToWorkspace,
  };
};
