package com.app.smartroller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import java.util.ArrayList;

public class ScheduleActivity extends AppCompatActivity {
    private int selectedHour = -1;
    private int selectedMinute = -1;

    private ArrayList<String> scheduleStrings = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    private Spinner spinnerAction;
    private ListView listView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("schedules", MODE_PRIVATE);
        Set<String> savedSet = prefs.getStringSet("list", new HashSet<>());

        for (String item : savedSet) {
            scheduleStrings.add(item);

            // Reparse the time and action to reschedule the alarm
            String[] parts = item.split(" - ");
            if (parts.length == 2) {
                String[] timeParts = parts[0].split(":");
                String action = parts[1].toLowerCase();
                if (timeParts.length == 2) {
                    int hour = Integer.parseInt(timeParts[0]);
                    int minute = Integer.parseInt(timeParts[1]);
                    scheduleAlarm(hour, minute, action);
                }
            }
        }


        setContentView(R.layout.activity_schedule);

        spinnerAction = findViewById(R.id.spinnerAction);
        listView = findViewById(R.id.listViewSchedules);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.actions_array, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(spinnerAdapter);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scheduleStrings);
        listView.setAdapter(listAdapter);

        Button btnPickTime = findViewById(R.id.btnPickTime);
        btnPickTime.setOnClickListener(v -> showTimePicker());

        Button btnAdd = findViewById(R.id.btnAddSchedule);
        btnAdd.setOnClickListener(v -> addSchedule());

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            scheduleStrings.remove(position);
            listAdapter.notifyDataSetChanged();
            return true;
        });
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    Toast.makeText(this, "Time selected: " + String.format("%02d:%02d", hourOfDay, minute), Toast.LENGTH_SHORT).show();
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );
        dialog.show();
    }

    private void addSchedule() {
        if (selectedHour == -1 || selectedMinute == -1) {
            Toast.makeText(this, "Pick a time first", Toast.LENGTH_SHORT).show();
            return;
        }

        String action = spinnerAction.getSelectedItem().toString().toLowerCase();
        String entry = String.format("%02d:%02d - %s", selectedHour, selectedMinute, action);

        scheduleAlarm(selectedHour, selectedMinute, action);

        scheduleStrings.add(entry);
        listAdapter.notifyDataSetChanged();
        saveSchedulesToPreferences();
    }

    private void scheduleAlarm(int hour, int minute, String action) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, MqttAlarmReceiver.class);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("action", action);

        int requestCode = hour * 100 + minute;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    private void saveSchedulesToPreferences() {
        SharedPreferences prefs = getSharedPreferences("schedules", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> set = new HashSet<>(scheduleStrings);
        editor.putStringSet("list", set);
        editor.apply();
    }

}
