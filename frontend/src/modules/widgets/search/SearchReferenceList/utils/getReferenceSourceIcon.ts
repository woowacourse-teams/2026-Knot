import styled from "@emotion/styled";
import Notion from "@/assets/icons/notion.svg";
import type { ReferenceSource } from "../types/searchReference";

const NotionIcon = styled(Notion)`
  color: ${({ theme }) => theme.neutral[600]};
`;

export const REFERENCE_SOURCE_ICON: Record<ReferenceSource, typeof NotionIcon> =
  {
    notion: NotionIcon,
  };
const DEFAULT_REFERENCE_SOURCE_ICON = REFERENCE_SOURCE_ICON.notion;

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

  return (
    REFERENCE_SOURCE_ICON[referenceSource] ?? DEFAULT_REFERENCE_SOURCE_ICON
  );
};
