/**
 * Joystick module handler
 *
 * @constructor
 */
define(function () {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var FPS = 30;

    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    // private-members
    var mCanvas;
    var mContext2D;
    var mContainer;
    var mMouseX;
    var mMouseY;

    var mLeftTouch;
    var mLeftTouchStartPos;
    var mRightTouch;
    var mRightTouchStartPos;

    var mDrawingIntervalHandler;

    // member-flags
    var mIsTouchable;
    var mIsTrackingMouseMovement;

    ////////////////////////////////////
    ///////// Constructor
    ////////////////////////////////////
    /**
     * Create a new Joystick module handler
     * @private
     */
    (function _Joystick() {
        setupCanvas();

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
     *
     * @private
     */
    function draw() {
        mContext2D.clearRect(0, 0, mCanvas.width, mCanvas.height);

        if (mIsTouchable) {
            if (mLeftTouch != null) {
                drawJoystick(mContext2D, mLeftTouch, mLeftTouchStartPos, "55f");
            }

            if (mRightTouch != null) {
                drawJoystick(mContext2D, mRightTouch, mRightTouchStartPos, "f55");
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
    }

    /**
     * Reset our canvas to start fresh
     *
     * @param event
     * @private
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
     *
     * @private
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
     * @private
     */
    function onTouchStart(event) {
        parseTouchEvent(event.touches, true);
    }

    /**
     * Handle on touch move event
     *
     * @param event
     * @private
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
     * @private
     */
    function onTouchEnd(event) {
        mLeftTouch = null;
        mRightTouch = null;
    }

    /**
     * parse and keep reference to wanted touch events
     *
     * @param touches An array of touch events
     * @param defineStartPosition Whether to keep reference of starting positions
     * @private
     */
    function parseTouchEvent(touches, defineStartPosition) {
        mRightTouch = null;
        mLeftTouch = null;
        var halfWindowWidth = window.innerWidth / 2;
        for (var i = 0, max = touches.length; i < max; i++) {
            var touch = touches[i];
            if (touch.clientX > halfWindowWidth) {
                // right side
                mRightTouch = touch;
                if (defineStartPosition) {
                    mRightTouchStartPos = touch;
                }
            } else {
                // left side
                mLeftTouch = touch;
                if (defineStartPosition) {
                    mLeftTouchStartPos = touch;
                }
            }
        }
    }

    /**
     * Handle on mouse down event
     *
     * @param event
     * @private
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
     * @private
     */
    function onMouseMove(event) {
        mMouseX = event.offsetX;
        mMouseY = event.offsetY;
    }

    /**
     * Handle on mouse up event
     *
     * @param event
     * @private
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
        start: function() {
            mDrawingIntervalHandler = setInterval(draw, 1000 / FPS);
        },

        /**
         * Stop tracking mouse and touch movements
         */
        stop: function() {
            clearInterval(mDrawingIntervalHandler);
        }
    };
});