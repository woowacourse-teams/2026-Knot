/**
 * 로그인 콜백을 받는 loopback HTTP 서버 (기획서 5.2, RFC 8252 §8.3).
 *
 * 로그인을 시작할 때 `127.0.0.1`의 임의 포트에 열고, `GET /callback` 하나를 받으면
 * "앱으로 돌아가세요" 페이지를 준 뒤 바로 닫는다. 다른 경로·메서드는 404이며, 콜백은
 * 한 번만 받는다. state 대조·토큰 교환은 여기서 하지 않고 호출자(`loginFlow`)가 한다.
 * 쿼리 값(코드·state)은 로그에 남기지 않는다.
 */

import { createServer } from "node:http";
import type { IncomingMessage, Server, ServerResponse } from "node:http";
import { logger } from "../logging";
import { isCallbackLike, parseCallbackParams } from "./authorizeUrl";
import type { AuthCallbackParams } from "./authorizeUrl";

export const LOOPBACK_HOST = "127.0.0.1";
export const LOOPBACK_CALLBACK_PATH = "/callback";

export interface LoopbackServer {
  readonly port: number;
  /** 첫 콜백의 쿼리. 콜백 전에 `close()`되면 `LoopbackClosedError`로 거절된다 */
  readonly callback: Promise<AuthCallbackParams>;
  close(): void;
}

/** 콜백을 받기 전에 서버가 닫혔다(타임아웃·취소·앱 종료) */
export class LoopbackClosedError extends Error {
  constructor() {
    super("로그인 콜백을 받기 전에 loopback 서버가 닫혔다.");
    this.name = "LoopbackClosedError";
  }
}

export function startLoopbackServer(): Promise<LoopbackServer> {
  return new Promise((resolveServer, rejectServer) => {
    let settled = false;
    let resolveCallback: (params: AuthCallbackParams) => void = () => {};
    let rejectCallback: (error: Error) => void = () => {};
    const callback = new Promise<AuthCallbackParams>((resolve, reject) => {
      resolveCallback = resolve;
      rejectCallback = reject;
    });
    // 아무도 기다리지 않는 상태에서 닫혀도 unhandled rejection이 되지 않게 한다
    callback.catch(() => {});

    const server: Server = createServer((request, response) => {
      handleRequest(request, response, (params) => {
        if (settled) return false;
        settled = true;
        resolveCallback(params);
        // 응답을 보낸 뒤 닫는다(이벤트 루프 다음 턴). 열린 연결도 함께 끊는다
        setImmediate(() => {
          shutdown(server);
        });
        return true;
      });
    });

    const close = (): void => {
      if (!settled) {
        settled = true;
        rejectCallback(new LoopbackClosedError());
      }
      shutdown(server);
    };

    server.once("error", (error) => {
      logger.warn("[knot] loopback 서버 오류", { reason: error.name });
      rejectServer(error);
    });

    server.listen(0, LOOPBACK_HOST, () => {
      const address = server.address();
      if (address === null || typeof address === "string") {
        rejectServer(new Error("loopback 서버 주소를 읽지 못했다."));
        shutdown(server);
        return;
      }
      logger.info("[knot] loopback 서버 열림", { port: address.port });
      resolveServer({ port: address.port, callback, close });
    });
  });
}

function shutdown(server: Server): void {
  server.close();
  server.closeAllConnections();
}

/**
 * @param accept 콜백을 받아들이면 true. 이미 받았으면 false(두 번째 요청은 410)
 */
function handleRequest(
  request: IncomingMessage,
  response: ServerResponse,
  accept: (params: AuthCallbackParams) => boolean,
): void {
  const url = new URL(request.url ?? "/", `http://${LOOPBACK_HOST}`);
  if (request.method !== "GET" || url.pathname !== LOOPBACK_CALLBACK_PATH) {
    respond(response, 404, "찾을 수 없어요.");
    return;
  }

  const params = parseCallbackParams(url.searchParams);
  if (!isCallbackLike(params)) {
    respond(response, 400, "잘못된 요청이에요.");
    return;
  }

  if (!accept(params)) {
    respond(response, 410, "이미 처리된 로그인이에요. Knot 앱으로 돌아가세요.");
    return;
  }

  if (params.error !== undefined) {
    logger.warn("[knot] 로그인 콜백 실패", { error: params.error });
    respond(response, 200, "로그인하지 못했어요. Knot 앱으로 돌아가 다시 시도하세요.");
    return;
  }
  respond(response, 200, "로그인이 끝났어요. Knot 앱으로 돌아가세요. 이 창은 닫아도 돼요.");
}

function respond(response: ServerResponse, status: number, message: string): void {
  response.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store",
    "Referrer-Policy": "no-referrer",
    Connection: "close",
  });
  response.end(renderPage(message));
}

function renderPage(message: string): string {
  return [
    "<!doctype html>",
    '<html lang="ko"><head><meta charset="utf-8"><title>Knot</title>',
    "<style>body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;display:flex;",
    "align-items:center;justify-content:center;height:100vh;margin:0;color:#222;background:#fafafa}",
    "main{text-align:center;padding:32px}h1{font-size:20px;font-weight:600}</style></head>",
    `<body><main><h1>${escapeHtml(message)}</h1></main></body></html>`,
  ].join("");
}

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (char) => {
    switch (char) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}
