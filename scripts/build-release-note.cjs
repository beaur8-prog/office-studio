'use strict';
const fs=require('node:fs/promises');
const path=require('node:path');
const {root,readLock}=require('./common.cjs');
async function main(){
  const lock=await readLock();
  const previous=JSON.parse(await fs.readFile(path.join(root,'previous-apps-lock.json'),'utf8'));
  const word=lock.apps.find(app=>app.id==='word'),previousWord=previous.apps.find(app=>app.id==='word');
  const build=JSON.parse(await fs.readFile(path.join(root,'release',lock.version,'BUILD-win-x64.json'),'utf8'));
  const base=`https://github.com/beaur8-prog/office-studio/releases/download/v${lock.version}`;
  const date=new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',year:'numeric',month:'long',day:'numeric'}).format(new Date());
  const rows=lock.apps.map(app=>`| ${app.product} | ${app.version} | ${ {sheet:'표·수식·차트·XLSX/CSV',slide:'슬라이드 편집·발표·PPTX',word:'문서 작성·DOCX·PDF·HWP/HWPX'}[app.id]} |`).join('\n');
  const body=`> **자동 업데이트 안내 — Word ${previousWord.version}에서 ${word.version}로 업데이트할 수 있습니다**
>
> Word ${previousWord.version} 사용자는 **도움말 → 업데이트 확인…** 또는 앱의 업데이트 화면에서 ${word.version}를 내려받은 뒤 **‘종료하고 업데이트’**를 선택하세요. 문서를 저장하고 설치 화면의 안내에 따라 진행하면 새 버전으로 다시 실행됩니다. 이번 Word 배포는 설치 때 언어 선택창이 반복해서 나타나지 않도록 개선했습니다.
>
> 업데이트 기능이 없거나 **버튼을 눌러도 앱만 닫히는 이전 버전**은 문서를 저장하고 세 앱을 모두 종료한 뒤 아래의 통합 설치 파일을 한 번 직접 설치해 주세요. 버전이 1.0으로 표시되는 앱도 여기에 해당합니다. 자동 확인·다운로드는 앱별로 끌 수 있으며, 설치를 나중으로 미룰 수도 있습니다.

> **Windows 설치 안내 — ‘실행 안 함’ 버튼만 보일 때**
>
> **‘Windows의 PC 보호’ 화면에서 ‘실행 안 함’만 보이면, 창 안의 ‘추가 정보’를 누른 다음 새로 나타나는 ‘실행’을 선택하세요.** 영문 Windows에서는 **More info → Run anyway**입니다.
>
> 아래 GitHub 배포 주소에서 받은 **${build.filename}**인지 먼저 확인하세요. 현재 설치 파일은 배포용 코드 서명이 없어 경고가 나타날 수 있습니다. 브라우저의 ‘일반적으로 다운로드되지 않습니다’ 경고는 출처·파일명·아래 SHA-256을 확인한 뒤 해당 파일의 **유지/계속 다운로드**를 선택합니다.
>
> **‘실행’ 버튼이 없거나 회사·기관 정책으로 차단되면 관리자에게 설치 허용 여부를 확인하세요. 악성코드 탐지 경고가 나타나면 실행을 중단하세요.** [Microsoft의 SmartScreen 안내](https://learn.microsoft.com/windows/apps/package-and-deploy/smartscreen-reputation)

# 오피스 스튜디오 Windows 다운로드 및 사용 안내

**Office Studio ${lock.version} · ${date} · Windows 통합 설치·앱별 자동 업데이트**

Windows에서 표, 발표 자료, 문서를 만드는 세 가지 독립 앱을 한 번에 설치합니다. 일상적인 오피스 작업을 위한 대안으로, **Sheet Studio·Slide Studio·Word Studio**를 함께 제공합니다. 설치 후에는 시작 메뉴에서 필요한 앱을 각각 실행합니다.

이번 Windows 배포는 **Word ${word.version}의 설치 언어 자동 선택 개선**을 포함합니다. Sheet Studio와 Slide Studio는 이전 통합본의 버전을 유지합니다. 복잡한 문서는 변환·저장 후 원본과 비교해 확인하세요.

## 1. Windows 통합 다운로드

### [오피스 스튜디오 Windows 설치 파일 다운로드](${base}/${build.filename})

**Windows 10/11 · Intel/AMD 64비트(x64) · ${(build.bytes/1024/1024).toFixed(1)} MiB**

파일명: **${build.filename}**

한 파일에 세 앱의 실행 환경이 포함되어 있습니다. 설치와 기본 편집에는 Microsoft Office·Node.js·인터넷 연결이 필요하지 않습니다. 자동 업데이트 확인과 다운로드에는 인터넷 연결이 필요합니다. Windows 32비트와 Windows ARM 전용 설치 파일은 이번 통합 배포에 포함하지 않습니다.

| 포함 앱 | 버전 | 주로 하는 일 |
| --- | --- | --- |
${rows}

[SHA-256 확인 파일](${base}/${build.filename}.sha256) · [배포 구성·검증 기록](${base}/BUILD-win-x64.json) · [GitHub 릴리스](https://github.com/beaur8-prog/office-studio/releases/tag/v${lock.version})

## 2. Windows 설치와 실행

1. 작업 중인 문서를 파일로 저장하고 **Sheet Studio·Slide Studio·Word Studio를 모두 종료**합니다.
2. 위 설치 파일을 내려받아 실행합니다. 보안 경고가 나타나면 이 문서 맨 위 안내를 확인합니다.
3. 설치 마법사에서 필요한 앱을 선택합니다. **기본값은 세 앱 모두 설치**이며, 필요한 앱만 선택할 수도 있습니다.
4. 설치가 끝나면 시작 메뉴에서 **Sheet Studio**, **Slide Studio**, **Word Studio**를 실행합니다. 바로가기와 제거 항목도 앱별로 만들어집니다.

현재 Windows 사용자 계정에 설치하며 관리자 권한을 요구하지 않습니다. 통합 설치 파일은 각 앱의 설치 프로그램을 순서대로 실행합니다. 기존 앱이 실행 중이면 저장·종료 후 다시 진행하도록 안내합니다.

설치 중 한 앱에서 오류가 나면 앞서 설치를 마친 앱은 유지됩니다. 세 앱을 종료하고 디스크 여유 공간을 확인한 뒤 설치 파일을 다시 실행하세요.

## 3. 처음 사용하기

### Sheet Studio — 표와 계산

셀을 클릭해 글자와 숫자를 입력하고, 수식은 **=**로 시작합니다. 범위를 선택해 서식·정렬·필터를 적용하고 차트나 피벗테이블을 만들 수 있습니다. XLSX·CSV를 열고 저장하며, 중요한 원본은 보관하고 사본으로 작업하세요.

[파일 형식 지원 범위](https://github.com/beaur8-prog/sheet-studio/blob/v${lock.apps[0].version}/IO-NOTES.md)

### Slide Studio — 슬라이드와 발표

첫 제목 슬라이드에서 글자를 더블클릭해 입력하고 **새 슬라이드**로 장을 추가합니다. 텍스트·도형·이미지·표·차트를 넣고 **PPTX 저장**으로 파일을 만듭니다. 발표자 노트를 작성하고 발표하거나 PDF로 내보낼 수 있습니다.

### Word Studio — 문서와 PDF

본문을 클릭해 내용을 입력하고 상단 도구에서 글꼴·문단·표·그림을 편집합니다. **Ctrl+S**로 문서를 저장하며 DOCX·PDF 등 필요한 형식으로 내보낼 수 있습니다. HWP/HWPX 파일도 열어 작업할 수 있으며, 변환 결과는 원본과 비교해 확인하세요. 인쇄 전에는 인쇄 미리보기에서 페이지와 줄바꿈을 확인합니다.

## 4. 자동 업데이트

이번 통합본에 포함된 세 Windows 앱은 **새 정식 버전을 자동 확인하고 백그라운드에서 내려받습니다.** 다운로드가 준비되면 업데이트 화면이나 도움말 메뉴에서 **종료하고 업데이트**를 선택해 설치합니다. 설치 도우미가 시작되었는지 확인한 뒤 기존 문서 저장·종료 절차를 거치며, 문서 닫기를 취소하면 업데이트도 취소됩니다. 설치 화면이 표시되면 안내에 따라 진행하세요. 설치가 끝나면 앱을 다시 엽니다.

- **직접 확인:** 각 앱의 **도움말 → 업데이트 확인…**을 선택합니다. 메뉴가 숨겨져 있으면 **Alt** 키를 눌러 표시합니다.
- **자동 확인 설정:** **도움말 → 업데이트 자동 확인·다운로드**에서 켜거나 끕니다. 설정은 앱별로 기억합니다.
- **업데이트 단위:** Sheet·Slide·Word가 각각 자신의 새 버전을 받습니다. 업데이트 후에는 세 앱의 버전이 이 통합본의 구성표와 달라질 수 있습니다.
- **인터넷 연결이 없을 때:** 문서 편집을 계속할 수 있습니다. 연결된 뒤 다시 업데이트를 확인하세요.
- **이전 버전 사용자:** 자동 업데이트 기능이 없거나 앱만 종료되는 문제가 있는 기존 버전은 이번 통합본을 한 번 직접 설치해 주세요. 버전 표시가 1.0인 앱도 같은 방법으로 업데이트하세요.

다운로드한 업데이트 파일은 크기와 SHA-256을 확인한 뒤 사용합니다. 보안 정책 때문에 설치가 차단되거나 설치에 실패하면 문서를 저장하고 앱을 종료한 뒤 해당 앱의 정식 설치 파일로 다시 설치할 수 있습니다.

## 5. 문서 보관과 제거

**Windows 설정 → 앱 → 설치된 앱**에서 제거할 앱을 선택합니다. Windows 10에서는 **앱 및 기능**을 사용합니다. Sheet·Slide·Word를 각각 제거하며, 저장한 문서와 앱별 사용자 데이터는 보존합니다.

자동 복구 자료와 별개로 중요한 문서는 **직접 파일로 저장하고 백업**하세요. 설치·업데이트 전에 열려 있는 문서를 저장하는 것을 권장합니다.

## 6. 호환 범위와 문제 해결

일상적인 표·문서·발표 작업을 지원하지만 Microsoft Office의 모든 기능과 원본 서식을 완전히 재현하지는 않습니다. 복잡한 파일은 사본으로 열고 저장한 결과를 다시 확인하세요. 글꼴 차이로 줄바꿈과 배치가 달라질 수 있으므로 모양을 고정해 전달할 때는 PDF를 사용하세요.

- **Sheet:** Power Query·전체 Excel 기능·독립 VBA 실행은 제공하지 않습니다. 기존 XLSM/VBA 연결 작업에는 Windows에 설치된 Excel이 필요합니다.
- **Slide:** 복잡한 PPTX 기능과 원본의 모든 효과·서식은 차이가 날 수 있습니다. 중요한 발표는 사용할 PC에서 미리 열고 발표해 보세요.
- **Word:** 복잡한 필드·변경 내용 추적·매크로 등은 제한이 있습니다. DOCX·HWP/HWPX 변환 결과와 최종 인쇄 배치를 확인하세요.
- **설치가 중단됨:** 세 앱의 모든 창을 종료하고 저장 공간을 확인한 뒤 설치 파일을 다시 실행합니다.
- **업데이트가 실패함:** 인터넷 연결과 설치 정책을 확인한 뒤 도움말에서 다시 확인합니다. 저장한 문서를 보존한 채 정식 설치 파일로 재설치할 수 있습니다.

문제가 계속되면 운영체제, 앱 이름·버전, 오류 메시지와 재현 순서를 [Office Studio 이슈](https://github.com/beaur8-prog/office-studio/issues)에 남겨 주세요. 개인 정보가 담긴 문서는 공유하지 말고 민감한 내용을 제거한 예제를 사용하세요.

## 7. 다운로드 파일 확인

PowerShell에서 내려받은 파일이 있는 폴더로 이동한 뒤 실행합니다.

~~~powershell
Get-FileHash .\\${build.filename} -Algorithm SHA256
~~~

아래 값과 일치하는지 확인하세요.

~~~text
${build.sha256}
~~~

체크섬은 다운로드 중 손상이나 파일 변경 여부를 확인하는 값입니다. 다운로드 주소도 함께 확인하세요.

## 8. 이번 배포의 확인 결과

세 앱 설치 파일과 통합 설치 파일의 SHA-256을 확인했습니다. 격리된 Windows 환경에서 **통합 설치·재설치·앱별 제거·문서 및 사용자 데이터 보존**을 검사하고, **이전 앱 버전에서 업데이트 도우미로 새 버전 설치·실행·정상 종료**를 확인했습니다. 자동 업데이트의 다운로드 검증과 문서 닫기 취소 처리도 시험했습니다. 한글·공백·작은따옴표가 포함된 업데이트 경로도 확인했습니다.

[Windows 배포 검증 기록](${build.nativeCI?.url || 'https://github.com/beaur8-prog/office-studio/actions/workflows/windows-install.yml'})

## 공유 페이지 이용 안내

이 문서는 리토스의 읽기 전용 공유 페이지입니다. 별도 로그인 없이 볼 수 있습니다. 공유 호스트의 실행·네트워크 상태에 따라 연결이 일시적으로 중단될 수 있으며, 설치 파일은 위 GitHub 릴리스에서도 받을 수 있습니다.
`;
  await fs.mkdir(path.join(root,'publication'),{recursive:true});
  await fs.writeFile(path.join(root,'publication/downloads-ready.md'),body);
  console.log('Prepared Office Studio publication:',build.filename,build.sha256);
}
main().catch(error=>{console.error(error.stack);process.exitCode=1;});
