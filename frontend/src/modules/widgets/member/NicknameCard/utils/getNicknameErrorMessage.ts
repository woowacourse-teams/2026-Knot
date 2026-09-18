import {
  NICKNAME_ERROR_MESSAGE,
  NICKNAME_PATTERN,
  NICKNAME_WHITESPACE_ERROR_MESSAGE,
  NICKNAME_WHITESPACE_PATTERN,
} from "../constants/nickname";

/**
 * 닉네임이 규칙에 맞는지 검사해 보여줄 문구를 돌려줍니다. 통과하면 `undefined`예요.
 *
 * 공백을 먼저 봅니다. 허용 문자 검사에서도 공백은 걸리지만, 정책이 공백에만
 * 다른 문구를 쓰도록 정해두었기 때문이에요. 공백과 다른 금지 문자가 같이 있으면
 * 더 구체적인 공백 문구가 나갑니다.
 *
 * 빈 값은 "아직 입력 전"이라 에러가 아니에요. 비어 있을 때 버튼을 막는 건 카드가 따로 맡습니다.
 *
 * 글자 수는 여기서 보지 않습니다. 입력창의 `maxLength`가 21자째를 막고 있어서
 * 이 함수가 넘치는 값을 볼 일이 없어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237 닉네임 입력 에러}
 */
export const getNicknameErrorMessage = (nickname: string) => {
  if (NICKNAME_WHITESPACE_PATTERN.test(nickname)) {
    return NICKNAME_WHITESPACE_ERROR_MESSAGE;
  }

  return NICKNAME_PATTERN.test(nickname) ? undefined : NICKNAME_ERROR_MESSAGE;
};
