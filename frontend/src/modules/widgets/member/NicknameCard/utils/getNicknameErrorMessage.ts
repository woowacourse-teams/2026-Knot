/**
 * 닉네임에 쓸 수 있는 글자.
 *
 * 완성된 한글(`가-힣`) 말고 자모(`ㄱ-ㅎ`, `ㅏ-ㅣ`)도 넣었어요.
 * 한글을 입력하는 도중에는 `ㄷ` → `도` → `동`처럼 자모가 잠깐 값에 들어오는데,
 * 자모를 빼면 글자를 치는 내내 에러가 깜빡입니다.
 */
const NICKNAME_PATTERN = /^[ㄱ-ㅎㅏ-ㅣ가-힣a-zA-Z()-]+$/;

const NICKNAME_ERROR_MESSAGE = "한글, 영어와 () - 만 사용할 수 있어요.";

/**
 * 닉네임이 규칙에 맞는지 확인하고, 어긋나면 보여줄 메시지를 돌려줍니다.
 *
 * 문제가 없으면 `undefined`를 돌려주므로 `TextField`의 `errorMessage`에 그대로 넘기면 돼요.
 * 빈 값은 아직 입력하지 않은 상태라 에러로 보지 않습니다.
 *
 * 서버에도 같은 규칙이 있으므로, 규칙이 확정되면 정규식을 맞춰야 합니다.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1237 사용자 정보/닉네임 입력 에러}
 */
export const getNicknameErrorMessage = (nickname: string) => {
  if (nickname.length === 0) return undefined;

  return NICKNAME_PATTERN.test(nickname) ? undefined : NICKNAME_ERROR_MESSAGE;
};
