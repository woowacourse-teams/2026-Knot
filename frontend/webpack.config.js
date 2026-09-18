import fs from "fs";
import webpack from "webpack";
import path from "path";
import HtmlWebpackPlugin from "html-webpack-plugin";

const __dirname = import.meta.dirname;

export default (env, argv) => {
  const isDev = argv.mode === "development";

  const envFile = path.resolve(
    __dirname,
    isDev ? ".env.development" : ".env.production",
  );
  if (fs.existsSync(envFile)) {
    process.loadEnvFile(envFile);
  }

  // env에 API_MOCKING이 없으면 개발 모드에서만 켜요 (.env.development가 gitignore라 모드가 기본값이에요)
  const isApiMockingEnabled =
    process.env.API_MOCKING === undefined
      ? isDev
      : process.env.API_MOCKING === "true";

  return {
    entry: "./src/index.tsx", // 모듈 진입점
    devtool: isDev ? "eval-source-map" : "source-map", // 개발: 빠르고 원본 그대로 / 프로덕션: 별도 .map 파일
    output: {
      filename: "bundle.js",
      path: path.resolve(__dirname, "dist"),
      publicPath: "/", // 하위 라우트에서도 번들을 절대 경로로 로드해요
      clean: true, // 빌드 시 이전 산출물을 제거해요
    },
    devServer: {
      static: [
        { directory: path.join(__dirname, "dist") },
        // msw의 mockServiceWorker.js를 개발 서버에서만 서빙해요
        { directory: path.join(__dirname, "public") },
      ],
      hot: true,
      open: true,
      port: 3000,
      historyApiFallback: true, // SPA 라우팅을 위해 추가
      // mock 모드에서 GitHub 로그인 진입을 대신해요. GithubLoginButton은 페이지를 통째로
      // 이동시키는데 msw는 네비게이션을 가로채지 못하므로, dev 서버가 실제 백엔드처럼
      // 로그인 상태 쿠키(src/shared/api/mock/handlers/dev와 같은 약속)를 심고 302로 돌려보내요.
      // 기본은 기존 회원(→ /), 주소창에 ?scenario=onboarding을 붙이면 신규 가입(→ /onboarding)이에요.
      setupMiddlewares: (middlewares) => {
        if (isApiMockingEnabled) {
          middlewares.unshift({
            name: "mock-github-oauth",
            path: "/oauth2/authorization/github",
            middleware: (req, res) => {
              const { searchParams } = new URL(req.url, "http://localhost");
              const isOnboarding =
                searchParams.get("scenario") === "onboarding";

              res.statusCode = 302;
              res.setHeader(
                "Set-Cookie",
                `KNOT_MOCK_AUTH=${isOnboarding ? "onboarding" : "member"}; Path=/; SameSite=Lax`,
              );
              res.setHeader("Location", isOnboarding ? "/onboarding" : "/");
              res.end();
            },
          });
        }

        return middlewares;
      },
      client: {
        overlay: { errors: true, warnings: false }, // 빌드 오류만 브라우저에 오버레이 표시 (경고는 숨김)
      },
    },
    module: {
      rules: [
        {
          test: /\.(ts|tsx)$/, // .ts와 .tsx 파일을 대상으로
          use: [
            {
              loader: "babel-loader",
              options: {
                presets: [
                  "@babel/preset-env", // 최신 JS 문법을 변환해요
                  [
                    "@babel/preset-react", // JSX를 변환해요
                    {
                      runtime: "automatic",
                      importSource: "@emotion/react", // css prop을 위해 emotion의 jsx로 변환해요
                      development: isDev, // 프로덕션 빌드에서는 jsxDEV 대신 jsx를 사용해요
                    },
                  ],
                  "@babel/preset-typescript", // 타입스크립트를 변환해요
                ],
              },
            },
          ],
          exclude: /node_modules/,
        },
        {
          test: /\.css$/, // .css 파일을 처리해요
          use: [
            "style-loader", // CSS를 <style> 태그로 주입해요
            "css-loader", // CSS를 JavaScript 모듈로 변환해요
          ],
        },
        {
          test: /\.(png|jpg|jpeg|gif)$/i, // 이미지 파일 확장자
          type: "asset", // Asset Modules 사용
        },
        {
          test: /\.svg$/i,
          oneOf: [
            // ① 주소가 필요할 때: import x from "...svg?url"
            {
              resourceQuery: /url/,
              type: "asset",
              generator: { filename: "static/media/[hash][ext]" },
            },
            // ② 그 외 전부: 컴포넌트로 변환
            {
              use: [
                {
                  loader: "@svgr/webpack",
                  options: {
                    typescript: true, // 타입이 붙은 코드를 만들어요
                    jsxRuntime: "automatic", // import React를 넣지 않아요
                    dimensions: false, // 원본 width/height를 지워요 (viewBox는 남겨요)
                    expandProps: "end", // {...props}를 맨 뒤에 붙여 호출부가 이기게 해요
                    svgProps: {
                      width: "{size}",
                      height: "{size}",
                      focusable: "false",
                      "aria-hidden": "true", // 아이콘은 기본으로 낭독기에서 무시돼요
                    },
                    svgoConfig: {
                      plugins: [
                        {
                          name: "preset-default",
                          params: {
                            overrides: {
                              removeViewBox: false, // viewBox가 없으면 크기 조절이 안 돼요
                              cleanupIds: false, // id를 줄이면 아이콘끼리 충돌해요
                            },
                          },
                        },
                      ],
                    },
                    // size prop을 만들기 위해 코드 모양을 직접 정해요
                    template: ({ componentName, jsx, exports }, { tpl }) => tpl`
import * as React from 'react';
import type { SVGProps } from 'react';

export type IconProps = SVGProps<SVGSVGElement> & {
  size?: number | string;
};

const ${componentName} = ({ size = 24, ...props }: IconProps) => (
  ${jsx}
);

${exports}
`,
                  },
                },
              ],
            },
          ],
        },
        {
          test: /\.(woff|woff2|eot|ttf|otf)$/i, // 폰트 파일 확장자
          type: "asset/resource", // 폰트는 항상 별도 파일로 내보내요
          generator: {
            filename: "assets/[name][ext]", // 원하는 폴더와 이름 형태로 설정
          },
        },
      ],
    },
    resolve: {
      extensions: [".ts", ".tsx", ".js"],
      alias: {
        "@": path.resolve(__dirname, "src"),
        "@pages": path.resolve(__dirname, "src/pages"),
        "@widgets": path.resolve(__dirname, "src/modules/widgets"),
        "@features": path.resolve(__dirname, "src/modules/features"),
        "@routes": path.resolve(__dirname, "src/shared/routes"),
        "@api": path.resolve(__dirname, "src/shared/api"),
        "@composites": path.resolve(
          __dirname,
          "src/shared/components/composites",
        ),
        "@primitives": path.resolve(
          __dirname,
          "src/shared/components/primitives",
        ),
        "@constants": path.resolve(__dirname, "src/shared/constants"),
        "@provider": path.resolve(__dirname, "src/shared/provider"),
        "@hooks": path.resolve(__dirname, "src/shared/hooks"),
        "@utils": path.resolve(__dirname, "src/shared/utils"),
      },
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: "./index.html", // 이 파일을 기반으로 번들 스크립트가 주입된 HTML을 생성해요
        filename: "index.html", // 출력될 HTML 파일 이름
        inject: true, // <script> 태그 자동 삽입
      }),
      new webpack.DefinePlugin({
        // mock 모드에서는 오리진 없이(같은 오리진) 요청해야 msw와 mock OAuth 미들웨어가 받아요
        "process.env.API_BASE_URL": JSON.stringify(
          isApiMockingEnabled ? "" : process.env.API_BASE_URL,
        ),
        // 문자열 리터럴로 넣어 src/index.tsx의 분기가 빌드 시점에 접혀요
        "process.env.API_MOCKING": JSON.stringify(String(isApiMockingEnabled)),
      }),
    ],
  };
};
