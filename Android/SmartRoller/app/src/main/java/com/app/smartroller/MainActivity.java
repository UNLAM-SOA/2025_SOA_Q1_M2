package com.app.smartroller;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Button pauseButton = findViewById(R.id.pause_button);
        Button upButton = findViewById(R.id.up_button);
        Button downButton = findViewById(R.id.down_button);
        Button cmButton = findViewById(R.id.cm_button);
        TextView statusValue = findViewById(R.id.status_value);
        TextView modeValue = findViewById(R.id.mode_value);
        Button connectButton = findViewById(R.id.connect_button);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        cmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modeValue.setText(modeValue.getText()=="Manual" ? "Automatico" : "Manual");
            }
        });

        upButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        downButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Lanzar la nueva actividad
                Intent intent = new Intent(MainActivity.this, ConnectActivity.class);
                startActivity(intent);
            }
        });

    }


}