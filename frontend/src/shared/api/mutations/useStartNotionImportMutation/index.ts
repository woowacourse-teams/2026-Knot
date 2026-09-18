import { startNotionImportApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/imports";
import { useMutation } from "@tanstack/react-query";

/**
 * 워크스페이스의 Notion 동기화(Import)를 시작하는 뮤테이션 훅.
 *
 * 요청 본문이 없어 `mutate(workspaceId)`로 워크스페이스 ID만 넘겨요.
 * 시작 응답은 접수(202)일 뿐이라 성공·실패 판정은 응답의 실행 ID로
 * `useNotionImportStatusQuery`를 폴링하는 쪽이 맡아요. 동기화 결과를 읽는
 * 쿼리(페이지 트리·마지막 동기화 시각)가 아직 없어 여기서 무효화할 쿼리는 없어요.
 */
const useStartNotionImportMutation = () => {
  return useMutation({
    mutationFn: (workspaceId: number) => startNotionImportApi(workspaceId),
  });
};

export default useStartNotionImportMutation;
