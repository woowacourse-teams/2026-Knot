import {
  NICKNAME_ERROR_MESSAGE,
  NICKNAME_PATTERN,
} from "../constants/nickname";

/**
 * 닉네임이 허용 문자로만 이루어졌는지 검사해요.
 *
 * 규칙을 어기면 사용자에게 보여줄 문구를, 통과하면 `undefined`를 돌려줍니다.
 * 빈 값은 "아직 입력 전"이라 에러가 아니에요. 비어 있을 때 버튼을 막는 건 카드가 따로 맡습니다.
 *
 * 글자 수는 여기서 보지 않습니다. 입력창의 `maxLength`가 21자째를 막고 있어서
 * 이 함수가 넘치는 값을 볼 일이 없어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237 닉네임 입력 에러}
 */
export const getNicknameErrorMessage = (nickname: string) =>
  NICKNAME_PATTERN.test(nickname) ? undefined : NICKNAME_ERROR_MESSAGE;
