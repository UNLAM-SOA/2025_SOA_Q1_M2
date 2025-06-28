package com.app.smartroller;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.*;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class ConnectActivity extends AppCompatActivity {

    EditText inputBroker, inputPort, inputClientId, inputUser, inputPassword;
    TextView textStatus;
    Button btnConnect;

    MqttAndroidClient client;

    private SSLSocketFactory getSSLSocketFactory() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = getResources().openRawResource(R.raw.ca);  // tu archivo ca.crt
        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
            Log.d("MQTT", "CA cargada: " + ((java.security.cert.X509Certificate) ca).getSubjectDN());
        } finally {
            caInput.close();
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        return context.getSocketFactory();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        // Vincular componentes
        inputBroker = findViewById(R.id.inputBroker);
        inputPort = findViewById(R.id.inputPort);
        inputClientId = findViewById(R.id.inputClientId);
        inputUser = findViewById(R.id.inputUser);
        inputPassword = findViewById(R.id.inputPassword);
        textStatus = findViewById(R.id.textStatus);
        btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String broker = inputBroker.getText().toString().trim();
                String port = inputPort.getText().toString().trim();
                String clientId = inputClientId.getText().toString().trim();
                String user = inputUser.getText().toString().trim();
                String pass = inputPassword.getText().toString().trim();

                String serverUri = "ssl://" + broker + ":" + port;

                // Desconectar cliente anterior si existe
                if (client != null) {
                    try {
                        client.disconnect();
                        client.unregisterResources();
                        client.close();
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

                client = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);

                client.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        Log.d("MQTT", "Conexión perdida: " + cause != null ? cause.getMessage() : "null");
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

                MqttConnectOptions options = new MqttConnectOptions();
                options.setCleanSession(true);

                if (!user.isEmpty()) {
                    options.setUserName(user);
                }
                if (!pass.isEmpty()) {
                    options.setPassword(pass.toCharArray());
                }

                try {
                    options.setSocketFactory(getSSLSocketFactory());

                    IMqttToken token = client.connect(options);
                    token.setActionCallback(new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            textStatus.setText("Estado: Conectado");
                            textStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                            Toast.makeText(ConnectActivity.this, "Conectado al broker", Toast.LENGTH_SHORT).show();
                            topicSubscribe("/persiana");
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            textStatus.setText("Estado: Error de conexión");
                            textStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                            Toast.makeText(ConnectActivity.this, "Fallo la conexión", Toast.LENGTH_SHORT).show();
                            Log.e("MQTT", "Error al conectar", exception);
                        }
                    });

                } catch (Exception e) {
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
