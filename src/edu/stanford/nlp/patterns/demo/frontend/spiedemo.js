
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
  return function(responsestext) {
	$( '#loading' ).hide();
	var responses = jQuery.parseJSON(responsestext);
	if("okay" in responses){
	    if(responses.okay == "false"){
	    	handleError("Failed to execute.");
			resetDemo();
	    }
	} else{
    var entities = $.map(responses, function(el) { return el; });
		if (entities.length > 0) {
            var tmpHeader = '<div class="col-md-12 response btn-lg" >Extracted Entities</div>';
			var tmp = entities.map(function(val){return '<div class="col-md-3 entityEx">'+val+'</div>' })
			$( "#triples-container" ).html('<div class="row">'+tmpHeader+tmp.join("")+'</div>');
			}
		else{
			$("#triples-container").html('<div class="row"> <div class="col-md-12 response">No results returned!</div> </div>');
		}
		resetDemo();
	}
  }
}


$(document).ready(function(){
	var models = {"food":{"name":"Food Items","seeds":["crab salad","chicken","craft beer","wasabi tobiko eggs","salad","kung pao","chowder","horseradish","sandwich","butter chicken","icecream","dessert","cake"]},"sports":{"name":"Players","seeds":["Curabitur","molestie","nulla","quis","pharetra","congue.","Donec","fermentum","eros","eget","arcu","congue,","vitae","tincidunt","magna","lobortis.","Morbi","sollicitudin","eros","a","dolor","rutrum","bibendum."]},"drugs":{"name":"Drugs","seeds":["Vestibulum","in","nisi"]}};
	$( '#loading' ).hide();
	$( '#options' ).hide();
	$( '#learned' ).hide();
	$( "#accordion" ).accordion({collapsible: true,heightStyle: "content",active: false});
	$("#qmark").tooltip({
		'selector': '',
		'placement': 'right',
		'container':'body'
		});
	$( '#checkoptions').prop("checked",true);
	$( '#checkoptions').click(function(){$('#options').toggle();});
	$( "#seedwords" ).prop("readonly", true);
	jQuery.support.cors = true;
	$( ".message" ).dialog({modal: true,autoOpen: false});
	jQuery.each(models,function(nm, mod) {
		$("#modlist").append('<li><a href="#" data-value="'+nm+'">'+mod["name"]+'</a></li>')
		$.ajax({
			url: nm+".txt",
			dataType: 'text',
			success: function( response ) {models[nm]["learned"]=$.trim(response).split('\n');},
			error:  function( xhr ) {
						models[nm]["learned"]=false;
						var readyState = {1: "Loading",2: "Loaded",3: "Interactive",4: "Complete"};
						if(xhr.readyState !== 0 && xhr.status !== 0 && xhr.responseText !== undefined) {
							$( '#loading' ).hide();
						}
					}
		})
	});
	$("#modlist").append('<li id="divide" class="divider"></li>')
	$("#modlist").append('<li><a href="#" data-value="new">Train new model</a></li>')
	$(".dropdown-menu li a").click(function(){
		$('#model').html($(this).text() + '<span class="caret"></span>');
		$('#model').val($(this).data('value'));
			$( "#seedwords" ).prop("readonly", $(this).data('value') == "new"?false:true);
			if($(this).data('value') != "new"){
				var tmp = models[$(this).data('value')]["seeds"].slice(0,Math.min(10,models[$(this).data('value')]["seeds"].length)).join(" ,")
				var tmp2 = models[$(this).data('value')]["seeds"].length > 10?",...":""
				$( "#seedwords" ).val(tmp+tmp2)
				if(models[$(this).data('value')]["learned"] != false){
					$( '#learned' ).show();
					var tmp = models[$(this).data('value')]["learned"].map(function(val){return '<div class="col-md-3 entityEx">'+val+'</div>' })
					$( "#lentities" ).html('<div class="row"><p>'+tmp.join("")+'</p></div>');
					}
			  }else{
				$( "#seedwords" ).val("")
				$( "#learned" ).hide();
			  }
	});
	// Query submit
	$( "#form-query" ).submit(function( event ) {
    //don't actually submit anything
    event.preventDefault();
	$( "#triples-container" ).html("")
    // set the headers
    $( "#system-header" ).css("visibility", "visible");
    // raise Error if empty submission
    if ( $( '#q' ).val().trim() == '' || $( '#seedwords' ).val().trim() == '') {
		handleError("No input. Please enter the text and seed words.");
	}else{
		var ifil =  $( '#seedwords' ).val().trim()
		$( '#loading' ).show();
		// submission data
		var data = $(this).serializeArray();
		data.push({name:"model",value:$("#model").val()})
		// disable query button
		freezeDemo()
		// ajax request
		$.ajax({
			type: "POST",
			url: '/spied/spied',
			//url: "output.json",
			data: $.param(data),
			dataType: 'text',
			success: querySuccess("#triples-container"),
			error:  function( xhr ) {
						var readyState = {1: "Loading",2: "Loaded",3: "Interactive",4: "Complete"};
						if(xhr.readyState !== 0 && xhr.status !== 0 && xhr.responseText !== undefined) {
							$( '#loading' ).hide();
							handleError("Failed to execute query!");0
						}
					}
		})
	}
  });


  // Query button
  $( "#query-button" ).mousedown(function(event) {
    $( '#query-button' ).css('background', 'darkgray');
  });
  $( document ).mouseup(function(event) {
    $( '#query-button' ).css('background', 'grey');
  });
  $( "#query-button" ).click(function(event) { $( "#form-query" ).submit(); });
});
