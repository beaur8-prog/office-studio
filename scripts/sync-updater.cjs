'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { root } = require('./common.cjs');
async function main() {
  for (const id of ['sheet', 'slide', 'word']) {
    const destination = path.join(root, '..', id + '-studio', 'electron');
    for (const file of ['studio-updater.cjs', 'studio-update-windows.ps1']) await fs.copyFile(path.join(root, 'updater', file), path.join(destination, file));
    const testDirectory = path.join(root, '..', id + '-studio', 'tests', 'updates');
    await fs.mkdir(testDirectory, { recursive: true });
    const suiteTests = await fs.readFile(path.join(root, 'tests', 'updater.test.cjs'), 'utf8');
    const syncOnlyTest = suiteTests.indexOf("test('each app packages the exact shared updater implementation'");
    if (syncOnlyTest < 0) throw new Error('Missing suite-only test boundary');
    const appTests = suiteTests.slice(0, syncOnlyTest).replace("require('../updater/studio-updater.cjs')", "require('../../electron/studio-updater.cjs')");
    await fs.writeFile(path.join(testDirectory, 'studio-updater.cjs'), appTests);
    console.log('Synced updater to ' + id + '-studio');
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
