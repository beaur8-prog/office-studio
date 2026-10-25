'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {root,readLock}=require('./common.cjs');
async function main(){
  const windows=JSON.parse(await fs.readFile(path.join(root,'suite-lock.json'),'utf8'));
  const lock=await readLock(),release=path.join(root,'release/mac',lock.version);
  const build=JSON.parse(await fs.readFile(path.join(release,'BUILD-mac-arm64.json'),'utf8'));
  const native=JSON.parse(await fs.readFile(path.join(root,'test-results/mac/native-verification.json'),'utf8'));
  if(!native.passed||native.setupSHA256!==build.sha256||native.apps.length!==3)throw Error('Mac native verification is incomplete');
  const directory=path.join(root,'publication/mac');await fs.mkdir(directory,{recursive:true});
  const base=`https://github.com/beaur8-prog/office-studio/releases/download/${lock.tag}`;
  const url=base+'/'+build.filename;
  const date=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Seoul'}).format(new Date());
  const tick=String.fromCharCode(96);
  const contents=`> **Mac 설치 안내 — 보안 경고가 표시될 때**
>
> 아래 공식 GitHub 링크에서 받은 **${build.filename}**인지 확인하세요. 현재 배포본은 Apple Developer ID 서명·공증을 받지 않았으므로 개발자 확인 경고가 나타날 수 있습니다. 파일을 한 번 열어 차단된 뒤 **시스템 설정 → 개인정보 보호 및 보안 → 그래도 열기 → 열기**를 선택합니다. 구버전 macOS에서는 **시스템 환경설정 → 보안 및 개인정보 보호**에서 해당 파일의 열기 허용 항목을 확인하세요. 설치 후 앱을 처음 실행할 때도 앱별로 같은 확인이 필요할 수 있습니다.
>
> 파일 출처와 맨 아래 SHA-256을 확인한 경우에만 허용하세요. 악성 소프트웨어 감지·손상 경고가 뜨면 강제로 실행하지 말고 파일을 다시 내려받아 체크섬을 확인하세요. 조직에서 관리하는 Mac은 관리자 정책을 따라야 합니다. [Apple 공식 보안 경고 안내](https://support.apple.com/ko-kr/102445)

# 오피스 스튜디오 Mac 다운로드

**Office Studio ${lock.version} · ${date} · Apple Silicon 전용 통합 설치**

Sheet Studio·Slide Studio·Word Studio를 한 설치 파일로 설치합니다. 설치 화면의 **사용자화**에서 필요한 앱을 고를 수 있으며, 기본값은 세 앱 모두입니다.

## 1. 다운로드

### [오피스 스튜디오 Mac 설치 파일 다운로드](${url})

- **대상:** Apple Silicon(M1 이상) Mac · macOS 12 Monterey 이상
- **파일:** ${tick}${build.filename}${tick}
- **크기:** ${(build.bytes/1024/1024).toFixed(1)} MiB (${build.bytes.toLocaleString('en-US')}바이트)
- **설치 위치:** Finder의 **응용 프로그램** 폴더(${tick}/Applications${tick})
- **권한:** 관리자 인증 필요

Intel Mac에서는 실행할 수 없습니다. Microsoft Office·Node.js를 따로 설치할 필요가 없고, 설치와 기본 편집은 오프라인에서도 가능합니다. 업데이트 확인과 다운로드에는 인터넷 연결이 필요합니다.

[SHA-256 확인 파일](${url}.sha256) · [배포 구성·검증 기록](${base}/BUILD-mac-arm64.json) · [Mac 실행 검증 기록](${base}/MAC-VERIFICATION.json) · [GitHub Mac 릴리스](https://github.com/beaur8-prog/office-studio/releases/tag/${lock.tag})

## 2. 포함 앱과 개별 다운로드

| 앱 | 포함 버전 | 개별 다운로드 |
| --- | --- | --- |
${lock.apps.map(a=>`| ${a.product} | ${a.version} | [Mac ZIP](${a.asset.url}) |`).join('\n')}

통합 설치 파일은 세 앱을 응용 프로그램 폴더에 설치합니다. 개별 ZIP을 사용할 때는 압축을 풀고 원하는 앱을 응용 프로그램 폴더로 옮기세요.

## 3. 설치와 첫 실행

1. 중요한 문서를 저장하고 **세 Studio 앱을 모두 종료**합니다.
2. 다운로드한 PKG 파일을 엽니다. 개발자 경고가 나타나면 맨 위 안내를 따릅니다.
3. 설치 화면에서 계속을 누릅니다. 필요한 앱만 설치하려면 **사용자화**에서 선택합니다.
4. 관리자 인증 후 설치를 완료합니다. 실행 중인 앱이나 더 최신 버전의 기존 앱이 있으면 설치가 중단됩니다.
5. Finder의 **응용 프로그램**에서 Sheet Studio·Slide Studio·Word Studio를 엽니다. 앱 첫 실행 경고도 맨 위 안내에 따라 확인합니다.

이전에 다른 폴더에 설치한 복사본이 있다면 Finder에서 새 앱의 버전을 확인하세요. Dock이 예전 복사본을 가리키면 새 앱으로 다시 등록합니다.

## 4. 기본 사용

- **Sheet Studio:** 표·수식·차트 편집, XLSX·CSV 등의 열기와 저장.
- **Slide Studio:** 슬라이드 편집·발표, PPTX 열기와 저장. 이번 Mac 버전에는 네이티브 차트 가져오기·표시·저장 보존과 최근 표 표시 수정, Finder 파일 열기 수정을 포함합니다. 가져온 차트 데이터의 직접 편집에는 제한이 있습니다.
- **Word Studio:** 문서·표·서식 편집, DOCX 열기와 저장, HWP·HWPX 변환 지원.

각 앱의 파일 메뉴에서 문서를 열거나 저장합니다. 중요한 원본은 별도로 보관하고 다른 이름으로 저장해 확인하세요. Microsoft Office와 완전히 같은 기능·레이아웃을 보장하지 않으며, 복잡한 문서·매크로·외부 연결 등에는 제한이 있을 수 있습니다.

## 5. Mac 업데이트

이번 Mac 앱은 실행 15초 뒤와 이후 6시간마다 **해당 앱의 Mac 정식 릴리스**를 확인하고 새 ZIP 파일을 자동 다운로드합니다. Windows 릴리스와 별도로 확인합니다.

Mac 배포 인증서가 없으므로 앱을 자동 교체하지는 않습니다. 다운로드 준비 알림 또는 **도움말 → 업데이트 확인…**에서 **다운로드 파일 보기**를 선택한 뒤, 문서를 저장하고 앱을 종료하고 ZIP을 풀어 기존 앱을 교체하세요. 통합 PKG의 새 버전을 다시 설치하는 방법도 사용할 수 있습니다.

- **업데이트 끄기:** 각 앱의 **도움말 → 업데이트 자동 확인·다운로드** 체크를 해제합니다. 앱별로 기억합니다.
- **나중에 설치:** 새 버전이 준비되어도 직접 교체하기 전까지 기존 버전을 계속 사용할 수 있습니다.
- **이전 Mac 버전 사용자:** 업데이트 기능이 없는 기존 앱은 이번 배포본을 한 번 직접 설치해야 합니다.
- **파일 확인:** 업데이트는 파일 크기와 SHA-256을 검사한 뒤 제공합니다.

## 6. 문서 보존과 제거

설치 패키지는 앱 파일만 설치하며 저장한 문서와 사용자 설정 폴더를 설치 대상으로 포함하지 않습니다. 설치·교체 전에 문서를 저장하고 중요한 파일은 별도 백업하세요.

앱을 제거하려면 종료한 뒤 응용 프로그램 폴더의 해당 앱을 휴지통으로 이동합니다. 문서 파일과 사용자 설정·자동 복구 자료는 별도로 남을 수 있습니다.

## 7. 문제가 생기면

- **개발자 확인 경고:** 맨 위 설치 안내와 Apple 공식 안내를 확인합니다.
- **설치 중단:** 세 앱이 모두 종료됐는지 확인합니다. 이미 더 최신 앱이 있다면 해당 버전을 유지하세요.
- **앱이 실행되지 않음:** Apple Silicon Mac인지, macOS 12 이상인지, 응용 프로그램 폴더의 새 복사본을 열었는지 확인합니다.
- **업데이트 확인 실패:** 인터넷 연결을 확인한 뒤 도움말에서 다시 확인하거나 이 페이지의 개별 ZIP을 사용합니다.

## 8. 설치 파일 확인

다운로드한 파일의 SHA-256:

${tick.repeat(3)}text
${build.sha256}
${tick.repeat(3)}

터미널에서 다음 명령으로 확인할 수 있습니다. 다운로드 폴더나 파일명을 바꿨다면 경로도 맞춰주세요.

${tick.repeat(3)}sh
shasum -a 256 ~/Downloads/${build.filename}
${tick.repeat(3)}

표시된 값이 위 값과 정확히 같아야 합니다.

## 9. 이번 배포의 검증 범위

원본 ZIP과 통합 PKG의 체크섬, 포함 앱의 버전·Apple Silicon 실행 파일·앱 서명 무결성, 설치 앱 선택 항목을 확인했습니다. 최종 PKG에서 추출한 세 앱을 각각 별도 시험 프로필로 실행해 편집 화면·업데이트 메뉴·설정 저장·정상 종료를 검사하고, 포함 편집 코드가 최신 빌드와 같은지도 대조했습니다. 관리자 권한으로 기존 앱을 덮어쓰는 실제 시스템 설치와 Apple 공증은 이번 검증에 포함되지 않습니다.

이 문서는 리토스의 읽기 전용 공유 페이지입니다. 공유 호스트의 실행·네트워크 상태에 따라 연결이 중단될 수 있으며 설치 파일은 GitHub Mac 릴리스에서도 받을 수 있습니다.
`;
  await fs.writeFile(path.join(directory,'downloads-ready.md'),contents);
  const notes=`Office Studio ${lock.version} Mac 배포입니다. Apple Silicon(M1 이상) · macOS 12 이상에서 사용합니다.\n\n${lock.apps.map(a=>'- '+a.product+' '+a.version).join('\n')}\n\n한 PKG에서 설치할 앱을 선택할 수 있습니다. Developer ID 서명·공증은 없으며 시스템 설정의 앱별 열기 허용이 필요할 수 있습니다. 앱 업데이트는 Mac 릴리스를 자동 확인·다운로드한 뒤 ZIP을 직접 교체하는 방식입니다.\n\n최종 PKG의 앱 버전·서명 무결성·편집 코드 일치와 세 앱의 실제 시작·종료·업데이트 설정을 검증했습니다. 관리자 권한을 사용한 시스템 설치 시험은 수행하지 않았습니다. 자세한 범위는 BUILD-mac-arm64.json과 MAC-VERIFICATION.json을 확인하세요.\n\nWindows 배포는 별도 v${windows.version} 릴리스를 사용합니다.\n`;
  await fs.writeFile(path.join(directory,'release-notes.md'),notes);
  for(const app of lock.apps)await fs.writeFile(path.join(directory,app.id+'-release-notes.md'),`${app.product} ${app.version} · Mac Apple Silicon 배포\n\n- Apple Silicon(M1 이상), macOS 12 이상.\n- Mac 릴리스만 확인해 새 버전을 자동 다운로드하고, 문서 저장·종료 후 ZIP을 직접 교체하도록 안내합니다.\n- 도움말에서 자동 확인·다운로드를 끌 수 있습니다.\n- 앱은 ad-hoc 서명 상태이며 Apple Developer ID 서명·공증은 없습니다.\n${app.id==='slide'?'- 최근 표 행 높이와 테두리 표시 수정을 포함합니다.\n':''}\n이 버전의 Windows x64 파일과 Mac Apple Silicon 파일은 같은 앱 릴리스에서 제공합니다.\n`);
  console.log('Generated Mac distribution page and release notes');
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;});
