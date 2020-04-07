const path = require('path');
const { DefinePlugin } = require('webpack');

module.exports = {
  entry: './static/typescript/index.tsx',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'static/js')
  },
  devtool: 'source-map',
  resolve: {
    extensions: ['.ts', '.tsx', '.js']
  },
  module: {
    rules: [
      { test: /\.tsx?$/, use: [ 'ts-loader' ] },
      { test: /\.scss$/, use: [ 'style-loader', 'css-loader', 'sass-loader' ] },
      { test: /\.css$/,  use: [ 'style-loader', 'css-loader' ] },
    ]
  },
  devServer: {
    port: 8080,
    proxy: {
      '/': 'http://localhost:8081'
    },
    publicPath: '/js/'
  },
  plugins: [
    new DefinePlugin(['GIT_VERSION']),
  ],
};
