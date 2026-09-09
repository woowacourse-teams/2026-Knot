import { describe, expect, it } from "vitest";

import type { ReferenceChunk } from "../types/searchReference";
import { groupReferencesByPage } from "./groupReferencesByPage";

interface CreateChunkParams {
  rank: number;
  score: number;
  pageId: string;
}

const createChunk = ({ rank, score, pageId }: CreateChunkParams) =>
  ({
    rank,
    relevanceScore: score,
    source: "NOTION",
    notionPage: {
      id: pageId,
      title: `문서 ${pageId}`,
      notionUrl: `https://www.notion.so/${pageId}`,
      updatedAt: "2026-09-01T00:00:00Z",
    },
  }) satisfies ReferenceChunk;

describe("groupReferencesByPage", () => {
  it("같은 페이지의 청크를 하나로 묶고 가장 높은 점수를 대표 점수로 둔다", () => {
    const pages = groupReferencesByPage([
      createChunk({ rank: 1, score: 0.95, pageId: "a" }),
      createChunk({ rank: 2, score: 0.9, pageId: "b" }),
      createChunk({ rank: 3, score: 0.88, pageId: "a" }),
    ]);

    expect(pages).toHaveLength(2);
    expect(pages[0]).toMatchObject({
      id: "a",
      title: "문서 a",
      href: "https://www.notion.so/a",
      score: 0.95,
      rank: 1,
    });
    expect(pages[1]).toMatchObject({ id: "b", score: 0.9, rank: 2 });
  });

  it("대표 점수 내림차순으로 정렬하고, 점수가 같으면 앞선 순위가 먼저 온다", () => {
    const pages = groupReferencesByPage([
      createChunk({ rank: 1, score: 0.7, pageId: "a" }),
      createChunk({ rank: 2, score: 0.9, pageId: "b" }),
      createChunk({ rank: 3, score: 0.9, pageId: "c" }),
    ]);

    expect(pages.map((page) => page.id)).toEqual(["b", "c", "a"]);
  });

  it("빈 목록이면 빈 배열을 돌려준다", () => {
    expect(groupReferencesByPage([])).toEqual([]);
  });
});
