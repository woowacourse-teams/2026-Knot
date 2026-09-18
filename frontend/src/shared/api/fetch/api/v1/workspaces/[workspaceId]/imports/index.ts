import {
  PostNotionImportResponseDto,
  type PostNotionImportResponseRaw,
} from "@api/dto/notionImport";
import { httpClient } from "@api/httpClient";

export const NOTION_IMPORTS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/imports`;

/**
 * @description 워크스페이스의 Notion 동기화(Import)를 시작합니다. 이미 실행 중이면(409) 그 실행의 ID를 그대로 받아요
 * @param workspaceId - 워크스페이스 ID
 * @returns 상태 조회에 쓸 Import 실행 ID
 * @example
 * const { id } = await startNotionImportApi(1);
 */
export const startNotionImportApi = async (workspaceId: number) => {
  const response = await httpClient<PostNotionImportResponseRaw>({
    method: "post",
    url: NOTION_IMPORTS_API_PATH(workspaceId),
    // 202(새 실행)와 409(이미 실행 중)는 본문 모양이 같아 둘 다 성공으로 받아요
    validateStatus: (status) =>
      (status >= 200 && status < 300) || status === 409,
  });

  return new PostNotionImportResponseDto(response.data);
};
