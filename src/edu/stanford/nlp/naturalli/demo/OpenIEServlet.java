package edu.stanford.nlp.naturalli.demo; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.naturalli.NaturalLogicAnnotations;
import edu.stanford.nlp.naturalli.SentenceFragment;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * A simple web frontend to the Open IE System.
 *
 * @author Gabor Angeli
 */
public class OpenIEServlet extends HttpServlet  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(OpenIEServlet.class);
  StanfordCoreNLP pipeline = null;
  StanfordCoreNLP backoff = null;

  /**
   * Set the properties to the paths they appear at on the servlet.
   * See build.xml for where these paths get copied.
   * @throws ServletException Thrown by the implementation
   */
  public void init()  throws ServletException {
    Properties commonProps = new Properties() {{
      setProperty("depparse.extradependencies", "ref_only_uncollapsed");
      setProperty("parse.extradependencies", "ref_only_uncollapsed");
      setProperty("openie.splitter.threshold", "0.10");
      setProperty("openie.optimze_for", "GENERAL");
      setProperty("openie.ignoreaffinity", "false");
      setProperty("openie.max_entailments_per_clause", "1000");
      setProperty("openie.triple.strict", "true");
    }};
    try {
      String dataDir = getServletContext().getRealPath("/WEB-INF/data");
      System.setProperty("de.jollyday.config",
          getServletContext().getRealPath("/WEB-INF/classes/holidays/jollyday.properties"));
      commonProps.setProperty("pos.model", dataDir + "/english-left3words-distsim.tagger");
      commonProps.setProperty("ner.model", dataDir + "/english.all.3class.distsim.crf.ser.gz," + dataDir + "/english.conll.4class.distsim.crf.ser.gz," + dataDir + "/english.muc.7class.distsim.crf.ser.gz");
      commonProps.setProperty("depparse.model", dataDir + "/english_SD.gz");
      commonProps.setProperty("parse.model", dataDir + "/englishPCFG.ser.gz");
      commonProps.setProperty("sutime.rules", dataDir + "/defs.sutime.txt," + dataDir + "/english.sutime.txt," + dataDir + "/english.hollidays.sutime.txt");
      commonProps.setProperty("openie.splitter.model", dataDir + "/clauseSplitterModel.ser.gz");
      commonProps.setProperty("openie.affinity_models", dataDir);
    } catch (NullPointerException e) {
      log.info("Could not load servlet context. Are you on the command line?");
    }
    if (this.pipeline == null) {
      Properties fullProps = new Properties(commonProps);
      fullProps.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse,ner,natlog,openie");
      this.pipeline = new StanfordCoreNLP(fullProps);
    }
    if (this.backoff == null) {
      Properties backoffProps = new Properties(commonProps);
      backoffProps.setProperty("annotators", "parse,natlog,openie");
      backoffProps.setProperty("enforceRequirements", "false");
      this.backoff = new StanfordCoreNLP(backoffProps);

    }
  }

  /**
   * Annotate a document (which is usually just a sentence).
   */
  public void annotate(StanfordCoreNLP pipeline, Annotation ann) {
    if (ann.get(CoreAnnotations.SentencesAnnotation.class) == null) {
      pipeline.annotate(ann);
    } else {
      if (ann.get(CoreAnnotations.SentencesAnnotation.class).size() == 1) {
        CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
        for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
          token.remove(NaturalLogicAnnotations.OperatorAnnotation.class);
          token.remove(NaturalLogicAnnotations.PolarityAnnotation.class);
        }
        sentence.remove(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
        sentence.remove(NaturalLogicAnnotations.EntailedSentencesAnnotation.class);
        sentence.remove(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
        sentence.remove(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
        sentence.remove(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
        pipeline.annotate(ann);
      }
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

  private void runWithPipeline(StanfordCoreNLP pipeline, Annotation ann, Set<String> triples, Set<String> entailments) {
    // Annotate
    annotate(pipeline, ann);
    // Extract info
    for (CoreMap sentence : ann.get(CoreAnnotations.SentencesAnnotation.class)) {
      for (SentenceFragment fragment : sentence.get(NaturalLogicAnnotations.EntailedSentencesAnnotation.class)) {
        entailments.add(quote(fragment.toString()));
      }
      for (RelationTriple fragment : sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class)) {
        triples.add("[ " + quote(fragment.subjectGloss()) + ", " + quote(fragment.relationGloss()) + ", " + quote(fragment.objectGloss()) + " ]");
      }
    }

  }


  /**
   * Actually perform the GET request, given all the relevant information (already sanity checked).
   * This is the meat of the servlet code.
   * @param out The writer to write the output to.
   * @param q The query string.
   */
  private void doGet(PrintWriter out, String q) {
    // Clean the string a bit
    q = q.trim();
    if (q.length() == 0) {
      return;
    }
    char lastChar = q.charAt(q.length() - 1);
    if (lastChar != '.' && lastChar != '!' && lastChar != '?') {
      q = q + ".";
    }
    // Annotate
    Annotation ann = new Annotation(q);
    try {
      // Collect results
      Set<String> entailments = new HashSet<>();
      Set<String> triples = new LinkedHashSet<>();
      runWithPipeline(pipeline, ann, triples, entailments);  // pipeline must come before backoff
      if (triples.size() == 0) {
        runWithPipeline(backoff, ann, triples, entailments);   // backoff must come after pipeline
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

  /**
   * {@inheritDoc}
   */
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
      doGet(out, raw);
    }

    out.close();
  }

  /**
   * {@inheritDoc}
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }

  /**
   * A helper so that we can see how the servlet sees the world, modulo model paths, at least.
   */
  public static void main(String[] args) throws ServletException, IOException {
    OpenIEServlet servlet = new OpenIEServlet();
    servlet.init();
    IOUtils.console(line -> {
      StringWriter str = new StringWriter();
      PrintWriter out = new PrintWriter(str);
      servlet.doGet(new PrintWriter(out), line);
      out.close();
      System.out.println(str.toString());
    });
  }
}
