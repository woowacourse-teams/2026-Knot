import { describe, expect, it } from "vitest";

import { GetMeResponseDto, PostNicknameRequestDto } from "./auth";
import { GetChatMessagesResponseDto } from "./chatMessage";
import {
  GetChatSessionsResponseDto,
  PostChatSessionRequestDto,
  PostChatSessionResponseDto,
} from "./chatSession";
import {
  GetNotionConnectionResponseDto,
  PostNotionOAuthAuthorizationResponseDto,
} from "./notionConnection";
import {
  GetWorkspacesResponseDto,
  PostWorkspaceRequestDto,
  PutLastViewedWorkspaceRequestDto,
  WorkspaceListItemDto,
} from "./workspace";
import {
  GetWorkspaceInvitationResponseDto,
  PostInvitationAcceptRequestDto,
  PostWorkspaceInvitationReissueResponseDto,
  PostWorkspaceInvitationResponseDto,
} from "./workspaceInvitation";

describe("DTO 생성자 변환", () => {
  describe("워크스페이스", () => {
    it("목록 응답의 항목을 WorkspaceListItemDto로 감싼다", () => {
      const dto = new GetWorkspacesResponseDto({
        lastViewedWorkspaceId: null,
        workspaces: [{ id: 1, name: "Knot 팀" }],
      });

      expect(dto.lastViewedWorkspaceId).toBeNull();
      expect(dto.workspaces[0]).toBeInstanceOf(WorkspaceListItemDto);
      expect(dto.workspaces[0]).toEqual({ id: 1, name: "Knot 팀" });
    });

    it("생성 요청 본문의 이름은 앞뒤 공백을 제거한다", () => {
      expect(new PostWorkspaceRequestDto({ name: "  Knot 팀 " })).toEqual({
        name: "Knot 팀",
      });
    });

    it("마지막 본 워크스페이스 요청 본문은 workspaceId만 담는다", () => {
      expect(new PutLastViewedWorkspaceRequestDto({ workspaceId: 1 })).toEqual({
        workspaceId: 1,
      });
    });
  });

  describe("인증", () => {
    it("회원 정보 응답 필드를 그대로 옮긴다", () => {
      const raw = {
        memberId: 1,
        nickname: "노티드",
        profileImageUrl: "https://example.com/1.png",
      };

      expect(new GetMeResponseDto(raw)).toEqual(raw);
    });

    it("닉네임 요청 본문은 nickname만 담는다", () => {
      expect(new PostNicknameRequestDto({ nickname: "노티드" })).toEqual({
        nickname: "노티드",
      });
    });
  });

  describe("워크스페이스 초대", () => {
    const raw = {
      code: "X35D3S",
      linkToken: "Xk3vQ9mZp2LrT7wB1nHc4A",
      expiresAt: "2026-09-08T00:00:00.000Z",
    };

    it("조회·발급·재발급 응답이 같은 초대 모양을 돌려준다", () => {
      expect(new GetWorkspaceInvitationResponseDto(raw)).toEqual(raw);
      expect(new PostWorkspaceInvitationResponseDto(raw)).toEqual(raw);
      expect(new PostWorkspaceInvitationReissueResponseDto(raw)).toEqual(raw);
    });

    it("참여 요청 본문은 credential만 담는다", () => {
      expect(
        new PostInvitationAcceptRequestDto({ credential: "X35D3S" }),
      ).toEqual({ credential: "X35D3S" });
    });
  });

  describe("대화", () => {
    const session = {
      id: 100,
      title: "DB 기술 선정 관련 문서",
      createdAt: "2026-09-01T00:00:00.000Z",
      lastMessageAt: "2026-09-01T01:00:00.000Z",
    };

    it("세션 목록 응답을 sessions 필드에, 생성 응답을 세션 모양으로 돌려준다", () => {
      expect(new GetChatSessionsResponseDto([session]).sessions).toEqual([
        session,
      ]);
      expect(new PostChatSessionResponseDto(session)).toEqual(session);
    });

    it("세션 생성 요청 본문은 title이 없으면 직렬화 결과에도 없다", () => {
      expect(JSON.stringify(new PostChatSessionRequestDto({}))).toBe("{}");
      expect(new PostChatSessionRequestDto({ title: "새 대화" })).toEqual({
        title: "새 대화",
      });
    });

    it("메시지 목록 응답을 messages 필드에 담는다", () => {
      const message = {
        id: 1001,
        role: "USER" as const,
        content: "안녕",
        createdAt: "2026-09-01T00:00:00.000Z",
      };

      expect(new GetChatMessagesResponseDto([message]).messages).toEqual([
        message,
      ]);
    });
  });

  describe("Notion 연결", () => {
    it("연결 시작 응답의 authorizationUrl을 그대로 옮긴다", () => {
      const raw = {
        authorizationUrl:
          "https://api.notion.com/v1/oauth/authorize?client_id=abc&state=xyz",
      };

      expect(new PostNotionOAuthAuthorizationResponseDto(raw)).toEqual(raw);
    });

    it("연결 상태 응답의 status를 그대로 옮긴다", () => {
      expect(
        new GetNotionConnectionResponseDto({ status: "CONNECTED" }),
      ).toEqual({ status: "CONNECTED" });
      expect(
        new GetNotionConnectionResponseDto({ status: "REAUTH_REQUIRED" }),
      ).toEqual({ status: "REAUTH_REQUIRED" });
    });
  });
});
