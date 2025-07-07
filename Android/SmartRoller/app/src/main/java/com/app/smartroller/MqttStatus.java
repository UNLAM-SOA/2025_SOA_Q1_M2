package com.app.smartroller;

import android.os.Parcel;
import android.os.Parcelable;

public class MqttStatus implements Parcelable {
    public static String PAYLOAD_NAME = "mqttStatus";
    public String status = "failed";
    public String error;
    public boolean isConnected() {
        return "connected".equals(status);
    }
    public void setConnected() {
        status = "connected";
    }
    public void setFailed() {
        status = "failed";
    }
    public MqttStatus() {}

    public MqttStatus(String status, String error) {
        this.status = status;
        this.error = error;
    }

    protected MqttStatus(Parcel in) {
        status = in.readString();
        error = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(status);
        dest.writeString(error);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MqttStatus> CREATOR = new Creator<>() {
        @Override
        public MqttStatus createFromParcel(Parcel in) {
            return new MqttStatus(in);
        }

        @Override
        public MqttStatus[] newArray(int size) {
            return new MqttStatus[size];
        }
    };
}
