/**
 * Cloudflare Workers 정적 자산이 읽는 `_headers` 파일을 만들어요.
 *
 * 응답 헤더는 정적 자산 서버가 붙이는 것이라 번들 안에서는 손댈 수 없고, 이 파일 하나가
 * 배포된 모든 경로의 헤더를 정해요. 저장소에 고정해 두지 않고 빌드가 만드는 이유는
 * `connect-src`에 들어갈 API 오리진이 환경마다 다르고 운영 값이 저장소에 없기 때문이에요.
 *
 * 지시문의 정본은 `docs/electron-desktop-app-tech-plan.md` 9.4예요. 값을 바꾸려면 그 표를 먼저 고쳐요.
 */

/** jsDelivr에서 오는 Pretendard 스타일시트와 그 woff2 (index.html) */
const FONT_CDN_ORIGIN = "https://cdn.jsdelivr.net";

const toOrigin = (url) => {
  try {
    return new URL(url).origin;
  } catch {
    // mock 빌드는 같은 오리진으로 요청해서 baseURL이 비어 있어요
    return undefined;
  }
};

/**
 * CSP 지시문을 한 줄로 만들어요.
 *
 * `style-src`에 `'unsafe-inline'`이 있는 것은 Emotion이 런타임에 `<style>`을 만들기 때문이에요.
 * 정적 자산 응답에는 요청마다 다른 nonce를 넣을 수 없어서 nonce 방식을 쓸 수 없어요.
 * `img-src`를 `https:`로 넓게 연 것은 프로필·문서 이미지의 호스트가 고정되지 않기 때문이에요.
 */
const buildContentSecurityPolicy = (apiOrigin) =>
  [
    "default-src 'self'",
    "base-uri 'self'",
    "object-src 'none'",
    "frame-ancestors 'none'",
    "form-action 'self'",
    "script-src 'self'",
    `style-src 'self' 'unsafe-inline' ${FONT_CDN_ORIGIN}`,
    `font-src 'self' ${FONT_CDN_ORIGIN}`,
    "img-src 'self' data: https:",
    // 프론트와 API는 크로스 오리진이라 API 오리진을 열어 줘야 XHR·SSE가 나가요
    ["connect-src 'self'", apiOrigin].filter(Boolean).join(" "),
  ].join("; ");

/**
 * `_headers` 파일의 내용을 만들어요. 규칙은 `/*`(모든 경로) 하나예요.
 *
 * @param {{ apiBaseUrl?: string }} params - 번들에 주입한 것과 같은 API baseURL
 */
export const buildHeadersFile = ({ apiBaseUrl }) =>
  `/*\n  Content-Security-Policy: ${buildContentSecurityPolicy(toOrigin(apiBaseUrl))}\n`;

/**
 * 위 내용을 빌드 산출물(`dist/_headers`)로 내보내는 웹팩 플러그인.
 *
 * `copy-webpack-plugin` 같은 의존성을 더하지 않으려고 파일 하나를 직접 emit해요.
 */
export class HeadersPlugin {
  constructor({ apiBaseUrl }) {
    this.apiBaseUrl = apiBaseUrl;
  }

  apply(compiler) {
    const { Compilation, sources } = compiler.webpack;

    compiler.hooks.thisCompilation.tap("HeadersPlugin", (compilation) => {
      compilation.hooks.processAssets.tap(
        {
          name: "HeadersPlugin",
          stage: Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL,
        },
        () => {
          compilation.emitAsset(
            "_headers",
            new sources.RawSource(
              buildHeadersFile({ apiBaseUrl: this.apiBaseUrl }),
            ),
          );
        },
      );
    });
  }
}
