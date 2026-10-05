import type { SearchMessage } from "@/shared/types/search";

/**
 * 화면에 그려지는 대화 단위. 질문 하나와 그에 대한 답변 하나를 묶어요.
 * 답변이 아직 없으면 `answer`가 `null`이에요.
 */
export interface SearchTurn {
  question: SearchMessage;
  answer: SearchMessage | null;
}
