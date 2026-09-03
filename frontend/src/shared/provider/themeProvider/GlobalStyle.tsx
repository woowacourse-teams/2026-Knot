import { Global, css, useTheme } from "@emotion/react";

const GlobalStyle = () => {
  const theme = useTheme();

  return (
    <Global
      styles={css`
        *,
        *::before,
        *::after {
          box-sizing: border-box;
        }

        html {
          /* iOS에서 가로 모드로 돌릴 때 글자가 멋대로 커지는 걸 막아요 */
          -webkit-text-size-adjust: 100%;
        }

        html,
        body,
        #root {
          height: 100%;
        }

        body {
          margin: 0;
          background-color: ${theme.neutral[50]};
          color: ${theme.neutral[900]};
          font-family:
            "Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont,
            system-ui, Roboto, "Helvetica Neue", "Segoe UI", "Apple SD Gothic Neo",
            "Noto Sans KR", "Malgun Gothic", sans-serif;
          ${theme.text.body01};
          -webkit-font-smoothing: antialiased;
          -moz-osx-font-smoothing: grayscale;
        }

        h1,
        h2,
        h3,
        h4,
        h5,
        h6,
        p,
        figure {
          margin: 0;
        }

        ul,
        ol {
          margin: 0;
          padding: 0;
          list-style: none;
        }

        a {
          color: inherit;
          text-decoration: none;
        }

        button {
          padding: 0;
          border: none;
          background: none;
          font: inherit;
          color: inherit;
          cursor: pointer;
        }

        button:disabled {
          cursor: not-allowed;
        }

        input,
        textarea,
        select {
          font: inherit;
          color: inherit;
        }

        img,
        svg,
        video {
          display: block;
          max-width: 100%;
        }

        img,
        video {
          /* max-width로 줄어들 때 원본 비율을 유지해요 */
          height: auto;
        }
      `}
    />
  );
};

export default GlobalStyle;
