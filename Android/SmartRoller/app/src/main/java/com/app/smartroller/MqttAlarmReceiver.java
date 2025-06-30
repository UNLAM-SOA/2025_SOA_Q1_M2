package com.app.smartroller;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MqttAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("action");

        Log.d("MQTT_ALARM", "Alarm received, action: " + action);

        // Preparamos un Intent para decirle al servicio que publique
        Intent mqttIntent = new Intent(context, MqttService.class);
        mqttIntent.putExtra("action", "publish");
        mqttIntent.putExtra("topic", "/persiana");
        mqttIntent.putExtra("payload", action);

        // Iniciamos el servicio para publicar
        context.startService(mqttIntent);
    }
}
