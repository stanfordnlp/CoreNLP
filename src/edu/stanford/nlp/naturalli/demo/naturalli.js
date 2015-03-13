
function handleError(message) {
  console.log("FAILURE!");
  $( "#q" ).prop('disabled', false);
  $( "#query-button").unbind( "click" );
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
  $( "#triples-container").html('<div style="color:red; font-size:xx-large;">ERROR</div> <div style="color: black; font-size: 12pt">(' + message + ')<div>');
}

function querySuccess(query) {
  console.log("fn-rtn");
  return function(response) {
    console.log("SUCCESS!");
    $( "#q" ).prop('disabled', false);
    $( "#query-button").unbind( "click" );
    $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
    console.log(response.triples);
    var gloss = ""
    for (i = 0; i < response.triples.length; ++i) {
      gloss += '(' + response.triples[i][0] + '; ' + response.triples[i][1] + '; ' + response.triples[i][2] + ') <br/>';
    }
    $( "#triples-container" ).html(gloss);
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
    if ( $( '#q' ).val().trim() == '') { $( '#q' ).val('faeries are dancing in the field where I lost my bike.'); }
    // (start loading icon)
    $( '#triples-container' ).html('loading...');
    // (submission data)
    target = $(this).attr('action');
    getData = $(this).serialize();
    value = $( "#q" ).val();
    // (disable query button
    $( "#q" ).prop('disabled', true);
    $( "#query-button").unbind( "click" );
    // (ajax request)
    console.log("Querying...");
    $.ajax({
      url: target,
      data: getData,
      dataType: 'json',
      success: querySuccess(value)
    });
  });


  // Query button
  $( "#query-button" ).mousedown(function(event) {
    $( '#query-button' ).css('background', 'darkgray');
  });
  $( document ).mouseup(function(event) {
    $( '#query-button' ).css('background', '');
  });
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
});