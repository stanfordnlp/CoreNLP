package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;


/**
 * This class is designed to apply multiple Annotators
 * to an Annotation.  The idea is that you first
 * build up the pipeline by adding Annotators, and then
 * you take the objects you wish to annotate and pass
 * them in and get back in return a fully annotated object.
 * Please see the package level javadoc for sample usage
 * and a more complete description.
 *
 * @author Jenny Finkel
 */

public class AnnotationPipeline implements Annotator {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(AnnotationPipeline.class);

  protected static final boolean TIME = true;

  private final List<Annotator> annotators;
  private List<MutableLong> accumulatedTime;

  public AnnotationPipeline(List<Annotator> annotators) {
    this.annotators = annotators;
    if (TIME) {
      int num = annotators.size();
      accumulatedTime = new ArrayList<>(num);
      for (int i = 0; i < num; i++) {
        accumulatedTime.add(new MutableLong());
      }
    }
  }

  public AnnotationPipeline() {
    this(new ArrayList<>()); // It can't be a singletonList() since it isn't copied but is mutated.
  }

  public void addAnnotator(Annotator annotator) {
    annotators.add(annotator);
    if (TIME) {
      accumulatedTime.add(new MutableLong());
    }
  }

  /**
   * Run the pipeline on an input annotation.
   * The annotation is modified in place.
   *
   * @param annotation The input annotation, usually a raw document
   */
  @Override
  public void annotate(Annotation annotation) {
    Iterator<MutableLong> it = accumulatedTime.iterator();
    Timing t = new Timing();
    for (Annotator annotator : annotators) {
      if (Thread.interrupted()) {  // Allow interrupting
        throw new RuntimeInterruptedException();
      }
      if (TIME) {
        t.start();
      }
      annotator.annotate(annotation);
      if (TIME) {
        long elapsed = t.stop();
        MutableLong m = it.next();
        m.incValue(elapsed);
      }
    }
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * all available cores.
   *
   * @param annotations The input annotations to process
   */
  public void annotate(Iterable<Annotation> annotations) {
    annotate(annotations, Runtime.getRuntime().availableProcessors());
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * all available cores.
   *
   * @param annotations The input annotations to process
   * @param callback A function to be called when an annotation finishes.
   *                 The return value of the callback is ignored.
   */
  public void annotate(final Iterable<Annotation> annotations, final Consumer<Annotation> callback) {
    annotate(annotations, Runtime.getRuntime().availableProcessors(), callback);
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * threads given in numThreads.
   *
   * @param annotations The input annotations to process
   * @param numThreads The number of threads to run on
   */
  public void annotate(final Iterable<Annotation> annotations, int numThreads) {
    annotate(annotations, numThreads, in -> {});
  }

  /**
   * Annotate a collection of input annotations IN PARALLEL, making use of
   * threads given in numThreads
   * @param annotations The input annotations to process
   * @param numThreads The number of threads to run on
   * @param callback A function to be called when an annotation finishes.
   *                 The return value of the callback is ignored.
   */
  public void annotate(final Iterable<Annotation> annotations, int numThreads, final Consumer<Annotation> callback){
    // case: single thread (no point in spawning threads)
    if(numThreads == 1) {
      for(Annotation ann : annotations) {
        annotate(ann);
        callback.accept(ann);
      }
    }
    // Java's equivalent to ".map{ lambda(annotation) => annotate(annotation) }
    Iterable<Runnable> threads = () -> {
      final Iterator<Annotation> iter = annotations.iterator();
      return new Iterator<Runnable>() {
        @Override
        public boolean hasNext() {
          return iter.hasNext();
        }
        @Override
        public Runnable next() {
          if ( ! iter.hasNext()) {
            throw new NoSuchElementException();
          }
          final Annotation input = iter.next();
          return () -> {
            //(logging)
            String beginningOfDocument = input.toString().substring(0,Math.min(50,input.toString().length()));
            Redwood.startTrack("Annotating \"" + beginningOfDocument + "...\"");
            //(annotate)
            annotate(input);
            //(callback)
            callback.accept(input);
            //(logging again)
            Redwood.endTrack("Annotating \"" + beginningOfDocument + "...\"");
          };
        }
        @Override
        public void remove() {
          iter.remove();
        }
      };
    };
    // Thread
    Redwood.Util.threadAndRun(this.getClass().getSimpleName(), threads, numThreads );
  }

  /** Return the total pipeline annotation time in milliseconds.
   *
   *  @return The total pipeline annotation time in milliseconds
   */
  protected long getTotalTime() {
    long total = 0;
    for (MutableLong m: accumulatedTime) {
      total += m.longValue();
    }
    return total;
  }

  /** Return a String that gives detailed human-readable information about
   *  how much time was spent by each annotator and by the entire annotation
   *  pipeline.  This String includes newline characters but does not end
   *  with one, and so it is suitable to be printed out with a
   *  {@code println()}.
   *
   *  @return Human readable information on time spent in processing.
   */
  public String timingInformation() {
    StringBuilder sb = new StringBuilder();
    if (TIME) {
      sb.append("Annotation pipeline timing information:");
      sb.append(IOUtils.eolChar);
      Iterator<MutableLong> it = accumulatedTime.iterator();
      long total = 0;
      for (Annotator annotator : annotators) {
        MutableLong m = it.next();
        sb.append(StringUtils.getShortClassName(annotator)).append(": ");
        sb.append(Timing.toSecondsString(m.longValue())).append(" sec.");
        sb.append(IOUtils.eolChar);
        total += m.longValue();
      }
      sb.append("TOTAL: ").append(Timing.toSecondsString(total)).append(" sec.");
    }
    return sb.toString();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    Set<Class<? extends CoreAnnotation>> satisfied = Generics.newHashSet();
    for (Annotator annotator : annotators) {
      satisfied.addAll(annotator.requirementsSatisfied());
    }
    return satisfied;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    if (annotators.isEmpty()) {
      return Collections.emptySet();
    }
    return annotators.get(0).requires();
  }


  @SuppressWarnings("RedundantThrows")
  public static void main(String[] args) throws IOException {
    Timing tim = new Timing();
    AnnotationPipeline ap = new AnnotationPipeline();
    final boolean verbose = false;
    ap.addAnnotator(new TokenizerAnnotator(verbose, "en"));
    ap.addAnnotator(new WordsToSentencesAnnotator(verbose));
    // ap.addAnnotator(new NERCombinerAnnotator(verbose));
    // ap.addAnnotator(new OldNERAnnotator(verbose));
    // ap.addAnnotator(new NERMergingAnnotator(verbose));
    ap.addAnnotator(new ParserAnnotator(verbose, -1));
/*
    ap.addAnnotator(new UpdateSentenceFromParseAnnotator(verbose));
    ap.addAnnotator(new NumberAnnotator(verbose));
    ap.addAnnotator(new QuantifiableEntityNormalizingAnnotator(verbose));
    ap.addAnnotator(new StemmerAnnotator(verbose));
    ap.addAnnotator(new MorphaAnnotator(verbose));
**/
//    ap.addAnnotator(new SRLAnnotator());

    String text = ("USAir said in the filings that Mr. Icahn first contacted Mr. Colodny last September to discuss the benefits of combining TWA and USAir -- either by TWA's acquisition of USAir, or USAir's acquisition of TWA.");
    Annotation a = new Annotation(text);
    ap.annotate(a);
    System.out.println(a.get(CoreAnnotations.TokensAnnotation.class));
    for (CoreMap sentence : a.get(CoreAnnotations.SentencesAnnotation.class)) {
      System.out.println(sentence.get(TreeCoreAnnotations.TreeAnnotation.class));
    }

    if (TIME) {
      System.out.println(ap.timingInformation());
      log.info("Total time for AnnotationPipeline: " +
                         tim.toSecondsString() + " sec.");
    }
  }

}
