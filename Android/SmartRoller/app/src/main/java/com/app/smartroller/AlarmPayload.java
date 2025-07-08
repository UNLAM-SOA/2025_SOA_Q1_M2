package com.app.smartroller;

import android.os.Parcel;
import android.os.Parcelable;

public class AlarmPayload implements Parcelable {
    public static final String Name = "alarm";
    public String action = "";
    public String topic = "";
    public String payload = "";

    public AlarmPayload(String action, String topic, String payload) {
        this.action = action;
        this.topic = topic;
        this.payload = payload;
    }
    protected AlarmPayload(Parcel in) {
        action = in.readString();
        topic = in.readString();
        payload = in.readString();
    }

    public static final Creator<AlarmPayload> CREATOR = new Creator<AlarmPayload>() {
        @Override
        public AlarmPayload createFromParcel(Parcel in) {
            return new AlarmPayload(in);
        }

        @Override
        public AlarmPayload[] newArray(int size) {
            return new AlarmPayload[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(action);
        dest.writeString(topic);
        dest.writeString(payload);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
