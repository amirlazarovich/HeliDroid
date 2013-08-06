/**
 * Screen - Settings
 *
 * @constructor
 */
define(['socket.io', 'config', 'log', "jquery", "prototype"], function (io, config, log, $) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var TAG = "Screen-Settings";

    var COMMAND_SETTINGS = "settings";
    var COMMAND_GET = "get";
    var ACTION_TUNE = "tune";
    var ACTION_TILT = "tilt";
    var ACTION_CALIBRATE_TILT = "calibrate_tilt";
    var ACTION_TILT_OFFSET = "tilt_offset";
    var TUNE_PITCH = 1;
    var TUNE_ROLL = 2;
    var TUNE_YAW = 3;
    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    var mSocket;
    var autoRequestTilt = false;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////

    /**
     * Create a new Screen-Settings module handler
     */
    (function _ScreenSettings() {
        $('#pitch-submit').click(function() {
            onClick('pitch');
        });

        $('#roll-submit').click(function() {
            onClick('roll');
        });

        $('#yaw-submit').click(function() {
            onClick('yaw');
        });

        $('#all-submit').click(function() {
            onClick('pitch');
            onClick('roll');
            onClick('yaw');
        });

        $('#calibrate').click(function() {
            $(this).attr("disabled", "disabled");
            onCalibrate();
        });

        $('#tilt').click(function() {
            if (autoRequestTilt) {
                $(this).val("get tilt");
                autoRequestTilt = false;
            } else {
                $(this).val("stop");
                autoRequestTilt = true;
                $(this).attr("disabled", "disabled");
                onGetTilt();
            }
        });
    })();

    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
    /**
     * Invoked when the server sends a response
     *
     * @param {String} action
     * @param {Object} data
     */
    function onServerResponse(action, data) {
        log.d(TAG, "onServerResponse: action: " + action + ":: data: " + data);

        switch (action) {
            case ACTION_TUNE:
                setTunings('pitch', data.pitch);
                setTunings('roll', data.roll);
                setTunings('yaw', data.yaw);
                break;

            case ACTION_TILT:
                appendTilt(data);
                break;

            case ACTION_TILT_OFFSET:
                appendTiltOffset(data);
                break;
        }
    }

    function onCalibrate() {
        sendToDevice(COMMAND_SETTINGS, ACTION_CALIBRATE_TILT, null);
    }

    function onGetTilt() {
        sendToDevice(COMMAND_GET, ACTION_TILT, null);
    }

    /**
     * Emit calculated value to connected socket.
     *
     * @param {String} event
     * @param {String} type
     * @param {Object} data
     */
    function sendToDevice(event, type, data) {
        log.d(TAG, "sendToDevice: " + event + ":: " + type + ":: data: " + data);
        mSocket.emit(event,
            {
                type:type,
                data:data
            });
    }

    /**
     * Set values in text fields
     *
     * @param axis
     * @param pid
     */
    function setTunings(axis, pid) {
        $('#' + axis + '-kp').val(pid.kp);
        $('#' + axis + '-ki').val(pid.ki);
        $('#' + axis + '-kd').val(pid.kd);
    }

    /**
     * Append another tilt value
     *
     * @param data
     */
    function appendTilt(data) {
        var lbl = $('#tilt-lbl');
        lbl.html("<br/>pitch: " + data.pitch + ", roll: " + data.roll + ", yaw: " + data.yaw + lbl.html());
        $('#tilt').removeAttr("disabled");
        if (autoRequestTilt) {
            onGetTilt();
        }
    }

    /**
     * Append another tilt offset value
     *
     * @param data
     */
    function appendTiltOffset(data) {
        var lbl = $('#calibrate-lbl');
        lbl.html("<br/>pitch-offset: " + data.pitch + ", roll-offset: " + data.roll + ", yaw-offset: " + data.yaw + lbl.html());
        $('#calibrate').removeAttr("disabled");
    }

    /**
     * Get the protocol type of each axis
     *
     * @param axis
     * @return {Number}
     */
    function getType(axis) {
        switch (axis) {
            case 'pitch':
                return TUNE_PITCH;

            case 'roll':
                return TUNE_ROLL;

            case 'yaw':
                return TUNE_YAW;

            default:
                return 0;
        }
    }

    /**
     * Invoked after each "send" command
     *
     * @param axis
     */
    function onClick(axis) {
        var kp = $('#' + axis + '-kp').val();
        var ki = $('#' + axis + '-ki').val();
        var kd = $('#' + axis + '-kd').val();
        sendToDevice(COMMAND_SETTINGS, ACTION_TUNE, {
            type: getType(axis),
            kp: kp,
            ki: ki,
            kd: kd
        });
    }

    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
       start:function() {
           mSocket = io.connect("/");
           mSocket.on('response', onServerResponse);

           sendToDevice(COMMAND_GET, ACTION_TUNE, null);
           sendToDevice(COMMAND_GET, ACTION_TILT, null);
       }
    };
});