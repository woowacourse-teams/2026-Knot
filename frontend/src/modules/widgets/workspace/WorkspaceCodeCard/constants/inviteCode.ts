/** 참여 코드 글자 수. 입력창 `maxLength`이자 자동 검증을 시작하는 기준이에요. */
export const INVITE_CODE_LENGTH = 6;

/**
 * 미리보기 조회가 실패했을 때 입력창 아래에 띄울 문구.
 *
 * - `notFound`: 404. 만료됐거나 오타처럼 유효하지 않은 코드예요.
 * - `tooManyRequests`: 429. 짧은 시간에 코드를 너무 많이 시도했어요.
 * - `unknown`: 그 외(네트워크·5xx·timeout). 코드가 틀렸다고 단정할 수 없어 확인하지 못했다고만 알려요.
 */
export const INVITE_CODE_ERROR_MESSAGE = {
  notFound: "올바르지 않은 코드예요. 다시 확인해 주세요.",
  tooManyRequests: "요청이 너무 많아요. 잠시 후 다시 시도해 주세요.",
  unknown: "코드를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.",
} as const;
