/**
 * Configuration file
 */
define(['config'], function(config) {
    ////////////////////////////////////
    ///////// Private
    ////////////////////////////////////
    /**
     * print message to console
     *
     * @param {String} level
     * @param {String} tag
     * @param {String} msg
     */
    function log(level, tag, msg) {
        if (config.DEBUG) {
            console.log("[" + level + "/" + tag + "]      " + msg);
        }
    }


    ////////////////////////////////////
    ///////// Public
    ////////////////////////////////////
    return {
        /**
         * Log in DEBUG level
         *
         * @param {String} tag
         * @param {String} msg
         */
        d: function(tag, msg) {
            log("DEBUG", tag, msg);
        },

        /**
         * Log in INFO level
         *
         * @param {String} tag
         * @param {String} msg
         */
        i: function(tag, msg) {
            log("INFO", tag, msg);
        },

        /**
         * Log in WARN level
         *
         * @param {String} tag
         * @param {String} msg
         */
        w: function(tag, msg) {
            log("WARN", tag, msg);
        },

        /**
         * Log in ERROR level
         *
         * @param {String} tag
         * @param {String} msg
         */
        e: function(tag, msg) {
            log("ERROR", tag, msg);
        }
    };
});
