/**
 * Screen - Settings
 *
 * @constructor
 */
define(["socket.io", "jquery", "common", "prototype"], function (io, $, common) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var TAG = "Screen-No-Touch";
    var DEFAULT_SEND_VALUES_ON_CHANGE = true;
    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    var mSocket;
    var mThrottle;
    var mPitch;
    var mRoll;
    var mYaw;
    var mSendValuesOnChange;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////

    /**
     * Create a new Screen-No-Touch module handler
     */
    (function _ScreenNoTouch() {
        mSendValuesOnChange = DEFAULT_SEND_VALUES_ON_CHANGE;
        mThrottle = bind('throttle', 'pitch', 'yaw');
        mPitch = bind('pitch', 'roll', 'throttle');
        mRoll = bind('roll', 'yaw', 'pitch');
        mYaw = bind('yaw', 'throttle', 'roll');

        $('#btn-send').click(onBtnSendClick);
        $('#engineSwitch').change(onEngineChange);
        $('#autoSendSwitch').change(onAutoSendSwitchChange);
    })();

    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
    function validate(evt) {
        var theEvent = evt || window.event;
        var key = theEvent.keyCode || theEvent.which;
        key = String.fromCharCode( key );
        var regex = /[-0-9]|^\r$/;
        if( !regex.test(key) ) {
            theEvent.returnValue = false;
            if(theEvent.preventDefault) theEvent.preventDefault();
        }
    }

    function onBarsChange() {
        if (mSendValuesOnChange) {
            common.sendToDevice(mSocket, common.COMMAND_CONTROL, common.ACTION_STICKS,
                {
                    throttle: mThrottle.val(),
                    pitch: mPitch.val(),
                    roll: mRoll.val(),
                    yaw: mYaw.val()
                });
        }
    }

    function bind(itemName, nextItem, prevItem) {
        var item = $('#' + itemName);

        var itemValue = $('#' + itemName + '-val');
        itemValue.val(item.val());

        item.change(function(event) {
            itemValue.val($(this).val());
            onBarsChange();
        });

        itemValue.change(function(event) {
            var value = Number($(this).val());
            if (value != Number.NaN && value >= Number(item.attr('min')) && value <= Number(item.attr('max'))) {
                item.val(value);
                onBarsChange();
            }
        });

        itemValue.keypress(function(event) {
            validate(event);
        });

        item.keydown(function(event) {
            var keyCode = event.keyCode || event.which;
            var value = Number($(this).val());
            switch (keyCode) {
                case common.ARROW.up:
                    $('#' + prevItem).focus();
                    return false;

                case common.ARROW.down:
                    $('#' + nextItem).focus();
                    return false;
            }
        });

        $('#' + itemName + '-reset').click(function() {
            itemValue.val(0);
            item.val(0);
            onBarsChange();
        });

        return item;
    }

    /**
     * Invoked when the "send" button is clicked
     */
    function onBtnSendClick() {
        common.sendToDevice(mSocket, common.COMMAND_CONTROL, common.ACTION_STICKS,
            {
                throttle: mThrottle.val(),
                pitch: mPitch.val(),
                roll: mRoll.val(),
                yaw: mYaw.val()
            });
    }

    /**
     * Handle engine state switch
     *
     * @param event
     */
    function onEngineChange(event) {
        common.sendToDevice(mSocket, common.COMMAND_CONTROL, common.ACTION_STANDBY,
            {
                on: !event.target.checked
            });
    };

    /**
     * Invoked when the auto-send-switch is switched on/off
     *
     * @param event
     */
    function onAutoSendSwitchChange(event) {
        mSendValuesOnChange = event.target.checked;
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