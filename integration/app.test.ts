jest.setTimeout(10000);

describe('prchecklist', () => {
  const path = 'motemen/test-repository/pull/2';

  beforeEach(async () => {
    await page.goto(`http://localhost:8080/debug/auth-for-testing`);
    await page.goto(`http://localhost:8080/${path}`);
  });

  it('check', async () => {
    await page.waitForSelector('#checklist-items');

    const href = await page.$eval('.title a', (el: HTMLAnchorElement) => el.href);
    expect(href).toEqual(`https://github.com/${path}`);

    await page.click('#checklist-items ul li:nth-child(1) button');

    await page.waitFor(1000);

    const checked = await page.$eval('#checklist-items ul li:nth-child(1) button', (el: HTMLElement) => el.classList.contains('checked'));
    expect(checked).toBeTruthy();
  })
})
