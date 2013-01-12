/**
 * Joystick module handler
 *
 * @constructor
 */
define(function () {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var FPS = 35;

    ////////////////////////////////////
    ///////// Members
    ////////////////////////////////////
    // private-members
    var mCanvas;
    var mContext2D;
    var mContainer;
    var mMouseX;
    var mMouseY;
    var mTouches = []; // array of touch vectors
    var mTouchesStartingPos = []; // array of touch vectors starting position
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
            for (var i = 0; i < mTouches.length; i++) {
                var touch = mTouches[i];
                var touchStartingPos = mTouchesStartingPos[i];
                mContext2D.beginPath();
                mContext2D.strokeStyle = "cyan";
                mContext2D.lineWidth = 6;
                mContext2D.arc(touchStartingPos.clientX, touchStartingPos.clientY, 40, 0, Math.PI * 2, true);
                mContext2D.stroke();
                mContext2D.beginPath();
                mContext2D.strokeStyle = "cyan";
                mContext2D.lineWidth = 2;
                mContext2D.arc(touchStartingPos.clientX, touchStartingPos.clientY, 60, 0, Math.PI * 2, true);
                mContext2D.stroke();
                mContext2D.beginPath();
                mContext2D.strokeStyle = "cyan";
                mContext2D.arc(touch.clientX, touch.clientY, 40, 0, Math.PI * 2, true);
                mContext2D.stroke();
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
        mTouches = event.touches;
        mTouchesStartingPos = mTouches;
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
        mTouches = event.touches;
    }

    /**
     * Handle on touch end event
     *
     * @param event
     * @private
     */
    function onTouchEnd(event) {
        mTouches = event.touches;
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