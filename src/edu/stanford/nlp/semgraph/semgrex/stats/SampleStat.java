package edu.stanford.nlp.semgraph.semgrex.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.semgrex.SemgrexParseException;
import edu.stanford.nlp.semgraph.semgrex.SemgrexUtils;
import edu.stanford.nlp.trees.ud.CoNLLUDocumentWriter;
import edu.stanford.nlp.util.CoreMap;

/**
 * Prints a sample of the sentences for each combination of values the
 * keys take, to see what each row of a {@code count} chart looks like.
 *<br>
 * Script syntax:
 *<pre>
 * sample [-count N] [-seed S] [-conllu] [-lower KEY ...] KEY [KEY ...]
 *</pre>
 * For each combination, up to {@code -count} different sentences are
 * chosen at random from all of the sentences where it occurs, 10 by
 * default.  Two matches in one sentence count as one sentence, with both
 * matches marked.  The choice is the same each time the script is run
 * on the same corpus, unless {@code -seed} gives another seed.
 *<br>
 * The combinations come in the order {@code count} ranks them, most
 * matches first.  Each is headed by its values and how often it occurs,
 * followed by its sentences in corpus order, one per line: the
 * {@code sent_id}, then the words, with each word of a match, and each
 * of its named nodes, marked as {@code [[word]]}:
 *<pre>
 * form=lo xpos=pp3msa00: 496 matches in 480 sentences
 *   CESS-CAST-P-19981101-134-s20  Para no dormir se empieza a [[tocar]] ...
 *</pre>
 * With {@code -conllu}, each sentence is written in CoNLL-U instead,
 * with a {@code # sample} comment naming its combination and how often
 * it occurs, in place of the heading, and a {@code # highlight tokens}
 * comment for the marked words, as ConlluEditor shows them.
 *<br>
 * {@code -lower KEY} lowercases the values of that key, as for
 * {@code count}.
 *
 * @author John Bauer
 */
public class SampleStat implements SemgrexStat {
  /** How many sentences of each combination are printed, unless the script says otherwise */
  public static final int DEFAULT_COUNT = 10;
  /** The seed of the random choice, unless the script gives another */
  public static final long DEFAULT_SEED = 1234;

  private static final String COUNT = "-count";
  private static final String SEED = "-seed";
  private static final String CONLLU = "-conllu";

  /** Sentence order for words: by index, then empty index or copy count, as in 8, 8.1, 8.2, 9 */
  private static final Comparator<IndexedWord> SENTENCE_ORDER =
    Comparator.comparingInt(IndexedWord::index)
              .thenComparingInt(IndexedWord::getEmptyIndex)
              .thenComparingInt(IndexedWord::copyCount);

  /** One sentence chosen for a combination, with the words its matches marked */
  private static class Sample {
    final CoreMap sentence;
    /** where the sentence is in the corpus, counting from 1, for the order of the samples */
    final long position;
    final Set<IndexedWord> marked = new TreeSet<>(SENTENCE_ORDER);

    Sample(CoreMap sentence, long position) {
      this.sentence = sentence;
      this.position = position;
    }
  }

  /** What has been seen of one combination of values */
  private static class Combination {
    int matches = 0;
    int sentences = 0;
    final List<Sample> chosen = new ArrayList<>();
    /** the last sentence this combination was seen in */
    CoreMap lastSentence = null;
    /** the sample of that sentence, or null if it was not chosen */
    Sample lastSample = null;
  }

  private final List<String> keys;
  private final Set<String> lowered;
  private final int count;
  private final long seed;
  private final boolean conllu;
  /**
   * SplittableRandom rather than Random: Random's sequences from nearby
   * seeds, such as 1 and 2, stay close together for some of their values,
   * so -seed 1 and -seed 2 would choose nearly the same sentences
   */
  private final SplittableRandom random;

  private final Map<List<String>, Combination> combinations = new HashMap<>();
  /** the sentence the last match was in, and how many sentences have been seen */
  private CoreMap currentSentence = null;
  private long sentencesSeen = 0;

  SampleStat(List<String> keys, Set<String> lowered, int count, long seed, boolean conllu) {
    this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
    this.lowered = Collections.unmodifiableSet(new LinkedHashSet<>(lowered));
    this.count = count;
    this.seed = seed;
    this.conllu = conllu;
    this.random = new SplittableRandom(seed);
  }

