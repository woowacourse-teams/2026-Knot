declare module "*.css";

declare const process: {
  env: {
    API_BASE_URL: string;
  };
};
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
