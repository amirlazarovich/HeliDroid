/**
 * Configuration file
 */
define(['log', 'config'], function (log, config) {
    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
    var TAG = "common";

    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
        COMMAND_CONTROL:"control",

        COMMAND_SETTINGS:"settings",

        COMMAND_GET:"get",

        ACTION_STICKS:"sticks",

        ACTION_STANDBY:"standby",

        ACTION_TUNE:"tune",

        ACTION_TILT:"tilt",

        ACTION_CALIBRATE_TILT:"calibrate_tilt",

        ACTION_TILT_OFFSET:"tilt_offset",

        TUNE_PITCH:1,

        TUNE_ROLL:2,

        TUNE_YAW:3,

        ARROW:{
            left:37,
            up:38,
            right:39,
            down:40
        },

        log:log,

        config: config,

        /**
         * Emit calculated value to connected socket.
         *
         * @param {socket.io} socket
         * @param {String} event
         * @param {String} type
         * @param {Object} data
         */
        sendToDevice:function (socket, event, type, data) {
            log.d(TAG, "sendToDevice: " + event + ":: " + type + ":: data: " + JSON.stringify(data));
            socket.emit(event,
                {
                    type:type,
                    data:data
                });
        }
    };
});
