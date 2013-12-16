package org.apache.jsp;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import edu.stanford.nlp.time.Options;
import edu.stanford.nlp.time.suservlet.SUTimeServlet;
import org.apache.commons.lang3.StringEscapeUtils;

public final class header_jsp extends org.apache.jasper.runtime.HttpJspBase
    implements org.apache.jasper.runtime.JspSourceDependent {

  private static final javax.servlet.jsp.JspFactory _jspxFactory =
          javax.servlet.jsp.JspFactory.getDefaultFactory();

  private static java.util.List<java.lang.String> _jspx_dependants;

  private javax.el.ExpressionFactory _el_expressionfactory;
  private org.apache.tomcat.InstanceManager _jsp_instancemanager;

  public java.util.List<java.lang.String> getDependants() {
    return _jspx_dependants;
  }

  public void _jspInit() {
    _el_expressionfactory = _jspxFactory.getJspApplicationContext(getServletConfig().getServletContext()).getExpressionFactory();
    _jsp_instancemanager = org.apache.jasper.runtime.InstanceManagerFactory.getInstanceManager(getServletConfig());
  }

  public void _jspDestroy() {
  }

  public void _jspService(final javax.servlet.http.HttpServletRequest request, final javax.servlet.http.HttpServletResponse response)
        throws java.io.IOException, javax.servlet.ServletException {

    final javax.servlet.jsp.PageContext pageContext;
    javax.servlet.http.HttpSession session = null;
    final javax.servlet.ServletContext application;
    final javax.servlet.ServletConfig config;
    javax.servlet.jsp.JspWriter out = null;
    final java.lang.Object page = this;
    javax.servlet.jsp.JspWriter _jspx_out = null;
    javax.servlet.jsp.PageContext _jspx_page_context = null;


    try {
      response.setContentType("text/html");
      pageContext = _jspxFactory.getPageContext(this, request, response,
      			null, true, 8192, true);
      _jspx_page_context = pageContext;
      application = pageContext.getServletContext();
      config = pageContext.getServletConfig();
      session = pageContext.getSession();
      out = pageContext.getOut();
      _jspx_out = out;

      out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n\n\n\n\n\n<html>\n<head>\n  <META http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\">\n  <title>Stanford Temporal Tagger: SUTime</title>\n  <link rel=\"stylesheet\" href=\"http://nlp.stanford.edu/nlp.css\"/>\n  <link rel=\"stylesheet\" href=\"/sutime/calendarview.css\"/>\n  <link rel=\"stylesheet\" href=\"/sutime/sutime.css\"/>\n  <link rel=\"icon\" type=\"image/x-icon\" href=\"/sutime/favicon.ico\" />\n\n  <link rel=\"shortcut icon\" type=\"image/x-icon\" href=\"/sutime/favicon.ico\" />\n  <script type=\"text/javascript\" src=\"/sutime/prototype.js\"></script>\n  <script type=\"text/javascript\" src=\"/sutime/calendarview.js\"></script>\n  <script>\n     function displayOptions() {\n       var field = input.annotator;\n       if (annotator.options[annotator.options.selectedIndex].value == \"sutime\") {\n         $(sutimeOptions).style.display = \"\";\n       } else {\n         $(sutimeOptions).style.display = \"none\";\n       }\n     }\n     function setupCalendars() {\n         Calendar.setup(\n           {\n              dateField: 'd',\n");
      out.write("              triggerElement: 'popupCalendar'\n           }\n         )\n     }\n     function sampleSentence() {\n        input.q.value = \"Last summer, they met every Tuesday afternoon, from 1:00 pm to 3:00 pm.\"\n     }\n     function onLoad() {\n        displayOptions();\n        setupCalendars();\n        if (input.q.value == \"\") {\n          // Populate with sample sentence and date for today and submit\n          sampleSentence();\n          if (input.d.value == \"\") {\n            var date = new Date();\n            var m = date.getMonth() + 1;\n            var d = date.getDate();\n            var y = date.getFullYear();\n            input.d.value = \"\" + y + \"-\" + m + \"-\" + d;\n          }\n          input.submit();\n        }\n     }\n     Event.observe(window, 'load', onLoad )\n  </script>\n</head>\n<body>\n<h1>Stanford Temporal Tagger: SUTime</h1>\n<form name=\"input\" METHOD=\"POST\" ACTION=\"process\" accept-charset=\"UTF-8\">\n<table id=\"Main\">\n<tr><td>\nPlease enter a reference date (format must be YYYY-MM-DD):\n<br><br>\nDate:\n<input type=\"text\" id=\"d\" name=\"d\" ");

  String dateString = request.getParameter("d");
  if (dateString != null) {
    
      out.write("value=\"");
      out.print(StringEscapeUtils.escapeHtml4(dateString));
      out.write('"');

  }
      out.write("  />\n<input type=\"button\" value=\"Calendar\" id=\"popupCalendar\"/>\n</td><td>\n<div id=\"annoDiv\" style=\"display:none;\">\n<b>Annotate using</b>:<br><br>\n  <select id=\"annotator\" name=\"annotator\" onchange=\"javascript:displayOptions()\">\n    <option value=\"sutime\" ");
      out.print( "sutime".equals(request.getParameter("annotator"))? "selected='true'" : "" );
      out.write(" >SUTime</option>\n    <option value=\"gutime\" ");
      out.print( "gutime".equals(request.getParameter("annotator"))? "selected='true'" : "" );
      out.write(" >GUTime</option>\n    <option value=\"heideltime\" ");
      out.print( "heideltime".equals(request.getParameter("annotator"))? "selected='true'" : "" );
      out.write(" >HeidelTime</option>\n  </select>\n</div>\n</td></tr>\n<tr><td>\nPlease enter your text here (<a href=\"javascript:sampleSentence()\">sample sentence</a>):\n<br><br>\n<textarea name=\"q\" id=\"q\"\n         style=\"width: 400px; height: 8em\"\n         rows=\"31\" cols=\"7\">");

  String query = request.getParameter("q");
  if (query != null && !query.equals("")) {
    
      out.print(StringEscapeUtils.escapeHtml4(query));

  }

      out.write("</textarea>\n<br>\n<input type=\"submit\" value=\"Submit\" />\n<input type=\"button\" value=\"Clear\" onclick=\"this.form.elements['q'].value=''\"/>\n</td>\n<td>\n<div id=\"sutimeOptions\" style=\"display:none;\">\n<b>Options</b>:<br><br>\n<input type=\"checkbox\" name=\"markTimeRanges\"\n   ");
      out.print( SUTimeServlet.parseBoolean(request.getParameter("markTimeRanges")) ? 
       "checked" : "" );
      out.write(" /> Mark time ranges <br>\n<input type=\"checkbox\" name=\"includeNested\"\n   ");
      out.print( SUTimeServlet.parseBoolean(request.getParameter("includeNested")) ? 
       "checked" : "" );
      out.write(" /> Include nested <br>\n<input type=\"checkbox\" name=\"includeRange\"\n   ");
      out.print( SUTimeServlet.parseBoolean(request.getParameter("includeRange")) ? 
       "checked" : "" );
      out.write(" /> Include range <br>\n<br>\n<div id=\"rulesDiv\" name=\"rulesDiv\">\n  <br> Rules:\n  <select name=\"rules\">\n    <option value=\"english\" selected=\"true\">English</option>\n  </select>\n</div>\n<div id=\"relheurlevelDiv\" name=\"relheurlevelDiv\" style=\"display:none;\">\n<br> Relative heuristic level: <br>\n");

  String heuristicLevel = request.getParameter("relativeHeuristicLevel");
  Options.RelativeHeuristicLevel relativeHeuristicLevel = 
    Options.RelativeHeuristicLevel.NONE;
  if (heuristicLevel != null && !heuristicLevel.equals("")) {
    relativeHeuristicLevel = 
      Options.RelativeHeuristicLevel.valueOf(heuristicLevel);
  }

  for (Options.RelativeHeuristicLevel level :
         Options.RelativeHeuristicLevel.values()) {
  
      out.write(" <input type=\"radio\" name=\"relativeHeuristicLevel\"\n            value=\"");
      out.print( level );
      out.write("\"\n            ");
      out.print( level.equals(relativeHeuristicLevel) ? "checked" : "" );
      out.write(" />\n     ");
      out.print( level );
      out.write(" <br> ");

  }

      out.write("\n</div>\n</div>\n</td>\n</tr>\n</table>\n</form> \n\n");
    } catch (java.lang.Throwable t) {
      if (!(t instanceof javax.servlet.jsp.SkipPageException)){
        out = _jspx_out;
        if (out != null && out.getBufferSize() != 0)
          try { out.clearBuffer(); } catch (java.io.IOException e) {}
        if (_jspx_page_context != null) _jspx_page_context.handlePageException(t);
      }
    } finally {
      _jspxFactory.releasePageContext(_jspx_page_context);
    }
  }
}
