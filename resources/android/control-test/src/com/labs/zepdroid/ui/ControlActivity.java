package com.labs.zepdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.labs.zepdroid.R;
import com.labs.zepdroid.managers.ADKManager;
import com.labs.zepdroid.ui.Joystick.DualJoystickView;
import com.labs.zepdroid.ui.Joystick.JoystickMovedListener;
import com.labs.zepdroid.ui.Joystick.JoystickView;

/**
 *
 */
public class ControlActivity extends Activity implements View.OnClickListener, ADKManager.Callback {
    //////////////////////////////////////////
    // Constants
    //////////////////////////////////////////
    private static final String TAG = "ControlActivity";
    private static final int MAX_MOTOR_SPEED = toUnsignedByte(255);

    //////////////////////////////////////////
    // Members
    //////////////////////////////////////////
    private ProgressBar mProgress;
    private ToggleButton mBtnPower;
    private DualJoystickView mJoystick;
    private TextView mTextAck;

    private ADKManager mADKManager;

    //////////////////////////////////////////
    // Activity Flow
    //////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.control_activity);
        init();
    }

    @Override
    public void onResume() {
        super.onResume();
        mADKManager.connect();
    }

    @Override
    public void onPause() {
        super.onPause();
        mADKManager.disconnect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * Initialization process
     */
    private void init() {
        mADKManager = new ADKManager(this, this);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mBtnPower = (ToggleButton) findViewById(R.id.btn_power);
        mTextAck = (TextView) findViewById(R.id.ack);
        mJoystick = (DualJoystickView) findViewById(R.id.dual_joystick);

        // listeners
        mBtnPower.setOnClickListener(this);
        mJoystick.setOnJostickMovedListener(mListenerLeft, mListenerRight);

        // prepare buttons
        enableButtons(false);
        mBtnPower.setEnabled(true);
    }

    //////////////////////////////////////////
    // Overrides & Implementations
    //////////////////////////////////////////

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_power:
                onToggleSystemPower((ToggleButton) v);
                break;
        }
    }

    private JoystickMovedListener mListenerLeft = new JoystickMovedListener() {

        @Override
        public void OnMoved(JoystickView joystickView, int pan, int tilt) {
            Log.d(TAG, "OnMove-left:: pan: " + pan + ", tilt: " + tilt);
        }

        @Override
        public void OnReleased(JoystickView joystickView) {

        }

        public void OnReturnedToCenter(JoystickView joystickView) {

        }
    };

    private JoystickMovedListener mListenerRight = new JoystickMovedListener() {

        @Override
        public void OnMoved(JoystickView joystickView, int pan, int tilt) {
            Log.d(TAG, "OnMove-right:: pan: " + pan + ", tilt: " + tilt);
        }

        @Override
        public void OnReleased(JoystickView joystickView) {

        }

        public void OnReturnedToCenter(JoystickView joystickView) {

        }
    };

    @Override
    public void onADKAckReceived(boolean ack) {
        enableButtons(true);
        mProgress.setVisibility(View.GONE);
        mTextAck.setText(String.format("Ack received: %b", ack));
    }

    //////////////////////////////////////////
    // Private
    //////////////////////////////////////////

    private void onToggleSystemPower(ToggleButton power) {
        boolean powerOn = power.isChecked();

        if (powerOn) {
            sendCommand(ADKManager.COMMAND_STAND_BY, ADKManager.ACTION_ON, null);
        } else {
            sendCommand(ADKManager.COMMAND_STAND_BY, ADKManager.ACTION_OFF, null);
        }
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        mProgress.setVisibility(View.VISIBLE);
        enableButtons(false);
        mADKManager.sendCommand(command, action, data);
    }


    private void enableButtons(boolean enabled) {
        mBtnPower.setEnabled(enabled);
        mJoystick.setEnabled(enabled);
    }

    private static byte toUnsignedByte(int b) {
        return (byte) (b & 0xFF);
    }
}