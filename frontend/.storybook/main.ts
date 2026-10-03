import type { StorybookConfig } from "@storybook/react-webpack5";
import type { RuleSetRule } from "webpack";

import createAppWebpackConfig from "../webpack.config.js";

const config: StorybookConfig = {
  stories: ["../src/**/index.stories.tsx"],
  framework: "@storybook/react-webpack5",
  // 스토리와 컴포넌트의 JSDoc을 문서 페이지의 설명으로 보여 줘요
  // 스토리 parameters.design의 Figma 프레임을 Design 탭에 띄워요
  addons: ["@storybook/addon-docs", "@storybook/addon-designs"],
  // msw가 요청을 가로채려면 public/mockServiceWorker.js가 스토리북 루트에서 서빙돼야 해요
  staticDirs: ["../public"],
  // 앱과 같은 babel·svg·폰트 처리와 경로 별칭을 써야 스토리가 앱과 똑같이 그려져요
  webpackFinal: async (storybookConfig, { configType }) => {
    const appConfig = createAppWebpackConfig(
      {},
      { mode: configType === "PRODUCTION" ? "production" : "development" },
    );
    const appRules = appConfig.module.rules as RuleSetRule[];
    const isRuleFor = (rule: RuleSetRule, extension: string) =>
      rule.test instanceof RegExp && rule.test.test(extension);

    const storybookRules = (storybookConfig.module?.rules ?? []).map((rule) => {
      // Storybook 기본 규칙은 svg를 파일로 내보내므로, 앱처럼 컴포넌트로 변환되게 svg를 빼요
      if (rule && typeof rule === "object" && isRuleFor(rule, ".svg")) {
        return { ...rule, exclude: /\.svg$/i };
      }
      return rule;
    });

    return {
      ...storybookConfig,
      module: {
        ...storybookConfig.module,
        rules: [
          ...storybookRules,
          ...appRules.filter(
            (rule) => isRuleFor(rule, ".tsx") || isRuleFor(rule, ".svg"),
          ),
        ],
      },
      resolve: {
        ...storybookConfig.resolve,
        alias: {
          ...storybookConfig.resolve?.alias,
          ...appConfig.resolve.alias,
        },
      },
    };
  },
};

export default config;
