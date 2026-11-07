import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
if(!process.env.FIRESTORE_EMULATOR_HOST||!process.env.FIREBASE_AUTH_EMULATOR_HOST)throw Error('Use demo Firebase emulators only.');
if(process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:8180'||process.env.FIREBASE_AUTH_EMULATOR_HOST!=='127.0.0.1:9199')
  throw Error('Use firebase.test.json so these tests stay on The Choir dedicated ports.');
const web=fileURLToPath(new URL('../../web/',import.meta.url));
const run=spawnSync(process.execPath,['node_modules/@playwright/test/cli.js','test'],{cwd:web,stdio:'inherit',env:process.env});
process.exitCode=run.status??1;
