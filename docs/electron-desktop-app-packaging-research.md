# Electron 데스크톱 패키징·서명·배포·자동 업데이트 기술 조사

- 상위 문서: [`electron-desktop-app-knowledge.md`](./electron-desktop-app-knowledge.md) 3절의 상세 부록. 실행 정본은 [`electron-desktop-app-roadmap.md`](./electron-desktop-app-roadmap.md)
- 조사일: 2026-09-04 (모든 "확인 날짜"는 별도 표기가 없으면 2026-09-04)
- 대상 스택: React 19 + webpack 5 + TypeScript 7 + pnpm 11 + Node 22 웹 SPA → Electron 데스크톱 앱(macOS / Windows / Linux), CI는 GitHub Actions
- 조사 방법: 공식 문서(electronjs.org, electronforge.io, electron.build, github.com/electron/*, developer.apple.com, learn.microsoft.com, playwright.dev, docs.sentry.io, docs.github.com, pnpm.io, snapcraft/flathub 문서)를 WebFetch로 직접 읽고, 버전 번호는 npm registry(`registry.npmjs.org`)와 GitHub Releases API(`gh api`)로 재확인했다. JS 렌더링 때문에 본문이 비어 있던 Apple 페이지는 Apple 문서 JSON 엔드포인트(`developer.apple.com/tutorials/data/...json`)로 읽었다.
- 표기 규약
  - **[문서]** 해당 출처 문서에서 직접 확인한 내용
  - **[추론]** 문서 여러 개를 조합하거나 실무 관행으로 도출한 내용 (문서에 직접 문장이 없음)
  - **[미확인]** 조사했으나 확인하지 못한 내용 (문서 끝의 목록에 재정리)

---

## 0. 최신 버전 요약 (npm `latest` / GitHub Releases, 2026-09-04 확인)

| 패키지 | 최신 안정 | 게시일 | 프리릴리즈 | Node 요구 | 비고 |
|---|---|---|---|---|---|
| `electron` | 44.1.1 | 2026-09-01 | 45.0.0-alpha.4 | – | Chromium 152.0.7977.65 / Node 24.19.0 [문서 releases.electronjs.org] |
| `@electron-forge/cli` (외 모든 Forge 패키지) | 7.11.2 | 2026-05-20 | 8.0.0-alpha.10 (2026-07-02) | 7.x: `>=16.4.0`, 8.x: `>=22.12.0` (ESM) | Forge 8은 alpha 단계 |
| `electron-builder` | 26.15.3 (`latest`) | 2026-06-09 | 26.16.0(`v26` 태그, 2026-09-02) / 27.0.0-alpha.8(`next`, 2026-09-02) | 26: `>=14`, 27: `>=22.12.0` (ESM) | 27은 alpha |
| `electron-updater` | 6.8.9 | 2026-06-05 | 7.0.0-alpha.7 | – | electron-builder 전용 업데이터 |
| `electron-vite` | 5.0.0 | 2025-12-07 | 6.0.0-beta.1 (2026-04-12) | `^20.19.0 \|\| >=22.12.0` | peer `vite ^5 \|\| ^6 \|\| ^7` |
| `@electron/packager` | 20.3.0 | 2026-08-11 | – | `>=22.12.0` | Forge 7.11.2는 `^18.3.5`, Forge 8은 `^20.0.1` 사용 |
| `@electron/asar` | 4.3.0 | 2026-08-18 | – | – | |
| `@electron/rebuild` | 4.2.0 | 2026-07-07 | – | `>=22.12.0` | Forge 7.11.2는 `^3.7.0` |
| `@electron/universal` | 3.0.6 | 2026-07-02 | – | – | |
| `@electron/get` | 5.1.0 | 2026-07-27 | – | – | |
| `@electron/osx-sign` | 2.7.0 | 2026-08-18 | – | – | |
| `@electron/notarize` | 3.1.1 | 2025-10-31 | – | – | 3.0.0에서 `altool` 제거 |
| `@electron/windows-sign` | 2.0.6 | 2026-07-01 | – | – | |
| `@electron/fuses` | 2.1.3 | 2026-06-29 | – | – | |
| `update-electron-app` | 3.3.0 | 2026-06-28 | – | – | |
| `electron-winstaller` | 5.4.4 | 2026-07-01 | – | – | Squirrel.Windows |
| `electron-windows-msix` | 2.0.4 | 2025-09-08 | – | – | Forge `maker-msix` 기반 (bitdisaster/electron-windows-msix) |
| `electron-squirrel-startup` | 1.0.1 | 2024-05-13 | – | – | |
| `playwright` / `@playwright/test` | 1.62.1 | 2026-07-30 | 1.63.0-alpha/beta | `>=20` | |
| `@sentry/electron` | 7.18.0 | 2026-09-01 | – | – | `electron >= v23` 지원 [문서 README] |
| `electron-log` | 5.4.4 | 2026-05-14 | – | `>=14` | Electron 13+ [문서 README] |
| `vitest` | 5.0.0 | 2026-09-03 | – | `^22.12.0 \|\| ^24 \|\| >=26` | peer `vite ^6.4 \|\| ^7 \|\| ^8` |
| `electron-mocha` | 13.1.0 | 2025-01-16 | – | – | |
| `pnpm` | 11.25.0 (`latest`) | 2026-08-29 | `latest-12`: 12.3.1 | – | pnpm 12 라인이 이미 게시돼 있으나 `latest`는 11.x |
| `electron-link` | 0.6.0 | 2020-12-14 | – | – | 저장소 2022-12-15 아카이브 (읽기 전용) |

GitHub Actions 관련(GitHub Releases API, 2026-09-04): `actions/checkout` v7.0.1, `actions/setup-node` v7.0.0, `actions/cache` v6.1.0, `actions/upload-artifact` v7.0.1, `actions/download-artifact` v8.0.1, `softprops/action-gh-release` v3.0.3 (v3은 Node 24 런타임), `pnpm/setup` v2.1.0 (pnpm 11 이상 전용), `pnpm/action-setup` v6.0.10 (pnpm 10 이하용, 후속은 `pnpm/setup`).

Electron 릴리즈 타임라인 (releases.electronjs.org/releases.json 계산, 2026-09-04):

| Major | 첫 안정 릴리즈 | Chromium | Node | 최신 패치 |
|---|---|---|---|---|
| 40 | 2026-01-15 | 144 | 24.11 | 40.10.6 (2026-07-01) |
| 41 | 2026-03-10 | 146 | 24.14 | 41.10.7 (2026-08-24) |
| 42 | 2026-05-05 | 148 | 24.15 | 42.11.2 (2026-09-04) |
| 43 | 2026-06-30 | 150 | 24.17 | 43.6.0 (2026-09-04) |
| 44 | 2026-08-24 | 152 | 24.18 | 44.1.1 (2026-09-01) |

- [문서] Electron은 "최신 3개 안정 메이저를 지원"하고 8주 주기로 메이저를 낸다 (electronjs.org/docs/latest/tutorial/electron-timelines). → [추론] 2026-09-04 기준 지원 라인은 42/43/44, 41은 EOL.
- [문서] Electron 44부터 32비트(x86, armv7l) 빌드가 제공되지 않는다 (github.com/electron/packager README; electron.build/docs/features/multi-platform-build).

---

## 1. 빌드 도구 선택

### 1.1 Electron Forge (electronforge.io, github.com/electron/forge)

**버전·유지보수** [문서]
- 안정 7.11.2 (2026-05-20). 8.0.0-alpha.10 (2026-07-02)이 최신 프리릴리즈. 8.0.0-alpha.6 릴리즈 노트: "Electron Forge is now a set of ESM packages and requires at least Node.js 22.12.0"이며 모든 `@electron/*` 의존성을 최신 메이저로 올렸고, `@electron-forge/plugin-vite`는 Vite 7 사용, 다음 릴리즈에서 Vite 8·Vite 플러그인 ESM 번들링 예정. 로드맵: github.com/electron/forge/issues/4082.
- Forge 7.x 요구사항: Node 16.4.0 이상, git. 패키지 매니저는 npm·Yarn·pnpm(“pnpm은 Forge v7.7.0부터 지원”). (electronforge.io 첫 페이지)
- Electron 공식 문서가 "We recommend using Electron Forge"라고 권장 (electronjs.org/docs/latest/tutorial/application-distribution).

**Makers** (electronforge.io/config/makers/*, llms.txt 인덱스) [문서]

| Maker 패키지 | 산출물 | 빌드 가능 호스트·요구사항 |
|---|---|---|
| `@electron-forge/maker-squirrel` | `Setup.exe` + `-full.nupkg` + `RELEASES` | Windows, 또는 `mono`+`wine`을 설치한 Linux. Forge 문서: "macOS is not supported". (electron-winstaller README는 "Windows, or macOS/Linux with Wine and Mono"라고 적혀 있어 문서 간 불일치 — [미확인] macOS 호스트 지원 여부) |
| `@electron-forge/maker-zip` | zip | 어떤 OS에서든 가능. macOS zip은 `macUpdateManifestBaseUrl` 옵션으로 Squirrel.Mac용 `RELEASES.json` 생성 |
| `@electron-forge/maker-dmg` | dmg | macOS 전용 |
| `@electron-forge/maker-pkg` | pkg | macOS 전용 (darwin/mas 타깃; MAS 업로드용 또는 대체 배포) |
| `@electron-forge/maker-deb` | deb | Linux 또는 macOS, `fakeroot`+`dpkg` 필요 |
| `@electron-forge/maker-rpm` | rpm | Linux 전용, `rpm` 또는 `rpm-build` 필요 |
| `@electron-forge/maker-wix` | msi | Windows 전용, WiX Toolset v3 필요. Forge 문서: MSI는 "a worse user experience for installation"이며 엔터프라이즈 배포에 필요할 때만 권장 |
| `@electron-forge/maker-appx` | appx | Windows 10/11 + Windows SDK. Microsoft Store 대상 (`publisher`, `devCert`, `certPass`) |
| `@electron-forge/maker-msix` | msix | Windows 10/11 + Windows SDK. "MSIX support was added in Electron Forge v7.10 and is currently experimental". `@electron/windows-sign` 연동 |
| `@electron-forge/maker-flatpak` | flatpak | `flatpak`, `flatpak-builder`, `eu-strip`(elfutils) 필요, Flathub remote 추가 필요 |
| `@electron-forge/maker-snap` | snap | Linux 전용, `snapcraft` 필요 |

**Plugins** (electronforge.io/config/plugins) [문서]: `@electron-forge/plugin-webpack`, `@electron-forge/plugin-vite`(v7.5.0부터 **experimental** 표기), `@electron-forge/plugin-auto-unpack-natives`(네이티브 모듈을 `asar.unpack`에 자동 추가, 옵션 없음), `@electron-forge/plugin-fuses`(peer `@electron/fuses`), `@electron-forge/plugin-local-electron`, `@electron-forge/plugin-electronegativity`.

**Publishers** (llms.txt 인덱스) [문서]: `publisher-github`, `publisher-s3`, `publisher-gcs`, `publisher-bitbucket`, `publisher-electron-release-server`, `publisher-nucleus`, `publisher-snapcraft`.

**빌드 라이프사이클** [문서] (electronforge.io/core-concepts/build-lifecycle): `package`(→`@electron/packager`, macOS 서명·공증·네이티브 리빌드 포함) → `make` → `publish`. "Forge does not perform any bundling of your app code for production in the Package step without additional configuration" → 번들러 플러그인이나 hooks 사용.

**Hooks** [문서] (electronforge.io/config/hooks): `generateAssets`, `preStart`, `postStart`, `prePackage`, `packageAfterCopy`, `packageAfterPrune`, `packageAfterExtract`, `postPackage`, `preMake`, `postMake`(변환형), `readPackageJson`(변환형).

**asar 기본값** [문서]: Forge 7의 `packagerConfig.asar`는 기본 비활성(auto-unpack-natives 문서: "asar is disabled by default in Electron Packager"). Forge 8(alpha)에서 쓰는 `@electron/packager` 19+는 "ASAR is now enabled by default with `unpack: '**/{.**,**/}/**/*.node'`"이며 auto-unpack-natives 플러그인은 불필요해질 수 있음 (Forge 8.0.0-alpha.6 릴리즈 노트).

### 1.2 electron-builder (electron.build, github.com/electron-userland/electron-builder)

**버전·유지보수** [문서]
- npm `latest`는 26.15.3(2026-06-09). 26.16.0은 2026-09-02에 `v26` dist-tag로, 27.0.0-alpha.8은 `next`로 게시. electron-updater `latest` 6.8.9, `next` 7.0.0-alpha.7.
- 27.x(alpha) 변경점 [문서]: Node `>=22.12.0`·ESM, "Implicit publishing based on CI detection was removed"(반드시 `--publish` 명시), Linux 타깃이 생성된 셸 런처를 통해 실행, AppImage 정적 런타임 기본(libfuse2 불필요), `disableWebInstaller` 기본 true.
- 사이트 푸터 "Copyright © 2026 electron-builder contributors", 기부 페이지 존재. 커뮤니티 유지보수.

**타깃** [문서] (electron.build/docs/mac, /docs/win, /docs/linux)

| OS | 타깃 | 기본값 |
|---|---|---|
| macOS | `dmg`, `zip`, `pkg`, `mas`, `mas-dev`, `7z`, `tar.*`, `dir` | `dmg`+`zip` (둘 다 Squirrel.Mac 자동 업데이트에 필요) |
| Windows | `nsis`, `nsis-web`, `portable`, `appx`, `msix`, `msi`, `msi-wrapped`, `squirrel`(별도 `electron-builder-squirrel-windows` 필요, **deprecated**: "Please use nsis instead"), `7z`, `zip`, `tar.*`, `dir` | `nsis` |
| Linux | `AppImage`, `snap`, `deb`, `rpm`, `flatpak`, `pacman`, `apk`, `freebsd`, `p5p`, `7z`/`zip`/`tar.*`, `dir` | `AppImage`+`snap` |

- 자동 업데이트: `electron-updater` 내장(차등 업데이트, 스테이지 롤아웃, GitHub Releases/S3 등 다중 provider). 상세는 §3.3.
- 서명·공증: "macOS notarization and Windows Authenticode signing — all available out of the box" (전면 페이지). 상세는 §2.
- Fuses: `electronFuses` 설정 키로 지원, "electron-builder flips fuses after packaging and before signing" (electron.build/docs/tutorials/adding-electron-fuses).

### 1.3 Forge vs electron-builder 비교

| 항목 | Electron Forge 7.11.2 | electron-builder 26.15.3 |
|---|---|---|
| 공식 지원 | Electron 프로젝트 소유(github.com/electron/forge), Electron 문서가 공식 권장 [문서] | 커뮤니티(electron-userland) [문서] |
| 서명 | `@electron/osx-sign`, `@electron/windows-sign` 직접 사용, 설정은 `packagerConfig.osxSign`/`windowsSign`/maker별 [문서] | 자체 통합. `CSC_*` 환경변수 규약, `win.sign`(signtool/hsm/pkcs11/azure) [문서] |
| 공증 | `packagerConfig.osxNotarize` (`@electron/notarize`) [문서] | `mac.notarize: true` + `APPLE_*` 환경변수, 자동 staple [문서] |
| 자동 업데이트 | 내장 `autoUpdater`(Squirrel.Mac/Windows, MSIX) + `update-electron-app`/update.electronjs.org 또는 S3 정적 스토리지 [문서] | `electron-updater`(NSIS/AppImage/deb/rpm/pacman/macOS zip) [문서] |
| Windows 기본 설치기 | Squirrel.Windows [문서] | NSIS [문서] |
| Linux | deb/rpm/snap/flatpak/zip [문서] | 위 표 + AppImage/pacman 등 [문서] |
| 번들러 | webpack/Vite 플러그인, 또는 hooks로 외부 빌드 [문서] | 번들링은 별도(electron-vite 등) [문서] |
| pnpm | 공식 문서에 `node-linker=hoisted` 명시 [문서] | 공식 문서에 pnpm 호이스팅 관련 문장 없음 [미확인] |
| 네이티브 모듈 | package 단계에서 `@electron/rebuild` 자동 [문서] | `npmRebuild`/`install-app-deps` [문서] |
| 유지보수 | Electron 팀, 8.0 ESM 전환 진행 중 [문서] | 27.0 ESM 전환 alpha 진행 중 [문서] |

[추론] 이 프로젝트처럼 "웹 SPA 빌드는 그대로 두고 main/preload만 얹는" 구성에서는 두 도구 모두 가능하다. 공식 지원·`@electron/*` 도구와의 정합성·Squirrel/`update.electronjs.org` 무료 경로는 Forge가 유리하고, NSIS 설치기·차등 업데이트·스테이지 롤아웃·프라이빗/제네릭 서버 업데이트·AppImage는 electron-builder가 유리하다.

### 1.4 pnpm·모노레포 호환성

- [문서] Electron 공식 튜토리얼(electronjs.org/docs/latest/tutorial/tutorial-first-app): "Electron's packaging toolchain requires the `node_modules` folder to be physically on disk in the way that npm installs Node dependencies." … "you must set `nodeLinker: node-modules` in Yarn or `nodeLinker: hoisted` in pnpm if you are using those package managers."
- [문서] Forge CLI 문서(electronforge.io/cli): "If you are using pnpm, please set `node-linker=hoisted` in your project's `.npmrc` configuration." / "Its module resolution algorithm is naive and doesn't take into account symlinked dependencies nor Yarn's Plug'n'Play (PnP) format."
- [문서] pnpm 설정 문서(pnpm.io/settings/node-modules): `nodeLinker` 기본값 `isolated`, 값은 `isolated`/`hoisted`/`pnp`. `hoisted`는 "a flat `node_modules` without symlinks … Same as the node_modules created by npm or Yarn Classic". `publicHoistPattern` 기본 `[]`, `hoistPattern` 기본 `['*']`(가상 스토어 내부 숨김 디렉터리로 호이스트), `shamefullyHoist: true`는 `publicHoistPattern: ['*']`와 동일. 이 설정들은 **`pnpm-workspace.yaml`**(또는 글로벌 config)에 둔다 (pnpm.io/settings: "Settings are configured in `pnpm-workspace.yaml`…; Authorization-related settings are the exception: these are read from `.npmrc` files only."). → [추론] pnpm 11에서는 Forge 문서의 `.npmrc` `node-linker=hoisted` 표기보다 `pnpm-workspace.yaml`의 `nodeLinker: hoisted`가 정본이다. 모노레포에서 `frontend/` 워크스페이스만 hoisted로 만들 수는 없으므로(설정이 워크스페이스 루트 단위), Electron 앱을 별도 워크스페이스 루트로 두거나 전체를 hoisted로 전환하는 결정을 해야 한다.
- [문서] `publicHoistPattern`은 특정 패키지만 루트 `node_modules`로 노출하는 부분 해법이다. [추론] Forge/packager가 `node_modules`를 통째로 크롤링하므로 `publicHoistPattern`만으로는 심링크 문제가 남을 수 있어, 완전한 방법은 `nodeLinker: hoisted`다.
- [문서] electron-builder: 첫 페이지에 Yarn 3 PnP는 `nodeLinker: "node-modules"`가 필요하다는 안내가 있고 "npm, pnpm, bun" 설치 명령을 제시하지만, pnpm 호이스팅에 관한 문장은 공식 문서에서 찾지 못했다. 이슈 #7554/#7555, #9366(pnpm 모노레포 universal 빌드 실패)이 검색됐으나 본문은 확인하지 않았다 [미확인].
- [문서] Yarn PnP·심링크 비호환은 Forge와 electron-builder 모두 명시.

### 1.5 electron-vite, Forge Vite/webpack 플러그인 상태

- [문서] electron-vite 5.0.0(2025-12-07)이 사이트에 "현재 버전"으로 표기, 6.0.0-beta.1(2026-04-12). main/preload/renderer 세 프로세스 빌드와 HMR, V8 바이트코드 소스 보호를 제공. 배포 가이드(electron-vite.org/guide/distribution)는 electron-builder와 Forge 둘 다 소개하되, "Electron Forge's default output directory is `out` and forbids to override, which conflicts with electron-vite"라며 electron-vite `outDir`를 `dist`로 바꾸라고 안내.
- [문서] `@electron-forge/plugin-vite`: v7.5.0부터 experimental, 마이너 릴리즈에 breaking 가능, v7.9.0부터 `concurrent` 옵션. renderer `base`를 `./`로 자동 설정. Forge 8 alpha에서 Vite 7 사용.
- [문서] `@electron-forge/plugin-webpack`: `mainConfig` + `renderer.config` + `renderer.entryPoints[].{html,js,preload}`. Forge 7부터 preload가 기본 sandbox(Electron 20+ 동작과 일치). 네이티브 모듈은 `node-loader` + `@vercel/webpack-asset-relocator-loader@1.7.3`(Forge가 monkeypatch, 버전 고정). experimental 표기는 없음.
- [문서] webpack `output.publicPath`: "every file emitted to your `output.path` directory will be referenced from the `output.publicPath` location" / `'auto'`는 `import.meta.url`, `document.currentScript`, `script.src`, `self.location`에서 자동 결정 (webpack.js.org/guides/public-path).

### 1.6 기존 webpack 5 SPA 빌드를 그대로 renderer로 쓰는 구성 [추론, 근거 문서 병기]

목표: 기존 `webpack` 설정으로 만든 `dist/`를 renderer로 로드하고, main/preload만 별도 빌드한다.

1. renderer(기존 SPA)
   - `output.publicPath`를 `'auto'` 또는 `'./'`로 (근거: webpack public-path 가이드). 절대 경로 `/`는 `file://` 또는 커스텀 스킴에서 깨진다.
   - 라우팅은 `HashRouter` 또는 커스텀 스킴 + `protocol.handle`로 SPA 폴백을 구현. 근거 [문서] `protocol.registerSchemesAsPrivileged`는 `app` ready 전에 호출해야 하며 `standard`/`secure`/`supportFetchAPI`/`corsEnabled`/`stream` 권한을 줄 수 있고, `standard` 스킴은 "proper handling of relative URLs, FileSystem API access, and service worker support—benefits unavailable with the `file://` protocol". `protocol.handle('app', req => net.fetch(pathToFileURL(filePath)))` 예시가 문서에 있다 (electronjs.org/docs/latest/api/protocol).
   - 웹 API 호출 base URL은 빌드 시 환경변수(`API_BASE_URL`)로 절대 URL을 주입 (근거: 기존 CD 메모리, 이 저장소 관행).
2. main/preload
   - Forge 사용 시: `plugin-webpack`을 **쓰지 않고** `hooks.generateAssets`(또는 `prePackage`)에서 `pnpm build`(SPA)와 `tsc`/`esbuild`(main·preload)를 실행하고, `packagerConfig.ignore`로 소스·devDependencies를 제외 (근거: Forge hooks 문서, 빌드 라이프사이클 문서의 "hooks로 커스텀 빌드" 안내, `packagerConfig.ignore`).
   - electron-builder 사용 시: `files`에 `dist/**`, `dist-electron/**`, `package.json`만 포함 (근거: electron.build/docs/configuration `files`, electron-vite 배포 가이드의 files 예시).
   - `BrowserWindow.loadFile('dist/index.html')` 또는 `loadURL('app://-/index.html')`. preload는 `contextBridge`로만 API 노출, `contextIsolation`·`sandbox` 기본값 유지 (근거: process-model, security 체크리스트 [문서]).
3. Forge `plugin-webpack`을 renderer 없이 main/preload 전용으로 쓸 수 있는지는 문서에서 확인하지 못했다 [미확인]. Vite 플러그인은 experimental이므로, 기존 webpack 5 SPA 유지가 목적이면 hooks 방식이 문서 근거상 가장 단순하다 [추론].
4. TypeScript 7·React 19 자체에 대한 Electron 측 제약 문장은 조사 범위의 공식 문서에 없다 [미확인].

### 1.7 `@electron/*` 도구 요점 [문서]

- `@electron/packager` 20.3.0: Node ≥22.12. 타깃 win32 x86/x64/arm64, darwin/mas x64/arm64/**universal**, linux x86/x64/armv7l/arm64/mips64el (x86·armv7l은 Electron 43까지). `--ignore`, `--prune`(기본 true, devDependencies 제외), `osxSign`/`osxNotarize`/`windowsSign` 옵션. 출력 `<out>/<appname>-<platform>-<arch>`.
- `@electron/asar` 4.3.0: `asar pack|list|extract-file|extract`, `--unpack-dir` glob(`{**/x1,**/x2,z4/w1}`), 동일 내용 파일은 한 번만 저장. asar 제약(electronjs.org/docs/latest/tutorial/asar-archives): 읽기 전용, `fs.stat` 정보는 합성값, `child_process.execFile`/`fs.open`/`process.dlopen`은 임시 추출 발생, `exec`/`spawn`은 asar 내부 바이너리 미지원 → `.node` 등은 `unpack`으로 `app.asar.unpacked`에 둔다.
- `@electron/rebuild` 4.2.0: Electron 헤더로 네이티브 모듈 재빌드. `-a/--arch`, `-v/--version`, `-o/--only`, `-f/--force`, `--use-electron-clang`, `-t/--types`. Forge는 package 단계에서 자동 실행. Electron 문서(using-native-node-modules)는 `@electron/rebuild`를 권장하고, prebuild/node-pre-gyp 바이너리가 있으면 `--build-from-source`를 쓰지 말라고 안내.
- `@electron/universal` 3.0.6: x64+arm64 `.app` 병합. `x64ArchFiles`, `singleArchFiles`, `mergeASARs`, `infoPlistsToIgnore`. 유니버설 앱은 "twice as big because it contains two apps in one". `ElectronAsarIntegrity` 키를 Info.plist에 생성.
- `@electron/get` 5.1.0: 캐시 경로 Linux `$XDG_CACHE_HOME` 또는 `~/.cache/electron/`, macOS `~/Library/Caches/electron/`, Windows `%LOCALAPPDATA%/electron/Cache`. 환경변수 `ELECTRON_MIRROR`, `ELECTRON_NIGHTLY_MIRROR`, `ELECTRON_CUSTOM_DIR`, `ELECTRON_CUSTOM_FILENAME`, `ELECTRON_CUSTOM_VERSION`, `ELECTRON_GET_USE_PROXY`, `electron_config_cache`, `ELECTRON_SKIP_BINARY_DOWNLOAD`(installation 문서).
- `@electron/fuses` 2.1.3: `flipFuses()`는 "after your app has been packaged … but before code signing"에 호출. Apple Silicon에서 바로 서명하지 않으면 `resetAdHocDarwinSignature: true` 필요. 퓨즈 목록(electronjs.org/docs/latest/tutorial/fuses): `RunAsNode`, `EnableCookieEncryption`, `EnableNodeOptionsEnvironmentVariable`, `EnableNodeCliInspectArguments`, `EnableEmbeddedAsarIntegrityValidation`, `OnlyLoadAppFromAsar`, `LoadBrowserProcessSpecificV8Snapshot`, `GrantFileProtocolExtraPrivileges`, `WasmTrapHandlers`.

---

## 2. 코드 서명·공증

### 2.1 macOS

**계정·인증서·비용** [문서]
- Apple Developer Program: "$99 annual membership" (developer.apple.com/programs). Developer ID 인증서·공증·App Store 배포가 포함. 무료 계정은 "Certificates, Identifiers & Profiles", "Mac software notarization"에 접근 불가 (developer.apple.com/support/compare-memberships).
- 인증서 종류 (electronjs.org mac-app-store-submission-guide, electron.build code-signing-mac): 외부 배포 `Developer ID Application`(pkg는 `Developer ID Installer` 추가), MAS 제출 `Apple Distribution`(구 3rd Party Mac Developer Application) + `3rd Party Mac Developer Installer`, 로컬 MAS 테스트 `Apple Development`/`Mac Developer`(프로비저닝 프로파일 필요).

**공증 요구사항** (developer.apple.com/documentation/security/notarizing-macos-software-before-distribution, JSON 엔드포인트로 확인) [문서]
- "Notarization of macOS software is not App Review. The Apple notary service is an automated system that scans your software for malicious content, checks for code-signing issues…"
- 요구: Developer ID 인증서, Hardened Runtime, 모든 실행 파일 서명, secure timestamp, macOS 10.9+ SDK, `com.apple.security.get-task-allow`를 true로 두지 않음.
- "Starting November 1, 2023, the Apple notary service no longer accepts uploads from `altool` or Xcode 13 or earlier." → `notarytool` 또는 Xcode 14+.
- 소요 시간: "the notary service begins the scanning process, which usually takes less than an hour". `@electron/notarize` README는 "many minutes"라고 표현.
- macOS 10.14.5+에서 새 Developer ID로 서명된 소프트웨어, 10.15+에서 2019-06-01 이후 빌드된 모든 Developer ID 소프트웨어는 공증 필요. Mac App Store 배포는 공증 대상 아님.
- Stapling: 티켓을 앱에 붙여 오프라인 검증 가능. electron-builder는 `notarize: true`면 자동 staple [문서]; `@electron/notarize` README에는 자동 staple 여부가 명시돼 있지 않다 [미확인] (검증은 `xcrun stapler validate`).
- `notarytool` 워크플로 (developer.apple.com/documentation/security/customizing-the-notarization-workflow): `store-credentials` → `submit --wait` → `stapler staple`. 자격증명은 Apple ID+앱 암호 또는 App Store Connect API key.

**Hardened Runtime 예외 entitlements** (developer.apple.com/documentation/security/hardened-runtime) [문서]

| 키 | 의미 |
|---|---|
| `com.apple.security.cs.allow-jit` | `MAP_JIT`으로 쓰기·실행 가능 메모리 허용 |
| `com.apple.security.cs.allow-unsigned-executable-memory` | `MAP_JIT` 없이 쓰기·실행 메모리 허용 |
| `com.apple.security.cs.allow-dyld-environment-variables` | dyld 환경변수 영향 허용 |
| `com.apple.security.cs.disable-library-validation` | 서명되지 않은/다른 팀의 플러그인·프레임워크 로드 허용 |
| `com.apple.security.cs.disable-executable-page-protection` | 코드 서명 보호 전면 비활성 |
| `com.apple.security.cs.debugger` | 다른 프로세스에 디버거로 attach 허용 |

Electron 도구의 기본 세트 [문서, 소스 확인]:
- `@electron/osx-sign` `entitlements/default.darwin.plist`: `com.apple.security.cs.allow-jit` + `device.audio-input`/`bluetooth`/`camera`/`print`/`usb`, `personal-information.location`/`photos-library`.
- electron-builder `templates/entitlements.mac.plist`: `cs.allow-jit`, `cs.allow-unsigned-executable-memory`, `cs.disable-library-validation`. electron.build/docs/mac: "`com.apple.security.cs.allow-jit` (always required for V8) and `com.apple.security.cs.allow-unsigned-executable-memory` (Electron internals)".
- `@electron/notarize` README: `allow-jit` 필요, Electron 11 이하는 `allow-unsigned-executable-memory` 추가 필요.
- [추론] 현행 Electron 44에서는 `allow-jit`만 필수이고, `allow-unsigned-executable-memory`·`disable-library-validation`은 네이티브 모듈/서드파티 프레임워크 상황에 따라 선택. `disable-library-validation`은 보안 약화이므로 필요 없으면 제외.

**`@electron/osx-sign` 2.7.0 / `@electron/notarize` 3.1.1** [문서]
- osx-sign 옵션: `identity`, `hardenedRuntime`, `entitlements`, `optionsForFile(filePath)`, `type`('distribution' 기본/'development'), `provisioningProfile`, `keychain`, `platform`('darwin'/'mas'), `preAutoEntitlements`(기본 true; MAS에서 `application-identifier`/`team-identifier`/`application-groups` 자동 삽입), `preEmbedProvisioningProfile`.
- notarize: `notarytool`만 지원(3.0.0에서 altool 제거). 자격증명 3종: `appleId`+`appleIdPassword`+`teamId` / `appleApiKey`(.p8 경로)+`appleApiKeyId`+`appleApiIssuer` / `keychainProfile`(+`keychain`). Xcode 13+ 요구. 디버그 `DEBUG=electron-notarize*`.

**Forge 설정 키** (electronforge.io/guides/code-signing/code-signing-macos) [문서]
```js
// forge.config.js
packagerConfig: {
  osxSign: {},                               // @electron/osx-sign 기본값 사용, optionsForFile로 파일별 entitlements
  osxNotarize: {
    appleId: process.env.APPLE_ID,           // 또는 appleApiKey/appleApiKeyId/appleApiIssuer, 또는 keychainProfile
    appleIdPassword: process.env.APPLE_PASSWORD,   // 앱 암호(app-specific password), Apple ID 비밀번호가 아님
    teamId: process.env.APPLE_TEAM_ID,
  },
}
```
- 문서의 환경변수명(`APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID`, `APPLE_API_KEY`, `APPLE_API_KEY_ID`, `APPLE_API_ISSUER`)은 예시이며 [추론] Forge가 자동으로 읽는 규약은 아니다(코드에서 `process.env`로 직접 전달). Windows 쪽은 `@electron/windows-sign`이 `WINDOWS_CERTIFICATE_FILE`, `WINDOWS_CERTIFICATE_PASSWORD`, `WINDOWS_SIGNTOOL_PATH`, `WINDOWS_SIGN_WITH_PARAMS`를 읽는다 [문서 README].
- "From macOS Catalina onward, apps require both code signed and notarized to run without security warnings".

**electron-builder 설정** (electron.build/docs/features/code-signing/*, /docs/mac) [문서]
- 환경변수: `CSC_LINK`(.p12 경로/URL/base64), `CSC_KEY_PASSWORD`, `CSC_NAME`, `CSC_IDENTITY_AUTO_DISCOVERY`(기본 true; false면 서명 생략), `CSC_KEYCHAIN`, `CSC_INSTALLER_LINK`/`CSC_INSTALLER_KEY_PASSWORD`(pkg). Windows는 `WIN_CSC_LINK`/`WIN_CSC_KEY_PASSWORD`.
- base64 인코딩: macOS `base64 -i cert.p12 -o out.txt`, Linux `base64 cert.p12 > out.txt`. Windows 환경변수 8192자 제한 주의.
- `mac.notarize: true` + `mac.sign.hardenedRuntime: true`(darwin 기본 true, mas는 false). 자격증명 3종: `APPLE_ID`+`APPLE_APP_SPECIFIC_PASSWORD`+`APPLE_TEAM_ID` / `APPLE_API_KEY`(base64 .p8)+`APPLE_API_KEY_ID`+`APPLE_API_ISSUER`(+`APPLE_TEAM_ID`) / `APPLE_KEYCHAIN`+`APPLE_KEYCHAIN_PROFILE`. CI 권장은 API key("API keys don't expire and don't require two-factor authentication"). 자동 staple.
- `mac.sign.identity`: 미설정=키체인 자동 탐색, `null`=서명 생략, `"-"`=ad-hoc. `forceCodeSigning: true`로 미서명 빌드 실패 처리. `mac.universal.mergeASARs`(기본 true), `x64ArchFiles`, `singleArchFiles`.

**MAS 배포 vs Developer ID 배포** (electronjs.org mac-app-store-submission-guide, osx-sign README) [문서]
- MAS 빌드는 App Sandbox 필수: 최소 entitlements `com.apple.security.app-sandbox: true`, `com.apple.security.application-groups: [TEAM_ID.bundle.id]`. `Apple Distribution` 서명 앱은 App Store에서 받기 전엔 실행 불가. MAS 개발 빌드만 프로비저닝 프로파일 임베드 필요.
- MAS 빌드 제약: `crashReporter`와 `autoUpdater` 비활성, 일부 기기 영상 캡처·접근성·DNS 변경 감지 미동작.
- Developer ID 빌드: 샌드박스 강제 없음, 공증 필요, 자체 자동 업데이트 가능.

**CI에서 `.p12`를 keychain에 임포트하는 절차** (docs.github.com/en/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development) [문서, 스크립트 원문]
```yaml
- name: Install the Apple certificate
  env:
    BUILD_CERTIFICATE_BASE64: ${{ secrets.BUILD_CERTIFICATE_BASE64 }}
    P12_PASSWORD: ${{ secrets.P12_PASSWORD }}
    KEYCHAIN_PASSWORD: ${{ secrets.KEYCHAIN_PASSWORD }}
  run: |
    CERTIFICATE_PATH=$RUNNER_TEMP/build_certificate.p12
    KEYCHAIN_PATH=$RUNNER_TEMP/app-signing.keychain-db
    echo -n "$BUILD_CERTIFICATE_BASE64" | base64 --decode -o $CERTIFICATE_PATH
    security create-keychain -p "$KEYCHAIN_PASSWORD" $KEYCHAIN_PATH
    security set-keychain-settings -lut 21600 $KEYCHAIN_PATH
    security unlock-keychain -p "$KEYCHAIN_PASSWORD" $KEYCHAIN_PATH
    security import $CERTIFICATE_PATH -P "$P12_PASSWORD" -A -t cert -f pkcs12 -k $KEYCHAIN_PATH
    security set-key-partition-list -S apple-tool:,apple: -k "$KEYCHAIN_PASSWORD" $KEYCHAIN_PATH
    security list-keychain -d user -s $KEYCHAIN_PATH
