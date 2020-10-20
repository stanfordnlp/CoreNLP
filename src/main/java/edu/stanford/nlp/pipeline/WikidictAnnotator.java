package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.KBPRelationExtractor;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.time.TimeAnnotations;
import edu.stanford.nlp.time.Timex;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Interner;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.SystemUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * An annotator for entity linking to Wikipedia pages via the Wikidict.
 * <br>
 * The wikidict dictionary in particular is based on this paper: <br>
 * "A Cross-Lingual Dictionary for English Wikipedia Concepts" <br>
 * https://nlp.stanford.edu/pubs/crosswikis.pdf
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class WikidictAnnotator extends SentenceAnnotator {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(WikidictAnnotator.class);

  /** A pattern for simple numbers */
  private static final Pattern NUMBER_PATTERN = Pattern.compile("[0-9.]+");

  @ArgumentParser.Option(name="threads", gloss="The number of threads to run this annotator on")
  private int threads = 1;

  @ArgumentParser.Option(name="wikidict", gloss="The location of the <text, link, score> TSV file")
  private String wikidictPath = DefaultPaths.DEFAULT_WIKIDICT_TSV;

  @ArgumentParser.Option(name="threshold", gloss="The score threshold under which to discard links")
  private double threshold = 0.0;

  @ArgumentParser.Option(name="caseless", gloss="Ignore case when looking up entries in wikidict")
  private boolean wikidictCaseless = false;

  /**
   * The actual Wikidict dictionary.
   * <br>
   * Initialized with a huge size to limit the resizing needed when loading.
   * Load factor of 0.75 and 21M entries
   */
  private final Map<String, String> dictionary = new HashMap<>(30000000);

  /**
   * Create a new WikiDict annotator, with the given name and properties.
   */
  public WikidictAnnotator(String name, Properties properties) {
    ArgumentParser.fillOptions(this, name, properties);
    long startTime = System.currentTimeMillis();
    log.info("Reading Wikidict from " + wikidictPath);
    try {
      int i = 0;
      String[] fields = new String[3];
      // Keeping track of the previous link will let us reuse String
      // objects, assuming the file is sorted by the second column.
      // TODO: we actually didn't know where the dictionary creation
      // code is.  If it gets updated later and the dictionary is
      // rebuilt, please remember to change the code to update it by
      // the second column.
      String previousLink = "";
      int reuse = 0;
      for (String line : IOUtils.readLines(wikidictPath, "UTF-8")) {
        if (line.charAt(0) == '\t') {
          continue;
        }
        if (i % 1000000 == 0) {
          log.info("Loaded " + i + " entries from Wikidict [" + SystemUtils.getMemoryInUse() + "MB memory used; " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + " elapsed]");
        }
        i += 1;
        StringUtils.splitOnChar(fields, line, '\t');
        // Check that the read entry is above the score threshold
        if (threshold > 0.0) {
          double score = Double.parseDouble(fields[2]);
          if (score < threshold) {
            continue;
          }
        }
        String surfaceForm = fields[0];
        if (wikidictCaseless)
          surfaceForm = surfaceForm.toLowerCase();
        // save memory by reusing the string without using an interner
        // requires that the dictionary be sorted by link
        String link = fields[1];
        if (link.equals(previousLink)) {
          link = previousLink;
          reuse++;
        }
        // Add the entry
        dictionary.put(surfaceForm, link);
        previousLink = link;
      }
      log.info("Done reading Wikidict (" + dictionary.size() + " links read; " + (dictionary.size() - reuse) + " unique entities; " + Redwood.formatTimeDifference(System.currentTimeMillis() - startTime) + " elapsed)");
      if ((dictionary.size() - reuse) / (float) dictionary.size() > 0.35) {
        log.error("We expected a much higher fraction of key reuse in the dictionary.  It is possible the dictionary was recreated and then not sorted.  Please sort the dictionary by the second column and update the dictionary creation code to sort this way before writing.  This will save quite a bit of time loading without sacrificing memory performance.");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** @see WikidictAnnotator#WikidictAnnotator(String, Properties) */
  @SuppressWarnings("unused")
  public WikidictAnnotator(Properties properties) {
    this(STANFORD_LINK, properties);

  }

  /**
   * Try to normalize timex values to the form they would appear in the knowledge base.
   * @param timex The timex value to normalize.
   * @return The normalized timex value (e.g., dates have the time of day removed, etc.)
   */
  public static String normalizeTimex(String timex) {
    if (timex.contains("T") && !"PRESENT".equals(timex)) {
      return timex.substring(0, timex.indexOf("T"));
    } else {
      return timex;
    }
  }


  /**
   * Link the given mention, if possible.
   *
   * @param mention The mention to link, as given by {@link EntityMentionsAnnotator}
   *
   * @return The Wikidict entry for the given mention, or the normalized timex / numeric value -- as appropriate.
   */
  public Optional<String> link(CoreMap mention) {
    String surfaceForm = mention.get(CoreAnnotations.OriginalTextAnnotation.class) == null ? mention.get(CoreAnnotations.TextAnnotation.class) : mention.get(CoreAnnotations.OriginalTextAnnotation.class);
    // set up key for wikidict ; if caseless use lower case version of surface form
    String mentionSurfaceFormKey;
    if (wikidictCaseless)
      mentionSurfaceFormKey = surfaceForm.toLowerCase();
    else
      mentionSurfaceFormKey = surfaceForm;
    // get ner
    String ner = mention.get(CoreAnnotations.NamedEntityTagAnnotation.class);
    if (ner != null &&
        (KBPRelationExtractor.NERTag.DATE.name.equalsIgnoreCase(ner) ||
          "TIME".equalsIgnoreCase(ner) ||
          "SET".equalsIgnoreCase(ner)) &&
        mention.get(TimeAnnotations.TimexAnnotation.class) != null &&
        mention.get(TimeAnnotations.TimexAnnotation.class).value() != null) {
      // Case: normalize dates
      Timex timex = mention.get(TimeAnnotations.TimexAnnotation.class);
      if (timex.value() != null && !timex.value().equals("PRESENT") &&
          !timex.value().equals("PRESENT_REF") &&
          !timex.value().equals("PAST") &&
          !timex.value().equals("PAST_REF") &&
          !timex.value().equals("FUTURE") &&
          !timex.value().equals("FUTURE_REF")
        ) {
        return Optional.of(normalizeTimex(timex.value()));
      } else {
        return Optional.empty();
      }
    } else if (ner != null &&
        "ORDINAL".equalsIgnoreCase(ner) &&
        mention.get(CoreAnnotations.NumericValueAnnotation.class) != null) {
      // Case: normalize ordinals
      Number numericValue = mention.get(CoreAnnotations.NumericValueAnnotation.class);
      return Optional.of(numericValue.toString());
    } else if (NUMBER_PATTERN.matcher(surfaceForm).matches()) {
      // Case: keep numbers as is
      return Optional.of(surfaceForm);
    } else if (ner != null && !"O".equals(ner) && dictionary.containsKey(mentionSurfaceFormKey)) {
      // Case: link with Wikidict
      return Optional.of(dictionary.get(mentionSurfaceFormKey));
    } else {
      // Else: keep the surface form as is
      return Optional.empty();
    }
  }

  /** {@inheritDoc} */
  @Override
  protected int nThreads() {
    return threads;
  }

  /** {@inheritDoc} */
  @Override
  protected long maxTime() {
    return -1L;
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneSentence(Annotation annotation, CoreMap sentence) {
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      token.set(CoreAnnotations.WikipediaEntityAnnotation.class, "O");
    }

    for (CoreMap mention : sentence.get(CoreAnnotations.MentionsAnnotation.class)) {
      Optional<String> canonicalName = link(mention);
      if (canonicalName.isPresent()) {
        mention.set(CoreAnnotations.WikipediaEntityAnnotation.class, canonicalName.get());
        for (CoreLabel token : mention.get(CoreAnnotations.TokensAnnotation.class)) {
          token.set(CoreAnnotations.WikipediaEntityAnnotation.class, canonicalName.get());
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  protected void doOneFailedSentence(Annotation annotation, CoreMap sentence) {
    /* do nothing */
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.WikipediaEntityAnnotation.class);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    Set<Class<? extends CoreAnnotation>> requirements = new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.SentencesAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class,
        CoreAnnotations.MentionsAnnotation.class
    ));
    return Collections.unmodifiableSet(requirements);
  }


  /**
   * A debugging method to try entity linking sentences from the console.
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,entitymentions,entitylink");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    IOUtils.console("sentence> ", line -> {
      Annotation ann = new Annotation(line);
      pipeline.annotate(ann);
      List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
      System.err.println(StringUtils.join(tokens.stream().map(x -> x.get(CoreAnnotations.WikipediaEntityAnnotation.class)), "  "));
    });
  }
}


