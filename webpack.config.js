const path = require('path');

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
      { test: /\.tsx?$/, use: [ 'babel-loader', 'awesome-typescript-loader' ] },
      { test: /\.scss$/,
        use: [
          { loader: 'style-loader' },
          { loader: 'css-loader' },
          { loader: 'sass-loader' },
        ]
      },
      { test: /\.css$/,
        use: [
          { loader: 'style-loader' },
          { loader: 'css-loader' },
        ]
      }
    ]
  },
  devServer: {
    port: 8080,
    proxy: {
      '/': 'http://localhost:8081'
    },
    publicPath: '/js/'
  }
};
