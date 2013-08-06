/**
 * Screen - Settings
 *
 * @constructor
 */
define(['socket.io', 'config', 'log', "jquery", "prototype"], function (io, config, log, $) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var TAG = "Screen-No-Touch";

    var COMMAND_CONTROL = "control";
    var ACTION_STICKS = "sticks";

    var ARROW = {left: 37, up: 38, right: 39, down: 40 };
    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    var mSocket;
    var mThrottle;
    var mPitch;
    var mRoll;
    var mYaw;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////

    /**
     * Create a new Screen-Settings module handler
     */
    (function _ScreenSettings() {
        mThrottle = bind('throttle', 'pitch', 'yaw');
        mPitch = bind('pitch', 'roll', 'throttle');
        mRoll = bind('roll', 'yaw', 'pitch');
        mYaw = bind('yaw', 'throttle', 'roll');

        $('#btn-send').click(function() {
            sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                {
                    throttle: mThrottle.val(),
                    pitch: mPitch.val(),
                    roll: mRoll.val(),
                    yaw: mYaw.val()
                });
        });
    })();

    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
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

    function validate(evt) {
        var theEvent = evt || window.event;
        var key = theEvent.keyCode || theEvent.which;
        key = String.fromCharCode( key );
        var regex = /[-0-9]|\./;
        if( !regex.test(key) ) {
            theEvent.returnValue = false;
            if(theEvent.preventDefault) theEvent.preventDefault();
        }
    }

    function bind(itemName, nextItem, prevItem) {
        var item = $('#' + itemName);

        var itemValue = $('#' + itemName + '-val');
        itemValue.val(item.val());

        item.change(function(event) {
            itemValue.val($(this).val());
        });

        itemValue.change(function(event) {
            var value = Number($(this).val());
            if (value != Number.NaN && value >= Number(item.attr('min')) && value <= Number(item.attr('max'))) {
                item.val(value);
            }
        });

        itemValue.keypress(function(event) {
            validate(event);
        });

        item.keydown(function(event) {
            var keyCode = event.keyCode || event.which;
            var value = Number($(this).val());
            switch (keyCode) {
                case ARROW.up:
                    $('#' + prevItem).focus();
                    return false;

                case ARROW.down:
                    $('#' + nextItem).focus();
                    return false;
            }
        });

        return item;
    }

    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
       start:function() {
           mSocket = io.connect("/");
           mThrottle.focus();
       }
    };
});