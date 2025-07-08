package com.app.smartroller;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MqttAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mqttIntent = new Intent(context, MqttService.class);

        String action = intent.getStringExtra(ScheduleActivity.ACTION_NAME);
        var payload = new AlarmPayload(MqttService.ACTION_PUBLISH, MqttService.TOPIC_PERSIANA, action);
        mqttIntent.putExtra(AlarmPayload.Name, payload);

        context.startService(mqttIntent);
    }
}
