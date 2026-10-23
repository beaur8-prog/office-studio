'use strict';
const fs = require('node:fs/promises');
const { createReadStream } = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const targets = ['win-x64'];
const products = { sheet: 'Sheet Studio', slide: 'Slide Studio', word: 'Word Studio' };
const bundleIds = { sheet: 'local.lattice.sheetstudio', slide: 'local.slidestudio.app', word: 'local.wordstudio.app' };

function validateLock(lock) {
  if (lock.schema !== 1 || lock.product !== 'Office Studio' || !/^\d+\.\d+\.\d+$/.test(lock.version)) throw new Error('Invalid suite metadata');
  if (lock.target !== 'win-x64') throw new Error('Integrated releases support Windows x64 only.');
  if (!Array.isArray(lock.apps) || lock.apps.length !== 3 || new Set(lock.apps.map(a => a.id)).size !== 3) throw new Error('Expected three distinct Studio apps');
  for (const app of lock.apps) {
    if (products[app.id] !== app.product || bundleIds[app.id] !== app.bundleId || !/^\d+\.\d+\.\d+$/.test(app.version)) throw new Error('Invalid app metadata');
    if (Object.keys(app.assets || {}).some(target => !targets.includes(target))) throw new Error('Mac and Linux downloads belong to the separate app releases.');
    for (const target of targets) {
      const asset = app.assets?.[target];
      if (!asset || !/^[A-Za-z0-9][A-Za-z0-9._-]+$/.test(asset.filename) || !/^[a-f0-9]{64}$/.test(asset.sha256) || !Number.isSafeInteger(asset.bytes) || asset.bytes <= 0) throw new Error('Invalid asset: ' + app.id + '/' + target);
      const url = new URL(asset.url);
      if (!asset.filename.endsWith('.exe') || url.origin !== 'https://github.com' || url.pathname !== `/beaur8-prog/${app.id}-studio/releases/download/v${app.version}/${asset.filename}` || url.search || url.hash) throw new Error('Asset must use its pinned release URL');
    }
  }
  return lock;
}
async function readLock() { return validateLock(JSON.parse(await fs.readFile(path.join(root, 'suite-lock.json'), 'utf8'))); }
function targetArg(value) {
  if (!targets.includes(value)) throw new Error('Integrated releases support win-x64 only. Mac and Linux use separate app releases.');
  return value;
}
async function hashFile(file) {
  const hash = createHash('sha256');
  for await (const bytes of createReadStream(file)) hash.update(bytes);
  return hash.digest('hex');
}
async function verifyAsset(file, asset) {
  if ((await fs.stat(file)).size !== asset.bytes || await hashFile(file) !== asset.sha256) throw new Error('Input checksum mismatch: ' + asset.filename);
}
function xml(value) { return String(value).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[c]); }
function nsisQuote(value) {
  if (/[\r\n\0]/.test(value)) throw new Error('Invalid NSIS string');
  return '"' + value.replaceAll('$', () => '$$').replaceAll('"', () => '$\\"') + '"';
}
function run(command, args, capture = false) {
  try {
    return execFileSync(command, args, { stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit', encoding: capture ? 'utf8' : undefined });
  } catch (error) {
    if (capture) error.message += '\n' + String(error.stdout || '') + String(error.stderr || '');
    throw error;
  }
}
async function verifiedInputs(lock, target) {
  const inputs = [];
  for (const app of lock.apps) {
    const asset = app.assets[target];
    const file = path.join(root, 'inputs', asset.filename);
    await verifyAsset(file, asset);
    inputs.push({ ...app, asset, file });
  }
  return inputs;
}
module.exports = { root, products, validateLock, readLock, targetArg, hashFile, verifyAsset, verifiedInputs, xml, nsisQuote, run };
