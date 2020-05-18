const debug = !!process.env['DEBUG'];

if (!process.env['PRCHECKLIST_TEST_GITHUB_TOKEN']) {
  throw new Error('PRCHECKLIST_TEST_GITHUB_TOKEN must be set');
}

module.exports = {
  launch: {
    headless: debug ? false : true,
    slowMo: debug ? 100 : 0,
  },
  server: {
    command: "PRCHECKLIST_DATASOURCE=bolt:$(mktemp) yarn run serve",
    port: 8080,
  },
}
