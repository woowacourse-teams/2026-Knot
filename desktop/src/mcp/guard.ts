/**
 * 로컬 MCP 서버의 요청 검사 (로드맵 Q48).
 *
 * - 경로는 `/mcp` 하나뿐이다. 그 외는 404.
 * - `Origin` 헤더가 **있으면** 403. CLI는 Origin을 보내지 않고, 보내는 것은 브라우저이므로
 *   DNS 리바인딩을 막는다(MCP 스펙 MUST).
 * - `Host`가 `127.0.0.1:<port>`·`localhost:<port>`가 아니면 403.
 * - `Authorization: Bearer <연결 토큰>`이 없거나 다르면 401. 비교는 길이가 같을 때 상수 시간이다.
 *
 * Origin·Host를 토큰보다 먼저 검사해 브라우저 출처의 요청이 토큰 유무를 알 수 없게 한다.
 * 이 파일은 Electron·SDK에 의존하지 않는 순수 함수라 vitest로 직접 검증한다.
 */

import { timingSafeEqual } from "node:crypto";
import type { IncomingHttpHeaders } from "node:http";

export const MCP_PATH = "/mcp";

export interface McpGuardRequest {
  method?: string | undefined;
  url?: string | undefined;
  headers: IncomingHttpHeaders;
}

export interface McpGuardExpectation {
  port: number;
  token: string;
}

export type McpGuardVerdict = { ok: true } | { ok: false; status: 401 | 403 | 404; message: string };

export function allowedHosts(port: number): readonly string[] {
  return [`127.0.0.1:${port}`, `localhost:${port}`];
}

export function checkMcpRequest(request: McpGuardRequest, expected: McpGuardExpectation): McpGuardVerdict {
  if (pathOf(request.url) !== MCP_PATH) {
    return { ok: false, status: 404, message: "Not found" };
  }
  if (request.headers.origin !== undefined) {
    return { ok: false, status: 403, message: "Origin header is not allowed" };
  }
  const host = request.headers.host;
  if (typeof host !== "string" || !allowedHosts(expected.port).includes(host.toLowerCase())) {
    return { ok: false, status: 403, message: "Host is not allowed" };
  }
  if (!hasValidBearer(request.headers.authorization, expected.token)) {
    return { ok: false, status: 401, message: "Invalid or missing connection token" };
  }
  return { ok: true };
}

function pathOf(url: string | undefined): string {
  if (url === undefined) return "";
  const queryIndex = url.indexOf("?");
  return queryIndex === -1 ? url : url.slice(0, queryIndex);
}

function hasValidBearer(header: string | string[] | undefined, token: string): boolean {
  if (typeof header !== "string") return false;
  const match = /^Bearer\s+(\S+)\s*$/i.exec(header);
  if (match === null) return false;
  const presented = Buffer.from(match[1] ?? "", "utf8");
  const expected = Buffer.from(token, "utf8");
  if (presented.length !== expected.length) return false;
  return timingSafeEqual(presented, expected);
}
