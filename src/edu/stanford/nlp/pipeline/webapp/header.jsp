<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>

<%!
  String SELECTED(boolean value) {
    return value ? "selected=\"selected\"" : "";
  }
%>

<html lang="en-US" xml:lang="en-US" xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="application/xhtml+xml; charset=utf-8"/>

    <link href="http://nlp.stanford.edu/nlp.css" rel="stylesheet" 
          type="text/css" />
  <title>Stanford CoreNLP</title>
<style type="text/css">
<!--
#Footer {
position: relative;
bottom: 0px;
}
-->
</style>

  <link rel="icon" type="image/x-icon" href="/ner/favicon.ico" />
  <link rel="shortcut icon" type="image/x-icon" 
        href="/ner/favicon.ico" />

</head>
<body>

<div>
<h1>Stanford CoreNLP</h1>
<FORM name="myform" METHOD="POST" ACTION="process" accept-charset="UTF-8">
  <table>
    <tr><td>
      Output format:
      <% String format = request.getParameter("outputFormat"); %>
      <select name="outputFormat">
        <option value="visualise" <%=SELECTED("visualise".equals(format))%> >Visualise</option>
        <option value="pretty" <%=SELECTED("pretty".equals(format))%> >Pretty print</option>
        <option value="xml" <%=SELECTED("xml".equals(format))%> >XML</option>
        <option value="json" <%=SELECTED("json".equals(format))%> >JSON</option>
        <option value="conll" <%=SELECTED("conll".equals(format))%> >CoNLL</option>
      </select>
    </td></tr>
  
    <tr><td colspan=2>
      <br>Please enter your text here:<br><br>
      <textarea valign=top name="input" 
                style="width: 400px; height: 8em" rows=31 cols=7><% 
         String input = request.getParameter("input");
         if (input != null) {
           %><%=StringEscapeUtils.escapeHtml4(input)%><%
         }
         %></textarea>
    </td></tr>

    <tr><td align=left>
      <input type="submit" name="Process"/>
        <input type="button" value="Clear"
               onclick="this.form.elements['input'].value=''"/>
    </td></tr>
  </table>
</FORM>
</div>
