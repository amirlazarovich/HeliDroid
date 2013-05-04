package com.helidroid.dashboard.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;
import com.helidroid.dashboard.R;
import com.labs.adk.ADKManager;
import com.labs.adk.Callback;
import com.labs.commons.ADK;
import com.labs.commons.AnimUtils;

/**
 * @author Amir Lazarovich
 */
public class MainActivity extends Activity implements View.OnClickListener, Callback, SeekBar.OnSeekBarChangeListener {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    public static final String TAG = "MainActivity";

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private ADKManager mADKManager;

    private ProgressBar mProgress;
    private ToggleButton mBtnPower;
    private SeekBar mSpeedMotors;
    private TextView mTextAck;

    ///////////////////////////////////////////////
    // Activity Flow
    ///////////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
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
        mSpeedMotors = (SeekBar) findViewById(R.id.speed_motors);
        mTextAck = (TextView) findViewById(R.id.ack);

        // listeners
        mBtnPower.setOnClickListener(this);
        mSpeedMotors.setOnSeekBarChangeListener(this);

        // prepare buttons
        enableButtons(false);
        mBtnPower.setEnabled(true);
    }

    ///////////////////////////////////////////////
    // Overrides
    ///////////////////////////////////////////////

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_power:
                onToggleSystemPower((ToggleButton) v);
                break;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        sendCommand(ADK.COMMAND_MOTORS, ADK.ACTION_MOTOR_POWER, new byte[] {toUnsignedByte(progress)});
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onADKAckReceived(boolean ack) {
        enableButtons(ack);
        mTextAck.setTextColor(ack ?
                getResources().getColor(android.R.color.holo_green_light) :
                getResources().getColor(android.R.color.holo_red_light));
        mTextAck.setText(String.format("Ack received: %b", ack));
        AnimUtils.playTogether(
                AnimUtils.prepareShowViewAnimated(mTextAck, AnimUtils.SHORT_ANIM_TIME),
                AnimUtils.prepareHideViewAnimated(mProgress, AnimUtils.SHORT_ANIM_TIME)
        );
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////
    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        AnimUtils.playTogether(
                AnimUtils.prepareShowViewAnimated(mProgress, AnimUtils.SHORT_ANIM_TIME),
                AnimUtils.prepareHideViewAnimated(mTextAck, AnimUtils.SHORT_ANIM_TIME)
        );

        enableButtons(false);
        mADKManager.sendCommand(command, action, data);
    }

    /**
     * Handle toggling between motors' stand-by mode
     *
     * @param power
     */
    private void onToggleSystemPower(ToggleButton power) {
        boolean powerOn = power.isChecked();

        if (powerOn) {
            sendCommand(ADK.COMMAND_STAND_BY, ADK.ACTION_OFF, null);
        } else {
            sendCommand(ADK.COMMAND_STAND_BY, ADK.ACTION_ON, null);
        }
    }

    /**
     * Enable/Disable buttons
     *
     * @param enabled
     */
    private void enableButtons(boolean enabled) {
        mBtnPower.setEnabled(enabled);
        mSpeedMotors.setEnabled(enabled);
    }

    /**
     * Convert <code>val</code> to unsigned byte
     *
     * @param val
     * @return
     */
    private static byte toUnsignedByte(int val) {
        return (byte) (val & 0xFF);
    }
}
