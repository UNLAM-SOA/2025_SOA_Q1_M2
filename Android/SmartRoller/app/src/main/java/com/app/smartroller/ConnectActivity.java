package com.app.smartroller;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;

public class ConnectActivity extends AppCompatActivity {

    EditText inputBroker, inputPort, inputClientId, inputUser, inputPassword;
    TextView textStatus;
    Button btnConnect;

    MqttAndroidClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        // Vincular componentes
        inputBroker = findViewById(R.id.input_broker);
        inputPort = findViewById(R.id.input_port);
        inputClientId = findViewById(R.id.input_client_id);
        inputUser = findViewById(R.id.input_user);
        inputPassword = findViewById(R.id.input_password);
        textStatus = findViewById(R.id.text_status);
        btnConnect = findViewById(R.id.btn_connect);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String broker = inputBroker.getText().toString().trim(); // ej: tcp://192.168.0.10
                String port = inputPort.getText().toString().trim();     // ej: 1883
                String clientId = inputClientId.getText().toString().trim();
                String user = inputUser.getText().toString().trim();
                String pass = inputPassword.getText().toString().trim();

                String serverUri = broker + ":" + port;

                client = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);

                if (!user.isEmpty()) {
                    options.setUserName(user);
                }
                if (!pass.isEmpty()) {
                    options.setPassword(pass.toCharArray());
                }

                try {
                    IMqttToken token = client.connect(options);
                    token.setActionCallback(new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            textStatus.setText("Estado: Conectado");
                            textStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            Toast.makeText(ConnectActivity.this, "Conectado al broker", Toast.LENGTH_SHORT).show();

                            topicSubscribe("test/topic");
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            textStatus.setText("Estado: Error de conexión");
                            textStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            Toast.makeText(ConnectActivity.this, "Fallo la conexión", Toast.LENGTH_SHORT).show();
                            Log.e("MQTT", "Error al conectar", exception);
                        }
                    });

                    client.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            Log.d("MQTT", "Conexión perdida");
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) {
                            Log.d("MQTT", "Mensaje recibido en " + topic + ": " + message.toString());
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                            Log.d("MQTT", "Mensaje entregado");
                        }
                    });

                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void topicSubscribe(String topic) {
        try {
            client.subscribe(topic, 1);
            Log.d("MQTT", "Suscrito al tópico: " + topic);
        } catch (MqttException e) {
            Log.e("MQTT", "Error al suscribirse", e);
        }
    }
}
