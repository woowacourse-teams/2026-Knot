import { defineConfig } from "vitest/config";
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
  },
});
