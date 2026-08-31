package com.baliweddingdj.app;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class BwdFirebaseMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token){
        super.onNewToken(token);
        BwdCloud.saveToken(this,token);
    }

    @Override public void onMessageReceived(RemoteMessage message){
        super.onMessageReceived(message);
        String title="Bali Wedding DJ", body="You have a new update.";
        if(message.getNotification()!=null){
            if(message.getNotification().getTitle()!=null) title=message.getNotification().getTitle();
            if(message.getNotification().getBody()!=null) body=message.getNotification().getBody();
        }
        if(message.getData().containsKey("title")) title=message.getData().get("title");
        if(message.getData().containsKey("body")) body=message.getData().get("body");
        String bookingId=message.getData().get("booking_id");
        String type=message.getData().get("type");
        new WeddingDb(this).addNotification(BwdCloud.isOwner(this)?"admin":"customer",title,body);
        BwdCloud.showNotification(this,title,body,bookingId,type);
    }
}
