package com.app.smartroller;

import com.google.gson.annotations.SerializedName;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.gson.annotations.SerializedName;

public class ESP32 implements Parcelable {

    public static final String NAME = "ESP32";

    @SerializedName("estado")
    public String state = "";

    @SerializedName("modo")
    public String mode = "";

    @SerializedName("luz")
    public int light = -1;

    @SerializedName("error")
    public String error = "";

    public ESP32() {
    }

    protected ESP32(Parcel in) {
        state = in.readString();
        mode = in.readString();
        light = in.readInt();
        error = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(state);
        dest.writeString(mode);
        dest.writeInt(light);
        dest.writeString(error);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ESP32> CREATOR = new Creator<ESP32>() {
        @Override
        public ESP32 createFromParcel(Parcel in) {
            return new ESP32(in);
        }

        @Override
        public ESP32[] newArray(int size) {
            return new ESP32[size];
        }
    };
}
