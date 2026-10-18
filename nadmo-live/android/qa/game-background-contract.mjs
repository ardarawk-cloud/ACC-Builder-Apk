import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const root=new URL('../app/src/main/',import.meta.url);
const manifest=readFileSync(new URL('AndroidManifest.xml',root),'utf8');
const main=readFileSync(new URL('java/id/nadmo/live/MainActivity.java',root),'utf8');
const service=readFileSync(new URL('java/id/nadmo/live/GameCaptureService.java',root),'utf8');
assert.match(manifest,/<service[^>]*android:name="\.GameCaptureService"[^>]*android:process=":game"[^>]*android:stopWithTask="false"/,'Broadcast should live outside resource-heavy WebView process');
assert.match(manifest,/android:foregroundServiceType="mediaProjection\|microphone\|camera"/);
assert.match(service,/static final String ACTION_QUERY=/);
assert.match(service,/if\(ACTION_QUERY\.equals\(intent\.getAction\(\)\)\)/);
assert.match(service,/sendBroadcast\(event\)/);
// Real regression: STARTING broadcasts previously triggered ACTION_PAUSE,
// which rebroadcast STARTING across :game and Activity without end.
assert.match(main,/if\(!"radio"\.equals\(gameBroadcastKind\)&&LiveStatusPolicy\.shouldEnforceForegroundPrivacy\(activityVisible,next\)\)/,
  'Native Activity uses tested foreground privacy policy instead of raw status branching');
const policy=readFileSync(new URL('java/id/nadmo/live/LiveStatusPolicy.java',root),'utf8');
assert.match(policy,/return activityVisible && "live"\.equals\(status\);/,'Only live stream can generate native foreground PAUSE');
assert.doesNotMatch(main,/activityVisible\s*&&\s*\("live"\.equals\(next\)\|\|"reconnecting"/,
  'Never feed RECONNECTING back into PAUSE -> RECONNECTING loop');
assert.doesNotMatch(main,/activityVisible\s*&&\s*\("live"\.equals\(next\)\|\|"starting"/,
  'Never feed STARTING back into PAUSE -> STARTING loop');
assert.match(service,/if\(privacyPaused==paused\)\{updateGameFloatingControl\(\);return;\}/,
  'Repeated Android PAUSE must be idempotent, without sending more status broadcasts');
assert.match(main,/private String gameLiveState="stopped"/);
assert.match(main,/requestGameStatus\(\)/);
assert.match(main,/@Override protected void onResume\(\)/);
assert.doesNotMatch(main,/GameCaptureService\.(state|activeRoom|status)/,'Across isolated processes, static fields cannot reflect foreground service state');
assert.match(main,/MediaProjectionConfig\.createConfigForDefaultDisplay\(\)/);
assert.match(service,/firstFrameReceived\.compareAndSet\(false,true\)/);
assert.match(service,/cpuWakeLock\.acquire\(180000\)/);
assert.match(service,/\.pingInterval\(20,TimeUnit\.SECONDS\)/);
assert.match(manifest,/android.permission.ACCESS_NETWORK_STATE/);
assert.match(service,/registerDefaultNetworkCallback\(networkCallback\)/);
assert.match(service,/disconnectStaleSocket\("Server tidak mengirim heartbeat"\)/,'Reconnect after confirmed heartbeat failure');
assert.doesNotMatch(service,/if\(socket!=null\)disconnectStaleSocket\(diagnostic\)/,'Network handover must never kill healthy GAME socket');
assert.match(service,/DIAG_ARMED/);
assert.match(service,/Layanan GAME|SERVICE_INTERRUPTED/);
assert.match(service,/getHistoricalProcessExitReasons\(getPackageName\(\),0,12\)/,'Must inspect actual Android service-process exit, not guess from flag');
assert.match(service,/REASON_LOW_MEMORY/,'Report low memory distinctly');
assert.match(service,/REASON_CRASH_NATIVE/,'Report WebRTC/native crash distinctly');
assert.match(service,/REASON_CRASH/,'Report Java crash distinctly');
assert.match(service,/REASON_ANR/,'Report nonresponsive service distinctly');
assert.match(service,/capture_started_at/,'Do not conflate old stale process-exit events with a new GAME session');
assert.doesNotMatch(service,/SERVICE_INTERRUPTED: proses siaran GAME berhenti mendadak saat aplikasi lain dibuka/,'Do not claim unverified app switch caused a process death');
assert.match(service,/rememberFailure\("PROJECTION_STOP/);
assert.match(service,/WS_CLOSE_/);
assert.match(service,/diagnosticPrefs\(\)\.edit\(\)\.putBoolean\(DIAG_ARMED,false\)\.commit\(\)/);
console.log('PASS GAME broadcast lifecycle: isolated FGS, projection and mic, IPC status, background wake lock and reconnect');
