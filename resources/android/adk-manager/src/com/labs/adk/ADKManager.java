package com.labs.adk;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;
import com.labs.commons.SLog;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Protocol: [command - 1 byte][action - 1 byte][data - X bytes]
 *
 * @author Amir Lazarovich
 */
public class ADKManager implements Runnable {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "ADKManager";
    private static final String ACTION_USB_PERMISSION = "com.labs.adk.action.USB_PERMISSION";

    // adk-commands
    public static final byte COMMAND_STAND_BY = 1;
    public static final byte COMMAND_MOTORS = 2;
    public static final byte COMMAND_ROTATE = 3;

    // adk-actions
    public static final byte ACTION_MOTOR_POWER = 1;
    public static final byte ACTION_ORIENTATION = 2;
    public static final byte ACTION_TILT_LEFT_RIGHT = 3;
    public static final byte ACTION_TILT_UP_DOWN = 4;
    public static final byte ACTION_ON = 5;
    public static final byte ACTION_OFF = 6;

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private UsbManager mUsbManager;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private ExecutorService mPool;
    private Context mContext;
    private Handler mHandler;
    private Callback mCallback;
    private Thread mCommunicationThread;
    private final Object[] mLock;

    private boolean mConnected = false;
    private Timer mTimer;
    private BroadcastReceiver mUsbReceiver;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////

    public ADKManager(Context context, Callback callback) {
        mContext = context;
        mHandler = new Handler();
        mCallback = callback;
        mPool = Executors.newCachedThreadPool();
        mLock = new Object[0];
    }


    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    /**
     * Connect to the ADK
     */
    public void connect() {
        mTimer = new Timer();
        TimerTask reconnectTask = new TimerTask() {

            @Override
            public void run() {
                synchronized (mLock) {
                    if (!mConnected) {
                        SLog.d(TAG, "Connecting to ADK...");
                        connectToADK();
                    } else {
                        mTimer.cancel();
                    }
                }
            }
        };

        try {
            mTimer.schedule(reconnectTask, 0, 5000);
        } catch (IllegalStateException e) {
            SLog.e(TAG, "Can't schedule a task on a canceled timer", e);
        }
    }

