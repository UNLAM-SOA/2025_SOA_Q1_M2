package com.app.smartroller;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ConnectActivity extends AppCompatActivity {

    EditText inputBroker, inputPort, inputClientId, inputUser, inputPassword;
    TextView textStatus;
    Button btnConnect;

    private BroadcastReceiver mqttConnectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            MqttStatus status = intent.getParcelableExtra(MqttStatus.PAYLOAD_NAME);
            if (status.isConnected())
                goBackToMainActivity();
            else {
                toast(R.string.connection_error + status.error);
                textStatus.setText(R.string.disconnected_message);
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

        if (MqttService.isRunning) {
            textStatus.setText(R.string.connected_message);
            textStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
        }

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                connectToMqtt();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mqttConnectionReceiver, new IntentFilter(MqttService.MQTT_CONNECTION_STATUS), RECEIVER_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mqttConnectionReceiver);
    }

    private void connectToMqtt() {
        MqttConnectionParams params = new MqttConnectionParams(
                inputBroker.getText().toString(),
                inputPort.getText().toString(),
                inputClientId.getText().toString(),
                inputUser.getText().toString(),
                inputPassword.getText().toString()
        );

        if (params.broker.isEmpty() || params.port.isEmpty() || params.clientId.isEmpty()) {
            toast(getString(R.string.params_mandatory));
            return;
        }

        textStatus.setText(R.string.connecting_message);
        textStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));

        startMqttService(params);
    }

    private void startMqttService(MqttConnectionParams params) {
        Intent serviceIntent = new Intent(ConnectActivity.this, MqttService.class);
        serviceIntent.putExtra(MqttConnectionParams.NAME, params);
        ContextCompat.startForegroundService(ConnectActivity.this, serviceIntent);
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    private void goBackToMainActivity() {
        Intent mainIntent = new Intent(ConnectActivity.this, MainActivity.class);
        startActivity(mainIntent);
        finish();
    }
}
