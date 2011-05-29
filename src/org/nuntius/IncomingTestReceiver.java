package org.nuntius;

import org.nuntius.R;
import org.nuntius.client.AbstractMessage;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;


public class IncomingTestReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.w(getClass().getSimpleName(), "action="+intent.getAction());

        // message received!
        if (MessageCenterService.MESSAGE_RECEIVED.equals(intent.getAction())) {
            Bundle b = intent.getExtras();
            AbstractMessage<?> msg = AbstractMessage.fromBundle(b);
            Log.w(getClass().getSimpleName(), "class=" + msg.getClass().getName());
            Log.w(getClass().getSimpleName(), "content=" + msg.getTextContent());

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Notification no = new Notification(R.drawable.icon, msg.getTextContent(), System.currentTimeMillis());

            CharSequence contentTitle = "New message";
            CharSequence contentText = msg.getTextContent();
            Intent notificationIntent = new Intent(context, ThreadsActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
            no.setLatestEventInfo(context.getApplicationContext(), contentTitle, contentText, contentIntent);

            nm.notify(1, no);
        }
    }

}
