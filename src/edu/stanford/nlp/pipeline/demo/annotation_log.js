// -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
// vim:set ft=javascript ts=2 sw=2 sts=2 cindent:

var AnnotationLog = (function(window, undefined) {
    var AnnotationLog = function(dispatcher) {
      var annotationLoggingOn = false;
      var currentCollection = null;
      var currentDocument = null;
      var currentArguments = null;

      var rememberLoggingState = function(response) {
        annotationLoggingOn = response.annotation_logging;
      };

      var rememberCurrent = function(_collection, _document, _arguments) {
        currentCollection = _collection;
        currentDocument = _document;
        currentArguments = _arguments;
      };

      var logAction = function(_action) {
        if (!annotationLoggingOn) {
          // logging not requested for current collection
          return false;
        } else {
          dispatcher.post('ajax', [ {
            action: 'logAnnotatorAction',
            collection: currentCollection,
            'document': currentDocument,
            log: _action,
          }, null]);
        }
      }

      dispatcher.
          on('collectionLoaded', rememberLoggingState).
          on('current', rememberCurrent).
          on('logAction', logAction);
    }

    return AnnotationLog;
})(window);
