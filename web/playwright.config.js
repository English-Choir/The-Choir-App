import {defineConfig,devices} from '@playwright/test';
export default defineConfig({
  testDir:'./browser-tests',fullyParallel:false,workers:1,timeout:60000,
  use:{baseURL:'http://127.0.0.1:5173',trace:'retain-on-failure',screenshot:'only-on-failure'},
  projects:[
    {name:'desktop-chromium',use:{...devices['Desktop Chrome'],viewport:{width:1440,height:1050}}},
    {name:'iphone-webkit',use:{...devices['iPhone 13'],browserName:'webkit'}}
  ],
  reporter:[['list'],['json',{outputFile:'test-results/results.json'}]]
});
