package net.zhewei.fangzai;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NotifyBridge")
public class NotifyBridgePlugin extends Plugin {

    private static final String DEFAULT_CHANNEL = "fz-urgent";
    private NotificationManager nm;

    @Override
    public void load() {
        nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        // 緊急（最大音量 + 震動 + 鈴聲）
        NotificationChannel urgent = new NotificationChannel("fz-urgent", "緊急災害", NotificationManager.IMPORTANCE_HIGH);
        urgent.enableVibration(true);
        urgent.setVibrationPattern(new long[]{0, 300, 200, 300, 200, 300});
        urgent.setSound(Uri.parse("android.resource://net.zhewei.fangzai/" + R.raw.alert_urgent),
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build());
        urgent.setBypassDnd(true);
        urgent.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(urgent);

        // 警戒（大聲 + 震動 + 音效）
        NotificationChannel high = new NotificationChannel("fz-high", "警戒災害", NotificationManager.IMPORTANCE_HIGH);
        high.enableVibration(true);
        high.setVibrationPattern(new long[]{0, 200, 100, 200});
        high.setSound(Uri.parse("android.resource://net.zhewei.fangzai/" + R.raw.alert_high),
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build());
        high.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(high);

        // 一般（正常音量）
        NotificationChannel def = new NotificationChannel("fz-default", "災害示警", NotificationManager.IMPORTANCE_DEFAULT);
        def.enableVibration(true);
        def.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(def);

        // 資訊（靜音）
        NotificationChannel low = new NotificationChannel("fz-info", "資訊通知", NotificationManager.IMPORTANCE_LOW);
        low.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(low);
    }

    @PluginMethod
    public void notify(PluginCall call) {
        String title = call.getString("title", "築雲預警");
        String body = call.getString("body", "");
        String channelId = call.getString("channelId", DEFAULT_CHANNEL);
        int id = call.getInt("id", (int) (System.currentTimeMillis() % 1000000));
        boolean vibrate = call.getBoolean("vibrate", true);

        android.app.Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(getContext(), channelId);
        } else {
            builder = new android.app.Notification.Builder(getContext());
        }

        builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC);

        if (vibrate) {
            builder.setVibrate(new long[]{0, 300, 200, 300, 200, 300});
        }

        // 加 PendingIntent 讓點擊通知開啟 App
        Intent intent = getContext().getPackageManager().getLaunchIntentForPackage("net.zhewei.fangzai");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            builder.setContentIntent(PendingIntent.getActivity(getContext(), id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }

        try {
            nm.notify(id, builder.build());
            JSObject ret = new JSObject();
            ret.put("ok", true);
            ret.put("id", id);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("通知發送失敗: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        nm.cancelAll();
        call.resolve();
    }
}
