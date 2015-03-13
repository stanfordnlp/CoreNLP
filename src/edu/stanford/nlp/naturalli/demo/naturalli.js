var MOCK = false;
var ALLOW_FEEDBACK = true;

function generateUUID(){
  var d = new Date().getTime();
  var uuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
    var r = (d + Math.random()*16)%16 | 0;
    d = Math.floor(d/16);
    return (c=='x' ? r : (r&0x7|0x8)).toString(16);
  });
  return uuid;
};

function mkFeedbackButtons(inputQuery, truthValue) {
  $('#feedback-buttons').html(
     '  <a href="#" id="truth-correct" class="btn btn-sm btn-success"><span class="glyphicon glyphicon-ok"></span> NaturalLI is correct</a>' +
     '  |' +
     '  <a href="#" id="truth-incorrect" class="btn btn-sm btn-danger"><span class="glyphicon glyphicon-remove"></span> NaturalLI is wrong</a>'
     );
  $('#truth-correct').click(function (e) {
    provideFeedback(inputQuery, truthValue, truthValue);
  });
  $('#truth-incorrect').click(function (e) {
    provideFeedback(inputQuery, truthValue, !truthValue);
  });
}

function provideFeedback(query, guess, gold) {
  $('#feedback-buttons').html('<p style="font-size:large;">Thank you! <a href="#" id="truth-undo">(undo)</a></p>');
  var target = $('#form-query').attr('action') + '/feedback/';
  var getData = {"isCancel": false, "transactionID": generateUUID(), "source": $('#iam').val(),
              "q": query, "guess": guess, "gold": gold};
  $('#truth-undo').click(function(e) {
    getData.isCancel = true;
    // Issue cancel request
    mkFeedbackButtons(query, guess);
    if (!MOCK) {
      $.ajax({
        url: target,
        data: getData,
        dataType: 'json',
        success: function (data) { }
      });
    }
  });

  // Issue feedback
  if (!MOCK) {
    $.ajax({
      url: target,
      data: getData,
      dataType: 'json',
      success: function (data) { }
    });
  }

}

function displayTruth(source, truthValue, inputQuery) {
  // Show truth div
  $('#truth-container').html(
     '<p class="truth-gloss">' +
     '  NaturalLI <span id="truth-value" style="text-align:center;"></span> ' +
     '  that <span class="query-text">your query</span>. ' +
     '</p> ' +
     '<div id="feedback-buttons" class="centered well" style="width:325px; height:62px; padding:15px; text-align:center; vertical-align:middle;" ' +
            (ALLOW_FEEDBACK ? '> ' : ' hidden="true"> ') +
     '</div>');

  // Set up container
  mkFeedbackButtons(inputQuery, truthValue);
  $('.query-text').text(inputQuery);


  // Set truth value
  if (source == "none") {
    $("#truth-value").text("couldn't justify");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-unknown");
  } else if (truthValue) {
    $("#truth-value").text("confirms");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-false");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-true");
  } else if (!truthValue) {
    $("#truth-value").text("rejects");
    $("#truth-value").removeClass("truth-value-unknown");
    $("#truth-value").removeClass("truth-value-true");
    $("#truth-value").removeClass("truth-value-error");
    $("#truth-value").addClass("truth-value-false");
  }
}

function getMacCartneyRelation(raw) { return raw.substring(0, 1); }
function getMacCartneyGloss(raw) {
  if (raw.length > 4) {
    return '(' + raw.substring(3, raw.length - 1) + ')';
  } else {
    return '';
  }
}

