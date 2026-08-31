import { configDefaults, defineConfig } from "vitest/config";
import svgr from "vite-plugin-svgr";

const fromRoot = (path: string) => new URL(path, import.meta.url).pathname;

export default defineConfig({
  plugins: [
    svgr({
      include: "**/*.svg",
      svgrOptions: {
        jsxRuntime: "automatic",
        dimensions: false,
        expandProps: "end",
        svgProps: {
          width: "{size}",
          height: "{size}",
          focusable: "false",
          "aria-hidden": "true",
        },
        // webpack 설정과 같이 size prop을 만들어요. 플러그인이 jsx로 변환하므로 타입 표기는 넣지 않아요
        template: ({ componentName, jsx, exports }, { tpl }) => tpl`
const ${componentName} = ({ size = 24, ...props }) => (
  ${jsx}
);

${exports}
`,
      },
    }),
  ],
  resolve: {
    alias: {
      "@": fromRoot("./src"),
      "@pages": fromRoot("./src/pages"),
      "@widgets": fromRoot("./src/modules/widgets"),
      "@features": fromRoot("./src/modules/features"),
      "@routes": fromRoot("./src/shared/routes"),
      "@api": fromRoot("./src/shared/api"),
      "@composites": fromRoot("./src/shared/components/composites"),
      "@primitives": fromRoot("./src/shared/components/primitives"),
      "@constants": fromRoot("./src/shared/constants"),
      "@provider": fromRoot("./src/shared/provider"),
      "@hooks": fromRoot("./src/shared/hooks"),
      "@utils": fromRoot("./src/shared/utils"),
    },
  },
  oxc: {
    jsx: {
      runtime: "automatic",
      importSource: "@emotion/react",
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}", "src/**/test.{ts,tsx}"],
    // src/__test__는 Playwright E2E 전용이라 vitest에서 제외해요
    exclude: [...configDefaults.exclude, "src/__test__/**"],
  },
});
