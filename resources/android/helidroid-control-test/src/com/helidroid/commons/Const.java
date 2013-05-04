package com.helidroid.commons;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.helidroid.R;
import com.helidroid.ui.SettingsActivity;
import com.labs.commons.SLog;

/**
 * @author Amir Lazarovich
 */
public class Const {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "Const";

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    public String SERVER_ADDRESS;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////
    public Const(Context context) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        String serverAddress = sharedPref.getString(SettingsActivity.KEY_SERVER_ADDRESS, "");
        if (TextUtils.isEmpty(serverAddress)) {
            serverAddress = context.getString(R.string.server_address);
        }

        SERVER_ADDRESS = serverAddress;

        SLog.i(TAG, "Server address: %s", serverAddress);
    }
}
