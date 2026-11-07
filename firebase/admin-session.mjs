import { createRequire } from 'node:module';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

export const projectId = 'the-choir-app-39f0a';
export const ownerEmail = 'englishchoir00@gmail.com';
const require = createRequire(import.meta.url);
const cliAuth = require('firebase-tools/lib/auth.js');
const account = cliAuth.getAllAccounts().find(account => account.user.email === ownerEmail);
if (!account?.tokens?.refresh_token) throw new Error(`Sign in to Firebase CLI as ${ownerEmail} first.`);
cliAuth.setActiveAccount({}, account);
const scopes = ['email','openid','https://www.googleapis.com/auth/cloudplatformprojects.readonly','https://www.googleapis.com/auth/firebase','https://www.googleapis.com/auth/cloud-platform'];
let token = await cliAuth.getAccessToken(account.tokens.refresh_token, scopes);
const identity = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {headers:{Authorization:`Bearer ${token.access_token}`}}).then(r=>r.json());
if (identity.email !== ownerEmail) throw new Error('Refusing to use a different Firebase account.');

export async function googleRequest(url, options={}) {
  token = await cliAuth.getAccessToken(account.tokens.refresh_token, scopes);
  const response = await fetch(url,{...options,headers:{Authorization:`Bearer ${token.access_token}`,'Content-Type':'application/json',...options.headers}});
  const result = await response.json();
  if(!response.ok) throw new Error(`${response.status}: ${result.error?.message || 'Google API request failed'}`);
  return result;
}
const app = initializeApp({projectId, credential:{async getAccessToken(){
  const value = await cliAuth.getAccessToken(account.tokens.refresh_token,scopes);
  return {access_token:value.access_token,expires_in:value.expires_in || 3600};
}}});
export const adminAuth = getAuth(app);
export const databasePath = `projects/${projectId}/databases/(default)`;
export const firestoreUrl = `https://firestore.googleapis.com/v1/${databasePath}`;
export function firestoreFields(data) {
  const value = item => {
    if(item === null) return {nullValue:null};
    if(item instanceof Date) return {timestampValue:item.toISOString()};
    if(typeof item === 'string') return {stringValue:item};
    if(typeof item === 'boolean') return {booleanValue:item};
    if(typeof item === 'number') return Number.isInteger(item) ? {integerValue:String(item)} : {doubleValue:item};
    if(Array.isArray(item)) return {arrayValue:{values:item.map(value)}};
    return {mapValue:{fields:firestoreFields(item)}};
  };
  return Object.fromEntries(Object.entries(data).map(([key,item])=>[key,value(item)]));
}
