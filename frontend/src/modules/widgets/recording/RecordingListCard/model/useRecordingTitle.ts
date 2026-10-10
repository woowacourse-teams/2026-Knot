import useMeQuery from "@api/queries/useMeQuery";

/** 녹음 이름. 현재 녹음 조회는 내 녹음만 돌려주므로 로그인한 내 닉네임으로 만들어요. */
export const useRecordingTitle = () => {
  const { data: me, isLoading, isError } = useMeQuery();

  if (isLoading || isError || !me?.nickname) return "진행 중인 녹음";

  const nickname = me.nickname;

  return `${nickname} 님의 녹음`;
};
