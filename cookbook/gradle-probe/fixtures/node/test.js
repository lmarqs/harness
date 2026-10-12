const assert = require('node:assert/strict');
const { add } = require('fixture-math');
assert.equal(add(2, 3), 5);
console.log('Installed dependency works');
