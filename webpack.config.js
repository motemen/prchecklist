module.exports = {
  entry: './static/typescript/index.tsx',
  output: {
    filename: './static/js/bundle.js'
  },
  devtool: 'source-map',
  resolve: {
    extensions: ['.ts', '.tsx', '.js']
  },
  module: {
    rules: [
      { test: /\.tsx?$/, loader: 'awesome-typescript-loader' }
    ]
  }
};
