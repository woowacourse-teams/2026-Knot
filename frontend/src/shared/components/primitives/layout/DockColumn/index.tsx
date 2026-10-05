import styled from "@emotion/styled";

/**
 * 하단 독의 폭·위치를 정하는 열. 감싼 영역의 가운데에 양옆 여백을 두고 최대 폭까지만 늘어나요.
 *
 * 독과 같은 폭·위치에 놓여야 하는 탐색 대화 열도 이 열을 써요.
 */
export default styled.div`
  width: min(47.5rem, 100% - 5rem); /* 760px, 양옆 40px */
  margin: 0 auto;
`;