function loadJustification(elements, truthValue, hasTruthValue, inputQuery) {
  $("#justification-toggle-row").show();
  html = '';
  html = html +'<div class="row"><div class="col-md-8 col-md-offset-2 justification">';

  // Justification Fact
  html = html + '<div class="justification-singleline">';
  html = html + 'NaturalLI thinks <span class="justification-singleline-input"> ' + inputQuery + ' </span> is ' + (hasTruthValue ? truthValue : "unknown") + ' because ';
  if (elements.length == 1) {
    html = html + "it's a known OpenIE fact.";
  } else if (hasTruthValue) {
    html = html + 'of the OpenIE fact: <span class="justification-singleline-fact">' + elements[0].gloss + '</span>.';
  } else {
    html = html + "we couldn't find justification for it (thus we think it's implicitly false).";
  }
  html = html + '</div>';

  if (hasTruthValue) {
    // Justification Table
    // (table)
    html = html +'<table class="justification-table"><thead><tr>';
    html = html + '<th scope="col" style="width:25%;">Edge cost</th>';
    html = html + '<th scope="col" style="text-align:left; width:25%" colspan=2>MacCartney relation</th>';
    html = html + '<th scope="col" style="text-align:left;">Fact</th></tr></thead>';
    html = html +'<tbody>';
    for (var i = 0; i < elements.length; ++i) {
      html = html + '<tr>';
      html = html +'<td>' + elements[i].cost + '</td>';
      html = html +'<td><b>' + getMacCartneyRelation(elements[i].incomingRelation) + '</b></td>';
      html = html +'<td style="text-align:left;"><i>' + getMacCartneyGloss(elements[i].incomingRelation) + '</i></td>';
      html = html +'<td style="text-align:left;">' + elements[i].gloss + '</td>';
      html = html +'</tr>';
    }
    html = html + '</tbody></table></div></div>';
  }

  $('#justification-container').html(html);
}

function handleError(message) {
  $( "#q" ).prop('disabled', false);
  $( "#query-button").unbind( "click" );
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
  $("#truth-container").html('<div style="color:red; font-size:xx-large;">ERROR</div> <div style="color: black; font-size: 12pt">(' + message + ')<div>');
}

function querySuccess(query) {
  return function(response) {
    $( "#q" ).prop('disabled', false);
    $( "#query-button").unbind( "click" );
    $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
    if (response.success) {
      displayTruth(response.bestResponseSource, response.isTrue, query);
      loadJustification(response.bestJustification, response.isTrue, response.bestResponseSource != "none", query);
    } else {
      handleError(response.errorMessage);
    }
  }
}


$(document).ready(function(){
  jQuery.support.cors = true;

  if ($(window).width() < 650) {
    $( '#fork' ).hide();
  }

  // Justification
  $("#justification-toggle-row").hide();

  // Query submit
  $( "#form-query" ).submit(function( event ) {
    // (don't actually submit anything)
    event.preventDefault();
    // (create a default if not input was given)
    if ( $( '#q' ).val().trim() == '') { $( '#q' ).val('cats have tails'); }
    // (start loading icon)
    $( '#truth-container' ).html('<img src="loading.gif" style="height:75px; margin-top:-35px;"/>');
    // (submission data)
    target = $(this).attr('action');
    getData = $(this).serialize();
    value = $( "#q" ).val();
    // (disable query button
    $( "#q" ).prop('disabled', true);
    $( "#query-button").unbind( "click" );
    // (ajax request)
    if (MOCK) {
      // Construct a mock response, for testing only
      var mockResponse = {}
      mockResponse.success = true;
      mockResponse.errorMessage = "God is angry with your antics!";
      mockResponse.bestResponseSource = 1.0;
      mockResponse.isTrue = true;
      mockResponse.bestResponseSource = "mock";
      mockResponse.bestJustification = [{}];
      mockResponse.bestJustification[0].gloss = "because God said so";
      mockResponse.bestJustification[0].incomingRelation = "ϟ (the power of GOD)";
      mockResponse.bestJustification[0].cost = -42.0;
      querySuccess( value )(mockResponse);
    } else {
      // Issue a real query
      $.ajax({
        url: target,
        data: getData,
        dataType: 'json',
        success: querySuccess( value )
      });
    }
  });

//  if (MOCK) { $("#form-query").submit(); }

  // Query button
  $( "#query-button" ).mousedown(function(event) {
    $( '#query-button' ).css('background', 'darkgray');
  });
  $( document ).mouseup(function(event) {
    $( '#query-button' ).css('background', '');
  });
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });

  $( "#justification-toggle" ).click(function(event) {
    event.preventDefault();
    $("#justification-container").collapse('toggle');
    if ($('#justification-toggle-icon').hasClass('glyphicon-chevron-up')) {
    } else {
    }
  })
  $('#justification-container').on('hidden.bs.collapse', function (e) {
    $('#justification-toggle-icon').removeClass('glyphicon-chevron-up');
    $('#justification-toggle-icon').addClass('glyphicon-chevron-down');
  });
  $('#justification-container').on('shown.bs.collapse', function (e) {
    $('#justification-toggle-icon').removeClass('glyphicon-chevron-down');
    $('#justification-toggle-icon').addClass('glyphicon-chevron-up');
  });
});