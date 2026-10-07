import styled from "@emotion/styled";
import Avatar from "@primitives/ui/Avatar";

/** 팝오버 한 줄에 필요한 값 */
interface ConfirmationPerson {
  memberId: number;
  nickname: string;
  profileImageUrl: string | null;
}

interface ConfirmedPeopleCardProps {
  /** 확인 대상 조회 상태. 목록은 `ready`일 때만 그려요 */
  status: "loading" | "failed" | "ready";
  /** 확인한 사람. 위에 진하게 그려요 */
  confirmedPeople: ConfirmationPerson[];
  /** 아직 확인하지 않은 사람. 아래에 흐리게 그려요 */
  pendingPeople: ConfirmationPerson[];
}

/**
 * 확인 수에 포인터를 올리면 뜨는 카드. 확인한 사람을 위에, 아직 확인하지 않은 사람을 아래에 흐리게 보여줘요.
 *
 * "n명 중 m명" 같은 머리글과 확인한 시각은 두지 않아요(CONF-R5).
 * 목록을 아직 받지 못했거나 받지 못했으면 그 사실을 한 줄로 알려요.
 */
export default function ConfirmedPeopleCard({
  status,
  confirmedPeople,
  pendingPeople,
}: ConfirmedPeopleCardProps) {
  const getNotice = () => {
    if (status === "loading") return "목록을 불러오고 있어요";
    if (status === "failed") return "목록을 불러오지 못했어요";
    if (confirmedPeople.length + pendingPeople.length === 0) {
      return "확인 대상이 없어요";
    }

    return null;
  };

  const notice = getNotice();

  if (notice !== null) {
    return (
      <Root>
        <Notice>{notice}</Notice>
      </Root>
    );
  }

  return (
    <Root>
      <PeopleList aria-label="문서 확인 현황">
        {confirmedPeople.map((person) => (
          <PersonRow key={person.memberId} person={person} />
        ))}
        {pendingPeople.map((person) => (
          <PersonRow key={person.memberId} person={person} isMuted />
        ))}
      </PeopleList>
    </Root>
  );
}

interface PersonRowProps {
  person: ConfirmationPerson;
  /** 아직 확인하지 않은 사람이면 흐리게 그려요 */
  isMuted?: boolean;
}

function PersonRow({ person, isMuted = false }: PersonRowProps) {
  return (
    <Person $isMuted={isMuted}>
      <Avatar
        label={`${person.nickname} 프로필`}
        src={person.profileImageUrl ?? undefined}
        name={person.nickname}
        size={24}
      />
      <Name>{person.nickname}</Name>
    </Person>
  );
}

/** 피그마 Popover/확인한 사람: 폭 200px · 여백 10/12 · 모서리 16 · Shadow03 */
const Root = styled.div`
  width: 12.5rem; /* 200px */
  padding: 0.625rem 0.75rem; /* 10px 12px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;

const PeopleList = styled.ul`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
`;

const Person = styled.li<{ $isMuted: boolean }>`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  opacity: ${({ $isMuted }) => ($isMuted ? 0.4 : 1)};
`;

const Name = styled.span`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[900]};
  white-space: nowrap;
  text-overflow: ellipsis;

  ${({ theme }) => theme.text.body01};
`;

const Notice = styled.p`
  color: ${({ theme }) => theme.neutral[500]};

  ${({ theme }) => theme.text.caption02};
`;
