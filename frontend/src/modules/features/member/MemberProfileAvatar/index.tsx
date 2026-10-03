import useMeQuery from "@api/queries/useMeQuery";
import Avatar from "@primitives/ui/Avatar";

/**
 * 로그인한 회원의 프로필 아바타.
 *
 * 자세한 내용은 스토리북 `Member/MemberProfileAvatar`에서 확인해요.
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
