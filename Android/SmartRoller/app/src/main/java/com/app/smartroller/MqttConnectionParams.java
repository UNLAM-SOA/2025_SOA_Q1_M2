package com.app.smartroller;

import android.os.Parcel;
import android.os.Parcelable;

public class MqttConnectionParams implements Parcelable {
    public static String NAME = "params";
    public String broker;
    public String port;
    public String clientId;
    public String user;
    public String password;

    public MqttConnectionParams() {}

    public MqttConnectionParams(String broker, String port, String clientId, String user, String password) {
        this.broker = broker;
        this.port = port;
        this.clientId = clientId;
        this.user = user;
        this.password = password;
    }

    protected MqttConnectionParams(Parcel in) {
        broker = in.readString();
        port = in.readString();
        clientId = in.readString();
        user = in.readString();
        password = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(broker);
        dest.writeString(port);
        dest.writeString(clientId);
        dest.writeString(user);
        dest.writeString(password);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<MqttConnectionParams> CREATOR = new Creator<>() {
        @Override
        public MqttConnectionParams createFromParcel(Parcel in) {
            return new MqttConnectionParams(in);
        }

        @Override
        public MqttConnectionParams[] newArray(int size) {
            return new MqttConnectionParams[size];
        }
    };
}
