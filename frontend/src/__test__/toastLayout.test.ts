import { expect, test, type Page } from "@playwright/test";

const HOME_PATH = "/workspace/1";
const CHAT_PATH = HOME_PATH + "/chat";
const SAVED = "녹음을 저장했어요";

// 이동 기록을 만들어 실제 앱의 Provider가 토스트를 소비하도록 해요.
async function moveWithState(page: Page, path: string, state: unknown) {
  await page.evaluate(
    ({ path, state }) => {
      window.history.pushState(
        { ...window.history.state, key: crypto.randomUUID(), usr: state },
        "",
        path,
      );
      window.dispatchEvent(new PopStateEvent("popstate"));
    },
    { path, state },
  );
}

for (const viewport of [
  { width: 1440, height: 900 },
  { width: 390, height: 844 },
]) {
  test.describe(viewport.width + "px 토스트 레이아웃", () => {
    test.use({ viewport });

    test("같은 레이아웃에서는 유지하고 나갔다 돌아오면 이전 알림을 지운다", async ({
      page,
    }, testInfo) => {
      await page.goto(HOME_PATH);
      const navigation = page.getByRole("navigation", {
        name: "워크스페이스 화면 이동",
      });
      await expect(navigation).toBeVisible();
      await moveWithState(page, HOME_PATH, {
        toast: { variant: "success", message: SAVED },
      });

      const list = page.getByTestId("toast-list");
      await expect(list.getByText(SAVED)).toBeVisible();
      await expect
        .poll(async () => (await list.boundingBox())?.height ?? 0)
        .toBeGreaterThan(32);
      const listBox = await list.boundingBox();
      const dockBox = await page
        .getByRole("button", { name: "무엇이든 요청하기" })
        .boundingBox();
      expect(listBox).not.toBeNull();
      expect(dockBox).not.toBeNull();
      expect(listBox!.y + listBox!.height).toBeLessThanOrEqual(dockBox!.y);
      expect(listBox!.x).toBeGreaterThanOrEqual(0);
      expect(listBox!.x + listBox!.width).toBeLessThanOrEqual(viewport.width);
      await page.screenshot({
        path: testInfo.outputPath("inline-toast.png"),
        animations: "disabled",
      });

      await navigation.getByRole("button", { name: "탐색", exact: true }).click();
      await expect(page).toHaveURL(CHAT_PATH);
      await expect(list.getByText(SAVED)).toBeVisible();

      await moveWithState(page, "/workspace", null);
      await expect(list).toBeEmpty();
      await page.goBack();
      await expect(page).toHaveURL(CHAT_PATH);
      await expect(list).toBeEmpty();
    });

    test("도착 레이아웃에서 같은 알림을 표시하고 다른 state를 보존한다", async ({
      page,
    }, testInfo) => {
      await page.goto(HOME_PATH);
      await expect(
        page.getByRole("button", { name: "무엇이든 요청하기" }),
      ).toBeVisible();
      await moveWithState(page, HOME_PATH, {
        toast: { variant: "success", message: SAVED },
      });
      await expect(page.getByTestId("toast-list").getByText(SAVED)).toBeVisible();

      await moveWithState(page, "/workspace", {
        toast: { variant: "success", message: SAVED },
        returnTo: HOME_PATH,
      });

      const list = page.getByTestId("toast-list");
      await expect(list.getByText(SAVED)).toBeVisible();
      await expect(list).toHaveCSS("position", "fixed");
      await expect(list).toHaveCSS("bottom", "104px");
      await expect
        .poll(() => page.evaluate(() => window.history.state.usr))
        .toEqual({ returnTo: HOME_PATH });
      await expect
        .poll(async () => (await list.boundingBox())?.height ?? 0)
        .toBeGreaterThan(32);
      const listBox = await list.boundingBox();
      expect(listBox).not.toBeNull();
      expect(listBox!.x).toBeGreaterThanOrEqual(0);
      expect(listBox!.x + listBox!.width).toBeLessThanOrEqual(viewport.width);
      await page.screenshot({
        path: testInfo.outputPath("floating-toast.png"),
        animations: "disabled",
      });
    });
  });
}
