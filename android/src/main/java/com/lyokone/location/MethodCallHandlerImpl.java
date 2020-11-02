package com.lyokone.location;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

final class MethodCallHandlerImpl implements MethodChannel.MethodCallHandler {
    private static final String TAG = "MethodCallHandlerImpl";

    private final FlutterLocation location;
    @Nullable
    private MethodChannel channel;

    private static final String METHOD_CHANNEL_NAME = "lyokone/location";

    MethodCallHandlerImpl(FlutterLocation location) {
        this.location = location;
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (location.activity == null) {
            result.error("NO_ACTIVITY", null, null);
            return;
        }

        switch (call.method) {
            case "changeSettings":
//                onChangeSettings(call, result);
                break;
            case "updateOption":
                location.updateOption((Map) call.arguments);
                break;
            case "getLocation":
                onGetLocation(result);
                break;
            case "hasPermission":
                onHasPermission(result);
                break;
            case "requestPermission":
                onRequestPermission(result);
                break;
            case "serviceEnabled":
                onServiceEnabled(result);
                break;
            case "requestService":
                location.requestService(result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    /**
     * Registers this instance as a method call handler on the given
     * {@code messenger}.
     */
    void startListening(BinaryMessenger messenger) {
        if (channel != null) {
            Log.wtf(TAG, "Setting a method call handler before the last was disposed.");
            stopListening();
        }

        channel = new MethodChannel(messenger, METHOD_CHANNEL_NAME);
        channel.setMethodCallHandler(this);
    }

    /**
     * Clears this instance from listening to method calls.
     */
    void stopListening() {
        if (channel == null) {
            Log.d(TAG, "Tried to stop listening when no MethodChannel had been initialized.");
            return;
        }

        channel.setMethodCallHandler(null);
        channel = null;
    }


    private void onGetLocation(MethodChannel.Result result) {
        location.getLocationResult = result;
        if (!location.checkPermissions()) {
            location.requestPermissions();
        } else {
            location.startRequestingLocation();
        }
    }

    private void onHasPermission(MethodChannel.Result result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            result.success(1);
            return;
        }

        if (location.checkPermissions()) {
            result.success(1);
        } else {
            result.success(0);
        }
    }

    private void onServiceEnabled(MethodChannel.Result result) {
        try {
            result.success(location.checkServiceEnabled() ? 1 : 0);
        } catch (Exception e) {
            result.error("SERVICE_STATUS_ERROR", "Location service status couldn't be determined", null);
        }
    }

    private void onRequestPermission(MethodChannel.Result result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            result.success(1);
            return;
        }

        location.result = result;
        location.requestPermissions();
    }

}
