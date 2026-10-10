import styled from "@emotion/styled";
import { useId } from "react";
import { useParams } from "react-router";

import DocumentFolders from "./ui/DocumentFolders";

/**
 * 문서 목록 섹션. 워크스페이스의 모든 문서를 폴더(주제)별로 모아 보여주고, 행을 누르면 그 문서 보기로 가요.
 *
 * 주소의 워크스페이스 id는 읽은 이 자리에서 확인해요. 그래서 조회하는 쪽(`ui/DocumentFolders`)은 정수만 받고 다시 검사하지 않아요.
 */
export default function DocumentList() {
  const titleId = useId();
  const params = useParams();

  const workspaceId = Number(params.workspaceId);
  const isValidAddress = Number.isInteger(workspaceId);

  return (
    <Container aria-labelledby={titleId}>
      <Header>
        <Title id={titleId}>문서</Title>
        <Description>녹음하고 정리한 내용을 폴더별로 모아둬요</Description>
      </Header>
      {isValidAddress && <DocumentFolders workspaceId={workspaceId} />}
    </Container>
  );
}

/** 피그마 List/Docs: 폭 880px, 머리와 폴더 묶음 사이 28px */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
  width: 100%;
  max-width: 55rem; /* 880px */
`;

/** 피그마 머리: 제목과 설명 사이 6px */
const Header = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.375rem; /* 6px */
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[600]};
`;
