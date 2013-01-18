/**
 * Prototype existing objects (not really a good practice :)
 */
define(function () {
    Object.defineProperty(Array.prototype, 'contains', {
        enumerable:false,
        configurable:false,
        writable:false,
        value:function (value) {
            return this.indexOf(value) != -1;
        }
    });

    Object.defineProperty(Array.prototype, 'unique', {
        enumerable:false,
        configurable:false,
        writable:false,
        value:function () {
            var a = this.concat();
            for (var i = 0; i < a.length; ++i) {
                for (var j = i + 1; j < a.length; ++j) {
                    if (a[i] === a[j])
                        a.splice(j, 1);
                }
            }

            return a;
        }
    });
});



