// Copyright 2020 Google Inc. All Rights Reserved.

package com.google.android.iwlan;

import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IwlanErrorReporter {
    private final String TAG;
    private static final String ACTION_IWLAN_ERROR_REPORT =
            "com.android.iwlan.action.ACTION_IWLAN_ERROR_REPORT";
    private static final String EXTRA_STRING_IWLAN_ERROR_INFO = "iwlan_error_info";
    private static Map<IwlanError, Integer> sIwlanError = new ConcurrentHashMap<>();
    Context mContext;

    public IwlanErrorReporter(Context context) {
        mContext = context;
        TAG = IwlanErrorReporter.class.getSimpleName();
    }

    public void broadcastIwlanTunnelBringupErrorString(IwlanError error) {
        // If the same Iwlan Error has already occurred, skip sending intents for it.
        Integer count = sIwlanError.containsKey(error) ? sIwlanError.get(error) + 1 : 1;
        sIwlanError.put(error, count);
        if (count > 1) return;

        Log.d(TAG, "Broadcast Iwlan Error Infos: " + error.toString());
        StringBuilder sb = new StringBuilder();
        sb.append("Tunnel_bring_up_error: " + error.toString());

        Intent intent = new Intent(ACTION_IWLAN_ERROR_REPORT);
        intent.putExtra(EXTRA_STRING_IWLAN_ERROR_INFO, sb.toString());

        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, READ_PRIVILEGED_PHONE_STATE);
    }
}
