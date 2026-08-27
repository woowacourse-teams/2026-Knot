import type { FunctionComponent, SVGProps } from "react";
import Notion from "@/assets/icons/notion.svg";

import type { ReferenceSource } from "../types/searchReference";

type ReferenceSourceIcon = FunctionComponent<
  SVGProps<SVGSVGElement> & { size?: number | string }
>;

const REFERENCE_SOURCE_ICON: Record<ReferenceSource, ReferenceSourceIcon> = {
  notion: Notion,
};

const DEFAULT_REFERENCE_SOURCE_ICON = Notion;

/**
 * referenceSource에 해당하는 아이콘 컴포넌트를 반환합니다. 
 * 값이 없거나 등록되지 않은 출처면 notion 아이콘을 반환합니다.
 * 
 * @param referenceSource - 참조 문서의 출처
 * @returns 아이콘 컴포넌트
 
* @example
 * const ReferenceSourceIcon = getReferenceSourceIcon("notion");
 * return <ReferenceSourceIcon size={20} />;
 */
export const getReferenceSourceIcon = (referenceSource?: ReferenceSource) => {
  if (!referenceSource) return DEFAULT_REFERENCE_SOURCE_ICON;

  return REFERENCE_SOURCE_ICON[referenceSource] ?? DEFAULT_REFERENCE_SOURCE_ICON;
};