- name: Clean up keychain
  if: ${{ always() }}
  run: security delete-keychain $RUNNER_TEMP/app-signing.keychain-db
```
- GitHub 문서 예시에는 프로비저닝 프로파일 단계도 있으나 Developer ID 배포에는 불필요 [추론]. electron-builder는 `CSC_LINK`만 주면 임시 keychain 생성·임포트를 내부에서 수행하므로 위 절차가 필요 없고, Forge/`@electron/osx-sign`은 위 절차로 keychain에 넣은 뒤 `identity`/`keychain`을 지정한다 [추론].
- Secrets 크기 제한 48 KB (docs.github.com using-secrets) — `.p12`를 base64로 넣기에 충분 [추론].

**Gatekeeper 동작 변화** [문서]
- Apple 뉴스(2024-08-06, developer.apple.com/news/?id=saqachfa): "In macOS Sequoia, users will no longer be able to Control-click to override Gatekeeper when opening software that isn't signed correctly or notarized. They'll need to visit System Settings > Privacy & Security to review security information for software before allowing it to run."

### 2.2 Windows

**인증서 요구사항** [문서]
- CA/B Forum 규정: "As of June 2023, the CA/Browser Forum requires private keys for OV certificates to be stored on a hardware security module (HSM) or hardware token." (learn.microsoft.com/windows/apps/package-and-deploy/code-signing-options). Forge 문서도 동일("FIPS 140 Level 2" 하드웨어 저장 요구, 소프트웨어 OV 인증서는 더 이상 구매 불가).
- Microsoft 비교표(code-signing-options, 2026-08-29): OV $150–300/년, EV $400+/년, "EV certificates no longer bypass SmartScreen … That behavior was removed in 2024", 자체 서명·무서명은 SmartScreen 강한 차단. 오픈소스는 SignPath Foundation 무료 서명 언급.
- Electron 공식 code-signing 문서에는 "Since June 2023, Microsoft requires software to be signed with an 'extended validation' certificate"라는 문장이 있어 Microsoft 문서(EV가 더 이상 SmartScreen 우회 안 함)와 어긋난다. [추론] Microsoft 문서(2026-08-29 갱신)가 최신이며 Electron 문서 표현은 오래된 것으로 보인다.

**Azure Artifact Signing (구 Trusted Signing)** [문서]
- 명칭 변경: Microsoft Learn 문서가 "Artifact Signing (formerly Trusted Signing)"으로 개편(overview `ms.date` 2026-01-02). 리소스 공급자 이름은 여전히 `Microsoft.CodeSigning`, Azure Retail Prices API의 `serviceName`은 여전히 `Trusted Signing`.
- 가격 (Azure Retail Prices API `serviceName eq 'Trusted Signing'`, USD, 2026-09-04; 할당량은 azure.microsoft.com/pricing/details/artifact-signing): Basic 계정 $9.99/월(월 5,000 서명, 인증서 프로파일 유형별 1개), Premium $99.99/월(월 100,000 서명, 유형별 10개), 초과 서명 $0.005/건. FAQ: 일할 계산 없이 SKU 전액 청구, 무료/체험/스폰서 구독 불가(종량제 또는 EA 필요).
- 자격·지역 (quickstart, `ms.date` 2026-05-21): "Public Trust certificates are available to organizations in the United States, Canada, the European Union, the United Kingdom, Australia, New Zealand, Japan, South Korea, Singapore, Switzerland, Norway, and Israel. Individual developers must be located in the United States or Canada." (code-signing-options 페이지는 조직 미국·캐나다·EU·영국만 적어 두 문서가 다름 — quickstart가 더 상세.) 조직 신원 검증 1–20 영업일. Azure 리전에 Korea Central(`https://krc.codesigning.azure.net`) 포함.
- 인증서: FIPS 140-3 Level 3 HSM, 발급 인증서는 "valid for three days" (FAQ, 단기 인증서), EV 미발급, 커스텀 CN/O 불가(법적 실체명), 개인 키는 절대 제공되지 않음.
- SmartScreen: "Artifact Signing does not provide instant SmartScreen trust", 일관된 서명 ID로 평판 축적.
- 도구 연동: Forge 문서 — `@electron/windows-sign` v1.2.2 이상, `dotenv-cli`, `.env.trustedsigning`, `windowsSign.ts`; "Ensure that none of the paths have spaces in them. Otherwise, signing will fail." electron-builder — `win.sign.type: "azure"`(Beta) + `publisherName`, `endpoint`, `codeSigningAccountName`, `certificateProfileName`, `fileDigest`, `timestampRfc3161`(기본 `http://timestamp.acs.microsoft.com`), 환경변수 `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_CLIENT_CERTIFICATE_PATH`, `AZURE_FEDERATED_TOKEN_FILE` 등; macOS/Linux에서는 Wine으로 signtool 실행, .NET 8 런타임 포함.

