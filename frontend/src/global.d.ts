declare module "*.css";

declare namespace NodeJS {
  interface ProcessEnv {
    API_BASE_URL: string;
    /** DefinePlugin이 문자열로 넣어요. ProcessEnv가 문자열 인덱스 시그니처라 boolean으로 못 둬요 */
    API_MOCKING: "true" | "false";
  }
}
declare module "*.svg" {
  import type { FunctionComponent, SVGProps } from "react";
  const Component: FunctionComponent<
    SVGProps<SVGSVGElement> & { size?: number | string }
  >;
  export default Component;
}

declare module "*.svg?url" {
  const src: string;
  export default src;
}
