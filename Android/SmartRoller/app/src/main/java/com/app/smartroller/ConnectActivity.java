package com.app.smartroller;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ConnectActivity extends AppCompatActivity {

    EditText inputBroker, inputPort, inputClientId, inputUser, inputPassword;
    TextView textStatus;
    Button btnConnect;

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

                Toast.makeText(ConnectActivity.this, "Iniciando conexión...", Toast.LENGTH_SHORT).show();
                if(MqttService.isRunning){
                    Intent mainIntent = new Intent(ConnectActivity.this, MainActivity.class);
                    startActivity(mainIntent);
                    finish();
                }
                else {
                    Toast.makeText(ConnectActivity.this, "No se a podido conectar al broker", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