**`@electron/windows-sign` 2.0.6** [문서 README]: 폴더 내 서명 대상 파일을 찾아 SHA-1+SHA-256 서명. 방식: `.pfx`+비밀번호 / `signWithParams`로 signtool 커스텀 인자(HSM 공급자) / `hookFunction`·`hookModulePath` / DigiCert KeyLocker·AWS CloudHSM·Azure Key Vault HSM·Google Cloud KMS 호환. `timestampServer` 기본 DigiCert. Forge/Packager 사용자는 환경변수 `WINDOWS_CERTIFICATE_FILE`, `WINDOWS_CERTIFICATE_PASSWORD`, `WINDOWS_SIGNTOOL_PATH`, `WINDOWS_SIGN_WITH_PARAMS`로 설정 가능.

**Forge Windows 설정** (maker-squirrel) [문서]: `certificateFile`, `certificatePassword`, `signWithParams`, `windowsSign`, `remoteReleases`(델타 생성), `setupIcon`, `loadingGif`, `noMsi`. 앱은 `electron-squirrel-startup`으로 설치/업데이트/제거 이벤트 처리.

**electron-builder Windows 서명** (electron.build/docs/features/code-signing/code-signing-win) [문서]: `win.sign.type` = `signtool`(기본; 파일 인증서, macOS/Linux에서는 `osslsigncode` 사용, Wine 불필요) / `hsm`(Windows 전용, Beta) / `pkcs11`(macOS·Linux, Beta) / `azure`(Beta). 키: `certificateFile`, `certificatePassword`, `certificateSubjectName`, `certificateSha1`, `signingHashAlgorithms`(기본 sha1+sha256 이중 서명; msi 단일, appx/msix는 sha256만), `rfc3161TimeStampServer`, `sign`(커스텀 훅), `additionalCertificateFile`. `verifyUpdateCodeSignature` 기본 true. "You don't need Windows to sign a Windows app."

