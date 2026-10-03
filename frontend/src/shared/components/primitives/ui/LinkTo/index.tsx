import type { AnchorHTMLAttributes } from "react";
import { Link } from "react-router";

import { isExternalHref } from "./utils/isExternalHref";

interface LinkToProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  href: string;
}

/**
 * 링크 이동만 책임지는 컴포넌트.
 *
 * 동작 규칙은 스토리북 `Shared/LinkTo`에서 확인해요.
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
