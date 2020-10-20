<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">

<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.lang3.StringEscapeUtils" %>
<%@ page import="edu.stanford.nlp.util.ErasureUtils" %>

<%!
  private String SELECTED(boolean value) {
    return value ? "selected=\"selected\"" : "";
  }
%>

  <div id="Content">
    <div id="Section">
      <div id="ContentBody">
        <h1>Stanford Named Entity Tagger</h1>
        <FORM name="myform" METHOD="POST" ACTION="process"
              accept-charset="UTF-8">
          <table>
            <tr><td>
                Classifier:
                <select name="classifier">
                  <% List<String> classifiers = ErasureUtils.uncheckedCast(request.getAttribute("classifiers"));
                     String currentClassifier = request.getParameter("classifier");
                     if (currentClassifier == null) {
                       currentClassifier = "";
                     }
                     for (String classifier : classifiers) {
                       %><option value="<%= classifier %>"
                                 <%= SELECTED(currentClassifier.equals(classifier))%> ><%= classifier %></option><%
                     } %>
                </select>
            </td></tr>
            <tr><td>
                Output Format:
                <% String format = request.getParameter("outputFormat"); %>
                <select name="outputFormat">
                  <option value="highlighted" <%=SELECTED("highlighted".equals(format))%> >highlighted</option>
                  <option value="inlineXML" <%=SELECTED("inlineXML".equals(format))%> >inlineXML</option>
                  <option value="xml" <%=SELECTED("xml".equals(format))%> >xml</option>
                  <option value="slashTags" <%=SELECTED("slashTags".equals(format))%> >slashTags</option>
                  <option value="tabbedEntities" <%=SELECTED("tabbedEntities".equals(format))%> >slashTags</option>
                  <option value="tsv" <%=SELECTED("tsv".equals(format))%> >slashTags</option>
                </select>
            </td></tr>
            <tr><td>
                Preserve Spacing:
                <% String spacing = request.getParameter("preserveSpacing");
                   boolean spacingSelected = spacing == null || spacing.equals("true"); %>
                <select name="preserveSpacing">
                  <option value="true"
                          <%= SELECTED(spacingSelected) %>>yes</option>
                  <option value="false"
                          <%= SELECTED(!spacingSelected) %>>no</option>
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
    </div>
  </div>
