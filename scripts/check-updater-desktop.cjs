'use strict';
// Native startup/menu/close regression checks using disposable profiles only.
const fs = require('node:fs/promises');
const fsSync = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const assert = require('node:assert/strict');
const { root } = require('./common.cjs');
const workspace = path.dirname(root);
const { _electron, expect } = require(require.resolve('@playwright/test', { paths: [path.join(workspace, 'slide-studio')] }));

async function main() {
  const results = [];
  const binary = [workspace, path.join(workspace,'sheet-studio'), path.join(workspace,'slide-studio'), path.join(workspace,'word-studio')]
    .map(dir => path.join(dir,'node_modules/electron/dist', process.platform === 'darwin' ? 'Electron.app/Contents/MacOS/Electron' : process.platform === 'win32' ? 'electron.exe' : 'electron'))
    .find(file => fsSync.existsSync(file));
  if (!binary) throw new Error('Install a local Electron runtime first.');
  for (const id of ['sheet','slide','word']) {
    const profile = await fs.mkdtemp(path.join(os.tmpdir(), 'studio-updater-desktop-' + id + '-'));
    const env = { ...process.env, STUDIO_DISABLE_UPDATES:'1', SHEET_STUDIO_DATA:profile };
    delete env.ELECTRON_RUN_AS_NODE; delete env.SLIDE_STUDIO_DEV; delete env.WORD_STUDIO_DEV;
    const app = await _electron.launch({ executablePath:binary, args:[path.join(workspace,id+'-studio'),'--user-data-dir='+profile], env, timeout:30_000 });
    try {
      const page = await app.firstWindow();
      await page.locator({sheet:'.cell',slide:'.editable-slide',word:'.tiptap'}[id]).first().waitFor({timeout:30_000});
      const menu = await app.evaluate(({ Menu, dialog }) => {
        global.__updateDialogs = [];
        dialog.showMessageBox = async (...args) => { global.__updateDialogs.push(args.at(-1)); return {response:0}; };
        const help = Menu.getApplicationMenu().items.find(item => item.label === '도움말');
        if (!help) throw new Error('Update menu is missing');
        const items = help.submenu.items.map(item => ({label:item.label, checked:item.checked}));
        help.submenu.items.find(item => item.label === '업데이트 확인…').click();
        return items;
      });
      assert.ok(menu.some(item => item.label === '업데이트 자동 확인·다운로드' && item.checked));
      await expect.poll(() => app.evaluate(() => global.__updateDialogs.length)).toBe(1);
      assert.match(await app.evaluate(() => global.__updateDialogs[0].message),/설치된 배포 앱/);
      const result = { id, nativeStartup:true, updateMenu:true, manualCheckWithoutNetwork:true };
      if (id !== 'sheet') {
        const editor = id === 'word' ? page.locator('.tiptap') : page.getByRole('textbox',{name:'발표자 노트',exact:true});
        await editor.fill('UPDATER CLOSE GUARD — 보존할 시험 문서');
        await page.locator('body').click({position:{x:10,y:10}});
        await app.evaluate(({ BrowserWindow, dialog }) => {
          global.__closeChecks = 0;
          dialog.showMessageBoxSync = () => { global.__closeChecks++; return 0; };
          setTimeout(() => BrowserWindow.getAllWindows()[0].close(), 0);
        });
        await expect.poll(() => app.evaluate(() => global.__closeChecks)).toBe(1);
        if (id === 'slide') await expect(editor).toHaveValue('UPDATER CLOSE GUARD — 보존할 시험 문서');
        else await expect(editor).toHaveText('UPDATER CLOSE GUARD — 보존할 시험 문서');
        result.cancelCloseKeepsDocument = true;
      }
      results.push(result);
    } finally {
      await app.evaluate(({ dialog }) => { dialog.showMessageBoxSync = () => 1; }).catch(() => {});
      await app.close();
      await fs.rm(profile,{recursive:true,force:true});
    }
  }
  const destination = path.join(root,'test-results');
  await fs.mkdir(destination,{recursive:true});
  await fs.writeFile(path.join(destination,'updater-desktop.json'),JSON.stringify({platform:process.platform,results},null,2)+'\n');
  console.log(JSON.stringify(results,null,2));
}
main().catch(error=>{console.error(error);process.exitCode=1;});
