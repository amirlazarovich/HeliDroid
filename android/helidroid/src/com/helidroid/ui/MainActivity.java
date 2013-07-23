package com.helidroid.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.*;
import com.helidroid.App;
import com.helidroid.R;
import com.helidroid.R.id;
import com.helidroid.R.string;
import com.helidroid.managers.SocketManager;
import com.labs.commons.ADK;
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
    static final int ADK_CONNECTION_STATUS_CHANGE = 3;
    static final int SOCKET_CONNECTION_STATUS_CHANGE = 4;

    private static final int REQUEST_SETTINGS_ACTIVITY = 1;

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private SocketManager mSocketManager;
    ListView mListLog;
    TextView mTxtAck;
    ProgressBar mLoading;
    private TextView mTxtServerAddress;
    private TextView mTxtAdkStatus;
    private TextView mTxtSocketStatus;
    private ArrayAdapter<String> mAdapter;

    // member-flags
    boolean mIsLogActive;

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
        mIsLogActive = true;
        mSocketManager = new SocketManager(this, this);
        mListLog = (ListView) findViewById(id.list_log);
        mTxtAck = (TextView) findViewById(R.id.txt_ack);
        mLoading = (ProgressBar) findViewById(R.id.loading);
        mTxtServerAddress = (TextView) findViewById(R.id.txt_server_address);
        mTxtAdkStatus = (TextView) findViewById(R.id.txt_adk_status);
        mTxtSocketStatus = (TextView) findViewById(R.id.txt_socket_status);
        updateServerAddress();

        mAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, android.R.id.text1);
        mListLog.setAdapter(mAdapter);

        mSocketManager.connect(App.sConsts.SERVER_ADDRESS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSocketManager.disconnect();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_SETTINGS_ACTIVITY:
                updateServerAddress();
                mSocketManager.changeServerAddress(App.sConsts.SERVER_ADDRESS);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case id.menu_settings:
                openSettings();
                return true;

            case id.menu_reconnect:
                mSocketManager.reconnect(App.sConsts.SERVER_ADDRESS);
                return true;

            case id.menu_clear_log:
                mAdapter.clear();
                return true;

            case id.menu_toggle_log:
                mIsLogActive = !mIsLogActive;
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////
    @Override
    public void onConnected() {
        mHandler.sendMessage(Message.obtain(null,
                ADK_CONNECTION_STATUS_CHANGE,
                true));
    }

    @Override
    public void onDisconnected() {
        mHandler.sendMessage(Message.obtain(null,
                ADK_CONNECTION_STATUS_CHANGE,
                false));
    }

    @Override
    public void onSocketConnected() {
        mHandler.sendMessage(Message.obtain(null,
                SOCKET_CONNECTION_STATUS_CHANGE,
                true));
    }

    @Override
    public void onSocketDisconnected() {
        mHandler.sendMessage(Message.obtain(null,
                SOCKET_CONNECTION_STATUS_CHANGE,
                false));
    }

    @Override
    public void onSentCommand(byte command, byte action, byte[] data) {
        SLog.d(TAG, "onSentCommand");
        mHandler.sendMessage(Message.obtain(null,
                SENT_COMMAND,
                command,
                action,
                data));
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
        Toast.makeText(this, "Couldn't connect to socket, please replace Server Address", Toast.LENGTH_SHORT).show();
        openSettings();
    }

    //////////////////////////////////////////
    // Private
    //////////////////////////////////////////

    /**
     * Display server address on screen
     */
    private void updateServerAddress() {
        mTxtServerAddress.setText(String.format("Server address: %s", App.sConsts.SERVER_ADDRESS));
    }

    /**
     * Open settings activity
     */
    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivityForResult(intent, REQUEST_SETTINGS_ACTIVITY);
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
                    if (mIsLogActive) {
                        byte command = (byte) msg.arg1;
                        byte action = (byte) msg.arg2;
                        byte[] data = (byte[]) msg.obj;
                        String throttle = "{empty}";
                        String pitch = "{empty}";
                        String roll = "{empty}";
                        String yaw = "{empty}";
                        if (data != null && data.length > 0) {
                            throttle = String.valueOf(data[0]);
                            if (data.length > 1) {
                                pitch = String.valueOf(data[1]);
                                if (data.length > 2) {
                                    roll = String.valueOf(data[2]);
                                    if (data.length > 3) {
                                        yaw = String.valueOf(data[3]);
                                    }
                                }
                            }
                        }

                        mAdapter.add(getString(R.string.log_template,
                                ADK.parseCommand(command),
                                ADK.parseAction(action),
                                throttle, pitch, roll, yaw));
                        mListLog.smoothScrollToPosition(mAdapter.getCount() - 1);
                    }

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

                case ADK_CONNECTION_STATUS_CHANGE: {
                    boolean connected = (Boolean) msg.obj;

                    if (connected) {
                        mTxtAdkStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                        mTxtAdkStatus.setText(getString(string.adk_connected));
                    } else {
                        mTxtAdkStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        mTxtAdkStatus.setText(getString(string.adk_disconnected));
                    }
                    break;
                }

                case SOCKET_CONNECTION_STATUS_CHANGE: {
                    boolean connected = (Boolean) msg.obj;

                    if (connected) {
                        mTxtSocketStatus.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                        mTxtSocketStatus.setText(getString(string.socket_connected));
                    } else {
                        mTxtSocketStatus.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                        mTxtSocketStatus.setText(getString(string.socket_disconnected));
                    }
                    break;
                }

            }

        }
    };
}