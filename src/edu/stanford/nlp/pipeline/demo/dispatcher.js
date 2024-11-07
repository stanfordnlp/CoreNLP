// -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
// vim:set ft=javascript ts=2 sw=2 sts=2 cindent:
// TODO: does 'arguments.callee.caller' work?

var Dispatcher = (function($, window, undefined) {
    var Dispatcher = function() {
      var that = this;

      var table = {};

      var on = function(message, host, handler) {
        if (handler === undefined) {
          handler = host;
          host = arguments.callee.caller;
        }
        if (table[message] === undefined) {
          table[message] = [];
        }
        table[message].push([host, handler]);
        return this;
      };

      // Notify listeners that we encountered an error in an asynch call
      var inAsynchError = false; // To avoid error avalanches
      var handleAsynchError = function(e) {
        if (!inAsynchError) {
          inAsynchError = true;
          // TODO: Hook printout into dispatch elsewhere?
          console.warn('Handled async error:', e);
          that.post('dispatchAsynchError', [e]);
          inAsynchError = false;
        } else {
          console.warn('Dropped asynch error:', e);
        }
      };

      var post = function(asynch, message, args, returnType) {
        if (typeof(asynch) !== 'number') {
          // no asynch parameter
          returnType = args;
          args = message;
          message = asynch;
          asynch = null;
        }
        if (args === undefined) {
          args = [];
        }
        var results = [];
        // DEBUG: if (typeof(message) != "string" || !(message.match(/mouse/) || message == "hideComment")) console.log(message, args);

        if (typeof(message) === 'function') {
          // someone was lazy and sent a simple function
          var host = arguments.callee.caller;
          if (asynch !== null) {
            result = setTimeout(function() {
              try {
                message.apply(host, args);
              } catch(e) {
                that.handleAsynchError(e);
              }
            }, asynch);
          } else {
            result = message.apply(host, args);
          }
          results.push(result);
        } else {
          // a proper message, propagate to all interested parties
          var todo = table[message];
          if (todo !== undefined) {
            $.each(todo, function(itemNo, item) {
              var result;
              if (asynch !== null) {
                result = setTimeout(function() {
                  try {
                    item[1].apply(item[0], args);
                  } catch (e) {
                    that.handleAsynchError(e);
                  }
                }, asynch);
              } else {
                result = item[1].apply(item[0], args);
              }
              results.push(result);
            });
/* DEBUG
          } else {
            console.warn('Message ' + message + ' has no subscribers.'); // DEBUG
*/
          }
        }
        if (returnType == 'any') {
          var i = results.length;
          while (i--) {
            if (results[i] !== false) return results[i];
          }
          return false;
        }
        if (returnType == 'all') {
          var i = results.length;
          while (i--) {
            if (results[i] === false) return results[i];
          }
        }
        return results;
      };

      var proxy = function(destination, message) {
        this.on(message, function() {
          destination.post(message, Array.prototype.slice.call(arguments));
        });
      };

      var dispatcher = {
        on: on,
        post: post,
        proxy: proxy,
      };
      Dispatcher.dispatchers.push(dispatcher);
      return dispatcher;
    };

    Dispatcher.dispatchers = [];
    Dispatcher.post = function(asynch, message, args, returnType) {
      $.each(Dispatcher.dispatchers, function(dispatcherNo, dispatcher) {
        dispatcher.post(asynch, message, args, returnType);
      });
    };

    return Dispatcher;
})(jQuery, window);
