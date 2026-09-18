import {
  WORKSPACE_NAME_ERROR_MESSAGE,
  WORKSPACE_NAME_PATTERN,
} from "../constants/workspaceName";

/**
 * 워크스페이스 이름이 허용 문자로만 이루어졌는지 검사해요.
 *
 * 규칙을 어기면 사용자에게 보여줄 문구를, 통과하면 `undefined`를 돌려줍니다.
 * 빈 값은 "아직 입력 전"이라 에러가 아니에요. 비어 있을 때 버튼을 막는 건 카드가 따로 맡습니다.
 */
export const getWorkspaceNameErrorMessage = (name: string) =>
  WORKSPACE_NAME_PATTERN.test(name) ? undefined : WORKSPACE_NAME_ERROR_MESSAGE;
