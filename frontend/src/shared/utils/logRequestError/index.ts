/** 요청 실패를 안내 없이 콘솔에만 남겨요. `action`은 실패한 동작 이름이에요 */
export const logRequestError = (action: string, error: unknown) => {
  console.error(`${action} 요청에 실패했어요`, error);
};
