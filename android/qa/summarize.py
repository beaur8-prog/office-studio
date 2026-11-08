import json,pathlib,sys,re,base64,datetime
out=pathlib.Path(sys.argv[1]);records=[]
for id in ['sheet','slide','word']:
 for name in [id+'-native.log',id+'-reinstall.log']:
  text=(out/name).read_text()
  assert 'OK (' in text and 'FAILURES!!!' not in text and 'INSTRUMENTATION_FAILED' not in text
  for match in re.finditer(r'OFFICE_RESULT (\{[^\n]+\})',text):
   j=json.loads(match[1]);assert j['passed'];records.append(j)
  for match in re.finditer(r'OFFICE_ICON (\w+) ([A-Za-z0-9+/=]+)',text):
   (out/(match[1]+'-launcher-icon.png')).write_bytes(base64.b64decode(match[2]))
 expected={'identity-icons-offline','saf-open-cancel-and-chunked-read','saf-save-cancel-confirmed-write-and-provider-failure','real-document-intent-and-recovery-storage','real-chooser-fileprovider-share','same-package-signed-reinstall-preserves-storage'}
 assert {j['scenario'] for j in records if j['app']==id}==expected,(id,records)
(out/'native-verification.json').write_text(json.dumps({'verifiedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'deviceProfile':out.name,'passed':True,'nativeScenarios':len(records),'results':records,'physicalDeviceTested':False},ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'profile':out.name,'nativeScenarios':len(records),'passed':True}))
