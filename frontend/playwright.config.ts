import { defineConfig, devices } from "@playwright/test";

const BASE_URL = "http://localhost:3000";
const isCI = Boolean(process.env.CI);

export default defineConfig({
  testDir: "./src/__test__", // E2E 테스트는 test-strategy에 따라 전역 __test__ 폴더에 둬요
  testMatch: "**/*.test.ts",
  fullyParallel: true,
  forbidOnly: isCI, // CI에서 test.only가 남아 있으면 실패시켜요
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: "html",
  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry", // 재시도할 때만 트레이스를 남겨요
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "pnpm dev --no-open", // 브라우저 자동 열림 없이 dev 서버를 띄워요
    url: BASE_URL,
    reuseExistingServer: !isCI, // 로컬에서 이미 떠 있는 dev 서버가 있으면 재사용해요
    timeout: 120_000,
  },
});
