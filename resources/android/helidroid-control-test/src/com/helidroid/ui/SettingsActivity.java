package com.helidroid.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.helidroid.App;

/**
 * @author Amir Lazarovich
 */
public class SettingsActivity extends Activity implements OnSharedPreferenceChangeListener {
    //////////////////////////////////////////
    // Constants
    //////////////////////////////////////////
    public static final String KEY_SERVER_ADDRESS = "server_address";


    //////////////////////////////////////////
    // Activity Flow
    //////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_SERVER_ADDRESS)) {
            App.sConsts.SERVER_ADDRESS = sharedPreferences.getString(key, "");
        }
    }
}
