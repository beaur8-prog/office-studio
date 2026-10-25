'use strict';
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { root, readLock, targetArg, verifiedInputs, hashFile } = require('./common.cjs');

async function main() {
  const target = targetArg(process.argv[2]);
  const lock = await readLock();
  const inputs = await verifiedInputs(lock, target);
  const release = path.join(root, 'release', lock.version);
  await fs.mkdir(release, { recursive: true });
  const work = await fs.mkdtemp(path.join(os.tmpdir(), 'studio-suite-'));
  try {
    const builder = require('./windows.cjs');
    const { file, checks, evidence = {} } = await builder.build({ lock, inputs, work });
    const filename = `Office-Studio-${lock.version}-${target}-Setup.exe`;
    const output = path.join(release, filename);
    const sha256 = await hashFile(file);
    const bytes = (await fs.stat(file)).size;
    const temporary = output + '.partial';
    try { await fs.copyFile(file, temporary); await fs.rename(temporary, output); }
    finally { await fs.rm(temporary, { force: true }); }
    const report = {
      product: lock.product, version: lock.version, target, builtAt: new Date().toISOString(),
      filename, bytes, sha256, signing: 'unsigned',
      apps: inputs.map(app => ({ id: app.id, product: app.product, version: app.version, sourceCommit: app.sourceCommit, input: app.asset })),
      checks: { inputSHA256: true, ...checks, nativeInstall: false },
    };
    await fs.writeFile(path.join(release, `BUILD-${target}.json`), JSON.stringify(report, null, 2) + '\n');
    await fs.writeFile(path.join(release, `${filename}.sha256`), `${sha256}  ${filename}\n`);
    for (const [name, contents] of Object.entries(evidence)) await fs.writeFile(path.join(release, name), contents);
    console.log(JSON.stringify({ output, bytes, sha256, checks: report.checks }, null, 2));
  } finally { await fs.rm(work, { recursive: true, force: true }); }
}
main().catch(error => { console.error(error.stack); process.exitCode = 1; });
