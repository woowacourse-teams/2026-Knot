import useDocumentQuery from "@api/queries/useDocumentQuery";
import useClipboard from "@hooks/common/useClipboard";
import useTimeout from "@hooks/common/useTimeout";
import Button from "@primitives/ui/Button";
import { useParams } from "react-router";

/** 복사한 뒤 버튼 문구를 「복사됨」으로 보여주는 시간 */
const COPIED_DURATION_MS = 3000;

interface DocumentCopyButtonProps {
  /** 복사할 문서의 ID */
  documentId: number;
}

/**
 * 문서를 마크다운으로 클립보드에 복사하는 버튼(STT-R10).
 *
 * 본문에는 제목이 없어서 제목을 `#` 제목으로 앞에 붙여 복사해요.
 * 복사하면 3초 동안 「복사됨」으로 바뀌고, 토스트는 띄우지 않아요.
 * 문서 상세는 문서 보기 위젯과 같은 키로 조회해서 요청이 더 나가지 않아요.
 * 문서를 아직 받지 못했으면 아무것도 그리지 않아요.
 */
export default function DocumentCopyButton({
  documentId,
}: DocumentCopyButtonProps) {
  const params = useParams();
  const { data: documentDetail } = useDocumentQuery({
    workspaceId: Number(params.workspaceId),
    documentId,
  });
  const { copy } = useClipboard();
  const { start: startCopiedTimeout, isTimedOut: isCopied } = useTimeout({
    timeout: COPIED_DURATION_MS,
  });

  if (documentDetail === undefined) return null;

  const handleCopy = () => {
    copy({
      text: `# ${documentDetail.title}\n\n${documentDetail.content}`,
      onCopySuccess: startCopiedTimeout,
    });
  };

  return (
    <Button size="sm" variant="outline" onClick={handleCopy}>
      {isCopied ? "복사됨" : "복사"}
    </Button>
  );
}
