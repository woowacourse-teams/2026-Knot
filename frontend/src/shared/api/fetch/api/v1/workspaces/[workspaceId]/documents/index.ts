import {
  GetDocumentsResponseDto,
  type GetDocumentsResponseRaw,
} from "@api/dto/document";
import { httpClient } from "@api/httpClient";

export const DOCUMENTS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/documents`;

/** 서버가 한 번에 주는 최대 문서 수 */
const MAX_PAGE_SIZE = 100;

/**
 * 이어 받는 요청 횟수의 한계. 100개씩 20번이라 문서 2,000개까지 받아요.
 * 한계가 없으면 다음 페이지가 끝나지 않는 오류에서 요청이 멈추지 않아요
 */
const MAX_PAGE_COUNT = 20;

interface GetDocumentsApiParams {
  workspaceId: number;
  /** 앞 응답의 `nextCursor`. 없으면 첫 페이지를 받아요 */
  cursor?: string;
  /** 한 번에 받을 문서 수(1~100). 없으면 서버 기본값 50을 써요 */
  size?: number;
  /** 이 녹음에서 나온 문서만 받아요. 없으면 워크스페이스의 문서 전체가 대상이에요 */
  recordingSessionId?: number;
}

/**
 * @description 워크스페이스의 문서 목록 한 페이지와 주제 폴더 전체를 조회합니다. 녹음 ID를 주면 그 녹음에서 나온 문서만 대상이에요. 실패는 `400` 잘못된 cursor · size · 녹음 ID · `401` 미인증 · `403` 워크스페이스 멤버 아님으로 구분해요
 * @param params - 워크스페이스 ID·커서·한 번에 받을 문서 수·녹음 ID
 * @returns 주제 폴더 전체·최신순 문서 한 페이지·다음 페이지 커서
 * @example
 * const { topics, items, nextCursor } = await getDocumentsApi({ workspaceId: 1 });
 */
export const getDocumentsApi = async ({
  workspaceId,
  cursor,
  size,
  recordingSessionId,
}: GetDocumentsApiParams) => {
  const response = await httpClient<GetDocumentsResponseRaw>({
    method: "get",
    url: DOCUMENTS_API_PATH(workspaceId),
    params: { cursor, size, recordingSessionId },
  });

  return new GetDocumentsResponseDto(response.data);
};

interface GetAllDocumentsApiParams {
  workspaceId: number;
  /** 이 녹음에서 나온 문서만 받아요. 없으면 워크스페이스의 문서 전체가 대상이에요 */
  recordingSessionId?: number;
}

/**
 * @description 워크스페이스의 문서를 다음 페이지가 없을 때까지 이어 받아 하나로 합칩니다. 녹음 ID를 주면 그 녹음에서 나온 문서만 대상이에요. 도중에 한 요청이라도 실패하면 그때까지 받은 문서를 돌려주지 않고 실패해요
 * @param params - 워크스페이스 ID·녹음 ID
 * @returns 주제 폴더 전체·최신순 문서 전체(최대 2,000개)·다음 페이지 커서. 응답 하나가 아니라 여러 응답을 합친 값이에요
 * @example
 * const { topics, items } = await getAllDocumentsApi({ workspaceId: 1 });
 */
export const getAllDocumentsApi = async ({
  workspaceId,
  recordingSessionId,
}: GetAllDocumentsApiParams) => {
  const firstPage = await getDocumentsApi({
    workspaceId,
    size: MAX_PAGE_SIZE,
    recordingSessionId,
  });
  const items = [...firstPage.items];
  let nextCursor = firstPage.nextCursor;

  for (
    let pageCount = 1;
    nextCursor !== null && pageCount < MAX_PAGE_COUNT;
    pageCount += 1
  ) {
    const page = await getDocumentsApi({
      workspaceId,
      cursor: nextCursor,
      size: MAX_PAGE_SIZE,
      recordingSessionId,
    });

    items.push(...page.items);
    nextCursor = page.nextCursor;
  }

  // 주제 폴더는 어느 응답에나 전체가 담겨 있어 첫 응답의 것을 써요.
  // 한계에서 멈췄으면 nextCursor가 null이 아니에요
  return { topics: firstPage.topics, items, nextCursor };
};
