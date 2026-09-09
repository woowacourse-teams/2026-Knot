import useDesktopAgentStatusQuery from "@api/queries/useDesktopAgentStatusQuery";
import useDesktop from "@hooks/common/useDesktop";
import useTimeout from "@hooks/common/useTimeout";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";

import type { AgentRegistrationTarget } from "@/shared/types/desktop";

import {
  AGENT_CONNECTION_MESSAGE,
  COPIED_DURATION_MS,
  RESTART_SETTLE_DELAY_MS,
} from "../constants/agentConnection";
import { parsePort } from "../utils/parsePort";

type RegistrationPreviews = Partial<Record<AgentRegistrationTarget, string>>;

/**
 * CLI 에이전트 연결 카드의 상태 조회·스니펫 복사·포트 변경·토큰 재발급 흐름.
 *
 * 데스크톱 셸의 preload `agent` API(기획서 4.4)만 부르고 서버 API는 쓰지 않아요.
 * `agent`가 없는 브라우저에서는 `isAvailable`이 false이고 나머지 값은 비어 있어요.
 *
 * - 상태는 쿼리(`useDesktopAgentStatusQuery`)가 들어올 때와 10초마다 읽고, 포트 변경·토큰
 *   재발급 뒤에는 바로 한 번, 잠시 뒤 한 번 더 읽어요. 셸은 저장·재기동을 시작하면 곧바로
 *   응답하고 서버가 실제로 열렸는지(포트 충돌 등)는 비동기로 정해지므로, 재기동 직후 읽은
 *   상태에는 결과가 아직 없기 때문이에요. 기동 실패는 거절이 아니라 상태의 `error`로 와요.
 * - 복사는 main이 클립보드에 쓰고 토큰을 가린 미리보기만 돌려주므로, 화면은 미리보기와
 *   2초 동안의 `복사됨`만 보여 줘요.
 * - 포트는 입력 중인 값(`portDraft`)이 있으면 그것을, 없으면 상태의 포트를 보여 줘요.
 * - 토큰 재발급은 기존 등록을 무효로 만드니 한 번 더 확인한 뒤 실행하고, 이전 미리보기는 지워요.
 */
export const useAgentConnection = () => {
  const { desktopApi } = useDesktop();
  const agent = desktopApi?.agent;
  const navigate = useNavigate();

  const { data: status = null, refetch: refetchStatus } =
    useDesktopAgentStatusQuery({ agent });

  const [copiedTarget, setCopiedTarget] =
    useState<AgentRegistrationTarget | null>(null);
  const [previews, setPreviews] = useState<RegistrationPreviews>({});
  const [copyError, setCopyError] = useState<string>();
  const { start: startCopiedTimeout } = useTimeout({
    timeout: COPIED_DURATION_MS,
    callback: () => setCopiedTarget(null),
  });

  const [portDraft, setPortDraft] = useState<string | null>(null);
  const [portError, setPortError] = useState<string>();
  const [isPortApplying, setIsPortApplying] = useState(false);

  const [isRotateConfirming, setIsRotateConfirming] = useState(false);
  const [isRotating, setIsRotating] = useState(false);
  const [rotateError, setRotateError] = useState<string>();

  const settleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (settleTimerRef.current !== null) clearTimeout(settleTimerRef.current);
    },
    [],
  );

  const refreshStatus = async () => {
    await refetchStatus();
  };

  /** 재기동을 시킨 뒤에는 결과가 정해질 시간을 두고 한 번 더 읽어요 */
  const refreshStatusAfterRestart = async () => {
    await refetchStatus();

    if (settleTimerRef.current !== null) clearTimeout(settleTimerRef.current);
    settleTimerRef.current = setTimeout(() => {
      settleTimerRef.current = null;
      void refetchStatus();
    }, RESTART_SETTLE_DELAY_MS);
  };

  const handleCopy = async (target: AgentRegistrationTarget) => {
    if (agent === undefined) return;

    try {
      const { preview } = await agent.copyRegistration(target);

      setPreviews((prev) => ({ ...prev, [target]: preview }));
      setCopyError(undefined);
      setCopiedTarget(target);
      startCopiedTimeout();
    } catch {
      setCopyError(AGENT_CONNECTION_MESSAGE.copyFailed);
    }
  };

  const portValue = portDraft ?? (status ? String(status.port) : "");

  const handlePortChange = (value: string) => {
    setPortDraft(value);
    setPortError(undefined);
  };

  const handlePortSubmit = async () => {
    if (agent === undefined || isPortApplying) return;

    const parsed = parsePort(portValue);
    if (!parsed.ok) {
      setPortError(AGENT_CONNECTION_MESSAGE.portInvalid);
      return;
    }

    setIsPortApplying(true);
    try {
      await agent.setPort(parsed.port);
      setPortDraft(null);
      setPortError(undefined);
      await refreshStatusAfterRestart();
    } catch {
      setPortError(AGENT_CONNECTION_MESSAGE.portFailed);
    } finally {
      setIsPortApplying(false);
    }
  };

  const handleRotateRequest = () => {
    setRotateError(undefined);
    setIsRotateConfirming(true);
  };

  const handleRotateCancel = () => setIsRotateConfirming(false);

  const handleRotateConfirm = async () => {
    if (agent === undefined || isRotating) return;

    setIsRotating(true);
    try {
      await agent.rotateToken();
      // 이전 미리보기는 옛 토큰으로 만든 것이라 새로 복사해야 해요
      setPreviews({});
      setCopiedTarget(null);
      setRotateError(undefined);
      setIsRotateConfirming(false);
      await refreshStatusAfterRestart();
    } catch {
      setRotateError(AGENT_CONNECTION_MESSAGE.rotateFailed);
    } finally {
      setIsRotating(false);
    }
  };

  const handleGoToWorkspace = () => navigate(PATH_ROUTE.HOME);

  return {
    isAvailable: agent !== undefined,
    status,
    handleRefresh: () => void refreshStatus(),

    copiedTarget,
    previews,
    copyError,
    handleCopy,

    portValue,
    portError,
    isPortApplying,
    handlePortChange,
    handlePortSubmit,

    isRotateConfirming,
    isRotating,
    rotateError,
    handleRotateRequest,
    handleRotateCancel,
    handleRotateConfirm,

    handleGoToWorkspace,
  };
};
