import type { ReferenceSource } from "../types/searchReference";

const REFERENCE_SOURCES = {
  NOTION: "notion",
} as const satisfies Record<string, ReferenceSource>;

/**
 * 서버가 주는 제공자 이름(`NOTION`)을 화면의 출처 키(`notion`)로 바꿉니다.
 * 모르는 제공자면 `undefined`를 돌려주고, 아이콘은 기본값(notion)으로 그려집니다.
 *
 * @param source - 서버의 제공자 이름
 * @returns 화면 출처 키. 모르는 제공자면 undefined
 * @example
 * toReferenceSource("NOTION"); // "notion"
 */
export const toReferenceSource = (source: string) =>
  source in REFERENCE_SOURCES
    ? REFERENCE_SOURCES[source as keyof typeof REFERENCE_SOURCES]
    : undefined;
