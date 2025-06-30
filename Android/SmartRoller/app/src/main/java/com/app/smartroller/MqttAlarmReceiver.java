package com.app.smartroller;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MqttAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");

        // Preparamos un Intent para decirle al servicio que publique
        Intent mqttIntent = new Intent(context, MqttService.class);
        mqttIntent.putExtra("action", MqttService.ACTION_PUBLISH);
        mqttIntent.putExtra("topic", MqttService.TOPIC_PERSIANA);
        mqttIntent.putExtra("payload", action);

        // Iniciamos el servicio para publicar
        context.startService(mqttIntent);
    }
}
