interface FormatSourceLabelParams {
  totalCount: number;
  foundCount: number;
}

/**
 * 근거 문서를 몇 개 찾았는지 알리는 문구를 만듭니다.
 * 답변이 끝나기 전에는 답변 위 회색 줄로, 끝난 뒤에는 근거 버튼의 라벨로 쓰이는 같은 문구입니다.
 *
 * @param totalCount - 워크스페이스에 쌓인 전체 문서 수
 * @param foundCount - 이번 답변의 근거가 된 문서 수
 * @returns 안내 문구. 찾은 문서가 없으면 `null`
 *
 * @example
 * formatSourceLabel({ totalCount: 112, foundCount: 3 });
 * // "문서 112개에서 3개를 찾았어요"
 */
export const formatSourceLabel = ({
  totalCount,
  foundCount,
}: FormatSourceLabelParams) => {
  if (foundCount === 0) return null;

  return `문서 ${totalCount.toLocaleString("ko-KR")}개에서 ${foundCount.toLocaleString("ko-KR")}개를 찾았어요`;
};
