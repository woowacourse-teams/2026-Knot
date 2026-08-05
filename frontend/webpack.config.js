const path = require("path");

module.exports = {
  entry: "./src/index.ts", // 모듈 진입점
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
        test: /\.ts$/, // .ts 파일들은
        use: "ts-loader", // ts-loader를 거쳐 처리돼요.
        exclude: /node_modules/, // 외부 모듈은 제외해요.
      },
    ],
  },
  resolve: {
    extensions: [".ts", ".js"],
  },
};
