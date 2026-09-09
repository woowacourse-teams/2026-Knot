import type { AgentRegistrationTarget } from "@/shared/types/desktop";

/** 복사 뒤 `복사됨` 표시를 유지하는 시간(ms). 초대 카드와 같아요 */
export const COPIED_DURATION_MS = 2000;

/**
 * 포트 변경·토큰 재발급 뒤 상태를 한 번 더 읽기까지 기다리는 시간(ms).
 *
 * 셸의 `setPort`·`rotateToken`은 저장·재기동을 시작하면 곧바로 응답하고, 서버가 실제로
 * 열렸는지는 그 뒤 0.5~1초 사이에 정해져요(2026-09-09 데스크톱 실측).
 */
export const RESTART_SETTLE_DELAY_MS = 1000;

/** MCP 서버 포트로 허용하는 범위(로드맵 Q47). 잘 알려진 포트는 피해요 */
export const AGENT_PORT_RANGE = { min: 1024, max: 65535 } as const;

interface RegistrationTargetItem {
  target: AgentRegistrationTarget;
  title: string;
  description: string;
}

/**
 * 복사 버튼을 보여 줄 대상. 순서가 화면 순서예요(로드맵 Q50).
 *
 * 앱은 사용자 홈의 CLI 설정 파일·스킬 디렉터리를 직접 고치지 않고, 사용자가 복사한 명령을
 * 터미널에서 실행하는 것으로만 등록이 이뤄져요.
 */
export const AGENT_REGISTRATION_TARGETS: RegistrationTargetItem[] = [
  {
    target: "claude-code",
    title: "Claude Code",
    description: "터미널에서 복사한 명령을 실행하면 Knot MCP 서버가 등록돼요.",
  },
  {
    target: "codex",
    title: "Codex CLI",
    description: "복사한 설정을 ~/.codex/config.toml에 붙여 넣어요.",
  },
  {
    target: "gemini",
    title: "Gemini CLI",
    description: "터미널에서 복사한 명령을 실행하면 Knot MCP 서버가 등록돼요.",
  },
  {
    target: "skill",
    title: "Knot 스킬",
    description:
      "에이전트가 언제 팀 문서를 검색할지 알려 주는 안내예요. 복사한 명령이 스킬 파일을 각 CLI의 스킬 폴더로 복사해요.",
  },
];

export const AGENT_STATUS_LABEL = {
  loading: "확인하는 중이에요",
  running: "연결 준비됨",
  stopped: "서버가 꺼져 있어요",
  failed: "서버를 열지 못했어요",
} as const;

export const AGENT_CONNECTION_MESSAGE = {
  copyFailed: "복사하지 못했어요. 잠시 후 다시 시도해 주세요.",
  portInvalid: `${AGENT_PORT_RANGE.min}~${AGENT_PORT_RANGE.max} 사이의 정수를 입력해 주세요.`,
  portFailed: "포트를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.",
  rotateFailed:
    "연결 토큰을 다시 발급하지 못했어요. 잠시 후 다시 시도해 주세요.",
  rotateConfirm:
    "재발급하면 지금 등록된 CLI는 더 이상 연결되지 않아요. 새 명령을 다시 복사해 등록해야 해요.",
  noToolCall: "아직 없어요",
} as const;

/** 워크스페이스 소유자 고지(로드맵 R27). 문서 본문이 어디로 가는지 숨기지 않아요 */
export const OWNER_NOTICE =
  "검색 결과의 문서 본문은 사용자의 CLI 에이전트와 그 에이전트가 쓰는 모델 제공자(Anthropic·OpenAI·Google)로 전송돼요. 워크스페이스 소유자가 동기화한 문서만 대상이에요.";
