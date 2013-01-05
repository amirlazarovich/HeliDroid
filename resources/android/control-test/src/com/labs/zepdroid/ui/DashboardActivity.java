package com.labs.zepdroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.labs.zepdroid.R;
import com.labs.zepdroid.managers.ADKManager;

/**
 * https://github.com/Epsiloni/ADKBareMinimum Based on Simon Monk code. Rewritten by Assaf Gamliel (goo.gl/E2MhJ) (assafgamliel.com). Feel free to contact
 * me with any question, I hope I can help. This code should give you a good jump start with your Android and Arduino project. -- This is the minimum you
 * need to communicate between your Android device and your Arduino device. If needed I'll upload the example I made (Sonar distance measureing device).
 */
public class DashboardActivity extends Activity implements View.OnClickListener, ADKManager.Callback, SeekBar.OnSeekBarChangeListener {
    //////////////////////////////////////////
    // Constants
    //////////////////////////////////////////
    private static final String TAG = "DashboardActivity";
    private static final int MAX_MOTOR_SPEED = toUnsignedByte(255);

    //////////////////////////////////////////
    // Members
    //////////////////////////////////////////
    private ProgressBar mProgress;
    private ToggleButton mBtnPower;
    private ToggleButton mBtnMotor1;
    private SeekBar mSpeedMotor1;
    private ToggleButton mBtnMotor2;
    private SeekBar mSpeedMotor2;
    private TextView mTextAck;
    private EditText mTextAngle;
    private Button mBtnSendAngle;
    private Button mBtnResetAngle;
    private Button mBtnRotateLeft;
    private Button mBtnRotateRight;
    private Button mBtnStopRotation;

    private ADKManager mADKManager;

    //////////////////////////////////////////
    // Activity Flow
    //////////////////////////////////////////
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
        mBtnMotor1 = (ToggleButton) findViewById(R.id.btn_motor_1);
        mSpeedMotor1 = (SeekBar) findViewById(R.id.speed_motor_1);
        mBtnMotor2 = (ToggleButton) findViewById(R.id.btn_motor_2);
        mSpeedMotor2 = (SeekBar) findViewById(R.id.speed_motor_2);
        mTextAck = (TextView) findViewById(R.id.ack);
        mTextAngle = (EditText) findViewById(R.id.txt_angle);
        mBtnSendAngle = (Button) findViewById(R.id.btn_send_angle);
        mBtnResetAngle = (Button) findViewById(R.id.btn_reset_angle);
        mBtnRotateLeft = (Button) findViewById(R.id.btn_rotate_left);
        mBtnRotateRight = (Button) findViewById(R.id.btn_rotate_right);
        mBtnStopRotation = (Button) findViewById(R.id.btn_stop_rotation);

        // listeners
        mBtnPower.setOnClickListener(this);
        mBtnMotor1.setOnClickListener(this);
        mSpeedMotor1.setOnSeekBarChangeListener(this);
        mBtnMotor2.setOnClickListener(this);
        mSpeedMotor2.setOnSeekBarChangeListener(this);
        mBtnSendAngle.setOnClickListener(this);
        mBtnResetAngle.setOnClickListener(this);
        mBtnRotateLeft.setOnClickListener(this);
        mBtnRotateRight.setOnClickListener(this);
        mBtnStopRotation.setOnClickListener(this);

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

            case R.id.btn_motor_1:
                onToggleMotorPower((ToggleButton) v, ADKManager.COMMAND_MOTOR_1);
                break;
            case R.id.btn_motor_2:
                onToggleMotorPower((ToggleButton) v, ADKManager.COMMAND_MOTOR_2);
                break;

            case R.id.btn_send_angle:
                onSendAngle(v);
                break;

            case R.id.btn_reset_angle:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_RESET_ROTATION, null);
                break;

            case R.id.btn_rotate_left:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_START_ROTATE, new byte[] {ADKManager.DIRECTION_LEFT});
                break;

            case R.id.btn_rotate_right:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_START_ROTATE, new byte[] {ADKManager.DIRECTION_RIGHT});
                break;

            case R.id.btn_stop_rotation:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_END_ROTATE, null);
                break;
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        ToggleButton motor = null;
        byte commandMotor = 0;
        switch (seekBar.getId()) {
            case R.id.speed_motor_1:
                motor = mBtnMotor1;
                commandMotor = ADKManager.COMMAND_MOTOR_1;
                break;

            case R.id.speed_motor_2:
                motor = mBtnMotor2;
                commandMotor = ADKManager.COMMAND_MOTOR_2;
                break;
        }

        boolean powerCutOff = setMotorPowerIndicator(motor, progress);
        if (powerCutOff) {
            sendCommand(commandMotor, ADKManager.ACTION_POWER_OFF, null);
        } else {
            sendCommand(commandMotor, ADKManager.ACTION_POWER_ON, new byte[] {ADKManager.DIRECTION_LEFT, toUnsignedByte(progress)});
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

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
     * Toggle motor power
     *
     * @param motor
     */
    private void onToggleMotorPower(ToggleButton motor, byte command) {
        boolean powerOn = motor.isChecked();

        if (powerOn) {
            sendCommand(command, ADKManager.ACTION_POWER_ON, new byte[] {ADKManager.DIRECTION_LEFT, (byte) MAX_MOTOR_SPEED});
        } else {
            sendCommand(command, ADKManager.ACTION_POWER_OFF, null);
        }
    }

    /**
     * Send angle to ADK
     *
     * @param view
     */
    private void onSendAngle(View view) {
        if (!TextUtils.isEmpty(mTextAngle.getText())) {
            try {
                int signedAngle = Integer.parseInt(mTextAngle.getText().toString());
                byte angle = toUnsignedByte(signedAngle);
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_ROTATE_BY_ANGLE, new byte[] {angle});
            } catch (Exception e) {
                Toast.makeText(this, "Invalid angle, please use values in range of 0 - 180", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Fixes motor toggle button state
     *
     * @param motor
     * @param progress
     * @return Whether power was cut off
     */
    private boolean setMotorPowerIndicator(ToggleButton motor, int progress) {
        boolean powerCutOff = false;
        if (progress > 0 && !motor.isChecked()) {
            motor.setChecked(true);
        } else if (progress == 0 && motor.isChecked()) {
            motor.setChecked(false);
            powerCutOff = true;
        }

        return powerCutOff;
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
        mBtnMotor1.setEnabled(enabled);
        mSpeedMotor1.setEnabled(enabled);
        mBtnMotor2.setEnabled(enabled);
        mSpeedMotor2.setEnabled(enabled);
        mTextAngle.setEnabled(enabled);
        mBtnSendAngle.setEnabled(enabled);
        mBtnResetAngle.setEnabled(enabled);
        mBtnRotateLeft.setEnabled(enabled);
        mBtnRotateRight.setEnabled(enabled);
        mBtnStopRotation.setEnabled(enabled);
    }

    private static byte toUnsignedByte(int b) {
        return (byte) (b & 0xFF);
    }
}