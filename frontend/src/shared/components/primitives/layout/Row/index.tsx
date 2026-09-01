import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";

/**
 * 자식을 가로로 나열하는 레이아웃 프리미티브.
 *
 * 방향만 다를 뿐 `Stack`과 동작이 같아요. `align`은 세로 정렬,
 * `justify`는 가로 정렬이 됩니다. props와 `gap` 사용 기준은 `Stack` 참고.
 *
 * @example
 * <Row align="center" gap={0.5}>
 *   <GithubIcon />
 *   <span>GitHub으로 시작하기</span>
 * </Row>
 */
export default styled(Stack)`
  flex-direction: row;
`;
