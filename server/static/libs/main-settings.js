require.config({
    baseUrl: 'static/libs/',
    paths: {
        // the left side is the module ID,
        // the right side is the path to
        // the jQuery file, relative to baseUrl.
        // Also, the path should NOT include
        // the '.js' file extension. This example
        // is using jquery-2.0.3.min located at
        // static/libs/jquery-2.0.3.min.js, relative to
        // the HTML page.
        jquery: 'jquery-2.0.3.min'
    }
});

require(["screen-settings"], function(screenSettings) {
    screenSettings.start();
});
