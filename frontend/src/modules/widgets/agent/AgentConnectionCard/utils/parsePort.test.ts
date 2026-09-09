import { describe, expect, it } from "vitest";

import { parsePort } from "./parsePort";

describe("parsePort", () => {
  it("1024~65535 사이의 정수를 받는다", () => {
    expect(parsePort("47871")).toEqual({ ok: true, port: 47871 });
    expect(parsePort("1024")).toEqual({ ok: true, port: 1024 });
    expect(parsePort("65535")).toEqual({ ok: true, port: 65535 });
  });

  it("앞뒤 공백은 무시한다", () => {
    expect(parsePort(" 47871 ")).toEqual({ ok: true, port: 47871 });
  });

  it("범위 밖이면 거절한다", () => {
    expect(parsePort("1023")).toEqual({ ok: false });
    expect(parsePort("65536")).toEqual({ ok: false });
    expect(parsePort("0")).toEqual({ ok: false });
  });

  it("정수가 아니면 거절한다", () => {
    expect(parsePort("")).toEqual({ ok: false });
    expect(parsePort("4787.1")).toEqual({ ok: false });
    expect(parsePort("-47871")).toEqual({ ok: false });
    expect(parsePort("포트")).toEqual({ ok: false });
    expect(parsePort("1e4")).toEqual({ ok: false });
  });
});
