package edu.stanford.nlp.time.suservlet;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.time.Options;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.CoreMap;

import edu.stanford.nlp.util.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;

public class SUTimeServlet extends HttpServlet {

  private SUTimePipeline pipeline; // = null;

  @Override
  public void init() throws ServletException {
    String dataDir = getServletContext().getRealPath("/WEB-INF/data");
    String taggerFilename = dataDir + "/english-left3words-distsim.tagger";
    Properties pipelineProps = new Properties();
    pipelineProps.setProperty("pos.model", taggerFilename);
    pipeline = new SUTimePipeline(pipelineProps);
    System.setProperty("de.jollyday.config",
            getServletContext().getRealPath("/WEB-INF/classes/holidays/jollyday.properties"));
  }

  public static boolean parseBoolean(String value) {
    if (StringUtils.isNullOrEmpty(value)) {
      return false;
    }
    if (value.equalsIgnoreCase("on")) {
      return true;
    }
    return Boolean.parseBoolean(value);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/html; charset=UTF-8");

    this.getServletContext().getRequestDispatcher("/header.jsp").
      include(request, response);
    addResults(request, response);
    this.getServletContext().getRequestDispatcher("/footer.jsp").
      include(request, response);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    doGet(request, response);
  }

  private String getRuleFilepaths(String... files) {
    String rulesDir = getServletContext().getRealPath("/WEB-INF/data/rules");
    StringBuilder sb = new StringBuilder();
    for (String file:files) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(rulesDir + "/" + file);
    }
    return sb.toString();
  }

  private Properties getTimeAnnotatorProperties(HttpServletRequest request) {
    // Parses request and set up properties for time annotators
    boolean markTimeRanges =
            parseBoolean(request.getParameter("markTimeRanges"));
    boolean includeNested =
            parseBoolean(request.getParameter("includeNested"));
    boolean includeRange =
            parseBoolean(request.getParameter("includeRange"));
    boolean readRules = true;

    String heuristicLevel = request.getParameter("relativeHeuristicLevel");
    Options.RelativeHeuristicLevel relativeHeuristicLevel =
            Options.RelativeHeuristicLevel.NONE;
    if ( ! StringUtils.isNullOrEmpty(heuristicLevel)) {
      relativeHeuristicLevel = Options.RelativeHeuristicLevel.valueOf(heuristicLevel);
    }
    String ruleFile = null;
    if (readRules) {
      String rules = request.getParameter("rules");
      if ("English".equalsIgnoreCase(rules)) {
        ruleFile = getRuleFilepaths("defs.sutime.txt", "english.sutime.txt", "english.holidays.sutime.txt");
      }
    }

    // Create properties
    Properties props = new Properties();
    if (markTimeRanges) {
      props.setProperty("sutime.markTimeRanges", "true");
    }
    if (includeNested) {
      props.setProperty("sutime.includeNested", "true");
    }
    if (includeRange) {
      props.setProperty("sutime.includeRange", "true");
    }
    if (ruleFile != null) {
      props.setProperty("sutime.rules", ruleFile);
      props.setProperty("sutime.binders", "1");
      props.setProperty("sutime.binder.1", "edu.stanford.nlp.time.JollyDayHolidays");
      props.setProperty("sutime.binder.1.xml", getServletContext().getRealPath("/WEB-INF/data/holidays/Holidays_sutime.xml"));
      props.setProperty("sutime.binder.1.pathtype", "file");
    }
    props.setProperty("sutime.teRelHeurLevel",
            relativeHeuristicLevel.toString());
//    props.setProperty("sutime.verbose", "true");

//    props.setProperty("heideltime.path", getServletContext().getRealPath("/packages/heideltime"));
//    props.setProperty("gutime.path", getServletContext().getRealPath("/packages/gutime"));
    return props;
  }

  private static void displayAnnotation(PrintWriter out, String query, Annotation anno, boolean includeOffsets) {
    List<CoreMap> timexAnns = anno.get(TimeAnnotations.TimexAnnotations.class);
    List<String> pieces = new ArrayList<>();
    List<Boolean> tagged = new ArrayList<>();
    int previousEnd = 0;
    for (CoreMap timexAnn : timexAnns) {
      int begin =
              timexAnn.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int end =
              timexAnn.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      if (begin >= previousEnd) {
        pieces.add(query.substring(previousEnd, begin));
        tagged.add(false);
        pieces.add(query.substring(begin, end));
        tagged.add(true);
        previousEnd = end;
      }
    }
    if (previousEnd < query.length()) {
      pieces.add(query.substring(previousEnd));
      tagged.add(false);
    }

    out.println("<table id='Annotated'><tr><td>");
    for (int i = 0; i < pieces.size(); ++i) {
      if (tagged.get(i)) {
        out.print("<span style=\"background-color: #FF8888\">");
        out.print(StringEscapeUtils.escapeHtml4(pieces.get(i)));
        out.print("</span>");
      } else {
        out.print(StringEscapeUtils.escapeHtml4(pieces.get(i)));
      }
    }
    out.println("</td></tr></table>");

    out.println("<h3>Temporal Expressions</h3>");
    if (timexAnns.size() > 0) {
      out.println("<table>");
      out.println("<tr><th>Text</th><th>Value</th>");
      if (includeOffsets) {
        out.println("<th>Char Begin</th><th>Char End</th><th>Token Begin</th><th>Token End</th>");
      }
      out.println("<th>Timex3 Tag</th></tr>");
      for (CoreMap timexAnn : timexAnns) {
        out.println("<tr>");
        Timex timex = timexAnn.get(TimeAnnotations.TimexAnnotation.class);
        int begin =
                timexAnn.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int end =
                timexAnn.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        out.print("<td>" + StringEscapeUtils.escapeHtml4(query.substring(begin, end)) + "</td>");
        out.print("<td>" + ((timex.value() != null)? StringEscapeUtils.escapeHtml4(timex.value()):"") + "</td>");
        if (includeOffsets) {
          out.print("<td>" + begin + "</td>");
          out.print("<td>" + end + "</td>");
          out.print("<td>" + timexAnn.get(CoreAnnotations.TokenBeginAnnotation.class) + "</td>");
          out.print("<td>" + timexAnn.get(CoreAnnotations.TokenEndAnnotation.class) + "</td>");
        }
        out.print("<td>" + StringEscapeUtils.escapeHtml4(timex.toString()) + "</td>");
        out.println("</tr>");
      }
      out.println("</table>");
    } else {
      out.println("<em>No temporal expressions.</em>");
    }

    out.println("<h3>POS Tags</h3>");
    out.println("<table><tr><td>");
    for (CoreMap sentence : anno.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      for (CoreLabel token : tokens) {
        String tokenOutput =
                StringEscapeUtils.escapeHtml4(token.word() + "/" + token.tag());
        out.print(tokenOutput + " ");
      }
      out.println("<br>");
    }
    out.println("</td></tr></table>");
  }

  private void addResults(HttpServletRequest request,
                          HttpServletResponse response)
    throws IOException {
    // if we can't handle UTF-8, need to do something like this...
    //String originalQuery = request.getParameter("q");
    //String query = WebappUtil.convertString(originalQuery);

    String query = request.getParameter("q");
    String dateString = request.getParameter("d");
    // TODO: this always returns true...
    boolean dateError = ! pipeline.isDateOkay(dateString);
    boolean includeOffsets = parseBoolean(request.getParameter("includeOffsets"));
    PrintWriter out = response.getWriter();
    if (dateError) {
      out.println("<br><br>Warning: unparseable date " +
                  StringEscapeUtils.escapeHtml4(dateString));
    }

    if ( ! StringUtils.isNullOrEmpty(query)) {
      Properties props = getTimeAnnotatorProperties(request);
      String annotatorType = request.getParameter("annotator");
      if (annotatorType == null) {
        annotatorType = "sutime";
      }
      Annotator timeAnnotator = pipeline.getTimeAnnotator(annotatorType, props);
      if (timeAnnotator != null) {
        Annotation anno = pipeline.process(query, dateString, timeAnnotator);
        out.println("<h3>Annotated Text</h3> <em>(tagged using " + annotatorType + "</em>)");
        displayAnnotation(out, query, anno, includeOffsets);
      } else {
        out.println("<br><br>Error creating annotator for " + StringEscapeUtils.escapeHtml4(annotatorType));
      }


    }

  }

  private static final long serialVersionUID = 1L;

}
