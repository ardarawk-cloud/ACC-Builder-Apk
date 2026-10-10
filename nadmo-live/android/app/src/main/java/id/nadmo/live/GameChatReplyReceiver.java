package id.nadmo.live;

import android.app.NotificationManager;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Explicit, non-exported notification reply target. No overlay permission needed. */
public final class GameChatReplyReceiver extends BroadcastReceiver {
  private static final String TAG="NadmoGameReply";
  static final String REPLY_KEY="nadmo_chat_reply";
  @Override public void onReceive(Context context,Intent intent){
    if(intent==null||!GameCaptureService.ACTION_CHAT_REPLY.equals(intent.getAction()))return;
    Bundle input=RemoteInput.getResultsFromIntent(intent);
    if(input==null)return;
    CharSequence raw=input.getCharSequence(REPLY_KEY);
    String text=raw==null?"":raw.toString().trim();
    if(text.length()>250)text=text.substring(0,250);
    if(text.isEmpty())return;
    Intent send=new Intent(context,GameCaptureService.class)
      .setAction(GameCaptureService.ACTION_CHAT_REPLY)
      .putExtra(GameCaptureService.EXTRA_REPLY_TEXT,text)
      .putExtra(GameCaptureService.EXTRA_REPLY_ROOM,intent.getStringExtra(GameCaptureService.EXTRA_REPLY_ROOM));
    try{
      context.startService(send);  // Existing foreground service; no new capture/session requested.
      NotificationManager mgr=context.getSystemService(NotificationManager.class);
      if(mgr!=null)mgr.cancel(GameCaptureService.CHAT_NOTIFICATION_ID);
    }catch(Exception error){
      Log.e(TAG,"Chat reply dispatch failed",error);
    }
  }
}
