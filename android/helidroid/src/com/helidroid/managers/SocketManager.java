package com.helidroid.managers;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.text.TextUtils;
import com.helidroid.App;
import com.helidroid.R;
import com.helidroid.commons.Event;
import com.helidroid.commons.EventType;
import com.labs.adk.commons.utils.Utils;
import com.labs.adk.ADKManager;
import com.labs.adk.Callback;
import com.labs.commons.ADK;
import com.labs.commons.SLog;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Amir Lazarovich
 */
public class SocketManager implements IOCallback, Callback {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "SocketManager";
    private static final int PERIOD = 10000; // 10 seconds

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private ADKManager mADKManager;
    private Camera mCamera;
    private MediaPlayer mPlayer;
    private Timer mTimer;
    private SocketIO mSocket;

    // member-listeners
    private SocketListener mListener;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////

    public SocketManager(Context context, SocketListener listener) {
        mListener = listener;
        mADKManager = new ADKManager(context, this);
        initPlayer(context);
        initCamera();

        //Timer getPic = new Timer();
        //getPic.schedule(new TakePicTask(), 1000*3);
    }

    /**
     * Initialize the {@link android.media.MediaPlayer}
     *
     * @param context
     */
    private void initPlayer(Context context) {
        try {
            mPlayer = new MediaPlayer();
            AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.wholelottalove);
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setLooping(false);
            mPlayer.prepare();

        } catch (IOException e) {
            SLog.e(TAG, "Couldn't prepare/create media player", e);
        }
    }

    /**
     * Initialize the camera object
     */
    private void initCamera() {
        mCamera = Camera.open();
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setRotation(90);
        mCamera.setParameters(parameters);
        mCamera.setDisplayOrientation(90);
    }

    /**
     * Open connection
     *
     * @param serverAddress
     */
    private void connectToSocket(String serverAddress) {
        if (mSocket != null && mSocket.isConnected()) {
            SLog.d(TAG, "Already connected to socket");
            return;
        }

        try {
            mSocket = new SocketIO(serverAddress);
            mSocket.connect(this);
        } catch (Exception e) {
            SLog.e(TAG, "Couldn't open socket", e);
            mListener.onSocketFailure();
        }
    }
    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    /**
     * Reconnect to all attached devices
     *
     * @param serverAddress
     */
    public void reconnect(String serverAddress) {
        disconnect();
        connect(serverAddress);
    }


    /**
     * Disconnect from all attached devices
     */
    public void disconnect() {
        if (mADKManager != null) {
            mADKManager.disconnect();
        }

        if (mSocket != null) {
            if (mTimer != null) {
                mTimer.cancel();
            }

            mSocket.disconnect();
        }
    }

    /**
     * Connect to attached devices
     */
    public void connect(String serverAddress) {
        if (!TextUtils.isEmpty(serverAddress)) {
            mADKManager.connect();
            connectToSocket(serverAddress);
        } else {
            SLog.w(TAG, "Couldn't connect to server since no address was given");
        }
    }

    /**
     * Replace server address
     *
     * @param serverAddress
     */
    public void changeServerAddress(String serverAddress) {
        if (!mADKManager.isConnected()) {
            mADKManager.connect();
        }

        if (mSocket != null) {
            if (mTimer != null) {
                mTimer.cancel();
            }

            mSocket.disconnect();
        }

        connectToSocket(serverAddress);
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    @Override
    public void onDisconnect() {
        SLog.d(TAG, "Connection terminated");
        mListener.onSocketDisconnected();
    }

    public void onConnect() {
        SLog.d(TAG, "Connection established");

        if (mTimer != null) {
            mTimer.cancel();
        }

        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                SLog.d(TAG, "Trying to keepalive");
                try {
                    HttpClient client = new DefaultHttpClient();
                    HttpGet request = new HttpGet();
                    request.setURI(new URI(App.sConsts.SERVER_ADDRESS + "/keepalive"));
                    client.execute(request);
                } catch (Exception e) {
                    SLog.e(TAG, "Couldn't keepalive", e);
                    mTimer.cancel();
                    mListener.onSocketDisconnected();
                }
            }
        }, 0, PERIOD);

        mListener.onSocketConnected();
    }


    @Override
    public void onMessage(String data, IOAcknowledge ack) {
        SLog.d(TAG, "onMessage");
    }


    @Override
    public void onMessage(JSONObject json, IOAcknowledge ack) {
        SLog.d(TAG, "onMessagejson");
    }

    @Override
    public void on(String rawEvent, IOAcknowledge ack, Object... args) {
        SLog.d(TAG, "on:: event: %s, args[0]: %s", rawEvent, args[0]);

        Event event = Event.getByValue(rawEvent);
        EventType eventType = EventType.getByValue(args[0].toString());
        switch (event) {
            case CONTROL:
                if (args.length >= 2) {
                    onControlAction(eventType, (JSONObject) args[1]);
                } else {
                    SLog.w(TAG, "Missing values to process command Control");
                }
                break;

            case SETTINGS:
                if (args.length >= 2) {
                    onSettingsAction(eventType, (JSONObject) args[1]);
                } else {
                    SLog.w(TAG, "Missing values to process command Settings");
                }
                break;

            case GET:
                onGetAction(eventType);
                break;

            case FUNCTION:
                onFunctionAction(eventType, (args.length > 1) ? args[1] : null);
                break;

            case KEEP_ALIVE:
                SLog.d(TAG, "Keeping alive");
                break;

            default:
                SLog.w(TAG, "Unknown event received: %s", rawEvent);
        }
    }

    @Override
    public void onDataReceived(int command, byte[] data, int dataLength) {
        SLog.d(TAG, "onDataReceived:: command: %d, action: %d, dataLength: %d", command, data[1], dataLength);

        int action = data[1];
        switch (action) {
            case ADK.ACTION_TUNE:
                if (dataLength >= 38) {
                    float pitchKp = Utils.bytesToFloat(data, 2);
                    float pitchKi = Utils.bytesToFloat(data, 6);
                    float pitchKd = Utils.bytesToFloat(data, 10);

                    float rollKp = Utils.bytesToFloat(data, 14);
                    float rollKi = Utils.bytesToFloat(data, 18);
                    float rollKd = Utils.bytesToFloat(data, 22);

                    float yawKp = Utils.bytesToFloat(data, 26);
                    float yawKi = Utils.bytesToFloat(data, 30);
                    float yawKd = Utils.bytesToFloat(data, 34);

                    try {
                        JSONObject response = new JSONObject();
                        response.put("type", EventType.ACTION_TUNE.getValue());
                        JSONObject dataWrapper = new JSONObject();
                        dataWrapper.put("pitch", createJsonPID(pitchKp, pitchKi, pitchKd));
                        dataWrapper.put("roll", createJsonPID(rollKp, rollKi, rollKd));
                        dataWrapper.put("yaw", createJsonPID(yawKp, yawKi, yawKd));
                        response.put("data", dataWrapper);
                        SLog.d(TAG, "Send tuning parameters: %s", response.toString());
                        mSocket.emit(Event.RESPONSE.getValue(), response);
                    } catch (Exception e) {
                        SLog.e(TAG, "Couldn't create json response", e);
                    }
                } else {
                    SLog.w(TAG, "Missing values to process action Tune");
                }
                break;
        }
    }

    @Override
    public void onError(SocketIOException socketIOException) {
        SLog.e(TAG, "onError", socketIOException);
    }

    @Override
    public void onAckReceived(boolean ack) {
        mListener.onAckReceived(ack);
    }

    @Override
    public void onConnected() {
        mListener.onConnected();
    }

    @Override
    public void onDisconnected() {
        mListener.onDisconnected();
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////

    /**
     * Handle actions directed to the motors
     *
     * @param eventType
     * @param data
     */
    private void onControlAction(EventType eventType, JSONObject data) {
        switch (eventType) {
            case ACTION_STICKS:
                sendCommand(ADK.COMMAND_CONTROL,
                        ADK.ACTION_STICKS,
                        new byte[]{
                                (byte) data.optInt("throttle"),
                                (byte) data.optInt("pitch"),
                                (byte) data.optInt("roll"),
                                (byte) data.optInt("yaw")
                        });
                break;

            case ACTION_STANDBY:
                sendCommand(ADK.COMMAND_CONTROL,
                        ADK.ACTION_STANDBY,
                        new byte[]{
                                data.optBoolean("on", false) ? (byte) 1 : (byte) 0
                        });
                break;
            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
        }
    }

    /**
     * Handle actions directed to settings
     *
     * @param eventType
     * @param data
     */
    private void onSettingsAction(EventType eventType, JSONObject data) {
        switch (eventType) {
            case ACTION_TUNE:
                float Kp = (float) data.optDouble("kp");
                float Ki = (float) data.optDouble("ki");
                float Kd = (float) data.optDouble("kd");

                byte[] bKp = Utils.floatToBytes(Kp);
                byte[] bKi = Utils.floatToBytes(Ki);
                byte[] bKd = Utils.floatToBytes(Kd);

                sendCommand(ADK.COMMAND_SETTINGS,
                        ADK.ACTION_TUNE,
                        new byte[]{
                                (byte) data.optInt("type"),
                                bKp[0],
                                bKp[1],
                                bKp[2],
                                bKp[3],

                                bKi[0],
                                bKi[1],
                                bKi[2],
                                bKi[3],

                                bKd[0],
                                bKd[1],
                                bKd[2],
                                bKd[3]
                        });
                break;
        }
    }

    /**
     * Handle actions directed to get information from the adk device
     *
     * @param eventType
     */
    private void onGetAction(EventType eventType) {
        switch (eventType) {
            case ACTION_TUNE:
                sendCommand(ADK.COMMAND_GET,
                        ADK.ACTION_TUNE,
                        null);
                break;
        }
    }

    /**
     * Handle miscellaneous functions
     *
     * @param eventType
     * @param data
     */
    private void onFunctionAction(EventType eventType, Object data) {
        switch (eventType) {
            case TAKE_PICTURE:
                mCamera.takePicture(shutterCallback, rawCallback, null, jpegCallback);
                break;

            case TOGGLE_MUSIC:
                if (mPlayer.isPlaying()) {
                    mPlayer.stop();
                } else {
                    mPlayer.start();
                }
                break;

            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
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
        mADKManager.sendCommand(command, action, data);
        mListener.onSentCommand(command, action, data);
    }

    private JSONObject createJsonPID(float kp, float ki, float kd) {
        JSONObject pid = new JSONObject();
        try {
            pid.put("kp", kp);
            pid.put("ki", ki);
            pid.put("kd", kd);
        } catch (Exception e) {
            SLog.e(TAG, "Couldn't create PID json object", e);
        }

        return pid;
    }

    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////

    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            SLog.d(TAG, "onShutter");
        }
    };

    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            SLog.d(TAG, "onPictureTaken - raw");
        }
    };

    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            SLog.d(TAG, "onPictureTaken - wrote bytes: " + data.length);
            System.out.print("Length:" + data.length);
            new SentPicTask().execute(data);
            SLog.d(TAG, "onPictureTaken - jpeg");
        }
    };

    /**
     * Send picture taken to the server
     */
    private class SentPicTask extends AsyncTask<byte[], Integer, Long> {

        @Override
        protected Long doInBackground(byte[]... params) {
            SLog.d(TAG, " params[0] wrote bytes: " + params[0].length);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(App.sConsts.SERVER_ADDRESS);

            InputStream dataStream = new ByteArrayInputStream(params[0]);

            try {
                SLog.d(TAG, " before sending 0  " + dataStream.available());
            } catch (IOException e) {
                SLog.e(TAG, "The stream was closed", e);
            }

            InputStreamEntity reqEntity;
            try {
                reqEntity = new InputStreamEntity(dataStream, dataStream.available());
                reqEntity.setContentType("binary/octet-stream");

                SLog.d(TAG, " before sending 1" + reqEntity.getContentLength());

                //reqEntity.setChunked(true); // Send in multiple parts if needed
                httppost.setEntity(reqEntity);
                httpclient.execute(httppost);
            } catch (Exception e) {
                SLog.e(TAG, "Couldn't send image to server", e);
            }

            SLog.d(TAG, "Async op");
            return null;
        }
    }

    /**
     * Take pictures task
     */
    private class TakePicTask extends TimerTask {

        public void run() {
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    public interface SocketListener extends Callback {
        void onSentCommand(byte command, byte action, byte[] data);

        void onSocketFailure();

        void onSocketDisconnected();

        void onSocketConnected();
    }
}
