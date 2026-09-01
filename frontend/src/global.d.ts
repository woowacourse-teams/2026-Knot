declare module "*.css";

declare namespace NodeJS {
  interface ProcessEnv {
    API_BASE_URL: string;
    /** webpack DefinePlugin이 "true" | "false" 문자열로 넣어요. ProcessEnv의 문자열 인덱스 시그니처 때문에 boolean으로는 둘 수 없어요 */
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
