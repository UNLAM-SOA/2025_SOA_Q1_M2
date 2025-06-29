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
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
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

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MqttService.LocalBinder binder = (MqttService.LocalBinder) service;
            mqttService = binder.getService();
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
            String topic = intent.getStringExtra("topic");
            String state = intent.getStringExtra("estado");
            String mode = intent.getStringExtra("modo");
            int light = intent.getIntExtra("luz", -1);
            Log.d("MQTT Receiver", state + " " + mode + " " + light);
            updateUI(state,mode,light);
        }
    };

    @Override
    protected void onStart() {
        super.onStart();

        if (!MqttService.isRunning) {
            Toast.makeText(this, "Servicio MQTT no activo. Volviendo a conectar.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ConnectActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Intent intent = new Intent(this, MqttService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        Button pauseButton = findViewById(R.id.btnPause);
        Button upButton = findViewById(R.id.btnUp);
        Button downButton = findViewById(R.id.btndown);
        Button cmButton = findViewById(R.id.btnCm);
        Button connectButton = findViewById(R.id.btnConnect);
        textState = findViewById(R.id.txtState);
        textMode = findViewById(R.id.txtMode);
        textLight = findViewById(R.id.txtLight);


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        cmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mode = textMode.getText().toString().toLowerCase();
                mode = mode.equals("manual") ? "auto" : "manual";
                mqttService.publishMessage("/persiana", "cm " + mode);
            }
        });

        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && mqttService != null) {
                    mqttService.publishMessage("/persiana", "abrir");
                }
            }
        });

        downButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && mqttService != null) {
                    mqttService.publishMessage("/persiana", "cerrar");
                }
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isBound && mqttService != null) {
                    mqttService.publishMessage("/persiana", "pausar");
                }
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ConnectActivity.class);
                startActivity(intent);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mqttReceiver, new IntentFilter("MQTT_MESSAGE"), Context.RECEIVER_NOT_EXPORTED);
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
                // 🚨 Acción al detectar el shake
                Toast.makeText(this, "¡Shake detectado!", Toast.LENGTH_SHORT).show();
                // Podés ejecutar cualquier código acá
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    
    private void updateUI(String state, String mode, Integer light) {

            textState.setText(state);
            textLight.setText(""+light);
            textMode.setText(mode);

    }
}