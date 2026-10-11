import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const root=new URL('../app/src/main/',import.meta.url);
const activity=readFileSync(new URL('java/id/nadmo/live/MainActivity.java',root),'utf8');
const fallback=readFileSync(new URL('assets/index.html',root),'utf8');
const between=(start,end)=>{
 const from=activity.indexOf(start),to=activity.indexOf(end,from);
 assert.ok(from>=0&&to>from,'Missing WebView callback: '+start);
 return activity.slice(from,to);
};

// Regression: v0.10.9 substituted an old local document at the production URL.
// Checking cache mode alone cannot catch that substitution.
assert.match(activity,/ONLINE_URL="https:\/\/"\+LIVE_HOST\+"\/app\/"/);
assert.match(activity,/web\.loadUrl\(ONLINE_URL\)/);
const interception=between('shouldInterceptRequest(WebView v,WebResourceRequest req)',
 '@Override public void onReceivedError(');
assert.match(activity,/new WebViewAssetLoader\.Builder\(\)[\s\S]*?\.addPathHandler\("\/assets\/"/);
assert.match(interception,/return loader\.shouldInterceptRequest\(req\.getUrl\(\)\)/);
assert.doesNotMatch(interception,/getAssets\(|WebResourceResponse\(|LIVE_HOST|FALLBACK_URL/,
 'Production HTML must reach the network, not an APK asset response');
assert.doesNotMatch(activity,/getAssets\(\)\.open\("game-live-qa\.html"\)/);

// A slow network navigation must replay native GAME state after JS is ready.
const finished=between('onPageFinished(WebView view,String url)',
 '@Override public void onReceivedError(');
assert.match(finished,/trusted\(Uri\.parse\(url\)\).*requestGameStatus\(\)/);
assert.match(activity,/WebViewCompat\.addWebMessageListener\(web,"NadmoGame"/);
assert.match(activity,/if\(!isMainFrame\|\|!trusted\(origin\)/);

// Offline fallback remains local; a failed image/API subrequest must not replace
// the current online document or stop a room merely because that resource failed.
assert.match(activity,/FALLBACK_URL="https:\/\/appassets\.androidplatform\.net\/assets\/index\.html"/);
const networkError=between('onReceivedError(WebView view,WebResourceRequest req,',
 '@Override public void onReceivedHttpError(');
const httpError=between('onReceivedHttpError(WebView view,WebResourceRequest req,',
 '@Override public boolean shouldOverrideUrlLoading(');
for(const callback of [networkError,httpError]){
 assert.match(callback,/req\.isForMainFrame\(\) && LIVE_HOST\.equalsIgnoreCase\(req\.getUrl\(\)\.getHost\(\)\)/);
 assert.match(callback,/view\.loadUrl\(FALLBACK_URL\)/);
}
assert.match(httpError,/response\.getStatusCode\(\)>=500/);
assert.ok(fallback.includes('id="offgridEntry"')&&fallback.includes('id="offgridLaunch"'),
 'Offline OFFGRID entry must remain available in the local document');
console.log('PASS WebView document source contract: online deployed HTML, local outage fallback, trusted GAME replay');
