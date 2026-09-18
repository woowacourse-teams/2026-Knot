import styled from "@emotion/styled";

import ChevronDownIcon from "@/assets/icons/chevronDown.svg";
import ChevronRightIcon from "@/assets/icons/chevronRight.svg";
import FolderIcon from "@/assets/icons/folder.svg";

interface SidebarFolderRowProps {
  /** 트리 깊이. 0부터 시작하며 한 단계마다 18px씩 들여써요. */
  depth: number;
  name: string;
  documentCount: number;
  isExpanded: boolean;
  onToggle: () => void;
}

/**
 * 사이드바 폴더 행. 누르면 하위 항목을 펼치거나 접어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1444 Sidebar/FolderRow}
 */
export default function SidebarFolderRow({
  depth,
  name,
  documentCount,
  isExpanded,
  onToggle,
}: SidebarFolderRowProps) {
  return (
    <Row
      type="button"
      aria-expanded={isExpanded}
      onClick={onToggle}
      $depth={depth}
    >
      <Left>
        {isExpanded ? (
          <ChevronDownIcon size={12} />
        ) : (
          <ChevronRightIcon size={12} />
        )}
        <FolderIcon size={16} />
        <Name>{name}</Name>
      </Left>
      <Count>{documentCount}</Count>
    </Row>
  );
}

const Row = styled.button<{ $depth: number }>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  height: 2rem; /* 32px */
  padding-left: calc(
    0.5rem + 1.125rem * ${({ $depth }) => $depth}
  ); /* 8px + 18px × depth */
  padding-right: 0.625rem; /* 10px */
  border-radius: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[800]};
  text-align: left;
  transition: background-color 0.2s ease-in;

  &:hover,
  &:focus-visible {
    background-color: ${({ theme }) => theme.neutral[200]};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: -2px;
  }
`;

const Left = styled.span`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  min-width: 0;

  & > svg {
    flex-shrink: 0;
    color: ${({ theme }) => theme.neutral[400]};
  }
`;

const Name = styled.span`
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  ${({ theme }) => theme.text.caption02};
`;

const Count = styled.span`
  flex-shrink: 0;
  color: ${({ theme }) => theme.neutral[400]};
  ${({ theme }) => theme.text.caption01};
`;
