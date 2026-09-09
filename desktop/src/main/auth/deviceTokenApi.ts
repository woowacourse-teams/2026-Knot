/**
 * 디바이스 토큰 API 클라이언트 (기획서 5.2 API 계약, 로드맵 `A6`·`A7`).
 *
 * - `POST /api/v1/auth/device/token`   `{code, codeVerifier, device}` → 토큰 한 벌. 코드 만료·재사용·verifier 불일치는 400 `DEVICE_CODE_INVALID`
 * - `POST /api/v1/auth/device/refresh` `{refreshToken}` → 새 access + **새 refresh**(rotation). 재사용 감지는 401 `REFRESH_TOKEN_INVALID`
 * - `POST /api/v1/auth/device/revoke`  `{refreshToken}` → 200(무효 토큰이어도 200, RFC 7009)
 *
 * 세 호출 모두 `Authorization` 헤더를 붙이지 않는다(자격증명은 본문의 코드·리프레시 토큰이다).
 * 응답 헤더는 30초 안에 와야 하며(`knotApi`와 같은 값), 네트워크 오류·타임아웃은 `KNOT_API_UNREACHABLE`,
 * 본문을 못 읽은 HTTP 오류는 `UNKNOWN`, 모양이 계약과 다르면 `KNOT_API_MALFORMED`다.
 * 토큰·코드·verifier는 로그에 남기지 않는다.
 */

import { logger } from "../logging";

export const DEVICE_AUTH_TIMEOUT_MS = 30_000;

/** 서버가 정의한 오류 코드(기획서 5.2) */
export const DEVICE_CODE_INVALID = "DEVICE_CODE_INVALID";
export const REFRESH_TOKEN_INVALID = "REFRESH_TOKEN_INVALID";

export const DEVICE_AUTH_ERRORS = {
  unreachable: { code: "KNOT_API_UNREACHABLE", message: "Knot 서버에 연결하지 못했어요" },
  malformed: { code: "KNOT_API_MALFORMED", message: "Knot 서버 응답이 계약과 달라요" },
  unknown: { code: "UNKNOWN", message: "로그인 처리에 실패했어요" },
} as const;

export interface DeviceInfo {
  /** 기기 이름(호스트명). 기기 목록 화면(`A11`)에 보인다 */
  name: string;
  platform: string;
  appVersion: string;
}

/** 토큰 교환·갱신 응답 */
export interface DeviceTokenGrant {
  accessToken: string;
  refreshToken: string;
  /** 액세스 토큰 수명(초) */
  expiresIn: number;
  session: {
    id: string;
    deviceName: string;
  };
}

export class DeviceAuthError extends Error {
  /** HTTP 오류면 상태 코드, 네트워크·모양 오류면 null */
  readonly status: number | null;
  readonly code: string;

  constructor(status: number | null, code: string, message: string) {
    super(message);
    this.name = "DeviceAuthError";
    this.status = status;
    this.code = code;
  }
}

/** 리프레시 토큰이 더는 쓸 수 없는 오류인가(재사용 감지·폐기·만료). 이때는 로컬 세션을 지운다 */
export function isRefreshTokenInvalid(error: unknown): boolean {
  return error instanceof DeviceAuthError && (error.status === 401 || error.code === REFRESH_TOKEN_INVALID);
}

export interface DeviceTokenExchangeInput {
  code: string;
  codeVerifier: string;
  device: DeviceInfo;
}

export interface DeviceTokenApi {
  exchange(input: DeviceTokenExchangeInput, signal?: AbortSignal): Promise<DeviceTokenGrant>;
  refresh(refreshToken: string, signal?: AbortSignal): Promise<DeviceTokenGrant>;
  revoke(refreshToken: string, signal?: AbortSignal): Promise<void>;
}

export interface DeviceTokenApiOptions {
  apiOrigin: string;
  fetch?: typeof fetch;
  timeoutMs?: number;
}

export function createDeviceTokenApi(options: DeviceTokenApiOptions): DeviceTokenApi {
  const doFetch = options.fetch ?? fetch;
  const timeoutMs = options.timeoutMs ?? DEVICE_AUTH_TIMEOUT_MS;

  async function post(path: string, body: unknown, signal: AbortSignal | undefined): Promise<Response> {
    const signals = [AbortSignal.timeout(timeoutMs)];
    if (signal !== undefined) signals.push(signal);

    let response: Response;
    try {
      response = await doFetch(new URL(path, options.apiOrigin).toString(), {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify(body),
        signal: AbortSignal.any(signals),
      });
    } catch (error) {
      if (signal?.aborted === true && isAbortError(error)) throw error;
      logger.warn("[knot] 인증 서버 호출 실패", { path, reason: errorName(error) });
      throw new DeviceAuthError(null, DEVICE_AUTH_ERRORS.unreachable.code, DEVICE_AUTH_ERRORS.unreachable.message);
    }

    if (!response.ok) {
      throw await toAuthError(response, path);
    }
    return response;
  }

  async function postForGrant(path: string, body: unknown, signal: AbortSignal | undefined): Promise<DeviceTokenGrant> {
    const response = await post(path, body, signal);
    let json: unknown;
    try {
      json = await response.json();
    } catch (error) {
      logger.warn("[knot] 인증 응답 본문을 읽지 못했다", { path, reason: errorName(error) });
      throw malformed();
    }
    return parseGrant(json);
  }

  return {
    exchange(input, signal) {
      return postForGrant("/api/v1/auth/device/token", input, signal);
    },

    refresh(refreshToken, signal) {
      return postForGrant("/api/v1/auth/device/refresh", { refreshToken }, signal);
    },

    async revoke(refreshToken, signal) {
      await post("/api/v1/auth/device/revoke", { refreshToken }, signal);
    },
  };
}

function isAbortError(error: unknown): boolean {
  return error instanceof Error && error.name === "AbortError";
}

async function toAuthError(response: Response, path: string): Promise<DeviceAuthError> {
  let { code, message } = DEVICE_AUTH_ERRORS.unknown as { code: string; message: string };
  try {
    const body = (await response.json()) as { code?: unknown; message?: unknown };
    if (typeof body.code === "string" && typeof body.message === "string") {
      code = body.code;
      message = body.message;
    }
  } catch {
    // 본문이 비었거나 JSON이 아니다
  }
  logger.warn("[knot] 인증 서버 HTTP 오류", { path, status: response.status, code });
  return new DeviceAuthError(response.status, code, message);
}

function parseGrant(json: unknown): DeviceTokenGrant {
  if (typeof json !== "object" || json === null) throw malformed();
  const { accessToken, refreshToken, expiresIn, session } = json as Record<string, unknown>;
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    typeof refreshToken !== "string" ||
    refreshToken.length === 0 ||
    typeof expiresIn !== "number" ||
    !Number.isFinite(expiresIn) ||
    expiresIn <= 0 ||
    typeof session !== "object" ||
    session === null
  ) {
    throw malformed();
  }
  const { id, deviceName } = session as Record<string, unknown>;
  if ((typeof id !== "number" && typeof id !== "string") || typeof deviceName !== "string") throw malformed();
  return { accessToken, refreshToken, expiresIn, session: { id: String(id), deviceName } };
}

function malformed(): DeviceAuthError {
  logger.warn("[knot] 인증 응답의 모양이 계약과 다르다");
  return new DeviceAuthError(null, DEVICE_AUTH_ERRORS.malformed.code, DEVICE_AUTH_ERRORS.malformed.message);
}

function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