    /**
     * Connect to the ADK device
     */
    void connectToADK() {
        synchronized (mLock) {
            mUsbManager = UsbManager.getInstance(mContext);
            PendingIntent permissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);

            // register receiver
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
            filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
            mUsbReceiver = new UsbReceiver();
            mContext.registerReceiver(mUsbReceiver, filter);

            // assume the only connected usb device is our ADK
            UsbAccessory[] accessories = mUsbManager.getAccessoryList();
            UsbAccessory accessory = (accessories == null) ? null : accessories[0];

            if (accessory != null) {
                if (mUsbManager.hasPermission(accessory)) {
                    openAccessory(accessory);
                } else {
                    mUsbManager.requestPermission(accessory, permissionIntent);
                }
            }
        }
    }

    /**
     * Disconnect from the ADK
     */
    public void disconnect() {
        synchronized (mLock) {
            SLog.d(TAG, "Disconnecting from the ADK device");
            mConnected = false;
            if (mTimer != null) {
                mTimer.cancel();
            }

            if (mUsbReceiver != null) {
                try {
                    mContext.unregisterReceiver(mUsbReceiver);
                } catch (Exception e) {
                    SLog.e(TAG, e, "Couldn't unregister receiver");
                } finally {
                    mUsbReceiver = null;
                }

            }

            if (mFileDescriptor != null) {
                try {
                    mFileDescriptor.close();
                } catch (IOException e) {
                    SLog.e(TAG, e, "Couldn't close file descriptor");
                } finally {
                    mFileDescriptor = null;
                }
            }

            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    SLog.e(TAG, e, "Couldn't close input stream");
                } finally {
                    mInputStream = null;
                }
            }

            if (mOutputStream != null) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    SLog.e(TAG, e, "Couldn't close output stream");
                } finally {
                    mOutputStream = null;
                }
            }

            mAccessory = null;
        }
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data    May also be null if there's no data (if you read this, you rock!)
     */
    public void sendCommand(final byte command, final byte action, final byte[] data) {
        mPool.execute(new Runnable() {
            @Override
            public void run() {
                int dataLength = ((data != null) ? data.length : 0);

                ByteBuffer buffer = ByteBuffer.allocate(2 + dataLength);
                buffer.put(command);
                buffer.put(action);
                if (data != null) {
                    buffer.put(data);
                }

                if (mOutputStream != null) {
                    try {
                        SLog.d(TAG, "sendCommand: Sending data to ADK device: " + buffer);
                        mOutputStream.write(buffer.array());
                    } catch (IOException e) {
                        SLog.e(TAG, e, "sendCommand: Failed to send command to ADK device");
                        reconnect();
                    }
                } else {
                    SLog.d(TAG, "sendCommand: Send failed: mOutStream was null");
                    reconnect();
                }
            }
        });
    }

    public static String parseCommand(byte command) {
        switch (command) {
            case COMMAND_STAND_BY:
                return "Stand by";

            case COMMAND_MOTORS:
                return "Motors";

            case COMMAND_ROTATE:
                return "Rotate";

            default:
                return "Unknown";
        }
    }

    public static String parseAction(byte action) {
        switch (action) {
            case ACTION_MOTOR_POWER:
                return "Power";

            case ACTION_ORIENTATION:
                return "Orientation";

            case ACTION_TILT_LEFT_RIGHT:
                return "Tilt left right";

            case ACTION_TILT_UP_DOWN:
                return "Tilt up down";

            default:
                return "Unknown";
        }
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////

    /**
     * Try to reconnected
     */
    void reconnect() {
        SLog.i(TAG, "attempting to reconnect to ADK device");
        disconnect();
        connect();
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    /**
     * The running thread. It takes care of the communication between the Android and the ADK
     */
    @Override
    public void run() {
        int ret;
        byte[] buffer = new byte[16384];

        // Keeps reading messages forever.
        // There are probably a lot of messages in the buffer, each message 4 bytes.
        while (true) {
            try {
                ret = mInputStream.read(buffer);
                if (ret > 0) {
                    final boolean ack = buffer[0] == 1;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mCallback.onADKAckReceived(ack);
                        }
                    });
                }
            } catch (Exception e) {
                break;
            }

        }
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////

    /**
     * Open read and write to and from the ADK device
     *
     * @param accessory
     */
    void openAccessory(UsbAccessory accessory) {
        synchronized (mLock) {
            SLog.d(TAG, "Trying to attach ADK device");
            mFileDescriptor = mUsbManager.openAccessory(accessory);
            if (mFileDescriptor != null) {
                mAccessory = accessory;
                FileDescriptor fd = mFileDescriptor.getFileDescriptor();
                mInputStream = new FileInputStream(fd);
                mOutputStream = new FileOutputStream(fd);

                if (mCommunicationThread != null) {
                    mCommunicationThread.interrupt();
                }

                mCommunicationThread = new Thread(null, this, TAG);
                mCommunicationThread.start();
                mConnected = true;
                SLog.d(TAG, "Attached");
            } else {
                SLog.d(TAG, "openAccessory: accessory open failed");
            }
        }
    }

    /**
     * Run on UI thread
     *
     * @param runnable
     */
    private void runOnUiThread(Runnable runnable) {
        mHandler.post(runnable);
    }

    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////
    private class UsbReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            SLog.d(TAG, "Got USB intent ", action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (mLock) {
                    UsbAccessory accessory = UsbManager.getAccessory(intent);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        SLog.d(TAG, "USB permission denied");
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                SLog.d(TAG, "BroadcastReceiver:: USB Attached");
                connect();
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if (accessory != null && accessory.equals(mAccessory)) {
                    SLog.d(TAG, "BroadcastReceiver:: USB Detached");
                    disconnect();
                }
            }
        }
    }
}
