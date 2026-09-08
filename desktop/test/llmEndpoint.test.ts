import { describe, expect, it } from "vitest";
import { anthropicMessagesUrl, chatCompletionsUrl, isAllowedLlmEndpoint } from "../src/main/llm/endpoint";

// 로드맵 Q27: `https:` 전체 + `http://localhost`·`http://127.0.0.1`. 네비게이션 허용 목록과 별개다.
describe("isAllowedLlmEndpoint", () => {
  it("https는 호스트와 무관하게 허용한다", () => {
    expect(isAllowedLlmEndpoint("https://api.anthropic.com")).toBe(true);
    expect(isAllowedLlmEndpoint("https://api.openai.com/v1")).toBe(true);
    expect(isAllowedLlmEndpoint("https://my-proxy.example.com:8443/v1/")).toBe(true);
  });

  it("http는 localhost·127.0.0.1만 포트 무관으로 허용한다", () => {
    expect(isAllowedLlmEndpoint("http://localhost:1234/v1")).toBe(true);
    expect(isAllowedLlmEndpoint("http://127.0.0.1:11434/v1")).toBe(true);
    expect(isAllowedLlmEndpoint("http://LOCALHOST:1234/v1")).toBe(true);
  });

  it("로컬이 아닌 http·다른 스킴·URL 아님은 거부한다", () => {
    expect(isAllowedLlmEndpoint("http://192.168.0.10:1234/v1")).toBe(false);
    expect(isAllowedLlmEndpoint("http://[::1]:1234/v1")).toBe(false);
    expect(isAllowedLlmEndpoint("http://localhost.evil.com/v1")).toBe(false);
    expect(isAllowedLlmEndpoint("file:///etc/passwd")).toBe(false);
    expect(isAllowedLlmEndpoint("ws://localhost:1234")).toBe(false);
    expect(isAllowedLlmEndpoint("localhost:1234")).toBe(false);
    expect(isAllowedLlmEndpoint("")).toBe(false);
  });
});

// 로드맵 Q43: URL 조립
describe("chatCompletionsUrl", () => {
  it("baseUrl 뒤에 /chat/completions를 붙이고 끝 슬래시는 정리한다", () => {
    expect(chatCompletionsUrl("http://localhost:1234/v1")).toBe("http://localhost:1234/v1/chat/completions");
    expect(chatCompletionsUrl("http://localhost:1234/v1/")).toBe("http://localhost:1234/v1/chat/completions");
  });
});

describe("anthropicMessagesUrl", () => {
  it("기본 baseUrl에는 /v1/messages를 붙인다", () => {
    expect(anthropicMessagesUrl("https://api.anthropic.com")).toBe("https://api.anthropic.com/v1/messages");
    expect(anthropicMessagesUrl("https://api.anthropic.com/")).toBe("https://api.anthropic.com/v1/messages");
  });

  it("이미 /v1로 끝나면 /messages만 붙인다", () => {
    expect(anthropicMessagesUrl("https://api.anthropic.com/v1")).toBe("https://api.anthropic.com/v1/messages");
  });
});
