import "@emotion/react";

import type { AppTheme } from "./theme";

/**
 * emotion의 기본 Theme 타입을 우리 theme으로 덮어써요.
 * 덕분에 styled 컴포넌트와 css prop 안에서 ({ theme }) => theme... 자동완성이 동작해요.
 */
declare module "@emotion/react" {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface Theme extends AppTheme {}
}
