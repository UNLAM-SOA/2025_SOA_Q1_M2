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

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class MqttService extends Service {

    public static final String TOPIC_APP_PERSIANA = "/app_persiana";
    public static final String TOPIC_PERSIANA = "/persiana";
    public static final String PAYLOAD_OPEN = "abrir";
    public static final String PAYLOAD_CLOSE = "cerrar";
    public static final String PAYLOAD_PAUSE = "pausar";
    public static final String PAYLOAD_AUTO = "auto";
    public static final String PAYLOAD_MANUAL = "manual";
    public static final String MQTT_CONNECTION_STATUS = "MQTT_CONNECTION_STATUS";
    public static final String MQTT_MESSAGE_RECEIVED = "MQTT_MESSAGE";
    public static final String MQTT_DISCONNECT = "MQTT_DISCONNECT";
    public static final String ACTION_PUBLISH = "publish";
    private Mqtt3AsyncClient client;
    public static boolean isRunning = false;
    public static final String CHANNEL_ID = "MQTT_CHANNEL";
    private final IBinder binder = new LocalBinder();
    private final Gson gson = new Gson();

    public class LocalBinder extends android.os.Binder {
        public MqttService getService() {
            return MqttService.this;
        }
    }

    private final BroadcastReceiver disconnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MQTT_DISCONNECT.equals(intent.getAction())) {
                if (client != null && client.getState().isConnected()) {
                    try {
                        client.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                terminate();
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(disconnectionReceiver, new IntentFilter(MQTT_DISCONNECT), RECEIVER_EXPORTED);
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        unregisterReceiver(disconnectionReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        setNotificationInForeground();

        MqttConnectionParams params = intent.getParcelableExtra(MqttConnectionParams.NAME);
        String action = intent.getStringExtra("action");
        if ("publish".equals(action)) {
            String topic = intent.getStringExtra("topic");
            String payload = intent.getStringExtra("payload");
            publishMessage(topic, payload);
            return START_NOT_STICKY;
        }

        connectToMqtt(params);
        return START_STICKY;
    }

    private void connectToMqtt(MqttConnectionParams params) {
        try {
            client = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(params.clientId)
                    .serverHost(params.broker)
                    .serverPort(Integer.parseInt(params.port))
                    .addDisconnectedListener(context -> onConnectionError(context.getCause().getMessage()))
                    .buildAsync();

            var builder = client.connectWith();

            if (!params.user.isEmpty()) {
                builder.simpleAuth()
                        .username(params.user)
                        .password(params.password.getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth();
            }

            builder.cleanSession(true)
                    .send()
                    .whenComplete(((mqtt3ConnAck, throwable) -> {
                        if (throwable != null)
                            onConnectionError(throwable.getMessage());
                        else
                            onConnected();
                    }));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onConnected() {
        isRunning = true;
        subscribeToAppPersiana();
        sendBroadcastConnectionSuccess();
    }

    private void onConnectionError(String error) {
        sendBroadcastConnectionFailed(error);
        terminate();
    }

    private void terminate() {
        isRunning = false;
        stopForeground(true);
        stopSelf();
    }

    private void sendBroadcastConnectionSuccess() {
        MqttStatus status = new MqttStatus();
        status.setConnected();

        Intent successIntent = new Intent(MQTT_CONNECTION_STATUS);
        successIntent.putExtra(MqttStatus.PAYLOAD_NAME, status);

        sendBroadcast(successIntent);
    }

    private void sendBroadcastConnectionFailed(String error) {
        MqttStatus status = new MqttStatus();
        status.setFailed();
        status.error = error;

        Intent failureIntent = new Intent(MQTT_CONNECTION_STATUS);
        failureIntent.putExtra(MqttStatus.PAYLOAD_NAME, status);

        sendBroadcast(failureIntent);
    }

    private void subscribeToAppPersiana() {
        try {
            client.subscribeWith()
                    .topicFilter(TOPIC_APP_PERSIANA)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .callback(publish -> broadcastEsp32Status(publish.getPayloadAsBytes()))
                    .send();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void publishMessage(String topic, String payload) {
        if (client == null || !client.getState().isConnected()) {
            toast(getString(R.string.not_connected_warning));
            return;
        }

        try {
            client.publishWith()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .send();
            toast(getString(R.string.message_sent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.active_connection_title),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void setNotificationInForeground() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.active_connection_title))
                .setContentText(getString(R.string.listening_on) + TOPIC_PERSIANA)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.disconnect_btn), createDisconnectionBroadcast())
                .build();

        startForeground(1, notification);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void broadcastEsp32Status(byte[] bytes) {
        Intent intent = new Intent(MQTT_MESSAGE_RECEIVED);
        String payload = new String(bytes, StandardCharsets.UTF_8);
        ESP32 esp32;
        try {
            esp32 = gson.fromJson(payload, ESP32.class);
        } catch (Exception e) {
            esp32 = new ESP32();
            esp32.error = "Invalid JSON received";
        }
        intent.putExtra(ESP32.NAME, esp32);
        sendBroadcast(intent);
    }

    private PendingIntent createDisconnectionBroadcast() {
        Intent intent = new Intent(MQTT_DISCONNECT);
        return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }
}
