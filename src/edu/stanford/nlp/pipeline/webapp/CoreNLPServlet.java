package edu.stanford.nlp.pipeline.webapp;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Nodes;
import nu.xom.xslt.XSLTransform;

import org.apache.commons.lang3.StringEscapeUtils;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;

/** @author Gabor Angeli */
public class CoreNLPServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private StanfordCoreNLP pipeline;

  private XSLTransform corenlpTransformer;

  private String defaultFormat = "pretty";

  private static final int MAXIMUM_QUERY_LENGTH = 4096;

  @Override
  public void init() throws ServletException {
    pipeline = new StanfordCoreNLP();

    String xslPath = getServletContext().
                       getRealPath("/WEB-INF/data/CoreNLP-to-HTML.xsl");

    try {
      Builder builder = new Builder();
      Document stylesheet = builder.build(new File(xslPath));
      corenlpTransformer = new XSLTransform(stylesheet);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  @Override
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

  public void addResults(HttpServletRequest request,
                         HttpServletResponse response)
    throws ServletException, IOException {
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
      out.print("<div>This query is too long.  If you want to run very long queries, please download and use our <a href=\"http://nlp.stanford.edu/software/corenlp.html\">publicly released distribution</a>.</div>");
      return;
    }

    Annotation annotation = new Annotation(input);
    pipeline.annotate(annotation);

    String outputFormat = request.getParameter("outputFormat");
    if (outputFormat == null || outputFormat.trim().isEmpty()) {
      outputFormat = this.defaultFormat;
    }

    switch (outputFormat) {
      case "xml":
        outputXml(out, annotation);
        break;
      case "json":
        outputJson(out, annotation);
        break;
      case "conll":
        outputCoNLL(out, annotation);
        break;
      case "pretty":
        outputPretty(out, annotation);
        break;
      default:
        outputVisualise(out, annotation);
        break;
    }
  }

  public void outputVisualise(PrintWriter out, Annotation annotation)
    throws ServletException, IOException {
      // Note: A lot of the HTML generation in this method could/should be
      // done at a templating level, but as-of-yet I am not entirely sure how
      // this should be done in jsp. Also, a lot of the HTML is unnecessary
      // for the other outputs such as pretty print and XML.

      // Div for potential error messages when fetching the configuration.
      out.println("<div id=\"config_error\">");
      out.println("</div>");

      // Insert divs that will be used for each visualisation type.
      final int visualiserDivPxWidth = 700;
      Map<String, String> nameByAbbrv = new LinkedHashMap<>();
      nameByAbbrv.put("pos", "Part-of-Speech");
      nameByAbbrv.put("ner", "Named Entity Recognition");
      nameByAbbrv.put("coref", "Coreference");
      nameByAbbrv.put("basic_dep", "Basic Dependencies");
      //nameByAbbrv.put("collapsed_dep", "Collapsed dependencies");
      nameByAbbrv.put("collapsed_ccproc_dep",
          "Enhanced Dependencies");
      for (Map.Entry<String, String> entry : nameByAbbrv.entrySet()) {
        out.println("<h2>" + entry.getValue() + ":</h2>");
        out.println("<div id=\"" + entry.getKey() + "\" style=\"width:"
            + visualiserDivPxWidth + "px\">");
        out.println("    <div id=\"" + entry.getKey() + "_loading\">");
        out.println("        <p>Loading...</p>");
        out.println("    </div>");
        out.println("</div>");
        out.println("");
      }

      // Time to get the XML data into HTML.
      StringWriter xmlOutput = new StringWriter();
      pipeline.xmlPrint(annotation, xmlOutput);
      xmlOutput.flush();

      // Escape the XML to be embeddable into a Javascript string.
      String escapedXml = xmlOutput.toString().replaceAll("\\r\\n|\\r|\\n", ""
          ).replace("\"", "\\\"");

      // Inject the XML results into the HTML to be retrieved by the Javascript.
      out.println("<script type=\"text/javascript\">");
      out.println("// <![CDATA[");
      out.println("    stanfordXML = \"" + escapedXml + "\";");
      out.println("// ]]>");
      out.println("</script>");

      // Relative brat installation location to CoreNLP.
      final String bratLocation = "../brat";

      // Inject the location variable, we need it in Javascript mode.
      out.println("<script type=\"text/javascript\">");
      out.println("// <![CDATA[");
      out.println("    bratLocation = \"" + bratLocation + "\";");
      out.println("    webFontURLs = [\n" +
                  "        '"+ bratLocation + "/static/fonts/Astloch-Bold.ttf',\n" +
                  "        '"+ bratLocation + "/static/fonts/PT_Sans-Caption-Web-Regular.ttf',\n" +
                  "        '"+ bratLocation + "/static/fonts/Liberation_Sans-Regular.ttf'];");
      out.println("// ]]>");
      out.println("</script>");

      // Inject the brat stylesheet (removing this line breaks visualisation).
      out.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"" +
                  bratLocation + "/style-vis.css\"/>");

      // Include the Javascript libraries necessary to run brat.
      out.println("<script type=\"text/javascript\" src=\"" + bratLocation +
          "head.load.min.js\"></script>");
      // Main Javascript that hooks into all that we have introduced so far.
      out.println("<script type=\"text/javascript\" src=\"brat.js\"></script>");

      // Link to brat, I hope this is okay to have here...
      out.println("<h>Visualisation provided using the " +
          "<a href=\"http://brat.nlplab.org/\">brat " +
          "visualisation/annotation software</a>.</h>");
      out.println("<br/>");
  }

  public void outputPretty(PrintWriter out, Annotation annotation)
    throws ServletException {
    try {
      Document input = XMLOutputter.annotationToDoc(annotation, pipeline);

      Nodes output = corenlpTransformer.transform(input);
      for (int i = 0; i < output.size(); i++) {
        out.print(output.get(i).toXML());
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  private static void outputByWriter(Consumer<StringWriter> printer, PrintWriter out) {
    StringWriter output = new StringWriter();
    printer.accept(output);
    output.flush();

    String escapedXml = StringEscapeUtils.escapeHtml4(output.toString());
    String[] lines = escapedXml.split("\n");
    out.print("<div><pre>");
    for (String line : lines) {
      int numSpaces = 0;
      while (numSpaces < line.length() && line.charAt(numSpaces) == ' ') {
        out.print("&nbsp;");
        ++numSpaces;
      }
      out.print(line.substring(numSpaces));
      out.print("\n");
    }
    out.print("</pre></div>");
  }

  public void outputXml(PrintWriter out, Annotation annotation) throws IOException {
    outputByWriter(writer -> {
      try {
        pipeline.xmlPrint(annotation, writer);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }, out);
  }

  public void outputJson(PrintWriter out, Annotation annotation) throws IOException {
    outputByWriter(writer -> {
      try {
        pipeline.jsonPrint(annotation, writer);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }, out);
  }

  public void outputCoNLL(PrintWriter out, Annotation annotation) throws IOException {
    outputByWriter(writer -> {
      try {
        pipeline.conllPrint(annotation, writer);
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }, out);
  }

}
