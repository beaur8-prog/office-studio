'use strict';
const fs = require('node:fs/promises');
const { createWriteStream } = require('node:fs');
const { Readable } = require('node:stream');
const { pipeline } = require('node:stream/promises');
const { randomUUID } = require('node:crypto');
const path = require('node:path');
const { root, readLock, targetArg, verifyAsset } = require('./common.cjs');

async function main() {
  const target = targetArg(process.argv[2]);
  const lock = await readLock();
  const inputs = path.join(root, 'inputs');
  await fs.mkdir(inputs, { recursive: true });
  for (const app of lock.apps) {
    const asset = app.assets[target];
    const destination = path.join(inputs, asset.filename);
    try { await verifyAsset(destination, asset); console.log('Verified cached ' + asset.filename); continue; }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    const temporary = destination + '.' + randomUUID() + '.partial';
    try {
      console.log('Downloading ' + asset.url);
      const response = await fetch(asset.url, { signal: AbortSignal.timeout(600_000) });
      if (!response.ok || !response.body) throw new Error('Download failed: HTTP ' + response.status);
      await pipeline(Readable.fromWeb(response.body), createWriteStream(temporary, { flags: 'wx' }));
      await verifyAsset(temporary, asset);
      await fs.rename(temporary, destination);
    } finally { await fs.rm(temporary, { force: true }); }
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