  /**
   * Parses "sample [-count N] [-seed S] [-conllu] [-lower KEY ...] KEY [KEY ...]"
   *
   * @param context the stage this command is in, used to validate keys
   * @param args the arguments after "sample"
   * @return a new SampleStat
   * @throws SemgrexParseException if an option is unknown or malformed, there are no keys,
   *   a key isn't in the pattern, or a lowercased key is not one this command samples
   */
  public static SemgrexStat create(SemgrexStats.Context context, List<String> args) {
    List<String> keys = new ArrayList<>();
    Set<String> lowered = new LinkedHashSet<>();
    int count = DEFAULT_COUNT;
    long seed = DEFAULT_SEED;
    boolean conllu = false;

    for (int idx = 0; idx < args.size(); ++idx) {
      String arg = args.get(idx);
      if (COUNT.equals(arg) || SEED.equals(arg) || SemgrexStats.LOWER.equals(arg)) {
        if (idx + 1 >= args.size()) {
          throw new SemgrexParseException(arg + " needs a value after it");
        }
        ++idx;
        String value = args.get(idx);
        if (SemgrexStats.LOWER.equals(arg)) {
          lowered.add(value);
          continue;
        }
        try {
          if (COUNT.equals(arg)) {
            count = Integer.parseInt(value);
          } else {
            seed = Long.parseLong(value);
          }
        } catch (NumberFormatException e) {
          throw new SemgrexParseException(arg + " needs a number after it, not '" + value + "'");
        }
        if (count < 1) {
          throw new SemgrexParseException(COUNT + " must be at least 1");
        }
      } else if (CONLLU.equals(arg)) {
        conllu = true;
      } else if (arg.startsWith("-")) {
        throw new SemgrexParseException("Unknown option '" + arg + "' for sample.  Known options: " +
                                        COUNT + ", " + SEED + ", " + CONLLU + ", " + SemgrexStats.LOWER);
      } else {
        keys.add(arg);
      }
    }

    if (keys.isEmpty()) {
      throw new SemgrexParseException("sample needs at least one key to sample by");
    }
    context.validateKeys("sample", keys);
    SemgrexStats.checkLowered("sample", lowered, keys);
    return new SampleStat(keys, lowered, count, seed, conllu);
  }

  @Override
  public String name() {
    return "sample";
  }

  @Override
  public List<String> getKeys() {
    return keys;
  }

  /**
   * Counts the match, and chooses its sentence for its combination or not.
   *<br>
   * The choice is a reservoir sample: the k-th sentence of a combination
   * replaces one of the sentences chosen so far with probability
   * {@code count / k}, so that each of its sentences has the same chance
   * of being in the sample, without knowing in advance how many there are.
   */
  @Override
  public void accumulate(CoreMap sentence, SemgrexMatch match) {
    if (sentence != currentSentence) {
      currentSentence = sentence;
      ++sentencesSeen;
    }
    List<String> values = new ArrayList<>(keys.size());
    for (String key : keys) {
      values.add(CountStat.clean(SemgrexStats.keyValue(match, key, lowered)));
    }
    Combination combination = combinations.computeIfAbsent(Collections.unmodifiableList(values), k -> new Combination());
    ++combination.matches;

    if (combination.lastSentence != sentence) {
      combination.lastSentence = sentence;
      ++combination.sentences;
      combination.lastSample = null;
      if (combination.chosen.size() < count) {
        combination.lastSample = new Sample(sentence, sentencesSeen);
        combination.chosen.add(combination.lastSample);
      } else {
        int slot = random.nextInt(combination.sentences);
        if (slot < count) {
          combination.lastSample = new Sample(sentence, sentencesSeen);
          combination.chosen.set(slot, combination.lastSample);
        }
      }
    }
    if (combination.lastSample != null) {
      combination.lastSample.marked.add(match.getMatch());
      for (String name : match.getNodeNames()) {
        combination.lastSample.marked.add(match.getNode(name));
      }
    }
  }

  /**
   * The number of matches of a combination of values.
   *
   * @param values the value of each key, as the report shows it
   * @return the number of matches, 0 if the combination never occurred
   */
  public int getMatches(List<String> values) {
    Combination combination = combinations.get(values);
    return combination == null ? 0 : combination.matches;
  }

  /**
   * The {@code sent_id}s of the sentences chosen for a combination of values,
   * in corpus order.  A sentence without one is {@code sentence N}, its
   * position in the corpus.
   *
   * @param values the value of each key, as the report shows it
   * @return the ids, empty if the combination never occurred
   */
  public List<String> getSampleIds(List<String> values) {
    Combination combination = combinations.get(values);
    if (combination == null) {
      return Collections.emptyList();
    }
    return inCorpusOrder(combination).stream().map(SampleStat::sentenceId).collect(Collectors.toList());
  }

