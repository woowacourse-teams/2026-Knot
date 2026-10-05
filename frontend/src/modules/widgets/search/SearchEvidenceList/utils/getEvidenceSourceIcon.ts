import styled from "@emotion/styled";
import Notion from "@/assets/icons/notion.svg";
import type { EvidenceSource } from "../types/searchEvidence";

const NotionIcon = styled(Notion)`
  color: ${({ theme }) => theme.neutral[600]};
`;

export const EVIDENCE_SOURCE_ICON: Record<EvidenceSource, typeof NotionIcon> = {
  notion: NotionIcon,
};
const DEFAULT_EVIDENCE_SOURCE_ICON = EVIDENCE_SOURCE_ICON.notion;

/**
 * evidenceSource에 해당하는 아이콘 컴포넌트를 반환합니다. 
 * 값이 없거나 등록되지 않은 출처면 notion 아이콘을 반환합니다.
 * 
 * @param evidenceSource - 참조 문서의 출처
 * @returns 아이콘 컴포넌트
 
* @example
 * const EvidenceSourceIcon = getEvidenceSourceIcon("notion");
 * return <EvidenceSourceIcon size={20} />;
 */
export const getEvidenceSourceIcon = (evidenceSource?: EvidenceSource) => {
  if (!evidenceSource) return DEFAULT_EVIDENCE_SOURCE_ICON;

  return EVIDENCE_SOURCE_ICON[evidenceSource] ?? DEFAULT_EVIDENCE_SOURCE_ICON;
};
