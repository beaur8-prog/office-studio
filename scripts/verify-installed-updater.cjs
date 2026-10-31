'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const sources = require('../updater/app-sources.json');
const [file, id, version] = process.argv.slice(2);
const archive = fs.readFileSync(file);
const headerSize = archive.readUInt32LE(4);
const header = JSON.parse(archive.subarray(16, 16 + archive.readUInt32LE(12)).toString());
function read(name) {
  let entry = header;
  for (const part of name.split('/')) entry = entry.files[part];
  const offset = 8 + headerSize + Number(entry.offset);
  return archive.subarray(offset, offset + entry.size).toString();
}
assert.equal(JSON.parse(read('package.json')).version, version);
const expected = sources.apps[id];
assert.equal(expected.version, version, 'Pinned updater version differs');
const app = require('../suite-lock.json').apps.find(item => item.id === id);
assert.equal(expected.sourceCommit, app.sourceCommit, 'Pinned updater commit differs');
for (const name of ['studio-updater.cjs','studio-update-windows.ps1']) {
  const normalize = value => value.replaceAll('\r\n','\n');
  assert.equal(createHash('sha256').update(normalize(read('electron/' + name))).digest('hex'), expected.files[name], id + ': installed ' + name + ' differs from the pinned app source');
}
assert.match(read('electron/main.cjs'), /createStudioUpdater/);
console.log('Verified installed updater:', id, version);
