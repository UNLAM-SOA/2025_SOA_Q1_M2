package com.app.smartroller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
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

public class ScheduleActivity extends AppCompatActivity {
    private int selectedHour = -1;
    private int selectedMinute = -1;

    private ArrayList<String> scheduleStrings = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    private Spinner spinnerAction;
    private ListView listView;
    private final String SHARED_PREFERENCES = "schedules";
    private final String PREFERENCES_SET = "list";
    public static final String ACTION_NAME = "action";
    private class Alarm {
        public int hour = -1;
        public int minute = -1;
        public String action = "";
        public Alarm(int hour, int minute, String action) {
            this.hour = hour;
            this.minute = minute;
            this.action = action;
        }
        public Alarm(String alarmItem) {
            var parts = alarmItem.split(" - ");
            if (parts.length == 2) {
                var timeParts = parts[0].split(":");
                action = parts[1].toLowerCase();
                if (timeParts.length == 2) {
                    hour = Integer.parseInt(timeParts[0]);
                    minute = Integer.parseInt(timeParts[1]);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSchedulesSaved();
        configureFields();
    }

    private void showTimePicker() {
        Calendar now = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    toast(getString(R.string.time_selected) + String.format("%02d:%02d", hourOfDay, minute));
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true
        );
        dialog.show();
    }

    private void addSchedule() {
        if (selectedHour == -1 || selectedMinute == -1) {
            toast(getString(R.string.time_required));
            return;
        }

        String action = spinnerAction.getSelectedItem().toString().toLowerCase();

        String entry = String.format("%02d:%02d - %s", selectedHour, selectedMinute, action);

        scheduleAlarm(new Alarm(selectedHour, selectedMinute, action));

        scheduleStrings.add(entry);
        listAdapter.notifyDataSetChanged();
        saveSchedulesToPreferences();
    }

    private void scheduleAlarm(Alarm alarm) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent intent = new Intent(this, MqttAlarmReceiver.class);
        intent.putExtra("hour", alarm.hour);
        intent.putExtra("minute", alarm.minute);
        intent.putExtra("action", alarm.action);

        int requestCode = alarm.hour * 100 + alarm.minute;
        var pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, alarm.hour);
        calendar.set(Calendar.MINUTE, alarm.minute);
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
        editor.putStringSet(PREFERENCES_SET, set);
        editor.apply();
    }

    private void configureFields() {
        setContentView(R.layout.activity_schedule);

        spinnerAction = findViewById(R.id.spinnerAction);
        listView = findViewById(R.id.listViewSchedules);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.actions_array, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAction.setAdapter(spinnerAdapter);

        Button btnPickTime = findViewById(R.id.btnPickTime);
        btnPickTime.setOnClickListener(v -> showTimePicker());

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scheduleStrings);
        listView.setAdapter(listAdapter);

        Button btnAdd = findViewById(R.id.btnAddSchedule);
        btnAdd.setOnClickListener(v -> addSchedule());

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            scheduleStrings.remove(position);
            listAdapter.notifyDataSetChanged();
            return true;
        });
    }

    private void toast(String string) {
        Toast.makeText(this, string, Toast.LENGTH_SHORT).show();
    }

    private void loadSchedulesSaved() {
        SharedPreferences prefs = getSharedPreferences(SHARED_PREFERENCES, MODE_PRIVATE);
        Set<String> savedSet = prefs.getStringSet(PREFERENCES_SET, new HashSet<>());

        for (String item : savedSet) {
            scheduleStrings.add(item);
            scheduleAlarm(new Alarm(item));
        }
    }
}
