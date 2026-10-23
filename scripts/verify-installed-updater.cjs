'use strict';
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
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
for (const name of ['studio-updater.cjs','studio-update-windows.ps1']) {
  assert.equal(read('electron/' + name), fs.readFileSync(path.join(__dirname,'../updater',name),'utf8'));
}
assert.match(read('electron/main.cjs'), /createStudioUpdater/);
console.log('Verified installed updater:', id, version);
