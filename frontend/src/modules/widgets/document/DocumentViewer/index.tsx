import { useParams } from "react-router";

import DocumentContent from "./ui/DocumentContent";
import DocumentLoadFailed from "./ui/DocumentLoadFailed";

/**
 * 문서 보기 섹션. 주소의 문서를 불러와 제목과 본문을 보여줘요.
 *
 * 주소의 두 id는 읽은 이 자리에서 확인해요. 정수가 아니면 조회를 시작하지 않고 문서를 불러오지 못했다고 알려요.
 * 그래서 조회하는 쪽(`ui/DocumentContent`와 그 아래)은 정수만 받고 다시 검사하지 않아요.
 */
export default function DocumentViewer() {
  const params = useParams();

  const workspaceId = Number(params.workspaceId);
  const documentId = Number(params.documentId);
  const isValidAddress =
    Number.isInteger(workspaceId) && Number.isInteger(documentId);

  if (!isValidAddress) {
    // 주소가 잘못되면 다시 조회할 것이 없어 `다시 시도`에 동작을 넘기지 않아요
    return <DocumentLoadFailed />;
  }

  return <DocumentContent workspaceId={workspaceId} documentId={documentId} />;
}
