const path = require('path');

module.exports = {
  entry: './static/typescript/index.tsx',
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'static/js'),
    publicPath: '/js/'
  },
  devtool: 'source-map',
  resolve: {
    extensions: ['.ts', '.tsx', '.js']
  },
  module: {
    rules: [
      { test: /\.tsx?$/, use: [ 'ts-loader' ] },
      { test: /\.scss$/, use: [ 'style-loader', 'css-loader', { loader: 'sass-loader', options: { sassOptions: { loadPaths: [__dirname] } } } ] },
      { test: /\.css$/,  use: [ 'style-loader', 'css-loader' ] },
    ]
  },
  devServer: {
    port: 8080,
    proxy: [
      {
        context: () => true,
        target: 'http://localhost:8081'
      }
    ],
    devMiddleware: {
      publicPath: '/js/'
    }
  },
};
