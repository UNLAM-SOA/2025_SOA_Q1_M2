package com.app.smartroller;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    TextView textState;
    TextView textMode;
    TextView textLight;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastUpdate = 0;
    private float lastX, lastY, lastZ;
    private static final int SHAKE_THRESHOLD = 800;
    private MqttService mqttService;
    private boolean isBound = false;
    private final ESP32 esp32 = new ESP32();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mqttService = ((MqttService.LocalBinder) service).getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver mqttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            esp32.state = intent.getStringExtra(ServicePayload.FIELD_STATE);
            esp32.mode = intent.getStringExtra(ServicePayload.FIELD_MODE);
            esp32.light = intent.getIntExtra(ServicePayload.FIELD_LIGHT, -1);

            updateUI();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (!MqttService.isRunning) {
            toast(getString(R.string.mqtt_not_connected));
            goToConnectivityActivity();
        } else {
            Intent serviceIntent = new Intent(this, MqttService.class);
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        configureFields();
        configureSensorManager();
        configurePadding();
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mqttReceiver, new IntentFilter(MqttService.MQTT_MESSAGE_RECEIVED), Context.RECEIVER_EXPORTED);
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mqttReceiver);
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastUpdate) > 100) {
            long diffTime = currentTime - lastUpdate;
            lastUpdate = currentTime;
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000;
            if (speed > SHAKE_THRESHOLD) {
                mqttService.publishMessage(MqttService.TOPIC_PERSIANA, "ping");
            }
            lastX = x;
            lastY = y;
            lastZ = z;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateUI() {
        textState.setText(esp32.state);
        textLight.setText(esp32.light);
        textMode.setText(esp32.mode);
    }

    private void configureFields() {
        setContentView(R.layout.activity_main);

        textState = findViewById(R.id.txtState);
        textMode = findViewById(R.id.txtMode);
        textLight = findViewById(R.id.txtLight);

        findViewById(R.id.btnCm).setOnClickListener(v -> sendChange());
        findViewById(R.id.btnUp).setOnClickListener(v -> sendOpen());
        findViewById(R.id.btndown).setOnClickListener(v -> sendClose());
        findViewById(R.id.btnPause).setOnClickListener(v -> sendPause());

        findViewById(R.id.btnConnect).setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ConnectActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.btnTimer).setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, ScheduleActivity.class);
            startActivity(intent);
        });
    }

    private void goToConnectivityActivity() {
        Intent intent = new Intent(this, ConnectActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);

        finish();
    }

    private void sendChange() {
        String newMode = esp32.mode.equals(MqttService.PAYLOAD_MANUAL) ? MqttService.PAYLOAD_AUTO : MqttService.PAYLOAD_MANUAL;
        mqttService.publishMessage(MqttService.TOPIC_PERSIANA, "cm " + newMode);
    }

    private void sendOpen() {
        if (isBound && mqttService != null) {
            mqttService.publishMessage(MqttService.TOPIC_PERSIANA, MqttService.PAYLOAD_OPEN);
        }
    }

    private void sendClose() {
        if (isBound && mqttService != null) {
            mqttService.publishMessage(MqttService.TOPIC_PERSIANA, MqttService.PAYLOAD_CLOSE);
        }
    }

    private void sendPause() {
        if (isBound && mqttService != null) {
            mqttService.publishMessage(MqttService.TOPIC_PERSIANA, MqttService.PAYLOAD_PAUSE);
        }
    }

    private void configurePadding() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void configureSensorManager() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }
}