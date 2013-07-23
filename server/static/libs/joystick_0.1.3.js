/**
 * Joystick module handler
 *
 * @constructor
 */
define(['socket.io', 'simulated_touch_factory', 'config', 'log', "prototype"], function (io, touchFactory, config, log) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var TAG = "Joystick";
    var FPS = 30;
    var MAX_RANGE = 100;
    var DEFAULT_DURATION_SLOW_STOP = 100;//250;
    var HOLD_LEFT_JOYSTICK = true;
    var JOYSTICK_THRESHOLD = 10;

    var COMMAND_CONTROL = "control";
    var ACTION_STICKS = "sticks";
    var ACTION_STANDBY = "standby";
    var ACTION_LEFT_STICK = "left_stick";
    var ACTION_RIGHT_STICK = "right_stick";

    var LEFT_JOYSTICK_COLOR = "#5555ff";
    var RIGHT_JOYSTICK_COLOR = "#ff5555";

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
    var mMouseStartPosX;
    var mMouseY;
    var mMouseStartPosY;

    var mLeftTouch;
    var mLeftTouchStartPos;
    var mRightTouch;
    var mRightTouchStartPos;

    var mSimulatedTouches = [];
    var mTrackingTouchTypes = [];

    var mDrawingIntervalHandler;

    var mThrottle;
    var mRoll;
    var mPitch;
    var mYaw;

    // member-flags
    var mIsTouchable;
    var mIsTrackingMouseMovement;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////
    /**
     * Create a new Joystick module handler
     */
    (function _Joystick() {
        mThrottle = 0;
        mRoll = 0;
        mPitch = 0;
        mYaw = 0;
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
            if ((mTrackingTouchTypes.contains(LEFT_JOYSTICK) || (HOLD_LEFT_JOYSTICK && (mThrottle > JOYSTICK_THRESHOLD || mThrottle < -JOYSTICK_THRESHOLD))) && mLeftTouch != null && mLeftTouchStartPos != null) {
                drawJoystick(mContext2D, mLeftTouch, mLeftTouchStartPos, LEFT_JOYSTICK_COLOR);
            }

            if (mTrackingTouchTypes.contains(RIGHT_JOYSTICK) && mRightTouch != null && mRightTouchStartPos != null) {
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
                        clientX:simulatedTouch.startX,
                        clientY:simulatedTouch.startY
                    }, (simulatedTouch.id == LEFT_JOYSTICK) ? LEFT_JOYSTICK_COLOR : RIGHT_JOYSTICK_COLOR);

                log.d(TAG, "joystick-simulated-draw: (" + simulatedTouch.clientX + ", " + simulatedTouch.clientY + ")");
            }
        } else if (mIsTrackingMouseMovement) {
            drawJoystick(mContext2D,
                {
                    clientX:mMouseX,
                    clientY:mMouseY
                },
                {
                    clientX:mMouseStartPosX,
                    clientY:mMouseStartPosY
                }, "white");
        }
    }

    /**
     * Emit calculated value to connected socket.
     *
     * @param {String} event
     * @param {String} type
     * @param {Object} data
     */
    function sendToDevice(event, type, data) {
        mSocket.emit(event,
            {
                type:type,
                data:data
            });
        log.d(TAG, event + ":: " + type + ":: data: " + data);
    }

    /**
     * Draw the joystick on <code>context2D</code>
     *
     * @param {CanvasRenderingContext2D} context2D
     * @param touch
     * @param touchStartPos
     * @param style
     */
    function drawJoystick(context2D, touch, touchStartPos, style) {
        // Draw: joystick base - outer circle
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.globalAlpha = 1;
        context2D.lineWidth = 2;
        context2D.arc(touchStartPos.clientX, touchStartPos.clientY - mCanvas.offsetTop, 60, 0, Math.PI * 2, true);
        context2D.stroke();

        // Draw: joystick base - inner circle
        var R = 40;
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.lineWidth = 6;
        context2D.arc(touchStartPos.clientX, touchStartPos.clientY - mCanvas.offsetTop, R, 0, Math.PI * 2, true);
        context2D.stroke();

        // Draw: joystick handle
        // calculate joystick handle radius and alpha channel according to touch-move distance
        var distance = getDistance(touch, touchStartPos);
        var r = R - (distance / 8);
        if (r < 0) {
            r = 0;
        }

        var alpha = distance / MAX_RANGE;
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.fillStyle = style;
        context2D.globalAlpha = alpha;
        context2D.lineWidth = 2;
        context2D.arc(touch.clientX, touch.clientY - mCanvas.offsetTop, r, 0, Math.PI * 2, true);
        context2D.stroke();
        context2D.fill();

        // draw the joystick handle connecting lines
        context2D.beginPath();
        context2D.strokeStyle = style;
        context2D.lineWidth = 2;
        context2D.lineCap = "round";
        context2D.moveTo(touchStartPos.clientX, touchStartPos.clientY - mCanvas.offsetTop - R);
        context2D.lineTo(touch.clientX, touch.clientY - mCanvas.offsetTop - r);
        context2D.moveTo(touchStartPos.clientX, touchStartPos.clientY - mCanvas.offsetTop + R);
        context2D.lineTo(touch.clientX, touch.clientY - mCanvas.offsetTop + r);
        context2D.moveTo(touchStartPos.clientX - R, touchStartPos.clientY - mCanvas.offsetTop);
        context2D.lineTo(touch.clientX - r, touch.clientY - mCanvas.offsetTop);
        context2D.moveTo(touchStartPos.clientX + R, touchStartPos.clientY - mCanvas.offsetTop);
        context2D.lineTo(touch.clientX + r, touch.clientY - mCanvas.offsetTop);
        context2D.stroke();
    }

    /**
     * Reset our canvas to start fresh
     *
     * @param event
     */
    function resetCanvas(event) {
        // resize the canvas - but remember - this clears the canvas too.
        mCanvas.width = window.innerWidth;
        mCanvas.height = window.innerHeight - mCanvas.offsetTop;

        //make sure we scroll to the top left.
        window.scrollTo(0, 0);
    }

    /**
     * Prepare our canvas
     */
    function setupCanvas() {
        mCanvas = document.createElement('canvas');
        mContext2D = mCanvas.getContext('2d');
        mContainer = document.getElementById('canvasWrapper');
        mContainer.className = "container";
        var header = document.createElement('div');
        header.className = "header";

        document.body.appendChild(header);
        document.body.appendChild(mContainer);
        mContainer.appendChild(mCanvas);

        mCanvas.width = window.innerWidth;
        mCanvas.height = window.innerHeight - mCanvas.offsetTop;

        mContext2D.strokeStyle = "#ffffff";
        mContext2D.lineWidth = 2;

        var engineSwitch = document.getElementById("engineSwitch");
        engineSwitch.onchange = onEngineSwitch;
    }

    /**
     * Handle engine state switch
     *
     * @param event
     */
    function onEngineSwitch(event) {
        sendToDevice(COMMAND_CONTROL, ACTION_STANDBY,
                            {
                                on: !event.target.checked
                            });
    };

    /**
     * Handle on touch start event
     *
     * @param event
     */
    function onTouchStart(event) {
        log.d(TAG, "onTouchStart: start");
        cancelSimulatedTouches(mSimulatedTouches);
        var touchTypes = parseTouchEvent(event.touches, true);
        // append only unique touch types
        mTrackingTouchTypes = mTrackingTouchTypes.concat(touchTypes).unique();
        log.d(TAG, "onTouchStart: end");
    }

    /**
     * Handle on touch move event
     *
     * @param event
     */
    function onTouchMove(event) {
        log.d(TAG, "onTouchMove: start");
        // Prevent the browser from doing its default thing (scroll, zoom)
        event.preventDefault();
        parseTouchEvent(event.touches, false);
        log.d(TAG, "onTouchMove: end");
    }

    /**
     * Handle on touch end event
     *
     * @param event
     */
    function onTouchEnd(event) {
        log.d(TAG, "onTouchEnd: start");
        var activeTouchTypes = getTouchTypes(event.touches);

        // find which touch event just ended
        var endedTouchTypes = mTrackingTouchTypes.filter(function (n) {
            return !activeTouchTypes.contains(n);
        });

        // update which touch types we're tracking
        mTrackingTouchTypes = activeTouchTypes;
        initiateSlowStop(endedTouchTypes);
        log.d(TAG, "onTouchEnd: end");
    }

    /**
     * Get touch types
     *
     * @param touches
     * @return {Array}
     */
    function getTouchTypes(touches) {
        var touchTypes = [];
        var halfWindowWidth = window.innerWidth / 2;
        for (var i = 0, max = touches.length; i < max; i++) {
            var touch = touches[i];
            if (touch.clientX > halfWindowWidth) {
                touchTypes.push(RIGHT_JOYSTICK);
            } else {
                touchTypes.push(LEFT_JOYSTICK);
            }
        }

        return touchTypes;
    }

    /**
     * Auto animate joystick to its initial position
     *
     * @param {Array} touchTypes
     */
    function initiateSlowStop(touchTypes) {
        if ((!HOLD_LEFT_JOYSTICK || (mThrottle <= JOYSTICK_THRESHOLD && mThrottle >= -JOYSTICK_THRESHOLD)) && mLeftTouch != null && touchTypes.contains(LEFT_JOYSTICK)) {
            var leftSimulatedTouch = touchFactory.newSimulatedTouch(LEFT_JOYSTICK, mLeftTouch, mLeftTouchStartPos);
            mSimulatedTouches.push(leftSimulatedTouch);
            leftSimulatedTouch.run(FPS, DEFAULT_DURATION_SLOW_STOP, {
                onStep:function (startX, startY, currentX, currentY) {
                    mThrottle = Math.floor(calculateYAxis(currentY, startY));
                    mYaw = Math.floor(calculateXAxis(currentX, startX));
                    sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                                        {
                                            throttle: mThrottle,
                                            pitch: mPitch,
                                            roll: mRoll,
                                            yaw: mYaw
                                        });
                },

                onEnd:function (simulatedTouch) {
                    // remove simulated touch from the array of simulated touches
                    mSimulatedTouches.splice(mSimulatedTouches.indexOf(simulatedTouch), 1);
                    mThrottle = 0;
                    mYaw = 0;

                    // fix last values
                    sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                                        {
                                            throttle: mThrottle,
                                            pitch: mPitch,
                                            roll: mRoll,
                                            yaw: mYaw
                                        });
                }
            });

            mLeftTouch = null;
            mLeftTouchStartPos = null;
        }

        if (mRightTouch != null && touchTypes.contains(RIGHT_JOYSTICK)) {
            var rightSimulatedTouch = touchFactory.newSimulatedTouch(RIGHT_JOYSTICK, mRightTouch, mRightTouchStartPos);
            mSimulatedTouches.push(rightSimulatedTouch);
            rightSimulatedTouch.run(FPS, DEFAULT_DURATION_SLOW_STOP, {
                onStep:function (startX, startY, currentX, currentY) {
                    mPitch = Math.floor(calculateYAxis(currentY, startY));
                    mRoll = Math.floor(calculateXAxis(currentX, startX));
                    sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                                        {
                                            throttle: mThrottle,
                                            pitch: mPitch,
                                            roll: mRoll,
                                            yaw: mYaw
                                        });
                },

                onEnd:function (simulatedTouch) {
                    // remove simulated touch from the array of simulated touches
                    mSimulatedTouches.splice(mSimulatedTouches.indexOf(simulatedTouch), 1);
                    mPitch = 0;
                    mRoll = 0;

                    // fix last values
                    sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                                        {
                                            throttle: mThrottle,
                                            pitch: mPitch,
                                            roll: mRoll,
                                            yaw: mYaw
                                        });
                }
            });

            mRightTouch = null;
            mRightTouchStartPos = null;
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
     * @return {Array} array of touch types
     */
    function parseTouchEvent(touches, defineStartPosition) {
        log.d(TAG, "parseTouchEvent: start");
        var touchTypes = [];
        var halfWindowWidth = window.innerWidth / 2;
        for (var i = 0, max = touches.length; i < max; i++) {
            var touch = touches[i];
            if (touch.clientX > halfWindowWidth) {
                // right side
                if (defineStartPosition && !mTrackingTouchTypes.contains(RIGHT_JOYSTICK)) {
                    mRightTouchStartPos = touch;
                }

                if (isOutOfRange(touch, mRightTouchStartPos) || mRightTouchStartPos == null) {
                    continue;
                }

                log.d(TAG, "parseTouchEvent: calculating right");
                touchTypes.push(RIGHT_JOYSTICK);
                mRightTouch = touch;
                mPitch = calculateYAxis(mRightTouch.clientY - mCanvas.offsetTop, mRightTouchStartPos.clientY - mCanvas.offsetTop);
                mRoll = calculateXAxis(mRightTouch.clientX, mRightTouchStartPos.clientX);
            } else {
                // left side
                if (defineStartPosition && !mTrackingTouchTypes.contains(LEFT_JOYSTICK)
                    && (!HOLD_LEFT_JOYSTICK || mLeftTouchStartPos == undefined || (mThrottle <= JOYSTICK_THRESHOLD && mThrottle >= -JOYSTICK_THRESHOLD))) {
                    mLeftTouchStartPos = touch;
                }

                if (isOutOfRange(touch, mLeftTouchStartPos) || mLeftTouchStartPos == null) {
                    continue;
                }

                log.d(TAG, "parseTouchEvent: calculating left");
                touchTypes.push(LEFT_JOYSTICK);
                mLeftTouch = touch;
                mThrottle = calculateYAxis(mLeftTouch.clientY - mCanvas.offsetTop, mLeftTouchStartPos.clientY - mCanvas.offsetTop);
                mYaw = calculateXAxis(mLeftTouch.clientX, mLeftTouchStartPos.clientX);
            }
        }

        sendToDevice(COMMAND_CONTROL, ACTION_STICKS,
                            {
                                throttle: mThrottle,
                                pitch: mPitch,
                                roll: mRoll,
                                yaw: mYaw
                            });
        log.d(TAG, "parseTouchEvent: end");
        return touchTypes;
    }

    /**
     * Calculate values over the Y axis
     *
     * @param {Integer} currentY
     * @param {Integer} startY
     * @return {Integer}
     */
    function calculateYAxis(currentY, startY) {
        return startY - currentY;
    }

    /**
     * Calculate values over the X axis
     *
     * @param {Integer} currentX
     * @param {Integer} startX
     * @return {Integer}
     */
    function calculateXAxis(currentX, startX) {
        return currentX - startX;
    }

    /**
     * Check if reached maximum range
     *
     * @param touch
     * @param touchStartPos
     * @return {Boolean}
     */
    function isOutOfRange(touch, touchStartPos) {
        var distance = getDistance(touch, touchStartPos);
        return (distance > MAX_RANGE);
    }

    /**
     * Get the distance between two points
     *
     * @param touch
     * @param touchStartPos
     * @return {Number}
     */
    function getDistance(touch, touchStartPos) {
        if (touch == null || touchStartPos == null) {
            return 0;
        }

        var x = Math.pow(touch.clientX - touchStartPos.clientX, 2);
        var y = Math.pow(touch.clientY - touchStartPos.clientY, 2);
        return Math.sqrt(x + y);
    }

    /**
     * Handle on mouse down event
     *
     * @param event
     */
    function onMouseDown(event) {
        mIsTrackingMouseMovement = true;
        mMouseStartPosX = event.offsetX;
        mMouseStartPosY = event.offsetY;
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