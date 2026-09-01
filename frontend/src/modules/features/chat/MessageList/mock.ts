/**
 * 턴별 근거 문서 수. 질문 메시지 ID를 키로 씁니다.
 *
 * 메시지 조회 응답에는 없는 정보라 대응 API(#241)가 정해질 때까지 임시로 둡니다.
 * 키는 msw mock 응답의 질문 메시지 ID라, 실제 API를 붙이면 어느 턴에도 붙지 않습니다.
 */
export const mockSourceCounts: Record<
  number,
  { totalCount: number; foundCount: number }
> = {
  1001: { totalCount: 112, foundCount: 3 },
  1003: { totalCount: 112, foundCount: 5 },
};
