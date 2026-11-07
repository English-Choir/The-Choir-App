import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

// Run inside `firebase emulators:exec --project demo-the-choir --only auth,firestore`.
if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST)
  throw new Error('Start the demo emulators before running this test.');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const sdk=readFileSync(path.join(root,'android/local.properties'),'utf8').match(/^sdk\.dir=(.+)$/m)?.[1].trim();
if (!sdk) throw new Error('Android SDK path missing');
const adb=path.join(sdk,'platform-tools',process.platform==='win32'?'adb.exe':'adb');
const device=process.env.CHOIR_TEST_DEVICE || 'emulator-5580';
if (!device.startsWith('emulator-')) throw new Error('Use a test emulator.');
const run=args=>execFileSync(adb,['-s',device,...args],{encoding:'utf8',timeout:180000});
console.log(run(['install','-r',path.join(root,'android/app/build/outputs/apk/qa/app-qa.apk')]));
console.log(run(['install','-r',path.join(root,'android/app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk')]));
const output=run(['shell','am','instrument','-w','-r','-e','firebaseEmulators','true',
  '-e','firestorePort',process.env.FIRESTORE_EMULATOR_HOST.split(':').at(-1),
  '-e','authPort',process.env.FIREBASE_AUTH_EMULATOR_HOST.split(':').at(-1),'-e','class',
  'com.phantominnovation.choir.FirebaseIntegrationTest','com.phantominnovation.choir.qa.test/androidx.test.runner.AndroidJUnitRunner']);
console.log(output);
if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED/.test(output)) process.exitCode=1;
