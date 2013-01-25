package com.helidroid.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.helidroid.R;
import com.helidroid.managers.SocketManager;
import com.labs.adk.ADKManager;
import com.labs.commons.AnimUtils;
import com.labs.commons.SLog;

/**
 * @author Amir Lazarovich
 */
public class MainActivity extends Activity implements SocketManager.SocketListener {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "MainActivity";
    static final int SENT_COMMAND = 1;
    static final int ACK_RECEIVED = 2;

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private SocketManager mSocketManager;
    TextView mTxtLog;
    TextView mTxtAck;
    ProgressBar mLoading;

    ///////////////////////////////////////////////
    // Activity Flow
    ///////////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.helidroid.R.layout.activity_main);
        init();
    }

    /**
     * Initialization process
     */
    private void init() {
        mSocketManager = new SocketManager(this, this);
        mTxtLog = (TextView) findViewById(R.id.txt_log);
        mTxtAck = (TextView) findViewById(R.id.txt_ack);
        mLoading = (ProgressBar) findViewById(R.id.loading);

        mSocketManager.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSocketManager.disconnect();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    @Override
    public void onSentCommand(byte command, byte action, byte[] data) {
        SLog.d(TAG, "onSentCommand");
        mHandler.sendMessage(Message.obtain(null,
                SENT_COMMAND,
                command,
                action,
                (data != null) ? String.valueOf(data[0]) : "{empty}"));
    }

    @Override
    public void onAckReceived(boolean ack) {
        SLog.d(TAG, "onAckReceived: %b", ack);
        mHandler.sendMessage(Message.obtain(null,
                ACK_RECEIVED,
                ack));
    }

    @Override
    public void onSocketFailure() {
        SLog.w(TAG, "onSocketFailure");
        Toast.makeText(this, "Socket failure", Toast.LENGTH_SHORT).show();
        finish();
    }

    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////

    /**
     * Handle View changes on the UI thread
     */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SENT_COMMAND:
                    byte command = (byte) msg.arg1;
                    byte action = (byte) msg.arg2;
                    String value = (String) msg.obj;

                    mTxtLog.setText(getString(R.string.log_template,
                            ADKManager.parseCommand(command),
                            ADKManager.parseAction(action),
                            value));
                    AnimUtils.playTogether(
                            AnimUtils.prepareHideViewAnimated(mTxtAck, AnimUtils.MEDIUM_ANIM_TIME),
                            AnimUtils.prepareShowViewAnimated(mLoading, AnimUtils.MEDIUM_ANIM_TIME)
                    );
                    break;

                case ACK_RECEIVED:
                    boolean ack = (Boolean) msg.obj;

                    mTxtAck.setTextColor(ack ?
                            getResources().getColor(android.R.color.holo_green_light) :
                            getResources().getColor(android.R.color.holo_red_light));
                    mTxtAck.setText(getString(R.string.ack_template, ack));
                    AnimUtils.playTogether(
                            AnimUtils.prepareHideViewAnimated(mLoading, AnimUtils.MEDIUM_ANIM_TIME),
                            AnimUtils.prepareShowViewAnimated(mTxtAck, AnimUtils.MEDIUM_ANIM_TIME)
                    );
                    break;
            }

        }
    };
}