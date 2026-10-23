'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { root, run, nsisQuote, verifyAsset } = require('./common.cjs');

function sections(inputs) {
  return inputs.map(app => [
    `Section ${nsisQuote(app.product + ' ' + app.version)} ${app.id}`,
    '  SetOutPath "$PLUGINSDIR"',
    `  File /oname=${app.id}-setup.exe ${nsisQuote(app.file)}`,
    `  StrCpy $CurrentApp ${nsisQuote(app.product)}`,
    '  Call CheckAppsClosed',
    '  ClearErrors',
    `  ExecWait '"$PLUGINSDIR\\${app.id}-setup.exe" /S${app.id === 'slide' ? '' : ' /currentuser'}' $0`,
    '  IfErrors execution_failed',
    '  ${If} $0 == 3010',
    '    SetRebootFlag true',
    '  ${ElseIf} $0 != 0',
    '    Goto installation_failed',
    '  ${EndIf}',
    `  Delete "$PLUGINSDIR\\${app.id}-setup.exe"`,
    '  Goto section_done',
    '  execution_failed:',
    '    StrCpy $0 2',
    '  installation_failed:',
    '    DetailPrint "$CurrentApp: error $0"',
    '    MessageBox MB_ICONSTOP|MB_OK "$(FailedText)" /SD IDOK',
    '    SetErrorLevel 2',
    '    Quit',
    '  section_done:',
    'SectionEnd',
  ].join('\n')).join('\n\n');
}
async function build({ lock, inputs, work }) {
  const compiler = process.env.MAKENSIS || 'makensis';
  const sevenZip = process.env.SEVENZIP || (process.platform === 'darwin' ? '7zz' : '7z');
  run(compiler, ['-VERSION'], true);
  run(sevenZip, ['i'], true);
  const file = path.join(work, 'Office-Studio.exe');
  const generated = path.join(work, 'apps.nsh');
  await fs.writeFile(generated, sections(inputs));
  run(compiler, ['-V3', '-WX', '-INPUTCHARSET', 'UTF8', '-DSUITE_VERSION=' + lock.version, '-DOUTPUT_FILE=' + file, '-DAPP_SECTIONS=' + generated, path.join(root, 'scripts/windows-installer.nsi')]);
  run(sevenZip, ['t', file]);
  const extracted = path.join(work, 'verified');
  run(sevenZip, ['x', '-y', '-o' + extracted, file]);
  // NSIS stores temporary embedded files under $PLUGINSDIR in its archive.
  for (const app of inputs) await verifyAsset(path.join(extracted, '$PLUGINSDIR', app.id + '-setup.exe'), app.asset);
  return { file, checks: { nsisCompileWarningsAsErrors: true, installerArchiveIntegrity: true, embeddedInstallersSHA256: true } };
}
module.exports = { build, sections };
