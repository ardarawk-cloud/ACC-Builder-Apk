const core = require('./index');
const owner = require('./owner-inbox');

module.exports = {
  ...core,
  ...owner,
};
