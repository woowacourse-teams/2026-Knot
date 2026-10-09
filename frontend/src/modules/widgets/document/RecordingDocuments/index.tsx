import { useParams } from "react-router";

import LoadFailedState from "./ui/LoadFailedState";
import RecordingDocumentsContent from "./ui/RecordingDocumentsContent";

/**
 * 녹음을 끝낸 뒤 문서가 만들어질 때까지 보여 주는 정리 화면 섹션.
 *
 * 주소의 녹음 상태를 3초마다 다시 조회하고, 상태에 따라 정리 중 · 문서로 만들 내용 없음 · 문서를 만들지 못함 가운데 하나를 보여줘요.
 * 문서 만들기에 실패했으면 그 자리에서 다시 시도할 수 있고, 접수되면 정리 중으로 돌아가요.
 *
 * 주소의 두 id는 읽은 이 자리에서 확인해요. 정수가 아니면 조회를 시작하지 않고 문서를 불러오지 못했다고 알려요.
 * 그래서 조회하는 쪽(`ui/RecordingDocumentsContent`와 그 아래)은 정수만 받고 다시 검사하지 않아요.
 */
export default function RecordingDocuments() {
  const params = useParams();

  const workspaceId = Number(params.workspaceId);
  const recordingId = Number(params.recordingId);
  const isValidAddress =
    Number.isInteger(workspaceId) && Number.isInteger(recordingId);

  if (!isValidAddress) {
    // 주소가 잘못되면 다시 조회할 것이 없어 「다시 시도」에 동작을 넘기지 않아요
    return <LoadFailedState />;
  }

  // 다른 녹음의 주소로 바뀌면 다시 시도 결과 같은 앞 녹음의 상태가 남지 않도록 새로 그려요
  return (
    <RecordingDocumentsContent
      key={recordingId}
      workspaceId={workspaceId}
      recordingId={recordingId}
    />
  );
}
