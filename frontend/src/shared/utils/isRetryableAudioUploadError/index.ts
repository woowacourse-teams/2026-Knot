import { HTTP_ERROR_TYPE, isHttpError } from "@api/httpClient/error";
import axios from "axios";

/** 완료 확인 시점에 저장소에 아직 파일이 없어, 다시 올리면 풀릴 수 있는 오류 코드 */
const AUDIO_UPLOAD_NOT_COMPLETED = "AUDIO_UPLOAD_NOT_COMPLETED";

/**
 * 최종 오디오 업로드를 URL 발급부터 다시 시도할 만한 일시 실패인지 확인해요.
 *
 * 네트워크 오류·5xx·저장소 PUT 실패·`AUDIO_UPLOAD_NOT_COMPLETED`만 `true`이고, 그 밖의 4xx는 다시 보내도
 * 결과가 같아 `false`예요. 저장소 PUT은 httpClient를 거치지 않아 axios 오류 그대로 와요.
 */
export const isRetryableAudioUploadError = (error: unknown) => {
  if (isHttpError(error)) {
    return (
      error.type === HTTP_ERROR_TYPE.network ||
      error.type === HTTP_ERROR_TYPE.serverError ||
      error.code === AUDIO_UPLOAD_NOT_COMPLETED
    );
  }

  return axios.isAxiosError(error);
};