**SmartScreen 평판** (learn.microsoft.com/windows/apps/package-and-deploy/smartscreen-reputation, 2026-05-04) [문서]
- 두 신호: 게시자 평판(서명 인증서)과 파일 해시 평판. 무서명 파일은 버전마다 0에서 다시 시작. 서명 파일도 평판이 쌓일 때까지 경고 가능("can take several weeks and hundreds of clean installs").
- 무서명: "Windows protected your PC" 경고, 사용자가 "Run anyway"를 선택해야 실행; 기업 정책은 완전 차단 가능. Windows 11 Smart App Control은 무서명 파일 실행 차단.
- Store 배포는 Microsoft 인증서로 재서명되어 경고 없음.

**설치 형식 비교** [문서 + 추론]

| 형식 | 도구 | 자동 업데이트 | 비고 |
|---|---|---|---|
| Squirrel.Windows (`Setup.exe`+nupkg+RELEASES) | Forge maker-squirrel / electron-winstaller | Electron 내장 `autoUpdater` | Forge 기본. electron-builder에서는 deprecated |
| NSIS (`Setup.exe`, oneClick/assisted, perMachine) | electron-builder 기본 | `electron-updater`(차등 업데이트, blockmap) | `nsis-web` 웹 설치기도 있음 |
| MSI (WiX) | Forge maker-wix / electron-builder `msi`, `msi-wrapped` | 없음 (Forge 문서 UX 경고; electron-updater에 MSI 업데이터 없음 — 소스 `packages/electron-updater/src` 목록 확인) | 엔터프라이즈 GPO 배포용 |
| MSIX / APPX | Forge maker-msix(experimental)/maker-appx, electron-builder `msix`/`appx`, electron-windows-msix | Electron 내장 `autoUpdater`가 MSIX 지원(electron-windows-msix 경유), update.electronjs.org도 `.msix` 자산 지원. electron-builder 문서: "MSIX auto-updates are handled by the Microsoft Store (or your App Installer `.appinstaller` flow), not by electron-updater" | Store 제출·사이드로드. 사이드로드 시 `publisher`와 일치하는 신뢰 인증서로 서명 필요 |

**Microsoft Store 배포** [문서]
- 개발자 등록비: 개인 "$19 registration fee is waived"(storedeveloper.microsoft.com 경유, 신분증+셀피 검증), 기업 "$99 registration fee is waived"(DUNS 또는 서류, 2–5 영업일 수동 검토 가능). (learn.microsoft.com/windows/apps/publish/whats-new-individual-developer, whats-new-company-developer)
- MSIX 제출은 Microsoft가 재서명 → 인증서 불필요. MSI/EXE 제출은 "The binary and all of its Portable Executable (PE) files must be digitally signed with a code signing certificate that chains up to … Microsoft Trusted Root Program", 버전이 붙은 HTTPS 다운로드 URL 제출(제출 후 변경 불가), 무음 설치 필수(UAC는 허용), 웹 설치기 금지. (app-package-requirements)
- Electron windows-store-guide는 `electron-windows-store`(AppX) 기반 구식 안내이며 Windows 10 SDK·Anniversary Update를 요구 [문서]. electron-builder `msix` 타깃은 `createMsixupload: true`로 `.msixupload` 생성 [문서].

