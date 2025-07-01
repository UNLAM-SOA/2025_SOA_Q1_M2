package com.app.smartroller;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ConnectActivity extends AppCompatActivity {

    EditText inputBroker, inputPort, inputClientId, inputUser, inputPassword;
    TextView textStatus;
    Button btnConnect;

    private BroadcastReceiver mqttConnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra("status");

            if ("connected".equals(status)) {
                Intent mainIntent = new Intent(ConnectActivity.this, MainActivity.class);
                startActivity(mainIntent);
                finish();
            } else if ("failed".equals(status)) {
                String error = intent.getStringExtra("error");
                Toast.makeText(ConnectActivity.this, "Error al conectar: " + error, Toast.LENGTH_LONG).show();
                textStatus.setText("Estado: Desconectado");
                textStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        inputBroker = findViewById(R.id.inputBroker);
        inputPort = findViewById(R.id.inputPort);
        inputClientId = findViewById(R.id.inputClientId);
        inputUser = findViewById(R.id.inputUser);
        inputPassword = findViewById(R.id.inputPassword);
        textStatus = findViewById(R.id.textStatus);
        btnConnect = findViewById(R.id.btnConnect);

        if(MqttService.isRunning){
            textStatus.setText("Estado: Conectado.");
            textStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        }

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                String broker = inputBroker.getText().toString().trim();
                String port = inputPort.getText().toString().trim();
                String clientId = inputClientId.getText().toString().trim();
                String user = inputUser.getText().toString().trim();
                String password = inputPassword.getText().toString().trim();

                if (broker.isEmpty() || port.isEmpty() || clientId.isEmpty()) {
                    Toast.makeText(ConnectActivity.this, "Broker, puerto y clientId son obligatorios", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent serviceIntent = new Intent(ConnectActivity.this, MqttService.class);
                serviceIntent.putExtra("broker", broker);
                serviceIntent.putExtra("port", port);
                serviceIntent.putExtra("clientId", clientId);
                serviceIntent.putExtra("user", user);
                serviceIntent.putExtra("password", password);
                ContextCompat.startForegroundService(ConnectActivity.this, serviceIntent);

                textStatus.setText("Estado: Conectando...");
                textStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mqttConnectionReceiver, new IntentFilter(MqttService.MQTT_CONNECTION_STATUS), RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mqttConnectionReceiver);
    }
}
