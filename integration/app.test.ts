import * as path from "path";

jest.setTimeout(30 * 1000);

describe("prchecklist", () => {
  const targetPath = "motemen/test-repository/pull/2";
  const screenshotPath = process.env["TEST_SCREENSHOT_PATH"];

  const maySaveScreenshot = async (filename: string): Promise<void> => {
    if (screenshotPath) {
      await page.screenshot({
        path: path.join(screenshotPath, filename),
        fullPage: true,
      });
    }
  };

  beforeEach(async () => {
    await page.goto(`http://localhost:8080/debug/auth-for-testing`);
    await page.goto(`http://localhost:8080/${targetPath}`);
    await page.waitForNavigation({ waitUntil: "networkidle2" });
  });

  it("checks PRs", async () => {
    await page.waitForSelector("#checklist-items");

    const href = await page.$eval(
      ".title a",
      (el: HTMLAnchorElement) => el.href
    );
    expect(href).toEqual(`https://github.com/${targetPath}`);

    await maySaveScreenshot("01-pr-view-unchecked.png");

    await page.click("#checklist-items ul li:nth-child(1) button");

    await page.waitFor(1000);

    const checked = await page.$eval(
      "#checklist-items ul li:nth-child(1) button",
      (el: HTMLElement) => el.classList.contains("checked")
    );
    expect(checked).toBeTruthy();

    await maySaveScreenshot("02-pr-view-checked-1.png");

    const elems = await page.$$("#checklist-items ul li + li button");
    for (const el of elems) {
      await el.click();
    }

    await maySaveScreenshot("03-pr-view-checked-all.png");
  });

  it("changes stages", async () => {
    const stagesElem = await page.waitForSelector("select.stages");

    await expect(
      page.$eval("select.stages", (el) => (el as HTMLSelectElement).value)
    ).resolves.toEqual("qa");

    await maySaveScreenshot("11-pr-view-stage-before-change.png");

    await stagesElem.select("production");
    await page.waitForNavigation({ waitUntil: "networkidle2" });

    await expect(
      page.$eval("select.stages", (el) => (el as HTMLSelectElement).value)
    ).resolves.toEqual("production");

    await maySaveScreenshot("12-pr-view-stage-changed.png");
  });
});