  private static List<Sample> inCorpusOrder(Combination combination) {
    List<Sample> samples = new ArrayList<>(combination.chosen);
    samples.sort(Comparator.comparingLong(sample -> sample.position));
    return samples;
  }

  /** The sent_id of a sample's sentence, or its position in the corpus if it has none */
  private static String sentenceId(Sample sample) {
    List<String> comments = sample.sentence.get(CoreAnnotations.CommentsAnnotation.class);
    if (comments != null) {
      for (String comment : comments) {
        String trimmed = comment.trim();
        if (trimmed.startsWith("#")) {
          String[] pieces = trimmed.substring(1).split("=", 2);
          if (pieces.length == 2 && pieces[0].trim().equals("sent_id")) {
            return pieces[1].trim();
          }
        }
      }
    }
    return "sentence " + sample.position;
  }

  /** The words of a sample's sentence, with the marked ones in [[ ]] */
  private static String markedText(Sample sample) {
    SemanticGraph graph = sample.sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    List<String> words = new ArrayList<>();
    for (IndexedWord word : graph.vertexListSorted()) {
      String text = word.word().replaceAll("[\t\r\n]", " ");
      words.add(sample.marked.contains(word) ? "[[" + text + "]]" : text);
    }
    return String.join(" ", words);
  }

  /** A sample's sentence in CoNLL-U, with comments naming its combination and marking its words */
  private static String conllu(Sample sample, String combination) {
    SemanticGraph basic = sample.sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    SemanticGraph enhanced = sample.sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
    List<String> comments = new ArrayList<>();
    if (sample.sentence.get(CoreAnnotations.CommentsAnnotation.class) != null) {
      comments.addAll(sample.sentence.get(CoreAnnotations.CommentsAnnotation.class));
    }
    comments.add("# sample = " + combination);
    comments.add("# highlight tokens = " + sample.marked.stream().map(IndexedWord::toCopyOrEmptyIndex).collect(Collectors.joining(" ")));
    return new CoNLLUDocumentWriter().printSemanticGraph(basic, enhanced, false, comments);
  }

  /** The values of a combination as key=value pairs */
  private String describe(List<String> values) {
    List<String> pieces = new ArrayList<>();
    for (int i = 0; i < keys.size(); ++i) {
      pieces.add(keys.get(i) + "=" + values.get(i));
    }
    return String.join(" ", pieces);
  }

  @Override
  public String report() {
    if (combinations.isEmpty()) {
      return "No matches, nothing to sample." + System.lineSeparator();
    }
    List<Map.Entry<List<String>, Combination>> entries = new ArrayList<>(combinations.entrySet());
    entries.sort((first, second) -> {
        int cmp = Integer.compare(second.getValue().matches, first.getValue().matches);
        return cmp != 0 ? cmp : SemgrexUtils.compareKeys(first.getKey(), second.getKey());
      });

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<List<String>, Combination> entry : entries) {
      Combination combination = entry.getValue();
      String heading = describe(entry.getKey()) + ": " +
        combination.matches + (combination.matches == 1 ? " match" : " matches") + " in " +
        combination.sentences + (combination.sentences == 1 ? " sentence" : " sentences");
      if (conllu) {
        // each sentence ends with a blank line already, and a heading
        // between sentences would not be CoNLL-U
        for (Sample sample : inCorpusOrder(combination)) {
          sb.append(conllu(sample, heading));
        }
        continue;
      }
      if (sb.length() > 0) {
        sb.append(System.lineSeparator());
      }
      sb.append(heading).append(System.lineSeparator());
      for (Sample sample : inCorpusOrder(combination)) {
        sb.append("  ").append(sentenceId(sample)).append("  ").append(markedText(sample)).append(System.lineSeparator());
      }
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("sample");
    if (count != DEFAULT_COUNT) {
      sb.append(" ").append(COUNT).append(" ").append(count);
    }
    if (seed != DEFAULT_SEED) {
      sb.append(" ").append(SEED).append(" ").append(seed);
    }
    if (conllu) {
      sb.append(" ").append(CONLLU);
    }
    for (String key : lowered) {
      sb.append(" ").append(SemgrexStats.LOWER).append(" ").append(key);
    }
    for (String key : keys) {
      sb.append(" ").append(key);
    }
    return sb.toString();
  }
}
