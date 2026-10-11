package id.nadmo.live;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;
import org.junit.Test;

/**
 * Real network regression test using EXACTLY the OkHttp client from the Android
 * broadcaster. No fake websocket, screen/video, or Android device is involved.
 * Create -> server heartbeats -> force disconnect -> resume -> leave.
 */
public final class OkHttpSignalingE2ETest {
  private static final String ORIGIN="https://nadmo-live-beta-20261009.ardarawk.workers.dev";
  private static final String WSS="wss://nadmo-live-beta-20261009.ardarawk.workers.dev/ws";
  private static class Session extends WebSocketListener {
    final LinkedBlockingQueue<String> events=new LinkedBlockingQueue<>();
    final CountDownLatch opened=new CountDownLatch(1);
    WebSocket socket;
    volatile String failure="";
    @Override public void onOpen(WebSocket ws,Response response){
      socket=ws;opened.countDown();
    }
    @Override public void onMessage(WebSocket ws,String body){events.offer(body);}
    @Override public void onFailure(WebSocket ws,Throwable error,Response response){
      failure="HTTP "+(response==null?0:response.code())+" / "+error;
      opened.countDown();
    }
    @Override public void onClosed(WebSocket ws,int code,String reason){
      failure="CLOSED "+code+" / "+reason;
    }
    JSONObject until(String type,long timeoutSeconds) throws Exception{
      final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeoutSeconds);
      while(System.nanoTime()<deadline){
        String raw=events.poll(Math.max(100,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())),TimeUnit.MILLISECONDS);
        if(raw==null)continue;
        JSONObject event=new JSONObject(raw);
        if(type.equals(event.optString("type")))return event;
        if("error".equals(event.optString("type")))throw new AssertionError("NADMO server rejected: "+event.optString("message"));
      }
      throw new AssertionError("Expected "+type+", got "+failure);
    }
  }
  private static Session connect(OkHttpClient client) throws Exception{
    Session listener=new Session();
    WebSocket ws=client.newWebSocket(new Request.Builder().url(WSS).header("Origin",ORIGIN).build(),listener);
    assertTrue("OkHttp WS did not open: "+listener.failure,listener.opened.await(20,TimeUnit.SECONDS));
    assertNotNull("OkHttp open failed: "+listener.failure,listener.socket);
    return listener;
  }
  @Test public void nativeOkHttpHoldsAndResumesHostRoom() throws Exception{
    OkHttpClient client=new OkHttpClient.Builder()
      .pingInterval(20,TimeUnit.SECONDS).connectTimeout(12,TimeUnit.SECONDS).build();
    Session a=null,b=null;
    try{
      a=connect(client);
      assertTrue(a.socket.send("{\"type\":\"create\",\"title\":\"GAME SIGNAL E2E\",\"category\":\"Gaming\",\"mode\":\"public\",\"hostName\":\"Android signaling QA\"}"));
      JSONObject created=a.until("created",20);
      String id=created.getString("id"),token=created.getString("resumeToken");
      assertTrue(id.length()>0);assertTrue(token.length()>10);
      // Cloudflare DO emits a heartbeat approximately every 25 seconds.
      JSONObject heartbeat=a.until("heartbeat",42);
      assertTrue(heartbeat.has("at"));
      assertEquals("",a.failure);
      a.socket.cancel(); // Mobile network loss: crash/abrupt disconnect, not leave.
      b=connect(client);
      assertTrue(b.socket.send("{\"type\":\"resume\",\"id\":\""+id+"\",\"token\":\""+token+"\"}"));
      JSONObject resumed=b.until("resumed",20);
      assertEquals(id,resumed.getString("id"));
      assertTrue(b.socket.send("{\"type\":\"leave\"}"));
      b.until("left",10);
      System.out.println("PASS REAL ANDROID OKHTTP HOST: created, heartbeat, cancel, resume, leave");
    }finally{
      if(b!=null&&b.socket!=null)b.socket.cancel();
      if(a!=null&&a.socket!=null)a.socket.cancel();
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
  }
  @Test public void nativeHostReceivesActualViewerChatPacket() throws Exception{
    OkHttpClient client=new OkHttpClient.Builder()
      .pingInterval(20,TimeUnit.SECONDS).connectTimeout(12,TimeUnit.SECONDS).build();
    Session host=null,viewer=null;
    try{
      host=connect(client);
      assertTrue(host.socket.send(new JSONObject()
        .put("type","create").put("title","GAME CHAT SIGNAL TEST")
        .put("category","Gaming").put("mode","public")
        .put("hostName","NADMO QA").toString()));
      String room=host.until("created",20).getString("id");
      viewer=connect(client);
      assertTrue(viewer.socket.send(new JSONObject().put("type","join").put("id",room).toString()));
      viewer.until("joined",20);
      host.until("viewer-joined",20);
      String proof="CHAT_PING_"+System.nanoTime();
      assertTrue(viewer.socket.send(new JSONObject().put("type","chat").put("text",proof).toString()));
      JSONObject received=host.until("chat",20);
      assertEquals("Native GAME host must get viewer chat for popup notification",proof,received.optString("text"));
      assertTrue("Sender identifier needed to suppress host's own message",received.optString("from").length()>0);
      assertTrue(host.socket.send(new JSONObject().put("type","leave").toString()));
      host.until("left",10);
      System.out.println("PASS real viewer chat reaches GAME Android OkHttp host, ready for system notification");
    }finally{
      if(host!=null&&host.socket!=null)host.socket.cancel();
      if(viewer!=null&&viewer.socket!=null)viewer.socket.cancel();
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
  }

}
