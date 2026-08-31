import { describe, expect, it } from "vitest";

import { getInviteLink } from ".";

describe("getInviteLink", () => {
  it("참여 코드를 /workspace/code?code= 진입 경로에 넣어 origin이 붙은 링크와 표시용 경로를 만든다", () => {
    const code = "X35D3S";

    const { inviteLink, displayInviteLink } = getInviteLink(code);

    expect(displayInviteLink).toBe(`/workspace/code?code=${code}`);
    expect(inviteLink).toBe(
      `${window.location.origin}/workspace/code?code=${code}`,
    );
  });

  it("URL에 그대로 쓸 수 없는 문자는 인코딩한다", () => {
    const code = "a/b c?d#e";

    const { inviteLink, displayInviteLink } = getInviteLink(code);

    expect(displayInviteLink).toBe(
      `/workspace/code?code=${encodeURIComponent(code)}`,
    );
    expect(inviteLink).toBe(
      `${window.location.origin}/workspace/code?code=${encodeURIComponent(code)}`,
    );
  });
});
