package com.zepdroid;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.Toast;
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

public class ZepDroidActivity extends Activity implements ADKManager.Callback {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "ZepDroid";
    private static final String BASE_URI = "http://192.168.1.101:8099";
    private static final int PERIOD = 10 * 1000;  // repeat every sec.

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    ADKManager mADKManager;
    Preview preview;
    SocketIO socket;
    MediaPlayer player;
    Timer timer;

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
    public void onPause() {
        super.onPause();
        mADKManager.disconnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        mADKManager.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////
    @Override
    public void onADKAckReceived(boolean ack) {
        Log.d(TAG, "onADKAckReceived: " + ack);
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////
    /**
     * Initialization process
     */
    private void init() {
        timer = new Timer();
        mADKManager = new ADKManager(this, this);
        preview = new Preview(this);
        ((FrameLayout) findViewById(R.id.preview)).addView(preview);

        connectToSocket();
        initPlayer();
        initListeners();

        //Timer getPic = new Timer();
        //getPic.schedule(new TakePicTask(), 1000*3);
    }

    /**
     * Open connection
     */
    private void connectToSocket() {
        try {
            socket = new SocketIO(BASE_URI);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't open socket", e);
            Toast.makeText(this, "Couldn't open the socket", Toast.LENGTH_SHORT).show();
            finish();
        }

        socket.connect(new SocketCallbacks());
    }

    /**
     * Initialize the {@link MediaPlayer}
     */
    private void initPlayer() {
        try {
            player = new MediaPlayer();
            AssetFileDescriptor afd = ZepDroidActivity.this.getResources().openRawResourceFd(R.raw.wholelottalove);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setLooping(false);
            player.prepare();

        } catch (IOException e) {
            Log.e(TAG, "Couldn't prepare/create media player", e);
        }
    }

    /**
     * Initialize listeners
     */
    private void initListeners() {
        findViewById(R.id.btn_play_music).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // toggle playing
                if (ZepDroidActivity.this.player.isPlaying()) {
                    ZepDroidActivity.this.player.stop();
                } else {
                    ZepDroidActivity.this.player.start();
                }
            }
        });

