package org.apache.jsp;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import org.apache.commons.lang3.StringEscapeUtils;

public final class header_jsp extends org.apache.jasper.runtime.HttpJspBase
    implements org.apache.jasper.runtime.JspSourceDependent {


  String SELECTED(boolean value) {
    return value ? "selected=\"selected\"" : "";
  }

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

      out.write("\r\n\r\n");
      out.write("\r\n\r\n<html lang=\"en-US\" xml:lang=\"en-US\" xmlns=\"http://www.w3.org/1999/xhtml\">\r\n<head>\r\n    <meta http-equiv=\"Content-Type\" content=\"application/xhtml+xml; charset=utf-8\"/>\r\n\r\n    <link href=\"http://nlp.stanford.edu/nlp.css\" rel=\"stylesheet\" \r\n          type=\"text/css\" />\r\n  <title>Stanford CoreNLP</title>\r\n<style type=\"text/css\">\r\n<!--\r\n#Footer {\r\nposition: relative;\r\nbottom: 0px;\r\n}\r\n-->\r\n</style>\r\n\r\n  <link rel=\"icon\" type=\"image/x-icon\" href=\"/ner/favicon.ico\" />\r\n  <link rel=\"shortcut icon\" type=\"image/x-icon\" \r\n        href=\"/ner/favicon.ico\" />\r\n\r\n</head>\r\n<body>\r\n\r\n<div>\r\n<h1>Stanford CoreNLP</h1>\r\n<FORM name=\"myform\" METHOD=\"POST\" ACTION=\"process\" accept-charset=\"UTF-8\">\r\n  <table>\r\n    <tr><td>\r\n      Output format:\r\n      ");
 String format = request.getParameter("outputFormat"); 
      out.write("\r\n      <select name=\"outputFormat\">\r\n        <option value=\"visualise\" ");
      out.print(SELECTED("visualise".equals(format)));
      out.write(" >Visualise</option>\r\n        <option value=\"pretty\" ");
      out.print(SELECTED("pretty".equals(format)));
      out.write(" >Pretty print</option>\r\n        <option value=\"xml\" ");
      out.print(SELECTED("xml".equals(format)));
      out.write(" >XML</option>\r\n      </select>\r\n    </td></tr>\r\n  \r\n    <tr><td colspan=2>\r\n      <br>Please enter your text here:<br><br>\r\n      <textarea valign=top name=\"input\" \r\n                style=\"width: 400px; height: 8em\" rows=31 cols=7>");
 
         String input = request.getParameter("input");
         if (input != null) {
           
      out.print(StringEscapeUtils.escapeHtml4(input));

         }
         
      out.write("</textarea>\r\n    </td></tr>\r\n\r\n    <tr><td align=left>\r\n      <input type=\"submit\" name=\"Process\"/>\r\n        <input type=\"button\" value=\"Clear\"\r\n               onclick=\"this.form.elements['input'].value=''\"/>\r\n    </td></tr>\r\n  </table>\r\n</FORM>\r\n</div>\r\n");
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
