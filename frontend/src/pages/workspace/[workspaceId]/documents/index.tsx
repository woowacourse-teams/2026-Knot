import styled from "@emotion/styled";
import DocumentList from "@widgets/document/DocumentList";

/**
 * 문서 목록 화면 (`/workspace/:workspaceId/documents`)
 *
 * 워크스페이스의 모든 문서를 폴더(주제)별로 모아 보여준다. 행을 누르면 문서 보기 화면으로 간다.
 * GNB·사이드바·하단 독은 `WorkspaceLayout`이 담당하고, 이 페이지는 문서 목록 섹션을 가운데에 놓기만 한다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36054 문서 목록/기본
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-36187 문서 목록/빈 상태
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2115-8596 문서 목록/불러오기 실패
 */
export default function DocumentsPage() {
  return (
    <Root>
      <DocumentList />
    </Root>
  );
}

/** 피그마: 목록의 위 끝이 화면 위에서 112px. GNB(68px)와 본문 위 여백(20px) 아래로 24px을 띄운다 */
const Root = styled.div`
  display: flex;
  justify-content: center;
  min-height: 100%;
  padding: 1.5rem 1.5rem 7rem; /* 24px 24px 112px — 아래는 하단 독 자리 */
`;
