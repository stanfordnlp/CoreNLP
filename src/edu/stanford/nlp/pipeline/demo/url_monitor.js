// -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
// vim:set ft=javascript ts=2 sw=2 sts=2 cindent:
var URLMonitor = (function($, window, undefined) {
    var URLMonitor = function(dispatcher) {
      var that = this;

      var reloadData = true;
      var changed = false;
      var newIntArgs = {};

      that.url_hash = new URLHash();

      // Thanks to:
      // http://kilianvalkhof.com/2010/javascript/the-case-of-the-disappearing-favicon/
      var setFavicon = function() {
        var link = $('link[type="image/x-icon"]').remove().attr("href");
        $('<link href="'+ link +'" rel="shortcut icon" type="image/x-icon" />').appendTo('head');
      }

      var updateURL = function() {
        var new_hash = that.url_hash.getHash();
        changed = false;
        window.location.hash = new_hash;
        setFavicon();
        dispatcher.post('hideForm');
      };

      var setArguments = function(args, force) {
        var oldArgs = that.url_hash.arguments;
        var argSplit = URLHash.splitArgs(args);

        if (!Util.isEqual(that.url_hash.extArguments, argSplit[1])) {
          changed = true;
          that.url_hash.setArguments(args);
          dispatcher.post('argsChanged', [args, oldArgs]);
        }
        if (changed) {
          // hashchange event will trigger
          newIntArgs = argSplit[0];
          updateURL();
        } else if (force || !Util.isEqual(that.url_hash.intArguments, argSplit[0])) {
          // hash is same, but internal arguments differ
          that.url_hash.setArguments(args);
          // hashchange event won't trigger, but internal args have
          // changed, so we have to do updateState's job
          dispatcher.post('hideForm');
          dispatcher.post('argsChanged', [args, oldArgs]);
          dispatcher.post('current', [that.url_hash.collection,
              that.url_hash.document, that.url_hash.arguments, reloadData]);
          reloadData = true;
        }
      };

      var setDocument = function(doc, args) {
        var oldDoc = that.url_hash.document;
        if (oldDoc !== doc) {
          changed = true;
          that.url_hash.setDocument(doc);
          dispatcher.post('docChanged', [doc, oldDoc]);
        }
        setArguments(args || null);
      };

      var setCollection = function(coll, doc, args) {
        var oldColl = that.url_hash.collection;
        if (oldColl !== coll) {
          changed = true;
          that.url_hash.setCollection(coll);

          // keep "blind" down while loading new collection
          $('#waiter').dialog('open');

          dispatcher.post('ajax', [{
              action: 'getCollectionInformation',
              collection: coll
            }, 'collectionLoaded', {
              collection: coll,
              keep: true
            }]);
          dispatcher.post('collectionChanged', [coll, oldColl]);
        }
        setDocument(doc || '', args);
      }

      var updateState = function() {
        dispatcher.post('makeAjaxObsolete');
        if (!changed) {
          var new_url_hash = URLHash.parse(window.location.hash);
          setCollection(new_url_hash.collection, new_url_hash.document,
              $.extend(new_url_hash.arguments, newIntArgs));
          that.url_hash = new_url_hash;
          newIntArgs = {};
        }
       
        dispatcher.post('current', [that.url_hash.collection,
            that.url_hash.document, that.url_hash.arguments, reloadData]);
        reloadData = true;
      };

      var forceUpdate = function() {
        $(window).trigger('hashchange');
      };

      var preventReloadByURL = function() {
        reloadData = false;
      }
      var allowReloadByURL = function() {
        reloadData = true;
      }

      var init = function() {
        $(window).bind('hashchange', updateState);
        forceUpdate();
      }

      dispatcher.
          on('forceUpdate', forceUpdate).
          on('setArguments', setArguments).
          on('setDocument', setDocument).
          on('setCollection', setCollection).
          on('preventReloadByURL', preventReloadByURL).
          on('allowReloadByURL', allowReloadByURL).
          on('init', init);
    };

    return URLMonitor;
})(jQuery, window);

var URLHash = (function($, window, undefined) {
    var URLHash = function(collection, _document, _arguments) {
      var that = this;
      that.collection = collection;
      that.document = _document || '';
      that.arguments = _arguments || {};
      that.calcArgs();
    }

    URLHash.prototype = {
      calcArgs: function() {
        var args = URLHash.splitArgs(this.arguments);
        this.intArguments = args[0];
        this.extArguments = args[1];
      },

      setArgument: function(argument, value) {
        if (!this.arguments) {
          this.arguments = {};
        }
        this.arguments[argument] = value;
        this.calcArgs();
        return this;
      },

      setArguments: function(_arguments) {
        // the $.extend here basically takes a copy; raw assignment
        // would allow changes of the args to alter original, which
        // could be e.g. the "args" of search results
        this.arguments = $.extend({}, _arguments || {});
        this.calcArgs();
        return this;
      },

      setDocument: function(_document) {
        this.document = _document;
        return this;
      },

      setCollection: function(collection) {
        this.collection = collection;
        return this;
      },

      getHash: function() {
        var url_hash = this.collection + this.document;

        var url_args = Util.param(this.extArguments);

        if (url_args.length) {
          url_hash += '?' + url_args;
        }

        if (url_hash.length) {
          url_hash = '#' + url_hash;
        }

        return url_hash;
      },
    };

    // arguments that do not appear in the URL
    var INT_ARGS = ['match', 'matchfocus', 'edited'];

    URLHash.splitArgs = function(args) {
      var intArgs = {};
      var extArgs = $.extend({}, args);
      var intArgNameLen = INT_ARGS.length;
      for (var i = 0; i < intArgNameLen; i++) {
        intArgs[INT_ARGS[i]] = extArgs[INT_ARGS[i]];
        delete extArgs[INT_ARGS[i]];
      }
      return [intArgs, extArgs];
    };

    // TODO: Document and conform variables to the rest of the object
    URLHash.parse = function(hash) {
      if (hash.length) {
        // Remove the leading hash (#)
        hash = hash.substr(1);
      }

      var pathAndArgs = hash.split('?');
      var path = pathAndArgs[0] || '';
      var argsStr = pathAndArgs[1] || '';
      var coll;
      var slashPos = path.lastIndexOf('/');
      if (slashPos === -1) {
        coll = '/';
      } else {
        coll = path.substr(0, slashPos + 1);
        if (coll[coll.length - 1] !== '/') {
          coll += '/';
        }
        if (coll[0] !== '/') {
          coll = '/' + coll;
        }
      }
      var doc = path.substr(slashPos + 1);
      var args = Util.deparam(argsStr);
      return new URLHash(coll, doc, args);
    };

    return URLHash;
})(jQuery, window)
