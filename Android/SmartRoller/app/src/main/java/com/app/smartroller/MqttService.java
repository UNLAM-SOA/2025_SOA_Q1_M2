package com.app.smartroller;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;

public class MqttService extends Service {
    private MqttAndroidClient client;
    public static boolean isRunning = false;
    public static final String CHANNEL_ID = "MQTT_CHANNEL";
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends android.os.Binder {
        public MqttService getService() {
            return MqttService.this;
        }
    }

    private final BroadcastReceiver disconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("MQTT_DISCONNECT".equals(intent.getAction())) {
                if (client != null && client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (MqttException e) {
                        Log.e("MQTT", "Error al desconectar", e);
                    }
                }
                stopForeground(true);
                stopSelf();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        registerReceiver(disconnectReceiver, new IntentFilter("MQTT_DISCONNECT"), RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        unregisterReceiver(disconnectReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Conexión MQTT activa")
                .setContentText("Escuchando en /app_persiana")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", createDisconnectAction())
                .build();

        startForeground(1, notification);

        String broker = intent.getStringExtra("broker");
        String port = intent.getStringExtra("port");
        String clientId = intent.getStringExtra("clientId");
        String user = intent.getStringExtra("user");
        String password = intent.getStringExtra("password");
        connectToMqtt(broker, port, clientId, user, password);
        return START_STICKY;
    }

    private void connectToMqtt(String broker, String port, String clientId, String user, String password) {
        try {
            String serverUri = "tcp://" + broker + ":" + port;
            client = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            if (!user.isEmpty()) {
                options.setUserName(user);
                options.setPassword(password.toCharArray());
            }
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    subscribeToTopic("/app_persiana");
                    Intent successIntent = new Intent("MQTT_CONNECTION_STATUS");
                    successIntent.putExtra("status", "connected");
                    sendBroadcast(successIntent);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Intent failureIntent = new Intent("MQTT_CONNECTION_STATUS");
                    failureIntent.putExtra("status", "failed");
                    failureIntent.putExtra("error", exception.getMessage());
                    sendBroadcast(failureIntent);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void subscribeToTopic(String topic) {
        try {
            client.subscribe(topic, 1, (topic1, message) -> {
                Intent intent = buildIntentMqttMessaje(topic1, new String(message.getPayload()));
                sendBroadcast(intent);
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publishMessage(String topic, String payload) {
        if (client == null || !client.isConnected()) {
            Toast.makeText(this, "Debe estar conectado para enviar mensajes", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            client.publish(topic, message);
            Toast.makeText(this, "Mensaje enviado", Toast.LENGTH_SHORT).show();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MQTT Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private Intent buildIntentMqttMessaje(String topic, String payload) {
        Intent intent = new Intent("MQTT_MESSAGE");
        intent.putExtra("topic", topic);

        try {
            JSONObject json = new JSONObject(payload);

            String state = json.optString("estado", "");
            String mode = json.optString("modo", "");
            int light = json.optInt("luz", -1);

            intent.putExtra("estado", state);
            intent.putExtra("modo", mode);
            intent.putExtra("luz", light);

        } catch (JSONException e) {
            intent.putExtra("error", "Formato JSON inválido");
        }
        return intent;
    }

    private PendingIntent createDisconnectAction() {
        Intent intent = new Intent("MQTT_DISCONNECT");
        return PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