### 2.3 Linux

- [문서] Forge·electron-builder 공식 문서(linux, appimage, deb, rpm, snap, flatpak 페이지)에 deb/rpm/AppImage GPG 서명 옵션이나 절차는 없다. electron-updater에는 `allowUnverifiedLinuxPackages`(GPG 서명 강제, 기본 true) 옵션이 있다 [문서 auto-update]. → [추론] 리눅스 배포판 패키지 서명은 도구 밖(`dpkg-sig`, `rpm --addsign`, AppImage `--sign`)에서 해야 하며 관행상 필수는 아니다.
- Snap Store [문서] (ubuntu.com/docs/snapcraft/stable/how-to/publishing/publish-a-snap, reference/channels): `snapcraft login` → `snapcraft register <name>` → `snapcraft upload <file>.snap` 후 `snapcraft release <snap> <revision> <channel>` 또는 `snapcraft upload --release=<channel>`. 업로드 후 자동 리뷰, classic confinement는 사전 승인 필요. 채널 `track/risk/branch`, risk = `stable`/`candidate`/`beta`/`edge`, 기본 track `latest`, 사용자 기본 설치 채널 stable. CI 자격증명 `snapcraft export-login` → `SNAPCRAFT_STORE_CREDENTIALS`. electron-builder `publish: {provider: snapStore, repo, channels}`, snap `base` core24 권장(Electron 25+), LXD/Multipass/destructive/remote 빌드 모드. Electron 공식 snapcraft 가이드는 Forge/electron-builder, `electron-installer-snap`, `.deb`→snap 세 방식과 `TMPDIR: $XDG_RUNTIME_DIR` 등을 안내.
- Flathub [문서] (docs.flathub.org/docs/for-app-authors/submission; docs.flatpak.org/en/latest/electron.html): flathub/flathub 저장소 `new-pr` 브랜치로 PR 제출, 볼런티어 리뷰(기간 미정), 병합 후 1–2시간 내 게시. 매니페스트는 `base: org.electronjs.Electron2.BaseApp`, `base-version: '25.08'`(브랜치 목록: 18.08 … 25.08), 실행은 `zypak-wrapper` 래퍼 스크립트, `finish-args` 예시 `--share=ipc --device=dri --socket=x11 --socket=pulseaudio --share=network --env=ELECTRON_TRASH=gio`(Wayland는 "still experimental"). 기존 바이너리 재패키징 시 `patch-desktop-filename`.

### 2.4 서명 없이 배포할 때 사용자에게 보이는 경고와 우회 (교육용·사내 배포 관점)

| OS | 경고 | 우회 방법 | 출처 |
|---|---|---|---|
| macOS (Sequoia 이후) | "macOS can't verify that the app is free of malware" / 개발자 확인 불가 경고. 악성 판정 시 "will damage your computer" | 앱 실행 시도 → **System Settings › Privacy & Security › Open Anyway** → 재실행 후 Open. Control-click 우회는 Sequoia에서 제거 | support.apple.com/102445; developer.apple.com/news/?id=saqachfa [문서] |
| macOS (10.15–14) | 동일 경고 | Control-click › Open 또는 System Settings 승인 | Apple 공증 문서("Users must explicitly approve the software through System Preferences") [문서] |
| Windows | SmartScreen "Windows protected your PC" (무서명·자체서명 동일) | "More info" → "Run anyway". 기업 정책으로 우회 불가일 수 있음. Smart App Control(Win11) 켜진 기기는 무서명 실행 차단 | smartscreen-reputation, code-signing-options [문서] |
| Linux | 배포판 수준 경고 없음(AppImage 실행 권한 필요, snap/flatpak은 스토어 정책) | – | [추론] |

- [문서] Electron 공식: "Both Windows and macOS prevent users from running unsigned applications" 및 macOS에서 `safeStorage`, 로그인 아이템, 쿠키 암호화, `autoUpdater`는 서명·공증 앱에서만 정상 동작.
- [추론] 사내·교육용이라도 macOS 자동 업데이트(Squirrel.Mac)는 서명이 전제이므로, 최소 macOS Developer ID($99/년)는 필요하다. Windows는 무서명으로도 설치·수동 업데이트가 가능하지만 매 릴리즈마다 SmartScreen 경고를 각오해야 하며, 기관 관리 PC에서는 차단될 수 있다. 자체 서명 인증서 + 그룹 정책 배포는 관리형 기기에서만 유효.

---

## 3. 자동 업데이트

### 3.1 Electron 내장 `autoUpdater` (electronjs.org/docs/latest/api/auto-updater) [문서]
- macOS: Squirrel.Mac 기반, "Your application must be signed for automatic updates on macOS". 피드는 JSON(`url` 필수), 업데이트 없으면 HTTP 204.
- Windows: 설치 형태를 자동 감지 — Squirrel.Windows(`electron-winstaller`/Forge) 또는 MSIX(`electron-windows-msix`). 설치 직후 `--squirrel-firstrun`으로 실행되는 동안 업데이트 요청은 실패하므로 10초 지연 권장. Squirrel은 `/RELEASES` 엔드포인트(항상 유효한 응답)를 요구. `setFeedURL({url, headers, serverType, allowAnyVersion})`(MSIX 다운그레이드용 `allowAnyVersion`).
- Linux: "There is no built-in support for auto-updater on Linux" → 배포판 패키지 매니저 권장.
- 이벤트: `error`, `checking-for-update`, `update-available`, `update-not-available`, `update-downloaded`(releaseNotes, releaseName, releaseDate, updateURL), `before-quit-for-update`. 메서드: `setFeedURL`, `getFeedURL`, `checkForUpdates`, `quitAndInstall`.

### 3.2 `update-electron-app` 3.3.0 + update.electronjs.org [문서]
- 요구(README): macOS 또는 Windows, macOS 빌드 서명. update.electronjs.org 사용 시 **공개 GitHub 저장소** + GitHub Releases 게시; 정적 스토리지 사용 시 S3 등에 `**/{platform}/{arch}/{artifact}` 구조(`win32/x64/RELEASES`, `darwin/arm64/RELEASES.json`, zip)로 게시(`@electron-forge/publisher-s3`가 이 구조를 만든다).
- 옵션: `repo`, `updateInterval`(기본 10분, 최소 5분), `logger`, `notifyUser`(기본 true, 다운로드 후 재시작 다이얼로그), `updateSource: {type: UpdateSourceType.ElectronPublicUpdateService | StaticStorage, baseUrl}`.
- FAQ "Can I use this module by uploading my private app's builds to a public GitHub repository? Yes :)" → [추론] 소스는 비공개로 두고 릴리즈 전용 공개 저장소를 쓰면 무료 서비스 사용 가능.
- update.electronjs.org(README): "Your builds are code signed (macOS and MSIX only)". 자산 명명 규칙: macOS `.zip` + `-mac`/`-darwin`/`-osx` + `-arm64`/`-x64`/`-universal`; Windows Squirrel `.zip`/`.exe` + `-win32` + `-ia32`/`-x64`/`-arm64`(기본 x64); MSIX `.msix` + `-win32`. URL `https://update.electronjs.org/OWNER/REPO/PLATFORM-ARCH/VERSION`(+`/RELEASES`).

### 3.3 `electron-updater` 6.8.9 (electron.build/docs/features/auto-update, /docs/publish) [문서]
- 지원 타깃: macOS `dmg`(서명 필수, 메타데이터 생성을 위해 `zip`도 필요), Windows **NSIS만**(Squirrel.Windows 미지원; 소스에도 `NsisUpdater`만 존재), Linux `AppImage`/`deb`/`rpm`/`pacman`. MSIX/APPX/MSI는 electron-updater 대상 아님.
- 설정 5단계: `electron-updater` 설치 → `publish` 설정 → 빌드 시 `latest.yml`/`latest-mac.yml`/`latest-linux.yml` 생성 확인 → `import { autoUpdater } from 'electron-updater'` → `autoUpdater.checkForUpdatesAndNotify()`. `setFeedURL`은 호출하지 말 것(`app-update.yml` 자동 생성).
- Provider: GitHub(`GH_TOKEN`/`GITHUB_TOKEN`이 있으면 기본), S3, Spaces, R2, Bitbucket, GitLab, Keygen, Snap Store, generic/custom. GitHub 옵션 `releaseType`(기본 draft), `private`, `token`(설정 파일에 넣지 말 것), `tagNamePrefix`, `channel`, `vPrefixedTagName`. 27.x는 CI 감지 암묵 게시 제거.
- 프라이빗 GitHub: "Private GitHub provider only for very special cases — not intended and not suitable for all users."(앱에 토큰을 넣어야 함) → [추론] 프라이빗 배포는 S3/R2/generic 서버가 적합.
- 차등 업데이트: NSIS `differentialPackage`, AppImage는 "embeds a blockmap directly into the AppImage binary at build time". `disableDifferentialDownload` 옵션.
- 스테이지 롤아웃: `latest.yml`의 `stagingPercentage: 0–100`을 수동 편집, 사용자 ID 수치와 비교.
- 채널(electron.build/docs/tutorials/release-using-channels): 버전 `1.0.0-beta` 등 prerelease 접미사로 `latest`/`beta`/`alpha`, `generateUpdatesFilesForAllChannels: true`(이때 `allowDowngrade` 자동 true), 앱에서 `autoUpdater.channel = 'beta'`, `allowPrerelease`. "Github release does not respect the version in the version tag even if detectUpdateChannel is true. You must explicitly set `channel`".
- 옵션·이벤트: `autoDownload`, `autoInstallOnAppQuit`, `forceDevUpdateConfig`, `allowUnverifiedLinuxPackages`; 이벤트 `checking-for-update`, `update-available`, `update-not-available`, `download-progress`, `update-downloaded`, `update-cancelled`, `appimage-filename-updated`. macOS·Windows에서 코드 서명 검증(`verifyUpdateCodeSignature` 기본 true).

### 3.4 자체 업데이트 서버 (GitHub API 저장소 상태, 2026-09-04) [문서]
| 서버 | 최근 push | 비고 |
|---|---|---|
| vercel/hazel | 2024-06-10 | 아카이브 아님. GitHub Releases 캐시 프록시(15분), 프라이빗 저장소는 `TOKEN`, Vercel 배포 |
| GitbookIO/nuts | 2023-10-04 | 프라이빗 저장소 지원(Electron 문서) |
| ArekSredzki/electron-release-server | 2024-04-22 | 대시보드·GitHub 독립 |
| atlassian/nucleus | 2023-12-15 | 다중 앱·채널, Forge publisher 있음 |
[추론] 모두 2년 이상 활발하지 않아 장기 운영에는 정적 스토리지(S3/R2) + `update-electron-app` StaticStorage 또는 electron-updater generic provider가 더 안전하다.

### 3.5 Forge `publisher-github` + `update-electron-app` 조합 [문서]
- `publisher-github` 설정 `repository.owner/name`, `prerelease`, `draft`, `tagPrefix` 등(PublisherGitHubConfig), 토큰은 `GITHUB_TOKEN`(Actions에서는 `permissions: contents: write` 필요).
- 공개 저장소면 main 프로세스에 `require('update-electron-app')()` 두 줄로 완료(Forge auto-update 가이드: "by far the easiest way … if you're an open source app"). 프라이빗이면 S3 publisher + `UpdateSourceType.StaticStorage`, 또는 Nucleus/ERS.
- macOS zip maker의 `macUpdateManifestBaseUrl`로 `RELEASES.json`을 생성해 Squirrel.Mac 정적 업데이트 구성.

