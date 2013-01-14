/**
 * Joystick module handler
 *
 * @constructor
 */
define(['socket.io', 'simulated_touch_factory', 'config'], function (io, touchFactory, config) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var FPS = 30;
    var MAX_RANGE = 100;
    var DEFAULT_DURATION_SLOW_STOP = 500;

    var LEFT_JOYSTICK_COLOR = "55f";
    var RIGHT_JOYSTICK_COLOR = "f55";

    var LEFT_JOYSTICK = 0;
    var RIGHT_JOYSTICK = 1;
    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    // private-members
    var mSocket;
    var mCanvas;
    var mContext2D;
    var mContainer;
    var mMouseX;
    var mMouseY;

    var mLeftTouch;
    var mLeftTouchStartPos;
    var mRightTouch;
    var mRightTouchStartPos;

    var mSimulatedTouches = [];

    var mDrawingIntervalHandler;

    var mPower;
    var mOrientation;
    var mTiltUpDown;
    var mTiltLeftRight;

    // member-flags
    var mIsTouchable;
    var mIsTrackingMouseMovement;
    var mIsTrackingTouchEvents;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////
    /**
     * Create a new Joystick module handler
     */
    (function _Joystick() {
        setupCanvas();
        mSocket = io.connect("/");
        mIsTouchable = 'createTouch' in document;
        if (mIsTouchable) {
            mCanvas.addEventListener('touchstart', onTouchStart, false);
            mCanvas.addEventListener('touchmove', onTouchMove, false);
            mCanvas.addEventListener('touchend', onTouchEnd, false);
            window.onorientationchange = resetCanvas;
            window.onresize = resetCanvas;
        } else {
            mCanvas.addEventListener('mousedown', onMouseDown, false);
            mCanvas.addEventListener('mouseup', onMouseUp, false);
            mCanvas.addEventListener('mousemove', onMouseMove, false);
        }
    })();

    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
    /**
     * Draw our joystick(s)
     */
    function draw() {
        mContext2D.clearRect(0, 0, mCanvas.width, mCanvas.height);

        if (mIsTouchable) {
            if (mIsTrackingTouchEvents && mLeftTouch != null && mLeftTouchStartPos != null) {
                drawJoystick(mContext2D, mLeftTouch, mLeftTouchStartPos, LEFT_JOYSTICK_COLOR);
            }

            if (mIsTrackingTouchEvents && mRightTouch != null && mRightTouchStartPos != null) {
                drawJoystick(mContext2D, mRightTouch, mRightTouchStartPos, RIGHT_JOYSTICK_COLOR);
            }

            // execute simulated touches
            for (var i = 0, max = mSimulatedTouches.length; i < max; i++) {
                var simulatedTouch = mSimulatedTouches[i];
                if (!simulatedTouch.isReady()) {
                    continue;
                }

                drawJoystick(mContext2D, simulatedTouch,
                    {
                        clientX: simulatedTouch.startX,
                        clientY: simulatedTouch.startY
                    }, (simulatedTouch.id == LEFT_JOYSTICK) ? LEFT_JOYSTICK_COLOR : RIGHT_JOYSTICK_COLOR);

                if (config.DEBUG) {
                    console.log("joystick-simulated-draw: (" + simulatedTouch.clientX + ", " + simulatedTouch.clientY + ")");
                }
            }
        } else if (mIsTrackingMouseMovement) {
            mContext2D.beginPath();
            mContext2D.strokeStyle = "white";
            mContext2D.lineWidth = 6;
            mContext2D.arc(mouseStartX, mouseStartY, 40, 0, Math.PI * 2, true);
            mContext2D.stroke();
            mContext2D.beginPath();
            mContext2D.strokeStyle = "white";
            mContext2D.lineWidth = 2;
            mContext2D.arc(mouseStartX, mouseStartY, 60, 0, Math.PI * 2, true);
            mContext2D.stroke();
            mContext2D.beginPath();
            mContext2D.strokeStyle = "white";
            mContext2D.arc(mMouseX, mMouseY, 40, 0, Math.PI * 2, true);
            mContext2D.stroke();
        }
    }

    /**
     * Emit calculated value to connected socket
     *
     * @param event
     * @param obj
     */
    function sendToDevice(event, obj) {
        mSocket.emit(event, obj);
        if (config.DEBUG) {
            console.log(event + ": " + obj);
        }
    }

    /**
     * Emit calculated values to connected socket
     */
    function sendAllToDevice() {
        mSocket.emit("power", mPower);
        mSocket.emit("orientation", mOrientation);
        mSocket.emit("tilt_up_down", mTiltUpDown);
        mSocket.emit("tilt_left_right", mTiltLeftRight);

        if (config.DEBUG) {
            console.log("-------- Emitting --------");
            console.log("power: " + mPower);
            console.log("orientation: " + mOrientation);
            console.log("tilt_up_down: " + mTiltUpDown);
            console.log("tilt_left_right: " + mTiltLeftRight);
            console.log("---------------------------");
        }
    }

    /**
     * Draw the joystick on <code>context2D</code>
     *
     * @param context2D
     * @param touch
     * @param touchStartPos
     * @param style
     */
    function drawJoystick(context2D, touch, touchStartPos, style) {
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.lineWidth = 6;
        context2D.arc(touchStartPos.clientX, touchStartPos.clientY, 40, 0, Math.PI * 2, true);
        context2D.stroke();
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.lineWidth = 2;
        context2D.arc(touchStartPos.clientX, touchStartPos.clientY, 60, 0, Math.PI * 2, true);
        context2D.stroke();
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.arc(touch.clientX, touch.clientY, 40, 0, Math.PI * 2, true);
        context2D.stroke();

        //console.log("joystick-draw: (" + touch.clientX + ", " + touch.clientY + ")");
    }

    /**
     * Reset our canvas to start fresh
     *
     * @param event
     */
    function resetCanvas(event) {
        // resize the canvas - but remember - this clears the canvas too.
        mCanvas.width = window.innerWidth;
        mCanvas.height = window.innerHeight;

        //make sure we scroll to the top left.
        window.scrollTo(0, 0);
    }

    /**
     * Prepare our canvas
     */
    function setupCanvas() {
        mCanvas = document.createElement('canvas');
        mContext2D = mCanvas.getContext('2d');
        mContainer = document.createElement('div');
        mContainer.className = "container";

        mCanvas.width = window.innerWidth;
        mCanvas.height = window.innerHeight;
        document.body.appendChild(mContainer);
        mContainer.appendChild(mCanvas);

        mContext2D.strokeStyle = "#ffffff";
        mContext2D.lineWidth = 2;
    }

    /**
     * Handle on touch start event
     *
     * @param event
     */
    function onTouchStart(event) {
        cancelSimulatedTouches(mSimulatedTouches);
        parseTouchEvent(event.touches, true);
        mIsTrackingTouchEvents = true;
    }

    /**
     * Handle on touch move event
     *
     * @param event
     */
    function onTouchMove(event) {
        // Prevent the browser from doing its default thing (scroll, zoom)
        event.preventDefault();
        parseTouchEvent(event.touches, false);
    }

    /**
     * Handle on touch end event
     *
     * @param event
     */
    function onTouchEnd(event) {
        mIsTrackingTouchEvents = false;
        initiateSlowStop();
        mLeftTouch = null;
        mLeftTouchStartPos = null;
        mRightTouch = null;
        mRightTouchStartPos = null;
    }

    /**
     * Auto animate joystick to its initial position
     */
    function initiateSlowStop() {
        if (mLeftTouch != null) {
            var leftSimulatedTouch = touchFactory.newSimulatedTouch(LEFT_JOYSTICK, mLeftTouch, mLeftTouchStartPos);
            mSimulatedTouches.push(leftSimulatedTouch);
            leftSimulatedTouch.run(FPS, DEFAULT_DURATION_SLOW_STOP, function(simulatedTouch) {
                // remove simulated touch from the array of simulated touches
                mSimulatedTouches.splice(mSimulatedTouches.indexOf(simulatedTouch), 1);
            });
        }

        if (mRightTouch != null) {
            var rightSimulatedTouch = touchFactory.newSimulatedTouch(RIGHT_JOYSTICK, mRightTouch, mRightTouchStartPos);
            mSimulatedTouches.push(rightSimulatedTouch);
            rightSimulatedTouch.run(FPS, DEFAULT_DURATION_SLOW_STOP, function(simulatedTouch) {
                // remove simulated touch from the array of simulated touches
                mSimulatedTouches.splice(mSimulatedTouches.indexOf(simulatedTouch), 1);
            });
        }
    }

    /**
     * Cancel all simulated touches
     *
     * @param simulatedTouches
     */
    function cancelSimulatedTouches(simulatedTouches) {
        while ((simulatedTouch = simulatedTouches.pop()) != null) {
            simulatedTouch.cancel();
        }
    }

    /**
     * parse and keep reference to wanted touch events
     *
     * @param touches An array of touch events
     * @param defineStartPosition Whether to keep reference of starting positions
     */
    function parseTouchEvent(touches, defineStartPosition) {
        var halfWindowWidth = window.innerWidth / 2;
        for (var i = 0, max = touches.length; i < max; i++) {
            var touch = touches[i];
            if (touch.clientX > halfWindowWidth) {
                // right side
                if (isOutOfRange(touch, mRightTouchStartPos)) {
                    continue;
                }

                mRightTouch = touch;
                if (defineStartPosition) {
                    mRightTouchStartPos = touch;
                }

                mTiltUpDown = calculateYAxis(mRightTouch, mRightTouchStartPos);
                mTiltLeftRight = calculateXAxis(mRightTouch, mRightTouchStartPos);
                sendToDevice("tilt_up_down", mTiltUpDown);
                sendToDevice("tilt_left_right", mTiltLeftRight);
            } else {
                // left side
                if (isOutOfRange(touch, mLeftTouchStartPos)) {
                    continue;
                }

                mLeftTouch = touch;
                if (defineStartPosition) {
                    mLeftTouchStartPos = touch;
                }

                mPower = calculateYAxis(mLeftTouch, mLeftTouchStartPos);
                mOrientation = calculateXAxis(mLeftTouch, mLeftTouchStartPos);
                sendToDevice("power", mPower);
                sendToDevice("orientation", mOrientation);
            }
        }
    }

    /**
     * Calculate values over the Y axis
     *
     * @param touch
     * @param touchStartPos
     * @return {Number}
     */
    function calculateYAxis(touch, touchStartPos) {
        return touchStartPos.clientY - touch.clientY;
    }

    /**
     * Calculate values over the X axis
     *
     * @param touch
     * @param touchStartPos
     * @return {Number}
     */
    function calculateXAxis(touch, touchStartPos) {
        return touch.clientX - touchStartPos.clientX;
    }

    /**
     * Check if reached maximum range
     *
     * @param touch
     * @param touchStartPos
     * @return {Boolean}
     */
    function isOutOfRange(touch, touchStartPos) {
        if (touch == null || touchStartPos == null) {
            return false;
        }

        var x = Math.pow(touch.clientX - touchStartPos.clientX, 2);
        var y = Math.pow(touch.clientY - touchStartPos.clientY, 2);
        var value = Math.sqrt(x + y);
        return (value > MAX_RANGE);
    }

    /**
     * Handle on mouse down event
     *
     * @param event
     */
    function onMouseDown(event) {
        mIsTrackingMouseMovement = true;
        mouseStartX = event.offsetX;
        mouseStartY = event.offsetY;
    }

    /**
     * Handle on mouse move event
     *
     * @param event
     */
    function onMouseMove(event) {
        mMouseX = event.offsetX;
        mMouseY = event.offsetY;
    }

    /**
     * Handle on mouse up event
     *
     * @param event
     */
    function onMouseUp(event) {
        mIsTrackingMouseMovement = false;
    }

    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
        /**
         * Begin tracking mouse and touch movements
         */
        start:function () {
            mDrawingIntervalHandler = setInterval(draw, 1000 / FPS);
        },

        /**
         * Stop tracking mouse and touch movements
         */
        stop:function () {
            clearInterval(mDrawingIntervalHandler);
        }
    };
});