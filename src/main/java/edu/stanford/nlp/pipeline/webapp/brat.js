/**
 * Javascript to generate and embed brat visualisations of CoreNLP output.
 *
 * Author:  Pontus Stenetorp    <pontus stenetorp se>
 * Version: 2012-06-29
 */

// We rely on head.js being loaded from the HTML.
head.js(
    // External libraries
    bratLocation + '/client/lib/jquery.min.js',
    bratLocation + '/client/lib/jquery.svg.min.js',
    bratLocation + '/client/lib/jquery.svgdom.min.js',
    // brat helper modules
    bratLocation + '/client/src/configuration.js',
    bratLocation + '/client/src/util.js',
    bratLocation + '/client/src/annotation_log.js',
    bratLocation + '/client/lib/webfont.js',
    // brat modules
    bratLocation + '/client/src/dispatcher.js',
    bratLocation + '/client/src/url_monitor.js',
    bratLocation + '/client/src/visualizer.js'
);
head.ready(function() {
    // Short-hands for our visualisation types.
    vis_types = ['pos', 'ner', 'coref', 'basic_dep', 'collapsed_ccproc_dep'];
    // Data storage and state variables.
    var data = {};
    var conf = false;
    var done = {};
    $(vis_types).each(function(_, type) {
        done[type] = false;
    });
    
    var ajax_url = bratLocation + '/ajax.cgi';
    var conf_name = 'Stanford-CoreNLP';

    var handler = function() {
        if (!conf) {
            // We haven't received our configuration yet, wait for it!
            return
        }
        $(vis_types).each(function(_, type) {
            // Embed if we have received its data and not embedded it yet.
            if (data[type] && !done[type]) {
                // Note: Util.embed doesn't have any error handling, once this
                // exists it should be injected here.
                Util.embed(type, conf, data[type], webFontURLs);
                $('#' + type + '_loading').hide();
                done[type] = true;
            };
        });
    };

    // Fetch our configuration
    $.ajax(ajax_url, {
        'data': {
            'action': 'getConfiguration',
            'protocol': 1,
            'name': conf_name,
        },
        'success': function(config_data) {
            if (config_data.exception) {
                var error_msg = 'ERROR: Failed to convert ' + type + ' data!';
                console.log(error_msg);
                $('#config_error').text(error_msg +
                    ' Please contact the administrator.');
                return
            };
            conf = config_data;
            // Note: Could check for exceptions in the returned data here
            handler();
        },
        'error': function() {
            var error_msg = 'ERROR: Failed to load configuration!';
            console.log(error_msg);
            $('#config_error').text(error_msg
                + ' Please contact the administrator.');
        }
    });

    // Fetch converted data for all of our visualisation types
    $(vis_types).each(function(_, type) {
        $.ajax(ajax_url, {
            'data': {
                'action': 'convert',
                'data': stanfordXML,
                'protocol': 1,
                'src': 'stanford-' + type
            },
            'type': 'post',
            'success': function(converted_data) {
                if (converted_data.exception) {
                    var error_msg = 'ERROR: Failed to convert ' + type + ' data!';
                    console.log(error_msg);
                    $('#' + type + '_loading').text(error_msg +
                        ' Please contact the administrator.');
                    return
                };
                data[type] = converted_data;
                // Note: Could check for exceptions in the returned data here
                // Call the handler to potentially inject the data.
                handler();
            },
            'error': function() {
                var error_msg = 'ERROR: Failed to load ' + type + ' data!';
                console.log(error_msg);
                $('#' + type + '_loading').text(error_msg +
                    ' Please contact the administrator.');
            }
        });
    });
});