### 3.6 채널·버전·롤백·UX
- 버전 규칙 [문서]: Electron 자체는 SemVer(major=Chromium/Node/브레이킹). electron-updater는 SemVer prerelease 접미사로 채널 결정. update.electronjs.org/`update-electron-app`은 GitHub Release의 prerelease 플래그를 사용(README "How does this module handle GitHub release states?" 절 존재, 상세 [미확인]).
- 롤백 [추론]: 공식 문서에 롤백 절차는 없다. (a) 내장 autoUpdater/Squirrel: 이전 버전을 더 높은 버전 번호로 재게시하는 것이 유일한 안전 경로(Squirrel은 버전 비교 기반). (b) electron-updater: `allowDowngrade: true` + 채널 파일에서 이전 버전을 `latest`로 되돌리기, 또는 `stagingPercentage`를 0으로 낮춰 배포 중단. (c) MSIX: `setFeedURL({allowAnyVersion: true})`로 다운그레이드 허용 [문서].
- 권장 UX [문서+추론]: 백그라운드 자동 다운로드(`autoDownload`/내장 autoUpdater 기본) → `update-downloaded`에서 비차단 알림·"지금 재시작" 버튼 → `quitAndInstall()`; `autoInstallOnAppQuit`로 종료 시 설치. 시작 직후(Squirrel firstrun)에는 체크를 지연. 릴리즈 노트는 GitHub Release body(`generateReleaseNotes`) 활용.

---

## 4. CI/CD (GitHub Actions)

### 4.1 러너·요금·이미지 [문서]
- 러너 이미지(github.com/actions/runner-images): `ubuntu-latest`=Ubuntu 24.04, `windows-latest`=Windows Server 2025(VS2026), `macos-latest`=**macOS 26 arm64**, `macos-26-intel`(x64) 별도, `macos-15` arm64, macOS 14는 deprecated. "before moving the `-latest` label to a new OS version we will announce the change".
- 요금(docs.github.com about-billing-for-github-actions): Linux 2-core $0.006/분, Windows 2-core $0.010/분, macOS $0.062/분(≈10.33배). 무료 분: Free 2,000 / Pro·Team 3,000 / Enterprise Cloud 50,000. 공개 저장소는 표준 러너 무료. [추론] 프라이빗 저장소에서 macOS 잡이 20분이면 분 단위 배율 적용 시 약 207분 소모(2,000분 한도의 10%).
- 캐시(docs.github.com caching-dependencies): 저장소당 기본 10 GB, 7일 미접근 시 삭제. Secrets 48 KB 제한.
- GitHub Releases(docs.github.com about-releases): 파일당 2 GiB 미만, 릴리즈당 최대 1,000 자산, 총량·대역폭 제한 없음.

### 4.2 캐시 경로 [문서]
- pnpm store: `pnpm/setup@v2` `cache: true`(pnpm 11+; `packageManager` 필드로 버전 결정). electron-builder 문서 예시는 `actions/cache`로 `~/.cache/electron`(Linux/macOS 예시), `~\AppData\Local\electron`(Windows). `@electron/get` 캐시 경로는 §1.7 참조(macOS는 정확히 `~/Library/Caches/electron`).

### 4.3 3-OS 매트릭스 예시
- electron-builder 공식 예시(electron.build/docs/features/github-actions) [문서]: `strategy.matrix.os: [macos-latest, windows-latest, ubuntu-latest]`, `npx electron-builder --publish always`, env `GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}`, `CSC_LINK`, `CSC_KEY_PASSWORD`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID`, `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD`, `permissions: contents: write`, `on.push.tags: ['v*.*.*']`, 플랫폼별 `actions/cache` Electron 캐시. (문서 예시는 npm·`setup-node@v4`·Node 20 기준.)
- 아래는 이 프로젝트(pnpm 11, Node 22, Forge) 기준으로 재구성한 예시 [추론; 액션 버전은 2026-09-04 최신 태그, 동작 검증은 하지 않음]:

```yaml
name: desktop-release
on:
  push:
    tags: ['desktop-v*.*.*']
permissions:
  contents: write
