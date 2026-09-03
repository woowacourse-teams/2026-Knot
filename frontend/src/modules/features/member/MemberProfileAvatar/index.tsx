import useMeQuery from "@api/queries/useMeQuery";
import Avatar from "@primitives/ui/Avatar";

/**
 * 로그인한 회원의 프로필 아바타.
 *
 * GitHub OAuth로 받아 둔 프로필 이미지를 그리고, 아직 응답이 오기 전이거나 이미지가 없으면
 * 닉네임 첫 글자로 대신해요. 지금은 보여주기만 하고 누르는 동작은 없어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516 Avatar size=32}
 */
export default function MemberProfileAvatar() {
  const { data: me } = useMeQuery();

  return (
    <Avatar
      label="내 프로필"
      src={me?.profileImageUrl}
      name={me?.nickname}
      size={32}
    />
  );
}
