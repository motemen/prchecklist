module.exports = {
  port: 3000,
  server: false,
  proxy: {
    target: 'localhost:8080',
    reqHeaders: function (config) {
      return {
        host: 'localhost:3000'
      };
    }
  },
  open: false,
  files: [
    "src/main/webapp/stylesheets/*.css"
  ]
};
