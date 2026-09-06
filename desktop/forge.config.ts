/**
 * Electron Forge 설정 (기획서 10절, 결정 D5).
 *
 * A1 범위는 로컬 dev 빌드까지다. 서명·공증·DMG·Squirrel·publisher는 A3에서
 * 붙인다. macOS arm64에서 Fuse를 뒤집으면 원래 서명이 깨지므로
 * `resetAdHocDarwinSignature`로 임시(ad-hoc) 서명을 다시 입힌다(지식 §3.2).
 */

import { execFileSync } from "node:child_process";
import { MakerZIP } from "@electron-forge/maker-zip";
import { FusesPlugin } from "@electron-forge/plugin-fuses";
import type { ForgeConfig } from "@electron-forge/shared-types";
import { FuseV1Options, FuseVersion } from "@electron/fuses";

const config: ForgeConfig = {
  packagerConfig: {
    name: "Knot",
    executableName: process.platform === "linux" ? "knot" : "Knot",
    appBundleId: "kr.knoted.desktop",
    appCategoryType: "public.app-category.productivity",
    // Forge 7의 기본값은 off라 명시한다(지식 §3.2)
    asar: true,
    ignore: [
      /^\/src($|\/)/,
      /^\/test($|\/)/,
      /^\/scripts($|\/)/,
      /^\/\.vscode($|\/)/,
      /^\/tsconfig(\.\w+)?\.json$/,
      /^\/vitest\.config\.ts$/,
      /^\/forge\.config\.ts$/,
      /^\/pnpm-workspace\.yaml$/,
      /^\/pnpm-lock\.yaml$/,
      /^\/README\.md$/,
      /^\/CLAUDE\.md$/,
      /^\/\.gitignore$/,
      /^\/\.editorconfig$/,
      /^\/\.prettierrc$/,
      /^\/\.nvmrc$/,
    ],
  },
  makers: [new MakerZIP({}, ["darwin", "win32", "linux"])],
  plugins: [
    new FusesPlugin({
      version: FuseVersion.V1,
      resetAdHocDarwinSignature: process.platform === "darwin",
      [FuseV1Options.RunAsNode]: false,
      [FuseV1Options.EnableCookieEncryption]: true,
      [FuseV1Options.EnableNodeOptionsEnvironmentVariable]: false,
      [FuseV1Options.EnableNodeCliInspectArguments]: false,
      [FuseV1Options.EnableEmbeddedAsarIntegrityValidation]: true,
      [FuseV1Options.OnlyLoadAppFromAsar]: true,
      [FuseV1Options.GrantFileProtocolExtraPrivileges]: false,
    }),
  ],
  hooks: {
    generateAssets: async () => {
      execFileSync(process.execPath, ["scripts/build.mjs"], { stdio: "inherit" });
    },
  },
};

export default config;
