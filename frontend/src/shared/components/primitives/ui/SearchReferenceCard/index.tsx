import { ReactNode } from "react";
import styled from "@emotion/styled";
import OpenExternal from "@/assets/icons/openExternal.svg";
import Spacing from "@primitives/layout/Spacing";

interface SearchReferenceCardProps {
  title: string;
  documentPath: string;
  sourceIcon: ReactNode;
}

/**
 * AI 탐색 답변의 근거가 된 문서를 보여주는 Card UI.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-563&t=NtCKbgE8RjHqh556-11
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-573&t=NtCKbgE8RjHqh556-11
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=720-584&t=NtCKbgE8RjHqh556-11
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=988-7115&t=NtCKbgE8RjHqh556-11
 */

export default function SearchReferenceCard({
  title,
  documentPath,
  sourceIcon,
}: SearchReferenceCardProps) {
  return (
    <Container>
      <Header>
        <ReferenceSourceIconWrapper>{sourceIcon}</ReferenceSourceIconWrapper>

        <Spacing direction="horizontal" size={0.625} />

        <Title>{title}</Title>

        <ExternalLinkIconWrapper>
          <OpenExternal />
        </ExternalLinkIconWrapper>
      </Header>

      <Location>{documentPath}</Location>
    </Container>
  );
}

const Container = styled.a`
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  width: 100%;
  padding: 1.25rem 1.5rem;
  border-radius: 1.5rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};

  &:hover {
    --external-link-icon-opacity: 1;
  }
`;

const Header = styled.div`
  display: flex;
`;

const IconWrapper = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
`;
const ReferenceSourceIconWrapper = styled(IconWrapper)`
  width: 1.25rem;
  height: 1.25rem;
`;
const ExternalLinkIconWrapper = styled(IconWrapper)`
  flex-shrink: 0;
  width: 1.125rem;
  height: 1.125rem;
  opacity: var(--external-link-icon-opacity, 0);
  transition: opacity 0.3s ease-in;
  color: ${({ theme }) => theme.neutral[700]};
`;

const Title = styled.div`
  flex: 1;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Location = styled.div`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[400]};
`;
