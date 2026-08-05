const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");

module.exports = (env, argv) => {
  const isDev = argv.mode === "development";

  return {
    entry: "./src/index.tsx", // 모듈 진입점
    devtool: isDev ? "eval-source-map" : "source-map", // 개발: 빠르고 원본 그대로 / 프로덕션: 별도 .map 파일
    output: {
      filename: "bundle.js",
      path: path.resolve(__dirname, "dist"),
    },
    devServer: {
      static: {
        directory: path.join(__dirname, "dist"),
      },
      hot: true,
      open: true,
      port: 3000,
      historyApiFallback: true, // SPA 라우팅을 위해 추가
      client: {
        overlay: true, // 빌드 오류 시 브라우저에 오버레이 표시
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
                  "@babel/preset-react", // JSX를 변환해요
                  "@babel/preset-typescript", // 타입스크립트를 변환해요
                ],
              },
            },
          ],
          exclude: /node_modules/,
        },
      ],
    },
    resolve: {
      extensions: [".ts", ".tsx", ".js"],
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: "./index.html", // 이 파일을 기반으로 번들 스크립트가 주입된 HTML을 생성해요
        filename: "index.html", // 출력될 HTML 파일 이름
        inject: true, // <script> 태그 자동 삽입
      }),
    ],
  };
};
