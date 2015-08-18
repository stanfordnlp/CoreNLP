
function handleError(message) {
  $( '#messagebody' ).html(message);$('#message').modal('show');
  resetDemo();
}

function resetDemo(){
  $( "#q" ).prop('disabled', false);
  $( "#seedwords" ).prop('disabled', false);
  $( "#query-button").unbind( "click" );
  $( "#q" ).prop("readonly", false);
  $( "#seedwords" ).prop("readonly", false);
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
}

function freezeDemo(){
    $( "#q" ).prop("readonly", true);
    $( "#seedwords" ).prop("readonly", true);
    $( "#query-button").unbind( "click" );	
}


function querySuccess(elem) {
  return function(responses) {
	var entities = $.map(responses, function(el) { return el; });
	var ingein = window.setTimeout(tmpQuery, 5000);
	function tmpQuery(){
		$( '#triples-row > #loading' ).hide();
		if (entities.length > 0) {
			var tmp = entities.map(function(val){return '<a href="#" class="list-group-item">'+val+'</s>' })
			$( "#triples-container" ).html('<ul class="list-group">'+tmp.join("")+'</ul>');
			resetDemo();
			}
		else{
				handleError("No results returned!");
			}
		}
	}
}


$(document).ready(function(){
  $( '#triples-row > #loading' ).hide();
  jQuery.support.cors = true;
		$( ".message" ).dialog({
		  modal: true,
		  autoOpen: false,
		  buttons: {
			Ok: function() {
			  $( this ).dialog( "close" );
			}
		  }
		});
  if ($(window).width() < 650) {
    $( '#fork' ).hide();
  }

  // Justification
  $("#justification-toggle-row").hide();

  // Query submit
  $( "#form-query" ).submit(function( event ) {
    // (don't actually submit anything)
    event.preventDefault();
	$( "#triples-container" ).html("")
    // (set the headers)
    $( "#system-header" ).css("visibility", "visible");
    // (create a default if not input was given)
    if ( $( '#q' ).val().trim() == '' || $( '#seedwords' ).val().trim() == '') { handleError("No input. Please enter the text and seed words.");}
	else{
	var ifil =  $( '#seedwords' ).val().trim()
    // (start loading icon)
    $( '#triples-row > #loading' ).show();
    // (submission data)
    //target = $(this).attr('action');
    //getData = $(this).serialize();
    //value = $( "#q" ).val();
    // (disable query button
    tmpObj = {"hi":"hello"}
	freezeDemo()
    // (ajax request)
    $.ajax({
      url: 'spied/',
      data: tmpObj,
      dataType: 'json',
      success: querySuccess("#triples-container"),
      error:  function( xhr ) {
				
				var readyState = {1: "Loading",2: "Loaded",3: "Interactive",4: "Complete"};
				if(xhr.readyState !== 0 && xhr.status !== 0 && xhr.responseText !== undefined) {
					$( '#triples-row > #loading' ).hide();
					handleError("Failed to execute query!")}
				}
	})}
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
