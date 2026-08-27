/** 워크스페이스 이름 최대 글자 수. 카운터 `n/20`의 분모이기도 해요. */
export const WORKSPACE_NAME_MAX_LENGTH = 20;

/**
 * 워크스페이스 이름에 허용하는 문자. 한글·영어·공백만 통과해요.
 *
 * 자모(ㄱ-ㅎ, ㅏ-ㅣ)를 함께 허용하는 이유:
 * IME로 한글을 조합하는 도중("ㄴ" → "노")에 에러가 깜빡이지 않게 하려고요.
 */
export const WORKSPACE_NAME_PATTERN = /^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z ]*$/;

export const WORKSPACE_NAME_ERROR_MESSAGE =
  "한글, 영어와 공백만 사용할 수 있어요.";

/**
 * 초대 화면으로 넘어갈 때 쓰는 임시 workspaceId.
 *
 * 워크스페이스 생성 API(#216)가 붙으면 응답의 id로 교체하고 이 상수는 지워요.
 */
export const TEMP_WORKSPACE_ID = "temp";