        findViewById(R.id.btn_take_picture).setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                preview.camera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
        });
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        // We should show the user
        // mProgress.setVisibility(View.VISIBLE);
        // enableButtons(false);
        mADKManager.sendCommand(command, action, data);
    }

    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////

    private class SocketCallbacks implements IOCallback {
        @Override
        public void onDisconnect() {
            Log.d(TAG, "Connection terminated");
        }

        public void onConnect() {
            Log.d(TAG, "Connection established");

            timer.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    Log.d(TAG, "Trying to keepalive");
                    try {
                        HttpClient client = new DefaultHttpClient();
                        HttpGet request = new HttpGet();
                        request.setURI(new URI(BASE_URI + "/keepalive"));
                        client.execute(request);
                    } catch (Exception e) {
                        Log.e(TAG, "Couldn't keepalive", e);
                    }
                }
            }, 0, PERIOD);
        }


        @Override
        public void onMessage(String data, IOAcknowledge ack) {
            Log.d(TAG, "onMessage");
        }


        @Override
        public void onMessage(JSONObject json, IOAcknowledge ack) {
            Log.d(TAG, "onMessagejson");
        }

        @Override
        public void on(String event, IOAcknowledge ack, Object... args) {
            Log.d(TAG, "on " + args[0]);
            if (args[0].equals("keep_alive")) {
                Log.d(TAG, "got back keep alive from socket");
                return;
            }

            if (args[0].equals("ngn1start") && args[1].equals("up")) {
                Log.d(TAG, "ng1Start up");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_ON,
                        null);
            }

            if (args[0].equals("ngn1stop") && args[1].equals("up")) {
                Log.d(TAG, "ng1Stop up");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }

            if (args[0].equals("ngn2start") && args[1].equals("up")) {
                Log.d(TAG, "ng2Start up");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_ON,
                        null);
            }

            if (args[0].equals("ngn2stop") && args[1].equals("up")) {
                Log.d(TAG, "ng2Stop up");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }

            if (args[0].equals("center") && args[1].equals("up")) {
                Log.d(TAG, "center up");
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_RESET_ROTATION,
                        null);

            }

            if (args[0].equals("power_on") && args[1].equals("up")) {
                Log.d(TAG, "power on");
                sendCommand(ADKManager.COMMAND_STAND_BY,
                        ADKManager.ON,
                        null);
            }

            if (args[0].equals("power_off") && args[1].equals("up")) {
                Log.d(TAG, "power off");
                sendCommand(ADKManager.COMMAND_STAND_BY,
                        ADKManager.OFF,
                        null);
            }

            if (args[0].equals("forward") && args[1].equals("up")) {
                Log.d(TAG, "up: forward");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }
            if (args[0].equals("forward") && args[1].equals("down")) {
                Log.d(TAG, "down: forward");
                byte speed = (byte) 255;
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_ON,
                        new byte[]{ADKManager.DIRECTION_LEFT, speed});

            }
            if (args[0].equals("left") && args[1].equals("up")) {
                Log.d(TAG, "up: left");
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_END_ROTATE,
                        null);
            }
            if (args[0].equals("left") && args[1].equals("down")) {
                Log.d(TAG, "down: left");
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_START_ROTATE,
                        new byte[]{ADKManager.DIRECTION_LEFT});
            }
            if (args[0].equals("right") && args[1].equals("up")) {
                Log.d(TAG, "up: right");
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_END_ROTATE,
                        null);
            }
            if (args[0].equals("right") && args[1].equals("down")) {
                Log.d(TAG, "down: right");
                sendCommand(
                        ADKManager.COMMAND_ROTATE,
                        ADKManager.ACTION_START_ROTATE,
                        new byte[]{ADKManager.DIRECTION_RIGHT});
            }
            if (args[0].equals("back") && args[1].equals("up")) {
                Log.d(TAG, "up: back");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }
            if (args[0].equals("back") && args[1].equals("down")) {
                Log.d(TAG, "down: back");
                byte speed = (byte) 255;
                sendCommand(
                        ADKManager.COMMAND_MOTOR_2,
                        ADKManager.ACTION_POWER_ON,
                        new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
            }

            if (args[0].equals("elevate_up") && args[1].equals("up")) {
                Log.d(TAG, "up: elevate_up");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }
            if (args[0].equals("elevate_up") && args[1].equals("down")) {
                Log.d(TAG, "down: elevate_up");
                byte speed = (byte) 255;
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_ON,
                        new byte[]{ADKManager.DIRECTION_LEFT, (byte) speed});
            }

            if (args[0].equals("elevate_down") && args[1].equals("up")) {
                Log.d(TAG, "up: elevate_down");
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_OFF,
                        null);
            }
            if (args[0].equals("elevate_down") && args[1].equals("down")) {
                Log.d(TAG, "down: elevate_down");
                byte speed = (byte) 255;
                sendCommand(
                        ADKManager.COMMAND_MOTOR_1,
                        ADKManager.ACTION_POWER_ON,
                        new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
            }

            if (args[0].equals("music") && args[1].equals("up")) {
                Log.d(TAG, "up: music");
                // toggle playing
                if (ZepDroidActivity.this.player.isPlaying()) {
                    ZepDroidActivity.this.player.stop();

                } else {
                    ZepDroidActivity.this.player.start();
                }
            }
            if (args[0].equals("music") && args[1].equals("down")) {
                Log.d(TAG, "down: music");
            }

            if (args[0].equals("picture") && args[1].equals("up")) {
                Log.d(TAG, "up: picture");
                preview.camera.takePicture(shutterCallback, rawCallback, null, jpegCallback);
            }

            if (args[0].equals("picture") && args[1].equals("down")) {
                Log.d(TAG, "down: picture");
            }

        }

        @Override
        public void onError(SocketIOException socketIOException) {
            Log.e(TAG, "onError", socketIOException);
        }
    }

    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            Log.d(TAG, "onShutter");
        }
    };

    PictureCallback rawCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "onPictureTaken - raw");
        }
    };

    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length);
            System.out.print("Length:" + data.length);
            new SentPicTask().execute(data);
            Log.d(TAG, "onPictureTaken - jpeg");
        }
    };

    private class SentPicTask extends AsyncTask<byte[], Integer, Long> {

        @Override
        protected Long doInBackground(byte[]... params) {
            Log.d(TAG, " params[0] wrote bytes: " + params[0].length);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(BASE_URI);

            InputStream dataStream = new ByteArrayInputStream(params[0]);

            try {
                Log.d(TAG, " before sending 0  " + dataStream.available() );
            } catch (IOException e) {
                Log.e(TAG, "The stream was closed", e);
            }

            InputStreamEntity reqEntity;
            try {
                reqEntity = new InputStreamEntity(dataStream, dataStream.available());
                reqEntity.setContentType("binary/octet-stream");

                Log.d(TAG, " before sending 1" + reqEntity.getContentLength() );

                //reqEntity.setChunked(true); // Send in multiple parts if needed
                httppost.setEntity(reqEntity);
                httpclient.execute(httppost);
            } catch (Exception e) {
                Log.e(TAG, "Couldn't send image to server", e);
            }

            Log.d(TAG, "Async op");
            return null;
        }




    }

    private class TakePicTask extends TimerTask{

        public void run(){
            preview.camera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }
}
