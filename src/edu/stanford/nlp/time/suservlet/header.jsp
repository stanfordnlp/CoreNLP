<?xml version="1.0" encoding="UTF-8"?>

<%@ page import="edu.stanford.nlp.time.Options" %>
<%@ page import="edu.stanford.nlp.time.suservlet.SUTimeServlet" %>

<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>

<html>
<head>
  <META http-equiv="Content-Type" content="text/html;charset=UTF-8">
  <title>Stanford Temporal Tagger: SUTime</title>
  <link rel="stylesheet" href="http://nlp.stanford.edu/nlp.css"/>
  <link rel="stylesheet" href="/sutime/calendarview.css"/>
  <link rel="stylesheet" href="/sutime/sutime.css"/>
  <link rel="icon" type="image/x-icon" href="/sutime/favicon.ico" />

  <link rel="shortcut icon" type="image/x-icon" href="/sutime/favicon.ico" />
  <script type="text/javascript" src="/sutime/prototype.js"></script>
  <script type="text/javascript" src="/sutime/calendarview.js"></script>
  <script>
     function displayOptions() {
       var field = input.annotator;
       if (annotator.options[annotator.options.selectedIndex].value == "sutime") {
         $(sutimeOptions).style.display = "";
       } else {
         $(sutimeOptions).style.display = "none";
       }
     }
     function setupCalendars() {
         Calendar.setup(
           {
              dateField: 'd',
              triggerElement: 'popupCalendar'
           }
         )
     }
     function sampleSentence() {
        input.q.value = "Last summer, they met every Tuesday afternoon, from 1:00 pm to 3:00 pm."
     }
     function onLoad() {
        displayOptions();
        setupCalendars();
        if (input.q.value == "") {
          // Populate with sample sentence and date for today and submit
          sampleSentence();
          if (input.d.value == "") {
            var date = new Date();
            var m = date.getMonth() + 1;
            var d = date.getDate();
            var y = date.getFullYear();
            input.d.value = "" + y + "-" + m + "-" + d;
          }
          input.submit();
        }
     }
     Event.observe(window, 'load', onLoad )
  </script>
</head>
<body>
<h1>Stanford Temporal Tagger: SUTime</h1>
<form name="input" METHOD="POST" ACTION="process" accept-charset="UTF-8">
<table id="Main">
<tr><td>
Please enter a reference date (format must be YYYY-MM-DD):
<br><br>
Date:
<input type="text" id="d" name="d" <%
  String dateString = request.getParameter("d");
  if (dateString != null) {
    %>value="<%=StringEscapeUtils.escapeHtml4(dateString)%>"<%
  }%>  />
<input type="button" value="Calendar" id="popupCalendar"/>
</td><td>
<div id="annoDiv" style="display:none;">
<b>Annotate using</b>:<br><br>
  <select id="annotator" name="annotator" onchange="javascript:displayOptions()">
    <option value="sutime" <%= "sutime".equals(request.getParameter("annotator"))? "selected='true'" : "" %> >SUTime</option>
    <option value="gutime" <%= "gutime".equals(request.getParameter("annotator"))? "selected='true'" : "" %> >GUTime</option>
    <option value="heideltime" <%= "heideltime".equals(request.getParameter("annotator"))? "selected='true'" : "" %> >HeidelTime</option>
  </select>
</div>
</td></tr>
<tr><td>
Please enter your text here (<a href="javascript:sampleSentence()">sample sentence</a>):
<br><br>
<textarea name="q" id="q"
         style="width: 400px; height: 8em"
         rows="31" cols="7"><%
  String query = request.getParameter("q");
  if (query != null && !query.equals("")) {
    %><%=StringEscapeUtils.escapeHtml4(query)%><%
  }
%></textarea>
<br>
<input type="submit" value="Submit" />
<input type="button" value="Clear" onclick="this.form.elements['q'].value=''"/>
</td>
<td>
<div id="sutimeOptions" style="display:none;">
<b>Options</b>:<br><br>
<input type="checkbox" name="markTimeRanges"
   <%= SUTimeServlet.parseBoolean(request.getParameter("markTimeRanges")) ? 
       "checked" : "" %> /> Mark time ranges <br>
<input type="checkbox" name="includeNested"
   <%= SUTimeServlet.parseBoolean(request.getParameter("includeNested")) ? 
       "checked" : "" %> /> Include nested <br>
<input type="checkbox" name="includeRange"
   <%= SUTimeServlet.parseBoolean(request.getParameter("includeRange")) ? 
       "checked" : "" %> /> Include range <br>
<br>
<div id="rulesDiv" name="rulesDiv">
  <br> Rules:
  <select name="rules">
    <option value="english" selected="true">English</option>
  </select>
</div>
<div id="relheurlevelDiv" name="relheurlevelDiv" style="display:none;">
<br> Relative heuristic level: <br>
<%
  String heuristicLevel = request.getParameter("relativeHeuristicLevel");
  Options.RelativeHeuristicLevel relativeHeuristicLevel = 
    Options.RelativeHeuristicLevel.NONE;
  if (heuristicLevel != null && !heuristicLevel.equals("")) {
    relativeHeuristicLevel = 
      Options.RelativeHeuristicLevel.valueOf(heuristicLevel);
  }

  for (Options.RelativeHeuristicLevel level :
         Options.RelativeHeuristicLevel.values()) {
  %> <input type="radio" name="relativeHeuristicLevel"
            value="<%= level %>"
            <%= level.equals(relativeHeuristicLevel) ? "checked" : "" %> />
     <%= level %> <br> <%
  }
%>
</div>
</div>
</td>
</tr>
</table>
</form> 

