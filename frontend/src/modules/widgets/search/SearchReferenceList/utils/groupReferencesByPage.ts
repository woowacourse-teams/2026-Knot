import type { ReferenceChunk, ReferencePage } from "../types/searchReference";

/**
 * 청크 단위 출처(최대 8건)를 페이지 단위로 묶습니다.
 *
 * 같은 페이지의 청크가 여럿이면 하나로 합치고, 페이지의 대표 점수는 그중 가장 높은 청크 점수예요.
 * 목록은 대표 점수 내림차순이고, 점수가 같으면 더 앞선 순위의 페이지가 먼저 옵니다.
 *
 * @param chunks - 서버가 준 관련도 순 출처 목록
 * @returns 페이지로 묶인 목록(대표 점수 내림차순)
 * @example
 * groupReferencesByPage(searchReferences); // [{ id: "page-a", score: 0.95, ... }, ...]
 */
export const groupReferencesByPage = (chunks: ReferenceChunk[]) => {
  const pagesById = new Map<string, ReferencePage>();

  chunks.forEach((chunk) => {
    const { id, title, notionUrl, updatedAt } = chunk.notionPage;
    const grouped = pagesById.get(id);

    if (grouped === undefined) {
      pagesById.set(id, {
        id,
        title,
        href: notionUrl,
        score: chunk.relevanceScore,
        rank: chunk.rank,
        source: chunk.source,
        updatedAt,
      });
      return;
    }

    grouped.score = Math.max(grouped.score, chunk.relevanceScore);
    grouped.rank = Math.min(grouped.rank, chunk.rank);
  });

  return [...pagesById.values()].sort(
    (a, b) => b.score - a.score || a.rank - b.rank,
  );
};
