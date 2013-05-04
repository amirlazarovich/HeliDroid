/**
 * 2D simulated touch factory
 */
define(['config', 'log'], function (config, log) {
    ////////////////////////////////////
    ///////// Constants
    ////////////////////////////////////
    var TAG = "TouchFactory";

    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
        /**
         * Create a new simulated touch point representation in a 2D dimension
         *
         * @param id
         * @param currentTouch
         * @param startTouch
         * @return {Object}
         */
        newSimulatedTouch:function (id, currentTouch, startTouch) {
            return {
                id:id,
                clientX:currentTouch.clientX,
                clientY:currentTouch.clientY,
                startX:startTouch.clientX,
                startY:startTouch.clientY,
                intervalId:null,

                /**
                 * Check if ths simulated touch is ready
                 *
                 * @return {Boolean}
                 */
                isReady:function () {
                    return this.clientX != null && this.clientY != null && this.startX != null && this.startY != null;
                },

                /**
                 * Cancel simulation
                 */
                cancel:function () {
                    clearInterval(this.intervalId);
                },

                /**
                 * Run linear equation while trying to bring the joystick back to its initial position
                 *
                 * @param fps
                 * @param duration
                 * @param callback Invoked when simulation completes
                 */
                run:function (fps, duration, callback) {
                    var m = (this.startY - this.clientY) / (this.startX - this.clientX);
                    var varPoint;
                    var targetPoint;
                    var isTraversingY = false;
                    if (m == 'Infinity') {
                        isTraversingY = true;
                        varPoint = this.clientY;
                        targetPoint = this.startY;
                    } else {
                        var c = this.startY - (m * this.startX);
                        varPoint = this.clientX;
                        targetPoint = this.startX;
                    }

                    log.d(TAG, "** slow-stop: varX: " + varPoint + ", targetX: " + targetPoint);
                    var stepSign = (targetPoint > varPoint) ? 1 : -1;
                    var delay = 1000 / fps;
                    var totalFrames = fps * (duration / 1000);
                    var range = (targetPoint - varPoint);
                    var step = range / totalFrames;
                    var me = this;
                    log.d(TAG, "*** slow-stop: step: " + step + ", target values: (" + this.startX + ", " + this.startY + ")");
                    this.intervalId = setInterval(function () {
                        varPoint += step;
                        if (isTraversingY) {
                            me.clientY = varPoint;
                        } else {
                            var varY = m * varPoint + c;
                            me.clientX = varPoint;
                            me.clientY = varY;
                        }

                        log.d(TAG, "slow-stop: (" + me.clientX + ", " + me.clientY + ")");
                        log.d(TAG, "targetPoint: " + targetPoint + ", varX: " + varPoint + ", stepSign: " + stepSign);

                        if (((targetPoint - varPoint) * stepSign) <= 0) {
                            clearInterval(me.intervalId);
                            callback.onEnd(this);
                        } else {
                            callback.onStep(me.startX, me.startY, me.clientX, me.clientY);
                        }
                    }, delay);
                }
            };
        }
    };
});