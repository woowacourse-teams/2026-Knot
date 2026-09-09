import { afterEach, describe, expect, it, vi } from "vitest";
import { logMock } from "./helpers/logMock";

vi.mock("electron-log/main", () => ({ default: logMock }));

const { LoopbackClosedError, startLoopbackServer } = await import("../src/main/auth/loopbackServer");

const servers: Array<{ close(): void }> = [];

afterEach(() => {
  for (const server of servers.splice(0)) server.close();
});

async function start() {
  const server = await startLoopbackServer();
  servers.push(server);
  // close()가 두 번 불려도 안전하도록 이미 닫힌 뒤의 호출은 무시된다
  return server;
}

async function waitForClosed(port: number): Promise<void> {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    try {
      await fetch(`http://127.0.0.1:${port}/callback?code=x`);
    } catch {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("서버가 닫히지 않았다");
}

describe("startLoopbackServer", () => {
  it("127.0.0.1의 임의 포트에 열린다", async () => {
    const server = await start();
    expect(server.port).toBeGreaterThan(0);
  });

  it("GET /callback의 code·state를 돌려주고 안내 페이지를 준 뒤 닫힌다", async () => {
    const server = await start();
    const response = await fetch(`http://127.0.0.1:${server.port}/callback?code=dc&state=st`);

    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toContain("text/html");
    expect(response.headers.get("cache-control")).toBe("no-store");
    expect(await response.text()).toContain("Knot 앱으로 돌아가세요");
    await expect(server.callback).resolves.toEqual({ code: "dc", state: "st" });
    await waitForClosed(server.port);
  });

  it("실패 콜백(error)은 실패 안내 페이지를 주고 error를 돌려준다", async () => {
    const server = await start();
    const response = await fetch(`http://127.0.0.1:${server.port}/callback?error=access_denied`);

    expect(response.status).toBe(200);
    expect(await response.text()).toContain("로그인하지 못했어요");
    await expect(server.callback).resolves.toEqual({ error: "access_denied" });
  });

  it("다른 경로·메서드는 404이고 콜백은 계속 기다린다", async () => {
    const server = await start();
    let settled = false;
    server.callback.then(
      () => {
        settled = true;
      },
      () => {
        settled = true;
      },
    );

    expect((await fetch(`http://127.0.0.1:${server.port}/`)).status).toBe(404);
    expect((await fetch(`http://127.0.0.1:${server.port}/callback?code=dc`, { method: "POST" })).status).toBe(404);
    await new Promise((resolve) => setImmediate(resolve));
    expect(settled).toBe(false);
  });

  it("code도 error도 없는 요청은 400이다", async () => {
    const server = await start();
    expect((await fetch(`http://127.0.0.1:${server.port}/callback?state=st`)).status).toBe(400);
  });

  it("콜백 전에 닫으면 LoopbackClosedError로 거절되고 포트가 풀린다", async () => {
    const server = await start();
    const pending = server.callback;
    server.close();

    await expect(pending).rejects.toBeInstanceOf(LoopbackClosedError);
    await waitForClosed(server.port);
  });
});
