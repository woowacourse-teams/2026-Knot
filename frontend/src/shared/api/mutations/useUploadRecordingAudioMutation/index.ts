import { uploadRecordingAudioApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]/audioUploadUrl";
import { useMutation } from "@tanstack/react-query";

interface UploadRecordingAudioMutationVariables {
  uploadUrl: string;
  audio: Blob;
}

/**
 * 발급받은 Presigned URL로 최종 오디오를 객체 저장소에 직접 올리는 뮤테이션 훅.
 *
 * 우리 API가 아니라 요청 DTO가 없어요. 업로드 완료 확인 API는 아직 연결하지 않았어요.
 */
const useUploadRecordingAudioMutation = () => {
  return useMutation({
    mutationFn: (variables: UploadRecordingAudioMutationVariables) =>
      uploadRecordingAudioApi(variables),
  });
};

export default useUploadRecordingAudioMutation;
