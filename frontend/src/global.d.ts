declare module "*.css";

declare const process: {
    env: {
        API_BASE_URL: string;
    }
}

// SVGR 도입할 때 수정 필요
declare module "*.svg" {
  const src: string;
  export default src;
}