package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A simple web frontend to the Open IE System.
 *
 * @author Gabor Angeli
 */
public class OpenIEServlet extends HttpServlet {
  StanfordCoreNLP pipeline = null;

  public void init()  throws ServletException {
    String dataDir = getServletContext().getRealPath("/WEB-INF/data");
    System.setProperty("de.jollyday.config",
        getServletContext().getRealPath("/WEB-INF/classes/holidays/jollyday.properties"));
    if (this.pipeline == null) {
      this.pipeline = new StanfordCoreNLP(new Properties(){{
        setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,ner,natlog,openie");
        setProperty("pos.model", dataDir + "/english-left3words-distsim.tagger");
        setProperty("ner.model", dataDir + "/english.all.3class.distsim.crf.ser.gz," + dataDir + "/english.conll.4class.distsim.crf.ser.gz," + dataDir + "/english.muc.7class.distsim.crf.ser.gz");
        setProperty("depparse.model", dataDir + "/english_SD.gz");
        setProperty("sutime.rules", dataDir + "/defs.sutime.txt," + dataDir + "/english.sutime.txt," + dataDir + "/english.hollidays.sutime.txt");
        setProperty("openie.splitter.model", dataDir + "/clauseSplitterModel.ser.gz");
        setProperty("openie.affinity_models", dataDir);
        setProperty("openie.splitter.threshold", "0.10");
        setProperty("openie.optimze_for", "GENERAL");
        setProperty("openie.ignoreaffinity", "false");
        setProperty("openie.max_entailments_per_clause", "1000");
        setProperty("openie.triple.strict", "true");
      }});
    }
  }

  /**
   * Originally extracted from Jettison; copied from http://stackoverflow.com/questions/3020094/how-should-i-escape-strings-in-json
   * @param string The string to quote.
   * @return A quoted version of the string, safe to send over the wire.
   */
  public static String quote(String string) {
    if (string == null || string.length() == 0) {
      return "\"\"";
    }

    char         c = 0;
    int          i;
    int          len = string.length();
    StringBuilder sb = new StringBuilder(len + 4);
    String       t;

    sb.append('"');
    for (i = 0; i < len; i += 1) {
      c = string.charAt(i);
      switch (c) {
        case '\\':
        case '"':
          sb.append('\\');
          sb.append(c);
          break;
        case '/':
          //                if (b == '<') {
          sb.append('\\');
          //                }
          sb.append(c);
          break;
        case '\b':
          sb.append("\\b");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\f':
          sb.append("\\f");
          break;
        case '\r':
          sb.append("\\r");
          break;
        default:
          if (c < ' ') {
            t = "000" + Integer.toHexString(c);
            sb.append("\\u" + t.substring(t.length() - 4));
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/json; charset=UTF-8");
    PrintWriter out = response.getWriter();

    String raw = request.getParameter("q");
    if (raw == null || "".equals(raw)) {
      out.println("{ok:false, entailments:[], triples=[], msg=\"\"}");
    } else {
      Annotation ann = new Annotation(raw);
      try {
        // Annotate
        pipeline.annotate(ann);
        // Collect results
        List<String> entailments = new ArrayList<>();
        List<String> triples = new ArrayList<>();
        for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
          for (SentenceFragment fragment : sentence.get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class)) {
            entailments.add(quote(fragment.toString()));
          }
          for (RelationTriple fragment : sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {
            triples.add("[ " + quote(fragment.subjectLemmaGloss()) + ", " + quote(fragment.relationLemmaGloss()) + ", " + quote(fragment.objectLemmaGloss()) + " ]");
          }
        }
        // Write results
        out.println("{ " +
            "\"ok\":true, " +
            "\"entailments\": [" + StringUtils.join(entailments, ",") + "], " +
            "\"triples\": [" + StringUtils.join(triples, ",") + "], " +
            "\"msg\": \"\"" +
        " }");
      } catch (Throwable t) {
        out.println("{ok:false, entailments:[], triples:[], msg:" + quote(t.getMessage()) + "}");
      }
    }

    out.close();
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }
}
