var path = require('path');
var webpack = require('webpack');

module.exports = require('./scalajs.webpack.config');

module.exports.resolve = module.exports.resolve || {};
module.exports.resolve.alias = module.exports.resolve.alias || {};

module.exports.resolve.alias['raphael'] = 'webpack-raphael';

// inspired by https://stackoverflow.com/q/38610824/3714539
module.exports.resolve.alias['bootstrap-treeview'] =
  path.join(__dirname, 'node_modules', 'bootstrap-treeview', 'src', 'js', 'bootstrap-treeview.js');
