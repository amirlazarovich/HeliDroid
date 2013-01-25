package com.helidroid.managers;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import com.helidroid.App;
import com.helidroid.R;
import com.helidroid.commons.Event;
import com.helidroid.commons.EventType;
import com.labs.adk.ADKManager;
import com.labs.adk.Callback;
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
        mTimer = new Timer();
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
     */
    private void connectToSocket() {
        if (mSocket != null && mSocket.isConnected()) {
            SLog.d(TAG, "Already connected to socket");
            return;
        }

        try {
            mSocket = new SocketIO(App.sConsts.SERVER_ADDRESS);
        } catch (Exception e) {
            SLog.e(TAG, "Couldn't open socket", e);
            mListener.onSocketFailure();
        }

        mSocket.connect(this);
    }
    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    /**
     * Disconnect from all attached devices
     */
    public void disconnect() {
        mADKManager.disconnect();
        mSocket.disconnect();
    }

    /**
     * Connect to attached devices
     */
    public void connect() {
        mADKManager.connect();
        connectToSocket();
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    @Override
    public void onDisconnect() {
        SLog.d(TAG, "Connection terminated");
    }

    public void onConnect() {
        SLog.d(TAG, "Connection established");

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
                }
            }
        }, 0, PERIOD);
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
            case MOTORS:
                onMotorAction(eventType, (Integer) args[1]);
                break;

            case ROTATION:
                onRotationAction(eventType, (Integer) args[1]);
                break;

            case FUNCTION:
                onFunctionAction(eventType, (args.length > 1) ? args[1] : null);
                break;

            default:
                SLog.w(TAG, "Unknown event received: %s", rawEvent);
        }
    }

    @Override
    public void onError(SocketIOException socketIOException) {
        SLog.e(TAG, "onError", socketIOException);
    }

    @Override
    public void onADKAckReceived(boolean ack) {
        mListener.onAckReceived(ack);
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////
    /**
     * Handle actions directed to the motors
     *
     * @param eventType
     * @param value
     */
    private void onMotorAction(EventType eventType, Integer value) {
        switch (eventType) {
            case POWER:
                sendCommand(
                        ADKManager.COMMAND_MOTORS,
                        ADKManager.ACTION_MOTOR_POWER,
                        new byte[]{value.byteValue()});
                break;

            case STAND_BY:
                sendCommand(
                        ADKManager.COMMAND_STAND_BY,
                        value.byteValue(),
                        null);
                break;

            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
        }
    }

    /**
     * Handle actions directed to rotations
     *
     * @param eventType
     * @param value
     */
    private void onRotationAction(EventType eventType, Integer value) {
        switch (eventType) {
            case ORIENTATION:
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_ORIENTATION,
                        new byte[]{value.byteValue()});
                break;

            case TILT_UP_DOWN:
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_TILT_UP_DOWN,
                        new byte[]{value.byteValue()});
                break;

            case TILT_LEFT_RIGHT:
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_TILT_LEFT_RIGHT,
                        new byte[]{value.byteValue()});
                break;

            default:
                SLog.w(TAG, "Unknown event type detected: %s", eventType);
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
                SLog.d(TAG, " before sending 0  " + dataStream.available() );
            } catch (IOException e) {
                SLog.e(TAG, "The stream was closed", e);
            }

            InputStreamEntity reqEntity;
            try {
                reqEntity = new InputStreamEntity(dataStream, dataStream.available());
                reqEntity.setContentType("binary/octet-stream");

                SLog.d(TAG, " before sending 1" + reqEntity.getContentLength() );

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

        public void run(){
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    public interface SocketListener {
        void onSentCommand(byte command, byte action, byte[] data);
        void onAckReceived(boolean ack);
        void onSocketFailure();
    }
}
