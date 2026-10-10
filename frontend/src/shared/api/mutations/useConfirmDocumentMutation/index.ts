import { confirmDocumentApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/documents/[documentId]/confirmations/me";
import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";
import { documentKeys } from "@api/queryKey/document";
import { useMutation, useQueryClient } from "@tanstack/react-query";

interface ConfirmDocumentMutationVariables {
  workspaceId: number;
  documentId: number;
}

/**
 * 문서를 확인했다고 기록하는 뮤테이션 훅.
 *
 * 요청 본문이 없어 ID만 넘겨요.
 * 성공하면 그 문서의 상세 캐시를 무효화해요. 확인 대상 캐시는 상세 키 아래에 있어 함께 다시 받아요.
 * 응답에 내 확인 상태가 없어, 응답으로 캐시를 직접 고치지 않고 다시 받아요.
 * 확인 대상이 아니라는 409도 화면이 아는 내 상태가 서버와 다른 것이라, 같은 캐시를 무효화해요.
 */
const useConfirmDocumentMutation = () => {
  const queryClient = useQueryClient();

  const invalidateDocument = (variables: ConfirmDocumentMutationVariables) =>
    queryClient.invalidateQueries({
      queryKey: documentKeys.detail(variables),
    });

  return useMutation({
    mutationFn: (variables: ConfirmDocumentMutationVariables) =>
      confirmDocumentApi(variables),
    onSuccess: (_, variables) => invalidateDocument(variables),
    onError: (error, variables) => {
      if (isHttpError(error, HTTP_ERROR_TYPE.conflict)) {
        return invalidateDocument(variables);
      }
    },
  });
};

export default useConfirmDocumentMutation;
