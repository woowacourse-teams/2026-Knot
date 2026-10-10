import {
  GetRecordingResponseDto,
  type GetRecordingResponseRaw,
} from "@api/dto/recording";
import { httpClient } from "@api/httpClient";

export const RECORDING_API_PATH = (workspaceId: number, recordingId: number) =>
  `/api/v1/workspaces/${workspaceId}/recordings/${recordingId}`;

interface GetRecordingApiParams {
  workspaceId: number;
  recordingId: number;
}

/**
 * @description 내 녹음 하나의 상태를 조회합니다. 녹음을 끝낸 뒤 문서 정리가 어디까지 됐는지 확인할 때 써요
 * @param params - 워크스페이스 ID·녹음 세션 ID
 * @returns 화면용 종합 상태와 실패 단계, 문서 생성 작업 ID
 * @example
 * const { status } = await getRecordingApi({ workspaceId: 1, recordingId: 10 });
 */
export const getRecordingApi = async ({
  workspaceId,
  recordingId,
}: GetRecordingApiParams) => {
  const response = await httpClient<GetRecordingResponseRaw>({
    method: "get",
    url: RECORDING_API_PATH(workspaceId, recordingId),
  });

  return new GetRecordingResponseDto(response.data);
};
