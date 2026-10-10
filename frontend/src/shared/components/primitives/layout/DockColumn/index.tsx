import styled from "@emotion/styled";

/** 화면(본문 영역) 바닥에서 독 아래끝까지의 거리 */
export const DOCK_BOTTOM = "1.75rem"; /* 28px */

/**
 * 접힌 독의 높이. 펼친 독은 입력이 여러 줄이면 이보다 늘어나지만,
 * 독이 없는 화면이 맞출 기준은 독의 기본 높이라 이 값을 써요.
 */
export const DOCK_HEIGHT = "3.75rem"; /* 60px */

/** 독과 그 위 토스트 사이 간격 */
export const DOCK_TOAST_GAP = "1rem"; /* 16px */

/**
 * 하단 독의 폭·위치를 정하는 열. 감싼 영역의 가운데에 양옆 여백을 두고 최대 폭까지만 늘어나요.
 *
 * 독과 같은 폭·위치에 놓여야 하는 탐색 대화 열도 이 열을 써요.
 */
export default styled.div`
  width: min(47.5rem, 100% - 5rem); /* 760px, 양옆 40px */
  margin: 0 auto;
`;
