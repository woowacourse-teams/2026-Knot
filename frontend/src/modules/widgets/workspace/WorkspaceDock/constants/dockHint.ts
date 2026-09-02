/** 독을 열어 채팅을 시작하는 방법을 알려주는 문구. */
export const DOCK_HINT_TEXT =
  "독을 누르거나 아무 키나 입력하면 채팅이 시작됩니다";

/** 안내를 보여줄 최대 방문 횟수. 이만큼 보고 나면 다시 띄우지 않아요. */
export const DOCK_HINT_MAX_SEEN_COUNT = 3;

/** 안내를 몇 번 보여줬는지 브라우저에 남겨 두는 키. */
export const DOCK_HINT_SEEN_COUNT_KEY = "knot.workspaceDockHintSeenCount";

/** 이번 방문에서 안내를 이미 처리했는지 표시해 두는 키. 탭을 닫으면 지워져요. */
export const DOCK_HINT_VISIT_KEY = "knot.workspaceDockHintVisit";
