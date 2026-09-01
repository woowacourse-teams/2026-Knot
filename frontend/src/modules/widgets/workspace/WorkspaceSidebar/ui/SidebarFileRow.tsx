import styled from "@emotion/styled";

import FileIcon from "@/assets/icons/file.svg";

interface SidebarFileRowProps {
  /** 트리 깊이. 0부터 시작하며 한 단계마다 18px씩 들여써요. */
  depth: number;
  name: string;
}

/**
 * 사이드바 폴더 하위 문서 행. 문서 열기는 아직 없어 이름만 보여줘요.
 *
 * 들여쓰기는 같은 깊이의 폴더 행보다 2px 더 들어가요.
 * 부모 폴더 행의 들여쓰기(8px + 18px × (depth − 1))에 chevron 12px과 간격 8px을 더한 값이라,
 * 문서 아이콘이 부모 폴더 아이콘 아래 정렬돼요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=588-523 Sidebar/FileRow}
 */
export default function SidebarFileRow({ depth, name }: SidebarFileRowProps) {
  return (
    <Row $depth={depth}>
      <FileIcon size={16} />
      <Name>{name}</Name>
    </Row>
  );
}

const Row = styled.div<{ $depth: number }>`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  height: 2rem; /* 32px */
  padding-left: calc(
    0.625rem + 1.125rem * ${({ $depth }) => $depth}
  ); /* 10px + 18px × depth */
  padding-right: 0.625rem; /* 10px */
  border-radius: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[800]};

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
