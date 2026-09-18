import { describe, expect, it } from "vitest";

import { getInviteLink } from ".";

describe("getInviteLink", () => {
  it("linkToken을 /invite/<linkToken> 진입 경로에 넣어 origin이 붙은 링크와 표시용 경로를 만든다", () => {
    const linkToken = "Xk3vQ9mZp2LrT7wB1nHc4A";

    const { inviteLink, displayInviteLink } = getInviteLink(linkToken);

    expect(displayInviteLink).toBe(`/invite/${linkToken}`);
    expect(inviteLink).toBe(`${window.location.origin}/invite/${linkToken}`);
  });

  it("URL에 그대로 쓸 수 없는 문자는 인코딩한다", () => {
    const linkToken = "a/b c?d#e";

    const { inviteLink, displayInviteLink } = getInviteLink(linkToken);

    expect(displayInviteLink).toBe(`/invite/${encodeURIComponent(linkToken)}`);
    expect(inviteLink).toBe(
      `${window.location.origin}/invite/${encodeURIComponent(linkToken)}`,
    );
  });
});
