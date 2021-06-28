import * as path from "path";

jest.setTimeout(30 * 1000);

describe("prchecklist", () => {
  const targetPath = "motemen/test-repository/pull/2";
  const screenshotPath = process.env["TEST_SCREENSHOT_PATH"];

  beforeEach(async () => {
    await page.goto(`http://localhost:8080/debug/auth-for-testing`);
    await page.goto(`http://localhost:8080/${targetPath}`);
    await page.waitForNavigation({ waitUntil: "networkidle2" });
  });

  it("check", async () => {
    await page.waitForSelector("#checklist-items");

    const href = await page.$eval(
      ".title a",
      (el: HTMLAnchorElement) => el.href
    );
    expect(href).toEqual(`https://github.com/${targetPath}`);

    if (screenshotPath) {
      await page.screenshot({
        path: path.join(screenshotPath, "pr-view-unchecked.png"),
        fullPage: true,
      });
    }

    await page.click("#checklist-items ul li:nth-child(1) button");

    await page.waitFor(1000);

    const checked = await page.$eval(
      "#checklist-items ul li:nth-child(1) button",
      (el: HTMLElement) => el.classList.contains("checked")
    );
    expect(checked).toBeTruthy();

    if (screenshotPath) {
      await page.screenshot({
        path: path.join(screenshotPath, "pr-view-checked-1.png"),
        fullPage: true,
      });
    }
  });
});
