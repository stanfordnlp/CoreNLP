package edu.stanford.nlp.ie.regexp;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
//import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.time.TimeExpressionExtractor;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * A simple rule-based classifier that detects NUMBERs in a sequence of Chinese tokens. This classifier mimics the
 * behavior of {@link edu.stanford.nlp.ie.regexp.NumberSequenceClassifier} (without using SUTime) and works on Chinese sequence.
 *
 * TODO: An interface needs to be used to reuse code for NumberSequenceClassifier
 * TODO: Ideally a Chinese version of SUTime needs to be used to provide more flexibility and accuracy.
 *
 * @author Yuhao Zhang
 * @author Peng Qi
 */
public class ChineseNumberSequenceClassifier extends AbstractSequenceClassifier<CoreLabel> {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseNumberSequenceClassifier.class);

  private static final boolean DEBUG = false;

  private final boolean useSUTime;

  public static final boolean USE_SUTIME_DEFAULT = false;
  public static final String USE_SUTIME_PROPERTY = "ner.useSUTime";
  public static final String USE_SUTIME_PROPERTY_BASE = "useSUTime";
  public static final String SUTIME_PROPERTY = "sutime";

  private final TimeExpressionExtractor timexExtractor;

  public ChineseNumberSequenceClassifier() {
    this(new Properties(), USE_SUTIME_DEFAULT, new Properties());
  }

  public ChineseNumberSequenceClassifier(boolean useSUTime) {
    this(new Properties(), useSUTime, new Properties());
  }

  public ChineseNumberSequenceClassifier(Properties props, boolean useSUTime, Properties sutimeProps) {
    super(props);
    this.useSUTime = useSUTime;
    if(this.useSUTime) {
      // TODO: Need a Chinese version of SUTime
      log.warn("SUTime currently does not support Chinese. Ignore property ner.useSUTime.");
    }
    this.timexExtractor = null;
  }

  // All the tags we need
  public static final String NUMBER_TAG = "NUMBER";
  public static final String DATE_TAG = "DATE";
  public static final String TIME_TAG = "TIME";
  public static final String MONEY_TAG = "MONEY";
  public static final String ORDINAL_TAG = "ORDINAL";
  public static final String PERCENT_TAG = "PERCENT";

  // Patterns we need
  public static final Pattern CURRENCY_WORD_PATTERN =
      Pattern.compile("元|刀|(?:美|欧|澳|加|日|韩)元|英?镑|法郎|卢比|卢布|马克|先令|克朗|泰?铢|(?:越南)?盾|美分|便士|块钱|毛钱|角钱");
  // In theory 块 钱 should be separated by segmenter, but just in case segmenter fails
  // TODO(yuhao): Need to add support for 块 钱, 毛 钱, 角 钱, 角, 五 块 二
  public static final Pattern PERCENT_WORD_PATTERN1 = Pattern.compile("(?:百分之|千分之).+");
  public static final Pattern PERCENT_WORD_PATTERN2 = Pattern.compile(".+%");
  public static final Pattern DATE_PATTERN1 = Pattern.compile(".+(?:年代?|月份?|日|号|世纪)");
  public static final Pattern DATE_PATTERN2 = Pattern.compile("(?:星期|周|礼拜).+");
  public static final Pattern DATE_PATTERN3 = Pattern.compile("[0-9一二三四五六七八九零〇十]{2,4}");
  public static final Pattern DATE_PATTERN4 = Pattern.compile("(?:[0-9]{2,4}[/\\-\\.][0-9]+[/\\-\\.][0-9]+|[0-9]+[/\\-\\.][0-9]+[/\\-\\.][0-9]{2,4}|[0-9]+[/\\-\\.]?[0-9]+)");
  public static final Pattern DATE_PATTERN5 = Pattern.compile("[昨今明][天晨晚夜早]");
  public static final Pattern TIME_PATTERN1 = Pattern.compile(".+(?::|点|时)(?:过|欠|差)?(?:.+(?::|分)?|整?|钟?|.+刻)?(?:.+秒?)"); // This only works when POS = NT

  private static final Pattern CHINESE_AND_ARABIC_NUMERALS_PATTERN = Pattern.compile("[一二三四五六七八九零十〇\\d]+");
  // This is used to capture a special case of date in Chinese: 70 后 or 七零 后
  private static final String DATE_AGE_LOCALIZER = "后";

  // order it by number of characters DESC for handy one-by-one matching of string suffix
  public static final String[] CURRENCY_WORDS_VALUES = new String[] {"越南盾", "美元", "欧元", "澳元", "加元", "日元", "韩元",
      "英镑", "法郎", "卢比", "卢布", "马克", "先令", "克朗", "泰铢", "盾", "铢", "刀", "镑", "元"};

  public static final String[] DATE_WORDS_VALUES = new String[] {"明天", "后天", "昨天", "前天", "明年", "后年", "去年", "前年",
      "昨日", "明日", "来年", "上月", "本月", "目前", "今后", "未来", "日前", "最近", "当时", "后来", "那时", "这时", "今", "今天",
      "当今", "如今", "之后", "当代", "以前", "现在", "将来", "此时", "此前", "元旦"};
  public static final HashSet<String> DATE_WORDS = new HashSet<>(Arrays.asList(DATE_WORDS_VALUES));

  public static final String[] TIME_WORDS_VALUES = new String[] {"早晨", "清晨", "凌晨", "上午", "中午", "下午", "傍晚", "晚上",
      "夜间", "晨间", "晚间", "午前", "午后", "早", "晚"};
  public static final HashSet<String> TIME_WORDS = new HashSet<>(Arrays.asList(TIME_WORDS_VALUES));

  /**
   * Use a set of heuristic rules to assign NER tags to tokens.
   * @param document A {@link List} of something that extends {@link CoreMap}.
   * @return
   */
  @Override
  public List<CoreLabel> classify(List<CoreLabel> document) {
    // The actual implementation of the classifier
    PaddedList<CoreLabel> pl = new PaddedList<>(document, pad);
    for (int i = 0, sz = pl.size(); i < sz; i++) {
      CoreLabel me = pl.get(i);
      CoreLabel prev = pl.get(i - 1);
      CoreLabel next = pl.get(i + 1);
      // by default set to be "O"
      me.set(CoreAnnotations.AnswerAnnotation.class, flags.backgroundSymbol);

      // If current word is OD, label it as ORDINAL
      if(me.getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("OD")) {
        me.set(CoreAnnotations.AnswerAnnotation.class, ORDINAL_TAG);
      } else if(CURRENCY_WORD_PATTERN.matcher(me.word()).matches() &&
          prev.getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD")) {
        // If current word is currency word and prev word is a CD
        me.set(CoreAnnotations.AnswerAnnotation.class, MONEY_TAG);
      } else if(me.getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD")) {
        // TODO(yuhao): Need to support Chinese captial numbers like 叁拾 (This won't be POS-tagged as CD).
        // If current word is a CD
        if(PERCENT_WORD_PATTERN1.matcher(me.word()).matches() ||
            PERCENT_WORD_PATTERN2.matcher(me.word()).matches()) {
          // If current word is a percent
          me.set(CoreAnnotations.AnswerAnnotation.class, PERCENT_TAG);
        } else if(rightScanFindsMoneyWord(pl, i)) {
          // If one the right finds a currency word
          me.set(CoreAnnotations.AnswerAnnotation.class, MONEY_TAG);
        } else if(me.word().length() == 2 && CHINESE_AND_ARABIC_NUMERALS_PATTERN.matcher(me.word()).matches() &&
            DATE_AGE_LOCALIZER.equals(next.word())) {
          // This is to extract a special case of DATE: 70 后 or 七零 后
          me.set(CoreAnnotations.AnswerAnnotation.class, DATE_TAG);
        } else {
          // Otherwise we should safely label it as NUMBER
          me.set(CoreAnnotations.AnswerAnnotation.class, NUMBER_TAG);
        }
      } else if(me.getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("NT")) {
        // If current word is a NT (temporal noun)
        if(DATE_PATTERN1.matcher(me.word()).matches() ||
            DATE_PATTERN2.matcher(me.word()).matches() ||
            DATE_PATTERN3.matcher(me.word()).matches() ||
            DATE_PATTERN4.matcher(me.word()).matches() ||
            DATE_PATTERN5.matcher(me.word()).matches() ||
            DATE_WORDS.contains(me.word())) {
          me.set(CoreAnnotations.AnswerAnnotation.class, DATE_TAG);
        } else if(TIME_PATTERN1.matcher(me.word()).matches() ||
            TIME_WORDS.contains(me.word())) {
          me.set(CoreAnnotations.AnswerAnnotation.class, TIME_TAG);
        } else {
          // TIME may have more variants (really?) so always add as TIME by default
          me.set(CoreAnnotations.AnswerAnnotation.class, TIME_TAG);
        }
      } else if(DATE_AGE_LOCALIZER.equals(me.word()) && prev.word().length() == 2 &&
          CHINESE_AND_ARABIC_NUMERALS_PATTERN.matcher(prev.word()).matches()) {
        // Label 后 as DATE if the sequence is 70 后 or 七零 后
        me.set(CoreAnnotations.AnswerAnnotation.class, DATE_TAG);
      }
    }
    return document;
  }

  /**
   * Look along CD words and see if next thing is a money word.
   *
   * @param pl The list of CoreLabel
   * @param i The position to scan right from
   * @return Whether a money word is found
   */
  private static boolean rightScanFindsMoneyWord(List<CoreLabel> pl, int i) {
    int j = i;
    if (DEBUG) {
      log.info("rightScan from: " + pl.get(j).word());
    }
    int sz = pl.size();
    while (j < sz && pl.get(j).getString(CoreAnnotations.PartOfSpeechAnnotation.class).equals("CD")) {
      j++;
    }
    if (j >= sz) {
      return false;
    }
    String tag = pl.get(j).getString(CoreAnnotations.PartOfSpeechAnnotation.class);
    String word = pl.get(j).word();
    if (DEBUG) {
      log.info("rightScan testing: " + word + '/' + tag + "; answer is: " + Boolean.toString((tag.equals("NN") || tag.equals("NNS")) && CURRENCY_WORD_PATTERN.matcher(word).matches()));
    }
    return (tag.equals("M") || tag.equals("NN") || tag.equals("NNS")) && CURRENCY_WORD_PATTERN.matcher(word).matches();
  }

  @Override
  public List<CoreLabel> classifyWithGlobalInformation(List<CoreLabel> tokenSequence, CoreMap document, CoreMap sentence) {
    if(useSUTime) {
      log.fatal("ChineseNumberSequenceClassifier does not have SUTime implementation.");
    }
    return classify(tokenSequence);
  }

  @Override
  public void train(Collection<List<CoreLabel>> docs, DocumentReaderAndWriter<CoreLabel> readerAndWriter) {
    // Train is not needed for this rule-based classifier
  }

  @Override
  public void serializeClassifier(String serializePath) {
  }

  @Override
  public void serializeClassifier(ObjectOutputStream oos) {
  }

  @Override
  public void loadClassifier(ObjectInputStream in, Properties props) throws IOException, ClassCastException, ClassNotFoundException {
  }

  public static void main(String[] args) throws IOException {
   /* Properties props = StringUtils.argsToProperties("-props", "/Users/yuhao/Research/tmp/ChineseNumberClassifierProps.properties");
//    Properties props = StringUtils.argsToProperties("-props", "/Users/yuhao/Research/tmp/EnglishNumberClassifierProps.properties");
    props.setProperty("outputFormat", "text");
    props.setProperty("ssplit.boundaryTokenRegex", "\\n"); // one sentence per line
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    String docFileName = "/Users/yuhao/Research/tmp/chinese_number_examples.txt";
//    String docFileName = "/Users/yuhao/Research/tmp/english_number_examples.txt";
    List<String> docLines = IOUtils.linesFromFile(docFileName);
    PrintStream out = new PrintStream(docFileName + ".out");
    for (String docLine : docLines) {
      Annotation sentenceAnnotation = new Annotation(docLine);
      pipeline.annotate(sentenceAnnotation);
      pipeline.prettyPrint(sentenceAnnotation, out);
      pipeline.prettyPrint(sentenceAnnotation, System.out);
    }

    out.close();*/
  }
}
