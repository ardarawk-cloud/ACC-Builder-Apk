import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
const root=path.resolve('nadmo-live/android/app/src/main/java/id/nadmo/live');
const activity=fs.readFileSync(path.join(root,'MainActivity.java'),'utf8');
const service=fs.readFileSync(path.join(root,'GameCaptureService.java'),'utf8');
assert.match(activity,/else if\("launch-music"\.equals\(action\)\)launchMusic\(\)/);
assert.match(activity,/Intent\.makeMainSelectorActivity\(Intent\.ACTION_MAIN,Intent\.CATEGORY_APP_MUSIC\)/);
assert.match(activity,/\.addCategory\(Intent\.CATEGORY_HOME\)/);
assert.match(activity,/if\(!"radio"\.equals\(gameBroadcastKind\)/);
assert.match(activity,/\.putExtra\(GameCaptureService\.EXTRA_BROADCAST_KIND,gameBroadcastKind\)/);
assert.match(activity,/JSONObject\.quote\(gameBroadcastKind\)/);
assert.match(service,/radioMode="radio"\.equals\(intent\.getStringExtra\(EXTRA_BROADCAST_KIND\)\)/);
assert.match(service,/if\(!privacyPaused&&!radioMode\)\{transport\.onFrameCaptured\(captured\);return;\}/);
assert.match(service,/VideoFrame safe=blackPrivacyFrame\(captured\)/);
const ownership=fs.readFileSync(path.join(root,'PrivacyFrameOwnership.java'),'utf8');
assert.match(service,/PrivacyFrameOwnership\.newRetainedFrame\(/,'Cached privacy frame must acquire its own buffer reference');
assert.match(ownership,/cached\.retain\(\)/,'Frame owns one reference, independent of cached buffer');
assert.match(ownership,/new VideoFrame\(cached,rotation,timestampNs\)/,'Retained reference transferred to new VideoFrame');
assert.match(service,/synchronized\(this\)\{if\(privacyBlackBuffer!=null\)\{privacyBlackBuffer\.release\(\)/,'Cached ref released on shutdown');
assert.match(service,/\.put\("category",radioMode\?"DJ & Music":"Gaming"\)/);
assert.match(service,/\.put\("broadcastKind",radioMode\?"radio":"gaming"\)/);
assert.match(service,/gameAudio\.start\(projection\)/);
assert.match(service,/gameAudio\.setPaused\(paused\)/);
assert.match(service,/EXTRA_PROJECTION/);
assert.match(service,/\.putExtra\("broadcastKind",radioMode\?"radio":"gaming"\)/);
assert.match(service,/radioMode=false;/);
// P0 regression: observed phones were stuck in MENYAMBUNG for >30 seconds
// with no room ACK. Capture video callback must NOT gate socket creation.
assert.match(service,/initCapture\(grant\);[\s\S]{0,550}connectWhenCaptureStarted\(\);/);
assert.match(service,/if\(started&&!stopping&&ws==socket&&roomId\.isEmpty\(\)&&webSocketOpen\)/);
assert.match(service,/stopGame\("ROOM_ACK_TIMEOUT"\)/);
assert.match(service,/liveCreatedAt=0L;lastRestReminderHour=0;/);
assert.match(service,/liveCreatedAt=serverStart>1600000000000L/);
assert.match(activity,/if\(activityVisible&&"starting"\.equals\(gameLiveState\)\)requestGameStatus\(\)/);
// Regression observed on Android when tapping BUKA GAME:
// opening a heavy game can delay the first frame or trigger a transient
// default-network callback. Neither event may disconnect an acknowledged room.
assert.match(service,/Default network changed; awaiting actual socket failure or heartbeat timeout/);
assert.doesNotMatch(service,/if\(socket!=null\)disconnectStaleSocket\(diagnostic\)/);
assert.match(service,/PROJECTION_FRAME_DELAY/);
assert.match(service,/menu\.setVisibility\(privacyPaused\?View\.VISIBLE:View\.GONE\)/,'Secure RESUME must be visible immediately after opening a game');
assert.doesNotMatch(service,/stopGame\("PROJECTION_NO_FRAME/);
assert.match(service,/if\(!roomId\.isEmpty\(\)\)updateNotification\("LIVE terhubung/);
assert.match(activity,/private boolean ensureGameFloatingControls\(\)/);
assert.match(activity,/Settings\.ACTION_MANAGE_OVERLAY_PERMISSION/);
assert.match(activity,/private void launchGame\(\)[\s\S]{0,400}if\(!ensureGameFloatingControls\(\)\)return;/);
assert.match(activity,/private void launchMusic\(\)[\s\S]{0,400}if\(!ensureGameFloatingControls\(\)\)return;/);
console.log('GAME app-switch nonfatal capture/network regression, RADIO and secure RESUME permission PASS');
