import { AGENT_PORT_RANGE } from "../constants/agentConnection";

/**
 * 포트 입력값을 검사해 숫자로 바꿉니다.
 *
 * 앞뒤 공백은 무시하고, 1024~65535 사이의 정수만 받아요(로드맵 Q47). 소수·음수·빈 값·문자는 거절해요.
 *
 * @param value - 입력창의 문자열
 * @returns 통과하면 `{ ok: true, port }`, 아니면 `{ ok: false }`
 * @example
 * parsePort(" 47871 "); // { ok: true, port: 47871 }
 * parsePort("80"); // { ok: false }
 */
export const parsePort = (value: string) => {
  const trimmed = value.trim();

  if (!/^\d+$/.test(trimmed)) return { ok: false } as const;

  const port = Number(trimmed);

  if (port < AGENT_PORT_RANGE.min || port > AGENT_PORT_RANGE.max) {
    return { ok: false } as const;
  }

  return { ok: true, port } as const;
};
