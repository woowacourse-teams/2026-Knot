import type { AnchorHTMLAttributes } from "react";
import { Link } from "react-router";

import { isExternalHref } from "./utils/isExternalHref";

interface LinkToProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  href: string;
}

/**
 * 링크 이동만 책임지는 컴포넌트. 스타일을 갖지 않으므로 어떤 UI든 감쌀 수 있어요.
 *
 * `href`를 보고 이동 방식을 스스로 정합니다.
 * 앱 내부 경로면 react-router의 `Link`로 클라이언트 라우팅하고,
 * 외부 주소(`https:`, `mailto:` 등)면 `<a>`로 새 탭에서 엽니다.
 *
 * 새 탭 여부처럼 기본값을 바꾸고 싶으면 `target`을 직접 넘겨 덮어쓸 수 있어요.
 * 모양이 필요하면 `styled(LinkTo)`로 감싸 사용합니다.
 *
 * @example
 * <LinkTo href={getRouterPath({ routeKey: "CHAT", params: { workspaceId } })}>
 *   <SearchReferenceCard {...reference} />
 * </LinkTo>
 *
 * <LinkTo href="https://www.notion.so/page">
 *   <SearchReferenceCard {...reference} />
 * </LinkTo>
 */
export default function LinkTo({ href, children, ...props }: LinkToProps) {
  if (isExternalHref(href)) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
        {children}
      </a>
    );
  }

  return (
    <Link to={href} {...props}>
      {children}
    </Link>
  );
}