jobs:
  build:
    strategy:
      fail-fast: false
      matrix:
        include:
          - os: macos-latest      # macOS 26 arm64; x64/universal은 packager arch 옵션으로 교차 생성 가능
            electron_cache: ~/Library/Caches/electron
          - os: windows-latest
            electron_cache: ~\AppData\Local\electron\Cache
          - os: ubuntu-latest
            electron_cache: ~/.cache/electron
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v7
      - uses: pnpm/setup@v2            # pnpm 11+; version은 package.json의 packageManager에서 읽음
        with:
          runtime: node@22
          cache: true
      - uses: actions/cache@v6
        with:
          path: ${{ matrix.electron_cache }}
          key: electron-${{ runner.os }}-${{ hashFiles('pnpm-lock.yaml') }}
      - run: pnpm install --frozen-lockfile
      - name: Import Developer ID certificate (macOS)
        if: runner.os == 'macOS'
        run: |  # §2.1의 GitHub 공식 스크립트 사용 (BUILD_CERTIFICATE_BASE64 / P12_PASSWORD / KEYCHAIN_PASSWORD)
          ...
      - run: pnpm --filter desktop exec electron-forge publish   # publisher-github; 태그 기반이면 GitHub Release에 자산 업로드
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          APPLE_ID: ${{ secrets.APPLE_ID }}
          APPLE_PASSWORD: ${{ secrets.APPLE_APP_SPECIFIC_PASSWORD }}
          APPLE_TEAM_ID: ${{ secrets.APPLE_TEAM_ID }}
          WINDOWS_CERTIFICATE_FILE: ${{ runner.temp }}/cert.pfx        # 사전 단계에서 base64 디코드
          WINDOWS_CERTIFICATE_PASSWORD: ${{ secrets.WIN_CERT_PASSWORD }}
      - uses: actions/upload-artifact@v7
        with:
          name: desktop-${{ matrix.os }}
          path: out/make/**
```
- Forge `publish` 대신 `make` 후 `softprops/action-gh-release@v3`(`files`, `draft`, `prerelease`, `generate_release_notes`, `fail_on_unmatched_files`, `permissions: contents: write`)로 릴리즈를 만들 수도 있다 [문서].
- macOS 교차 아키텍처: `@electron/packager`는 darwin x64/arm64/universal 타깃을 지원하므로 arm64 러너에서 x64·universal도 패키징 가능(네이티브 모듈이 있으면 prebuild 필요) [문서 packager README + electron-builder multi-platform 문서의 "native dependencies … can only be compiled on the target platform unless prebuild is used"]. `macos-26-intel` 러너도 존재.
- electron-builder 교차 빌드 제약 [문서]: "macOS Code Signing works only on macOS. Cannot be fixed." Windows 타깃은 Linux(wine)에서도 가능하나 macOS에서는 불가; Docker `electronuserland/builder:wine`. Snap은 snapcraft+multipass/LXD 필요.

### 4.4 서명 secrets 규약 정리

| 용도 | electron-builder | Forge / `@electron/*` |
|---|---|---|
| macOS 인증서 | `CSC_LINK`(base64 .p12), `CSC_KEY_PASSWORD`, `CSC_NAME`, `CSC_KEYCHAIN`, `CSC_IDENTITY_AUTO_DISCOVERY` | keychain 임포트 후 `osxSign.identity`/`keychain` (규약 env 없음) [추론] |
| macOS 공증 | `APPLE_ID`+`APPLE_APP_SPECIFIC_PASSWORD`+`APPLE_TEAM_ID` / `APPLE_API_KEY`(base64 .p8)+`APPLE_API_KEY_ID`+`APPLE_API_ISSUER` / `APPLE_KEYCHAIN`+`APPLE_KEYCHAIN_PROFILE` | `osxNotarize`에 `process.env`로 직접 전달(문서 예시명 `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID`, `APPLE_API_KEY`, `APPLE_API_KEY_ID`, `APPLE_API_ISSUER`) |
| Windows 인증서 | `WIN_CSC_LINK`, `WIN_CSC_KEY_PASSWORD`(또는 `CSC_*`) | `WINDOWS_CERTIFICATE_FILE`, `WINDOWS_CERTIFICATE_PASSWORD`, `WINDOWS_SIGNTOOL_PATH`, `WINDOWS_SIGN_WITH_PARAMS` |
| Azure Artifact Signing | `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`(또는 인증서/연합 토큰) | 동일 `AZURE_*`(`.env.trustedsigning` + dotenv-cli) |
| GitHub 게시 | `GH_TOKEN`/`GITHUB_TOKEN`/`GITHUB_RELEASE_TOKEN` | `GITHUB_TOKEN` |
| Snap Store | `SNAPCRAFT_STORE_CREDENTIALS`/`SNAP_CSC_LINK` | publisher-snapcraft [미확인 상세] |

### 4.5 공증 시간·비용 [문서+추론]
- Apple: "usually takes less than an hour"; `@electron/notarize`: "many minutes". electron-builder/Forge 모두 `submit --wait`로 동기 대기하므로 macOS 잡 시간이 그만큼 늘어난다. [추론] 통상 2–10분이 흔하지만 보장은 없으며, 잡 `timeout-minutes`를 넉넉히(예: 60) 잡고 macOS 분 배율(10.33x)을 예산에 반영해야 한다.

---

## 5. 테스트·품질

### 5.1 Playwright의 Electron 지원 (playwright.dev/docs/api/class-electron, class-electronapplication) [문서]
- 상태: **experimental**. Electron 문서(automated-testing)도 "experimental Electron support via … Chrome DevTools Protocol".
- `_electron.launch({ args: ['main.js'], executablePath?, cwd?, env?, recordVideo?, timeout(기본 30s)?, tracesDir? })` → `ElectronApplication`: `firstWindow()`, `windows()`, `evaluate(fn, arg)`(main 프로세스에서 실행), `evaluateHandle`, `browserWindow(page)`, `context()`, `process()`, `close()`; 이벤트 `window`, `close`, `console`.
- 지원 Electron: v12.2.0+, v13.4.0+, v14+.
- 제약: "The `nodeCliInspect` fuse must not be disabled to avoid launch timeouts" → [추론] 프로덕션 퓨즈 세트(`EnableNodeCliInspectArguments: false`)를 적용한 패키지 앱은 Playwright로 띄울 수 없으므로 E2E는 미패키징(또는 퓨즈 미적용 테스트 빌드)으로 돌린다. 네이티브 다이얼로그는 가로채지 못하므로 `electronApplication.evaluate()`로 `dialog` 메서드를 mock.
- Playwright 1.62.1은 Node ≥20.

### 5.2 Vitest로 main/preload 단위 테스트 (vitest.dev/api/vi) [문서]
- `vi.mock('electron', factory)`는 호이스트되어 import 전에 적용. 팩토리 내부에서는 `vi.hoisted()` 변수만 참조 가능. 실제 모듈 일부 유지는 `vi.importActual`. 조건부 mock은 `vi.doMock`(비호이스트, `await import()`와 조합). 전역은 `vi.stubGlobal`.
- Vitest 5.0.0(2026-09-03)은 Node ^22.12 / ^24 / ≥26, Vite ^6.4/^7/^8. [추론] webpack 프로젝트라도 Vitest는 자체 Vite로 동작하므로 사용 가능(이미 저장소에서 vitest 사용 중). `electron-mocha` 13.1.0(2025-01)은 실제 Electron 런타임에서 mocha를 돌리는 대안이며 필요 시에만.

### 5.3 크래시·에러 수집
- Sentry (`@sentry/electron` 7.18.0, docs.sentry.io/platforms/javascript/guides/electron) [문서]: main(`@sentry/electron/main`)과 모든 renderer(`@sentry/electron/renderer`) 각각 `init`. renderer 이벤트는 main을 경유하므로 renderer `init`의 `dsn`/`release`/`environment`는 무시. 기본 통합: `SentryMinidump`(커스텀 업로더로 전체 컨텍스트 포함, 기본) vs `ElectronMinidump`(Electron `crashReporter` 업로더 사용, renderer/GPU 크래시 컨텍스트 최소, 둘 중 하나만), `MainContext`, `MainProcessSession`, `OnUncaughtException`, `ScopeToMain`, `PreloadInjection`, `ChildProcess`, `ElectronBreadcrumbs`, `ElectronNet`, `BrowserWindowSession`. renderer에 `browserTracingIntegration`, `replayIntegration`, `feedbackIntegration` 추가 가능. 소스맵은 `npx @sentry/wizard@latest -i sourcemaps` 또는 `@sentry/webpack-plugin`/`@sentry/vite-plugin`/`sentry-cli`(Electron 전용 URL 재작성 안내는 해당 페이지에 없음 [미확인]). 7.18.0 breaking: `electronBreadcrumbs`/`electronNet`/`childProcess`가 로그를 내려면 `logs: true`. `electron >= v23`.
- Electron `crashReporter` (electronjs.org/docs/latest/api/crash-reporter) [문서]: Crashpad 사용(업로드 프로토콜은 Breakpad 호환). `crashReporter.start({ submitURL, uploadToServer(기본 true), compress(기본 true), extra, globalExtra, rateLimit(macOS/Windows 시간당 1회) })`. 덤프 위치 `userData/Crashpad`, `app.setPath('crashDumps', …)`로 변경. 키 39바이트·값 127바이트 제한. 호환 서버: socorro, mini-breakpad-server, Sentry, BugSplat, Backtrace, Bugsnag.
- BugSplat (docs.bugsplat.com/integrations/desktop/electron.md) [문서]: `submitURL: https://{database}.bugsplat.com/post/electron/v2/crash.php`, Electron 프레임워크 심볼은 자동, 네이티브 애드온/electron-builder 패키징 앱은 `@bugsplat/symbol-upload -m`으로 `.sym` 업로드, JS 예외는 `bugsplat-node`. 가격은 문서에 없음 [미확인].

### 5.4 로깅 (`electron-log` 5.4.4, github.com/megahertz/electron-log) [문서]
- 기본 파일 위치: Linux `~/.config/{app name}/logs/main.log`, macOS `~/Library/Logs/{app name}/main.log`, Windows `%USERPROFILE%\AppData\Roaming\{app name}\logs\main.log`.
- main: `import log from 'electron-log/main'; log.initialize()`; renderer: `import log from 'electron-log/renderer'`(또는 전역 `__electronLog`); preload는 `electron-log/preload`가 IPC 브리지 역할.
- 파일 트랜스포트(docs/transports/file.md): `maxSize` 기본 1,048,576바이트(1 MB) 초과 시 `{filename}.old.log`로 회전(`archiveLogFn`으로 커스터마이즈), `resolvePathFn`, `fileName` 기본 `main.log`, `format` 기본 `[{y}-{m}-{d} {h}:{i}:{s}.{ms}] [{level}] {text}`, `level` 기본 `silly`, 동기 쓰기 기본. Electron 13+/Node 14+.
- [추론] 다중 파일 보관·일자별 회전은 내장되지 않으므로 필요하면 `archiveLogFn`으로 구현.

### 5.5 앱 크기·시작 시간 최적화
- asar [문서]: `require` 속도 향상·경로 길이 문제 완화. Forge 7은 `packagerConfig.asar: true` + auto-unpack-natives; packager 19+/Forge 8과 electron-builder는 기본 활성. ASAR 무결성(asar-integrity): macOS Electron 16+, Windows 30+, Forge 7.4.0+/Packager 18.3.1+가 자동 처리, 퓨즈 `EnableEmbeddedAsarIntegrityValidation`+`OnlyLoadAppFromAsar`.
- 프로덕션 퓨즈 세트 (electron.build/docs/tutorials/adding-electron-fuses 예시) [문서]: `runAsNode: false`, `enableCookieEncryption: true`, `enableNodeOptionsEnvironmentVariable: false`, `enableNodeCliInspectArguments: false`, `enableEmbeddedAsarIntegrityValidation: true`, `onlyLoadAppFromAsar: true`, `loadBrowserProcessSpecificV8Snapshot: false`, `grantFileProtocolExtraPrivileges: false`. Forge는 `@electron-forge/plugin-fuses`.
- V8 스냅샷 [문서]: `electron-link`(atom)는 2022-12-15 아카이브. `electron/mksnapshot`(`electron-mksnapshot`, 최근 push 2026-08-24)는 Electron 버전에 맞는 mksnapshot 바이너리로 `snapshot_blob.bin`·`v8_context_snapshot.bin` 생성. 퓨즈 `LoadBrowserProcessSpecificV8Snapshot`은 main 프로세스만 `browser_v8_context_snapshot.bin` 사용; Electron fuses 문서: 커스텀 스냅샷을 쓰면 "bootstraps the main process's Node.js environment from source"라 내장 스냅샷의 시작 이득 일부를 잃음. electron-builder 이슈 #8797(퓨즈 켜면 크래시)이 검색됨 [미확인]. [추론] 이 프로젝트 규모에서는 스냅샷보다 번들링·지연 로딩이 우선.
- 성능 체크리스트 (electronjs.org/docs/latest/tutorial/performance) [문서]: 무거운 모듈 포함 자제, 코드 지연 로딩(just-in-time `require`), main 블로킹 금지(worker/비동기 IPC), renderer는 `requestIdleCallback`·Web Worker, 불필요한 폴리필 제거, 네트워크 요청 번들링, 코드 번들링(webpack 등), `Menu.setApplicationMenu(null)`을 ready 전에 호출.
- `app.commandLine.appendSwitch` (command-line-switches) [문서]: `--js-flags`, `--disable-http-cache`, `--enable-logging[=file]`, `--lang`, `--disable-renderer-backgrounding`, `--no-sandbox`(테스트 전용). `--disable-gpu`/`--ignore-gpu-blocklist`는 해당 문서 목록에 없음(Chromium 스위치로는 존재) [미확인].

---

## 6. 배포·운영

### 6.1 다운로드 페이지·형식·아키텍처·크기
- 플랫폼 감지 [추론]: 공식 가이드는 없다. `navigator.userAgentData.platform`/`getHighEntropyValues(['architecture'])`(Chromium 계열) 또는 `navigator.platform`/UA 문자열로 OS를 판별하고, macOS는 브라우저에서 arm64/x64를 신뢰성 있게 구분하기 어려우므로 universal 빌드 하나를 기본 링크로 두거나 두 링크를 모두 노출한다.
- 권장 설치 형식 [문서+추론]
  - macOS: 배포용 `dmg`(공증·staple), 자동 업데이트용 `zip`(Squirrel.Mac은 zip을 받는다; Forge zip maker `RELEASES.json`, electron-builder는 dmg+zip 기본).
  - Windows: Forge면 Squirrel `Setup.exe`(자동 업데이트 내장), electron-builder면 NSIS `Setup.exe`(oneClick 기본, `perMachine`·`allowToChangeInstallationDirectory` 선택). MSI는 GPO/사내 배포 요청이 있을 때만(자동 업데이트 없음). MSIX는 Store 또는 App Installer 흐름.
  - Linux: AppImage(설치 불필요, electron-updater 지원, v27 정적 런타임) + deb/rpm, 스토어는 snap/flatpak.
- 아키텍처 [문서]: macOS x64/arm64/universal(유니버설은 2배 크기, `mergeASARs`로 일부 절감). Windows x64/arm64(Electron 6.0.8부터 arm64 지원, `npm_config_arch=arm64`; 네이티브 모듈은 arm64 빌드 필요). Linux x64/arm64. Electron 44부터 32비트 없음.
- 파일 크기 (Electron 44.1.1 공식 릴리즈 zip, GitHub Releases API, 2026-09-04) [문서]: darwin-arm64 123 MB, darwin-x64 127 MB, linux-x64 116 MB, linux-arm64 118 MB, win32-x64 150 MB, win32-arm64 148 MB. [추론] 앱 설치 파일은 여기에 앱 코드를 더한 뒤 dmg/NSIS/Squirrel 압축을 거치므로 대략 비슷한 자릿수(약 100–150 MB)로 보면 되고, 설치 후 디스크 점유는 압축 해제분만큼 더 크다.

### 6.2 라이선스·법률
- Electron 라이선스 [문서]: MIT. LICENSE 원문 "Copyright (c) Electron contributors / Copyright (c) 2013-2020 GitHub Inc." MIT는 저작권 고지와 허가 고지를 사본에 포함할 것을 요구.
- 동봉 파일 [문서, 소스 확인]: Electron 공식 배포 zip 매니페스트(`script/zip_manifests/dist_zip.{linux,win}.*.manifest`)에 `LICENSE`, `LICENSES.chromium.html`, `version`이 포함되고 macOS zip에도 동일 파일이 `Electron.app` 옆에 있다. electron-builder는 Windows/Linux 출력에서 `LICENSE`를 `LICENSE.electron.txt`로 이름 바꾸고(`ElectronFramework.ts`), macOS에서는 출력 디렉터리의 `LICENSE`·`LICENSES.chromium.html`을 삭제한다(`electronMac.ts`; `.app` 번들 밖 파일이므로 dmg에는 들어가지 않음) [추론]. `@electron/packager` 소스(`src/*.ts`)에는 라이선스 파일 처리 코드가 없어 Windows/Linux 출력 폴더에 원본 `LICENSE`·`LICENSES.chromium.html`이 그대로 남는다 [추론].
- 의무 여부 [미확인]: Electron 공식 문서(application-distribution, distribution-overview)에 "반드시 동봉하라"는 문장은 없다. electron/electron#34236(2022-05, open, stale-exempt)은 바이너리 릴리즈의 라이선스 준수(FFmpeg LGPL 소스, 일부 의존성 attribution 누락)를 묻는 이슈로 유지자 답변이 없다. [추론] MIT·BSD 계열의 고지 의무를 보수적으로 지키려면 `LICENSE.electron.txt`(또는 LICENSE)와 `LICENSES.chromium.html`을 설치본에 유지하고, macOS는 앱 내 "정보/오픈소스 라이선스" 화면에서 노출하는 편이 안전하다. 앱 자체의 서드파티 npm 라이선스 고지도 별도로 생성해야 한다.

### 6.3 텔레메트리·개인정보 [추론, 근거 문서 병기]
- Sentry renderer `replayIntegration`은 화면 세션을 기록하므로 개인정보 마스킹 정책 없이 켜지 말 것. Sentry `sendDefaultPii` 등 기본값은 Sentry 문서 확인 필요 [미확인].
- `crashReporter`의 `extra`/`globalExtra`에 개인정보를 넣지 않는다(키/값 길이 제한도 있음) [문서 제약 근거].
- MAS 배포 시 App Store 개인정보 라벨·샌드박스 entitlements가 필요하며 `crashReporter`/`autoUpdater`가 비활성이라는 점을 설계에 반영 [문서].
- 사용자 동의(opt-in) UI, 진단 데이터 범위 문서화, 국내 개인정보보호법·GDPR 대상 여부 판단은 법무 검토 사항으로 남긴다.

---

## 7. 이 프로젝트 관점의 정리 [추론]

1. 렌더러는 기존 webpack 5 SPA 빌드를 그대로 쓰고(`publicPath: 'auto'`/`'./'`, HashRouter 또는 커스텀 `app://` 스킴), main/preload만 `tsc`/esbuild로 별도 빌드한다. 번들러 플러그인(webpack/Vite) 없이 Forge hooks 또는 electron-builder `files` 설정으로 연결하는 것이 문서 근거가 가장 단순하다.
2. pnpm 11 모노레포에서는 `pnpm-workspace.yaml`에 `nodeLinker: hoisted`가 사실상 필수(Electron·Forge 공식 문구). 저장소 전체에 영향이 있으므로 데스크톱 앱을 별도 워크스페이스 루트로 분리할지 먼저 결정해야 한다.
3. 도구 선택: 공개 저장소 + Squirrel/Squirrel.Mac + `update-electron-app`(무료 update.electronjs.org)이면 Forge; NSIS·차등 업데이트·스테이지 롤아웃·S3/R2 프라이빗 업데이트·AppImage가 필요하면 electron-builder. Forge 8/electron-builder 27은 아직 alpha이므로 안정 라인(7.11.2 / 26.15.3)으로 시작한다.
4. 서명: macOS는 Apple Developer Program $99/년이 사실상 필수(공증 없으면 Sequoia에서 System Settings 승인 강제, 자동 업데이트 불가). Windows는 Azure Artifact Signing(Basic $9.99/월, 한국 조직은 Public Trust 대상 국가 목록에 포함, 개인 개발자는 미국·캐나다만)이 최저 비용이며, EV는 더 이상 SmartScreen 이점이 없다. 교육용·사내 배포면 Windows 무서명 + 우회 안내로 시작하고 macOS만 서명하는 절충도 가능하다.
5. CI: 3-OS 매트릭스, macOS 러너 10.33배 요금과 공증 대기 시간을 예산에 반영, 인증서는 base64 secret(48 KB 제한)으로 keychain 임포트, 태그 푸시로 릴리즈.
6. 테스트: Playwright Electron(experimental)은 퓨즈 `EnableNodeCliInspectArguments`가 켜진 빌드에서만 동작하므로 E2E는 미패키징 빌드로, 프로덕션 퓨즈는 별도 검증(`npx @electron/fuses read`).

---

## 확인하지 못한 항목

1. Forge `maker-squirrel`을 macOS 호스트에서 빌드할 수 있는지 — Forge 문서는 "macOS is not supported", electron-winstaller README는 "macOS/Linux with Wine and Mono"로 상충.
2. electron-builder 공식 문서의 pnpm 호이스팅 지침 — 문서에 문장이 없고 GitHub 이슈(#7554, #7555, #9366)만 검색됨(본문 미열람).
3. `@electron-forge/plugin-webpack`을 renderer 없이 main/preload 전용으로 구성하는 방법.
4. `@electron/notarize` 3.1.1의 자동 stapling 여부(README에 명시 없음; electron-builder는 자동 staple 명시).
5. `update-electron-app`이 GitHub Release의 draft/prerelease 상태를 어떻게 다루는지(README에 해당 절이 있으나 내용 미열람).
6. Sentry Electron 소스맵의 Electron 전용 URL(`app://`, `file://`) 재작성 지침과 `sendDefaultPii` 기본값.
7. BugSplat 가격·무료 티어.
8. Electron 공식 문서상 `LICENSE`/`LICENSES.chromium.html` 동봉 의무 문장(없음)과 electron/electron#34236의 유지자 입장.
9. Linux deb/rpm/AppImage GPG 서명 절차(Forge/electron-builder 문서에 없음)와 electron-updater `allowUnverifiedLinuxPackages`의 정확한 검증 방식.
10. `--disable-gpu`, `--ignore-gpu-blocklist`가 Electron command-line-switches 문서에 없는 이유(Chromium 스위치로 동작하는지).
11. Apple 공증 실제 평균 소요 시간(공식 표현은 "usually less than an hour"뿐).
12. Azure Artifact Signing 한국 조직 대상 가능 여부의 최종 확인 — quickstart(2026-05-21)는 South Korea 포함, code-signing-options(2026-08-29)는 미국·캐나다·EU·영국만 기재해 두 문서가 다름.
13. Forge `publisher-snapcraft` 상세 설정·자격증명 규약.
14. electron-builder 이슈 #8797(`LoadBrowserProcessSpecificV8Snapshot` 크래시)의 현재 상태.
15. Squirrel.Windows 자동 업데이트에서 "설치형(portable 아님)"이 필요하다는 조건의 정확한 공식 문장 — autoUpdater 문서는 Squirrel.Windows 설치기 또는 MSIX로 설치된 앱을 전제한다고만 확인.

## 버전 확인 필요 (착수 시 재조회 권장)

- Electron 44.1.1 → 착수 시점의 최신 3개 지원 라인(8주 주기로 바뀜) 및 45 안정화 시점.
- Electron Forge 8.0.0 정식 릴리즈 여부(현재 alpha.10, 2026-07-02) — 정식이면 packager 20/asar 기본 활성/ESM으로 설정 방식이 달라짐.
- electron-builder 26.16.0이 `latest`로 승격됐는지(현재 `v26` 태그) 및 27.0.0 정식 여부.
- electron-vite 6.0.0 정식 여부(Vite 8 대응).
- `@electron-forge/plugin-vite` experimental 표기 해제 여부.
- `@electron/windows-sign`의 Trusted Signing 경로 공백 제한 해소 여부.
- Playwright 1.63 이후 Electron 지원 상태(experimental 유지 여부).
- Vitest 5.x와 Vite 8 조합 안정성.
- pnpm 11 → 12 전환 시점(`latest-12` 12.3.1이 이미 게시됨)과 `pnpm/setup` 지원 범위.
- GitHub Actions 러너 `macos-latest`가 macOS 26에서 다음 버전으로 옮겨가는 공지, `actions/*` 메이저 태그.
- Azure Artifact Signing 가격·대상 국가(문서 갱신 잦음), Microsoft Store 무료 등록 플로우 유지 여부.
- Apple Developer Program 연회비($99)와 notarytool 요구 Xcode 버전.

---

## 출처 (확인 날짜 2026-09-04)

Electron 공식
- https://releases.electronjs.org/ , https://releases.electronjs.org/releases.json
- https://www.electronjs.org/docs/latest/tutorial/electron-timelines
- https://www.electronjs.org/docs/latest/tutorial/electron-versioning
- https://www.electronjs.org/docs/latest/tutorial/application-distribution
- https://www.electronjs.org/docs/latest/tutorial/distribution-overview
- https://www.electronjs.org/docs/latest/tutorial/tutorial-first-app
- https://www.electronjs.org/docs/latest/tutorial/tutorial-packaging
- https://www.electronjs.org/docs/latest/tutorial/code-signing
- https://www.electronjs.org/docs/latest/tutorial/mac-app-store-submission-guide
- https://www.electronjs.org/docs/latest/tutorial/windows-store-guide
- https://www.electronjs.org/docs/latest/tutorial/windows-arm
- https://www.electronjs.org/docs/latest/tutorial/updates
- https://www.electronjs.org/docs/latest/api/auto-updater
- https://www.electronjs.org/docs/latest/api/crash-reporter
- https://www.electronjs.org/docs/latest/api/protocol
- https://www.electronjs.org/docs/latest/api/app
- https://www.electronjs.org/docs/latest/api/command-line-switches
- https://www.electronjs.org/docs/latest/tutorial/asar-archives , /asar-integrity , /fuses , /performance , /security , /process-model , /installation , /using-native-node-modules , /automated-testing , /snapcraft
- https://github.com/electron/forge/releases , https://github.com/electron/packager , https://github.com/electron/asar , https://github.com/electron/rebuild , https://github.com/electron/universal , https://github.com/electron/get , https://github.com/electron/osx-sign (+ `entitlements/default.darwin.plist`, `src/util-entitlements.ts`), https://github.com/electron/notarize , https://github.com/electron/windows-sign , https://github.com/electron/fuses , https://github.com/electron/update-electron-app , https://github.com/electron/update.electronjs.org , https://github.com/electron/windows-installer , https://github.com/electron/mksnapshot , https://github.com/electron/electron/blob/main/LICENSE , https://github.com/electron/electron/issues/34236 , `script/zip_manifests/*`

Electron Forge
- https://www.electronforge.io/ , /cli , /llms.txt , /core-concepts/build-lifecycle , /config/configuration , /config/hooks , /config/plugins , /config/plugins/webpack , /config/plugins/vite , /config/plugins/fuses , /config/plugins/auto-unpack-natives , /config/makers/{squirrel.windows,zip,dmg,pkg,deb,rpm,wix-msi,appx,msix,flatpak,snapcraft} , /config/publishers/github , /config/publishers/s3 , /guides/code-signing/code-signing-macos , /guides/code-signing/code-signing-windows , /advanced/auto-update

electron-builder
- https://www.electron.build/ , /docs/ , /docs/configuration , /docs/mac , /docs/win , /docs/linux , /docs/nsis , /docs/appimage , /docs/snap , /docs/msix , /docs/squirrel-windows , /docs/publish , /docs/features/code-signing/ , /docs/features/code-signing/code-signing-mac , /docs/features/code-signing/code-signing-win , /docs/features/code-signing/notarization , /docs/features/auto-update , /docs/features/multi-platform-build , /docs/features/github-actions , /docs/tutorials/release-using-channels , /docs/tutorials/code-signing-windows-apps-on-unix , /docs/tutorials/adding-electron-fuses , https://www.electron.build/sitemap.xml
- https://github.com/electron-userland/electron-builder/releases , `packages/app-builder-lib/src/electron/ElectronFramework.ts` , `packages/app-builder-lib/src/electron/mac/electronMac.ts` , `packages/app-builder-lib/templates/entitlements.mac.plist` , `packages/electron-updater/src/`

electron-vite
- https://electron-vite.org/ , https://electron-vite.org/guide/distribution , https://github.com/alex8088/electron-vite/releases

Apple
- https://developer.apple.com/programs/ , https://developer.apple.com/support/compare-memberships/
- https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution (JSON 엔드포인트)
- https://developer.apple.com/documentation/security/customizing-the-notarization-workflow
- https://developer.apple.com/documentation/security/hardened-runtime (JSON 엔드포인트)
- https://developer.apple.com/news/?id=saqachfa , https://support.apple.com/en-us/102445

Microsoft
- https://learn.microsoft.com/en-us/azure/artifact-signing/overview , /faq , /quickstart
- https://azure.microsoft.com/en-us/pricing/details/artifact-signing/ , https://prices.azure.com/api/retail/prices (serviceName 'Trusted Signing')
- https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options
- https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation
- https://learn.microsoft.com/en-us/windows/security/operating-system-security/virus-and-threat-protection/microsoft-defender-smartscreen/
- https://learn.microsoft.com/en-us/windows/apps/publish/ , /whats-new-individual-developer , /whats-new-company-developer , /publish-your-app/msi/app-package-requirements
- https://learn.microsoft.com/en-us/windows/security/application-security/application-control/app-control-for-business/deployment/use-code-signing-for-better-control-and-protection

GitHub / CI
- https://docs.github.com/en/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development
- https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-actions
- https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/caching-dependencies-to-speed-up-workflows
- https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions
- https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- https://github.com/actions/runner-images , https://github.com/softprops/action-gh-release , https://github.com/pnpm/setup , https://github.com/pnpm/action-setup , GitHub Releases API(actions/*)

pnpm
- https://pnpm.io/settings , https://pnpm.io/settings/node-modules

테스트·품질
- https://playwright.dev/docs/api/class-electron , https://playwright.dev/docs/api/class-electronapplication , https://github.com/microsoft/playwright/releases
- https://vitest.dev/api/vi.html
- https://docs.sentry.io/platforms/javascript/guides/electron/ , /configuration/integrations/ , /configuration/integrations/electronminidump/ , /sourcemaps/ , https://github.com/getsentry/sentry-electron (README, releases)
- https://github.com/megahertz/electron-log , `docs/transports/file.md`
- https://docs.bugsplat.com/integrations/desktop/electron.md
- https://github.com/atom/electron-link

배포 스토어
- https://ubuntu.com/docs/snapcraft/stable/how-to/publishing/publish-a-snap/ , https://ubuntu.com/docs/snapcraft/stable/reference/channels/
- https://docs.flathub.org/docs/for-app-authors/submission , https://docs.flatpak.org/en/latest/electron.html , https://github.com/flathub/org.electronjs.Electron2.BaseApp (branches, `org.electronjs.Electron2.BaseApp.yml`)

업데이트 서버
- https://github.com/vercel/hazel , GitHub API: GitbookIO/nuts, ArekSredzki/electron-release-server, atlassian/nucleus

기타
- https://webpack.js.org/guides/public-path/
- https://github.com/bitdisaster/electron-windows-msix
- npm registry: https://registry.npmjs.org/<package> (모든 버전·게시일·engines)
