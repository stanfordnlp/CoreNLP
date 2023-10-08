package edu.stanford.nlp.ie.ner.webapp;

import java.awt.Color;
import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.nlp.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.crf.NERGUI;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreAnnotations;

/**
 *  This is a servlet interface to the CRFClassifier.
 *
 *  @author Dat Hoang 2011
 *  @author John Bauer
 *
 **/

public class NERServlet extends HttpServlet {

  private static final long serialVersionUID = 1584102147050497227L;

  private String format;
  private boolean spacing;
  private String defaultClassifier;
  private List<String> classifiers = new ArrayList<>();
  private Map<String, CRFClassifier<CoreMap>> ners;

  private static final int MAXIMUM_QUERY_LENGTH = 3000;

  @Override
  public void init() throws ServletException {
    format = getServletConfig().getInitParameter("outputFormat");
    if (format == null || format.trim().isEmpty()) {
      throw new ServletException("Invalid outputFormat setting.");
    }

    String spacingStr = getServletConfig().getInitParameter("preserveSpacing");
    if (spacingStr == null || spacingStr.trim().isEmpty()) {
      throw new ServletException("Invalid preserveSpacing setting.");
    }
    //spacing = Boolean.valueOf(spacingStr).booleanValue();
    spacingStr = spacingStr.trim().toLowerCase();
    spacing = "true".equals(spacingStr);

    String path = getServletContext().getRealPath("/WEB-INF/data/models");
    for (String classifier : new File(path).list()) {
      classifiers.add(classifier);
    }
    // TODO: get this from somewhere more interesting?
    defaultClassifier = classifiers.get(0);

    for (String classifier : classifiers) {
      log(classifier);
    }

    ners = new HashMap<>();
    for (String classifier : classifiers) {
      CRFClassifier<CoreMap> model = null;
      String filename = "/WEB-INF/data/models/" + classifier;
      InputStream is = getServletConfig().getServletContext().getResourceAsStream(filename);

      if (is == null) {
        throw new ServletException("File not found. Filename = " + filename);
      }
      try {
        if (filename.endsWith(".gz")) {
          is = new BufferedInputStream(new GZIPInputStream(is));
        } else {
          is = new BufferedInputStream(is);
        }
        model = CRFClassifier.getClassifier(is);
      } catch (IOException e) {
        throw new ServletException("IO problem reading classifier.");
      } catch (ClassCastException e) {
        throw new ServletException("Classifier class casting problem.");
      } catch (ClassNotFoundException e) {
        throw new ServletException("Classifier class not found problem.");
      } finally {
        IOUtils.closeIgnoringExceptions(is);
      }
      ners.put(classifier, model);
    }
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/html; charset=UTF-8");

    this.getServletContext().getRequestDispatcher("/header.jsp").
      include(request, response);
    request.setAttribute("classifiers", classifiers);
    this.getServletContext().getRequestDispatcher("/ner.jsp").
      include(request, response);
    addResults(request, response);
    this.getServletContext().getRequestDispatcher("/footer.jsp").
      include(request, response);
  }

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
  {
    doGet(request, response);
  }

  private void addResults(HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
    String input = request.getParameter("input");
    if (input == null) {
      return;
    }
    input = input.trim();
    if (input.isEmpty()) {
      return;
    }

    PrintWriter out = response.getWriter();
    if (input.length() > MAXIMUM_QUERY_LENGTH) {
      out.print("This query is too long.  If you want to run very long queries, please download and use our <a href=\"http://nlp.stanford.edu/software/CRF-NER.html\">publicly released distribution</a>.");
      return;
    }

    String outputFormat = request.getParameter("outputFormat");
    if (outputFormat == null || outputFormat.trim().isEmpty()) {
      outputFormat = this.format;
    }

    boolean preserveSpacing;
    String preserveSpacingStr = request.getParameter("preserveSpacing");
    if (preserveSpacingStr == null || preserveSpacingStr.trim().isEmpty()) {
      preserveSpacing = this.spacing;
    } else {
      preserveSpacingStr = preserveSpacingStr.trim();
      preserveSpacing = Boolean.valueOf(preserveSpacingStr);
    }

    String classifier = request.getParameter("classifier");
    if (classifier == null || classifier.trim().isEmpty()) {
      classifier = this.defaultClassifier;
    }

    CRFClassifier<CoreMap> nerModel = ners.get(classifier);
    // check that we weren't asked for a classifier that doesn't exist
    if (nerModel == null) {
      out.print(StringEscapeUtils.escapeHtml4("Unknown model " + classifier));
      return;
    }

    if (outputFormat.equals("highlighted")) {
      outputHighlighting(out, nerModel, input);
    } else {
      out.print(StringEscapeUtils.escapeHtml4(nerModel.classifyToString(input, outputFormat, preserveSpacing)));
    }

    response.addHeader("classifier", classifier);
    // a non-existent outputFormat would have just thrown an exception
    response.addHeader("outputFormat", outputFormat);
    response.addHeader("preserveSpacing", String.valueOf(preserveSpacing));
  }

  private static void outputHighlighting(PrintWriter out,
                                         CRFClassifier<CoreMap> classifier,
                                         String input) {
    Set<String> labels = classifier.labels();
    String background = classifier.backgroundSymbol();
    List<List<CoreMap>> sentences = classifier.classify(input);
    Map<String, Color> tagToColorMap =
      NERGUI.makeTagToColorMap(labels, background);

    StringBuilder result = new StringBuilder();
    int lastEndOffset = 0;
    for (List<CoreMap> sentence : sentences) {
      for (CoreMap word : sentence) {
        int beginOffset = word.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int endOffset = word.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        String answer = word.get(CoreAnnotations.AnswerAnnotation.class);

        if (beginOffset > lastEndOffset) {
          result.append(StringEscapeUtils.escapeHtml4(input.substring(lastEndOffset, beginOffset)));
        }
        // Add a color bar for any tagged words
        if (!background.equals(answer)) {
          Color color = tagToColorMap.get(answer);
          result.append("<span style=\"color:#ffffff;background:" +
                        NERGUI.colorToHTML(color) + "\">");
        }

        result.append(StringEscapeUtils.escapeHtml4(input.substring(beginOffset, endOffset)));
        // Turn off the color bar
        if (!background.equals(answer)) {
          result.append("</span>");
        }

        lastEndOffset = endOffset;
      }
    }
    if (lastEndOffset < input.length()) {
      result.append(StringEscapeUtils.escapeHtml4(input.substring(lastEndOffset)));
    }
    result.append("<br><br>");
    result.append("Potential tags:");
    for (Map.Entry<String, Color> stringColorEntry : tagToColorMap.entrySet()) {
      result.append("<br>&nbsp;&nbsp;");
      Color color = stringColorEntry.getValue();
      result.append("<span style=\"color:#ffffff;background:" +
                    NERGUI.colorToHTML(color) + "\">");
      result.append(StringEscapeUtils.escapeHtml4(stringColorEntry.getKey()));
      result.append("</span>");
    }
    out.print(result);
  }

}
