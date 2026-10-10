import { describe, expect, it } from "vitest";

import { groupDocumentsByTopic } from ".";

describe("groupDocumentsByTopic", () => {
  it("폴더를 이름순으로 놓는다", () => {
    const folders = groupDocumentsByTopic({
      topics: [
        { topic: "회원 관리", documentCount: 1 },
        { topic: "사용자 인터뷰", documentCount: 1 },
        { topic: "주간 회의", documentCount: 1 },
      ],
      documents: [
        { id: 3, topic: "회원 관리" },
        { id: 2, topic: "사용자 인터뷰" },
        { id: 1, topic: "주간 회의" },
      ],
    });

    expect(folders.map(({ topic }) => topic)).toEqual([
      "사용자 인터뷰",
      "주간 회의",
      "회원 관리",
    ]);
  });

  it("폴더 안의 문서는 받은 순서를 그대로 지킨다", () => {
    const folders = groupDocumentsByTopic({
      topics: [
        { topic: "주간 회의", documentCount: 2 },
        { topic: "회원 관리", documentCount: 2 },
      ],
      documents: [
        { id: 4, topic: "주간 회의" },
        { id: 3, topic: "회원 관리" },
        { id: 2, topic: "주간 회의" },
        { id: 1, topic: "회원 관리" },
      ],
    });

    expect(folders).toEqual([
      {
        topic: "주간 회의",
        documentCount: 2,
        documents: [
          { id: 4, topic: "주간 회의" },
          { id: 2, topic: "주간 회의" },
        ],
      },
      {
        topic: "회원 관리",
        documentCount: 2,
        documents: [
          { id: 3, topic: "회원 관리" },
          { id: 1, topic: "회원 관리" },
        ],
      },
    ]);
  });

  it("폴더의 문서 수는 받은 문서를 세지 않고 서버가 준 값을 쓴다", () => {
    const [folder] = groupDocumentsByTopic({
      topics: [{ topic: "회원 관리", documentCount: 12 }],
      documents: [{ id: 1, topic: "회원 관리" }],
    });

    expect(folder.documentCount).toBe(12);
    expect(folder.documents).toHaveLength(1);
  });

  it("주제 목록에 없는 주제의 문서도 폴더를 만들어 넣고, 문서 수는 받은 문서를 센다", () => {
    const folders = groupDocumentsByTopic({
      topics: [{ topic: "회원 관리", documentCount: 1 }],
      documents: [
        { id: 3, topic: "새 주제" },
        { id: 2, topic: "회원 관리" },
        { id: 1, topic: "새 주제" },
      ],
    });

    expect(folders).toEqual([
      {
        topic: "새 주제",
        documentCount: 2,
        documents: [
          { id: 3, topic: "새 주제" },
          { id: 1, topic: "새 주제" },
        ],
      },
      {
        topic: "회원 관리",
        documentCount: 1,
        documents: [{ id: 2, topic: "회원 관리" }],
      },
    ]);
  });

  it("받은 문서가 하나도 없는 주제도 폴더로 남긴다", () => {
    const folders = groupDocumentsByTopic({
      topics: [{ topic: "회원 관리", documentCount: 3 }],
      documents: [],
    });

    expect(folders).toEqual([
      { topic: "회원 관리", documentCount: 3, documents: [] },
    ]);
  });

  it("주제도 문서도 없으면 빈 배열을 돌려준다", () => {
    expect(groupDocumentsByTopic({ topics: [], documents: [] })).toEqual([]);
  });
});
