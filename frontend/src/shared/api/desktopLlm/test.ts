import { describe, expect, it, vi } from "vitest";

import type { LlmStreamHandlers } from "@/shared/types/desktop";

import { streamDesktopAnswer, type DesktopLlmApi } from ".";

/** 셸의 `llm.streamAnswer`를 흉내 내요. 콜백을 붙잡아 두고 테스트가 직접 불러요 */
const fakeLlm = () => {
  const cancel = vi.fn();
  let handlers: LlmStreamHandlers | null = null;
  const streamAnswer = vi.fn((_input, on: LlmStreamHandlers) => {
    handlers = on;
    return cancel;
  });

  const llm = {
    getStatus: vi.fn(),
    signIn: vi.fn(),
    signOut: vi.fn(),
    onStatusChanged: vi.fn(),
    streamAnswer,
  } as unknown as DesktopLlmApi;

  return {
    llm,
    cancel,
    streamAnswer,
    get on() {
      if (handlers === null)
        throw new Error("streamAnswer가 아직 불리지 않았어요");
      return handlers;
    },
  };
};

const PARAMS = { workspaceId: 7, sessionId: 42, content: "질문" };

describe("streamDesktopAnswer", () => {
  it("ID를 문자열로 넘기고, 조각을 흘린 뒤 complete를 값으로 돌려준다", async () => {
    const fake = fakeLlm();
    const onChunk = vi.fn();

    const pending = streamDesktopAnswer({ llm: fake.llm, ...PARAMS, onChunk });
    fake.on.chunk("안녕");
    fake.on.chunk("하세요");
    fake.on.complete({ messageId: 11 });

    await expect(pending).resolves.toEqual({ kind: "complete", messageId: 11 });
    expect(onChunk.mock.calls.map(([delta]) => delta)).toEqual([
      "안녕",
      "하세요",
    ]);
    expect(fake.streamAnswer).toHaveBeenCalledWith(
      { workspaceId: "7", sessionId: "42", content: "질문" },
      expect.any(Object),
    );
  });

  it("error는 코드·문구·폴백 여부를 그대로 값으로 돌려준다", async () => {
    const fake = fakeLlm();

    const pending = streamDesktopAnswer({
      llm: fake.llm,
      ...PARAMS,
      onChunk: vi.fn(),
    });
    fake.on.error({
      code: "SUBSCRIPTION_NOT_SIGNED_IN",
      message: "로그인 안 됨",
      fallback: true,
    });

    await expect(pending).resolves.toEqual({
      kind: "error",
      code: "SUBSCRIPTION_NOT_SIGNED_IN",
      message: "로그인 안 됨",
      fallback: true,
    });
  });

  it("signal이 끊기면 셸의 취소 함수를 부르고 cancelled로 끝나며, 늦게 온 조각은 버린다", async () => {
    const fake = fakeLlm();
    const onChunk = vi.fn();
    const controller = new AbortController();

    const pending = streamDesktopAnswer({
      llm: fake.llm,
      ...PARAMS,
      onChunk,
      signal: controller.signal,
    });
    fake.on.chunk("앞");
    controller.abort();
    fake.on.chunk("늦음");
    fake.on.complete({ messageId: 1 });

    await expect(pending).resolves.toEqual({ kind: "cancelled" });
    expect(fake.cancel).toHaveBeenCalledTimes(1);
    expect(onChunk.mock.calls.map(([delta]) => delta)).toEqual(["앞"]);
  });

  it("이미 끊긴 signal이면 셸을 부르지 않는다", async () => {
    const fake = fakeLlm();
    const controller = new AbortController();
    controller.abort();

    await expect(
      streamDesktopAnswer({
        llm: fake.llm,
        ...PARAMS,
        onChunk: vi.fn(),
        signal: controller.signal,
      }),
    ).resolves.toEqual({ kind: "cancelled" });
    expect(fake.streamAnswer).not.toHaveBeenCalled();
  });
});
