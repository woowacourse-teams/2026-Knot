import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";

/**
 * 자식을 가로로 나열하는 레이아웃 프리미티브.
 *
 * 동작 규칙은 스토리북 `Shared/Layout/Row`에서 확인해요.
 */
export default styled(Stack)`
  flex-direction: row;
`;
