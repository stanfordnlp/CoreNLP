package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
//import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.StringUtils;
import org.junit.*;

import java.util.*;

import static org.junit.Assert.*;

/**
 * <p>A test for the {@link NaturalLogicAnnotator} setting the right
 * {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.OperatorAnnotation}s.</p>
 *
 * <p>
 *   Failures on tests which do not start with "fracas" should be looked into -- these are generally fairly toy
 *   sentences that should not be parsed incorrectly. Failures on the fracas examples can potentially arise just by virtue
 *   of the parser changing, and you should not feel too bad about commenting them. In addition, every so often the sentences
 *   commented out should be uncommented to see if they now work -- a better parser should fix some of these sentences.
 * </p>
 *
 * @author Gabor Angeli
 */
public class OperatorScopeITest {

  private static final StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties(){{
    setProperty("annotators", "tokenize,ssplit,pos,lemma,parse,natlog");
    setProperty("ssplit.isOneSentence", "true");
    setProperty("tokenize.class", "PTBTokenizer");
    setProperty("tokenize.language", "en");
    setProperty("natlog.neQuantifiers", "true");
  }});

  @SuppressWarnings("unchecked")
  private Optional<OperatorSpec>[] annotate(String text) {
    Annotation ann = new Annotation(text);
    pipeline.annotate(ann);
    //System.out.println(ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(TreeCoreAnnotations.TreeAnnotation.class));
    List<CoreLabel> tokens = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(CoreAnnotations.TokensAnnotation.class);
    Optional<OperatorSpec>[] scopes = new Optional[tokens.size()];
    Arrays.fill(scopes, Optional.empty());
    for (int i = 0; i < tokens.size(); ++i) {
      if (tokens.get(i).containsKey(NaturalLogicAnnotations.OperatorAnnotation.class)) {
        scopes[i] = Optional.of(tokens.get(i).get(NaturalLogicAnnotations.OperatorAnnotation.class));
      }
    }
    return scopes;
  }

  private void checkScope(int subjBegin, int subjEnd, int objBegin, int objEnd, Optional<OperatorSpec> guess) {
    assertTrue("No quantifier found", guess.isPresent());
    assertEquals("Bad subject begin " + guess.get(), subjBegin, guess.get().subjectBegin);
    assertEquals("Bad subject end " + guess.get(), subjEnd, guess.get().subjectEnd);
    assertEquals("Bad object begin " + guess.get(), objBegin, guess.get().objectBegin);
    assertEquals("Bad object end " + guess.get(), objEnd, guess.get().objectEnd);
  }

  private void checkScope(int subjBegin, int subjEnd, Optional<OperatorSpec> guess) {
    assertTrue("No quantifier found", guess.isPresent());
    assertEquals("Bad subject begin " + guess.get() + "  (expected=" + subjBegin + " got=" + guess.get().subjectBegin + ")", subjBegin, guess.get().subjectBegin);
    assertEquals("Bad subject end " + guess.get() + "  (expected=" + subjEnd + " got=" + guess.get().subjectEnd + ")", subjEnd, guess.get().subjectEnd);
    assertEquals("Two-place quantifier matched", subjEnd, guess.get().objectBegin);
    assertEquals("Two place quantifier matched", subjEnd, guess.get().objectEnd);
  }

  @SuppressWarnings("ConstantConditions")
  private void checkScope(String spec) {
    String[] terms = spec
        .replace(",", " ,")
        .replace(".", " .")
        .split("\\s+");
//    int quantStart = -1;
    int quantEnd = -1;
    int subjBegin = -1;
    int subjEnd = -1;
    int objBegin = -1;
    int objEnd = -1;
    boolean seenSubj = false;
    int tokenIndex = 0;
    List<String> cleanSentence = new ArrayList<>();
    for (String term : terms) {
      switch (term) {
        case "{":
//          quantStart = tokenIndex;
          break;
        case "}":
          quantEnd = tokenIndex;
          break;
        case "[":
          if (!seenSubj) {
            subjBegin = tokenIndex;
          } else {
            objBegin = tokenIndex;
          }
          break;
        case "]":
          if (!seenSubj) {
            subjEnd = tokenIndex;
            seenSubj = true;
          } else {
            objEnd = tokenIndex;
          }
          break;
        default:
          cleanSentence.add(term);
          tokenIndex += 1;
          break;
      }
    }
    Optional<OperatorSpec>[] scopes = annotate(StringUtils.join(cleanSentence, " "));
    System.err.println("Checking [@ " + (quantEnd - 1) + "]:  " + spec);
    if (objBegin >= 0 && objEnd >= 0) {
      checkScope(subjBegin, subjEnd, objBegin, objEnd, scopes[quantEnd - 1]);
    } else {
      checkScope(subjBegin, subjEnd, scopes[quantEnd - 1]);
    }
  }

  @Test
  public void annotatorRuns() {
    annotate("All green cats have tails.");
  }

  @Test
  public void negationMidSentence() {
    checkScope(0, 1, 3, 6, annotate("Obama was not born in Dallas")[2]);
  }

  @Test
  public void all_X_verb_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats eat mice.")[0]);
    checkScope(1, 2, 2, 4, annotate("All cats have tails.")[0]);
  }

  @Test
  public void all_X_want_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats want milk.")[0]);
  }

  @Test
  public void all_X_verb_prep_Y() {
    checkScope(1, 2, 2, 5, annotate("All cats are in boxes.")[0]);
    checkScope(1, 2, 2, 5, annotate("All cats voted for Roosevelt.")[0]);
    checkScope(1, 5, 5, 8, annotate("All cats who like dogs voted for Teddy.")[0]);
    checkScope(1, 2, 2, 6, annotate("All cats have spoken to Fido.")[0]);
  }

  @Test
  public void all_X_be_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats are cute")[0]);
  }

  @Test
  public void all_X_can_Y() {
    checkScope(1, 2, 2, 4, annotate("All cats can purr")[0]);
  }

  @Test
  public void all_X_relclause_verb_Y() {
    // TODO: parser doesn't attach "eat fish" to "cats"
    //checkScope(1, 5, 5, 7, annotate("All cats who like dogs eat fish.")[0]);
  }

  @Test
  public void all_of_X_verb_Y() {
    checkScope(2, 4, 4, 6, annotate("All of the cats hate dogs.")[1]);
    checkScope(2, 6, 6, 9, annotate("Each of the other 99 companies owns one computer.")[1]);
  }

  @Test
  public void PER_predicate() {
    checkScope(0, 1, 1, 4, annotate("Felix likes cat food.")[0]);
  }

  @Test
  public void PER_has_predicate() {
    checkScope(0, 1, 1, 5, annotate("Felix has liked cat food.")[0]);
  }

  @Test
  public void PER_predicate_prep() {
    checkScope(0, 1, 1, 7, annotate("Jack paid the bank for 10 years")[0]);
  }

  @Test
  public void PER_has_predicate_prep() {
    checkScope(0, 1, 1, 5, annotate("Felix has spoken to Fido.")[0]);
  }

  @Test
  public void PER_is_nn() {
    checkScope(0, 1, 1, 4, annotate("Felix is a cat.")[0]);
  }

  @Test
  public void PER_is_jj() {
    checkScope(0, 1, 1, 3, annotate("Felix is cute.")[0]);
  }

  @Test
  public void few_x_verb_y() {
    checkScope(1, 2, 2, 4, annotate("all cats chase dogs")[0]);
  }

  @Test
  public void a_few_x_verb_y() {
    //checkScope(2, 3, 3, 5, annotate("a few cats chase dogs")[1]);
    //assertFalse(annotate("a few cats chase dogs")[0].isPresent());
  }

  @Test
  public void binary_no() {
    checkScope(1, 2, 2, 4, annotate("no cats chase dogs")[0]);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void unary_not() {
    Optional<OperatorSpec>[] quantifiers = annotate("some cats don't like dogs");
    checkScope(1, 2, 2, 6, quantifiers[0]); // some
    checkScope(4, 6, quantifiers[3]); // no
    assertFalse(quantifiers[3].get().isBinary());  // is unary no
  }

  @Test
  public void num_X_verb_Y() {
    checkScope(1, 2, 2, 4, annotate("Three cats eat mice.")[0]);
    checkScope(1, 2, 2, 4, annotate("3 cats have tails.")[0]);
  }

  @Test
  public void at_least_num_X_verb_Y() {
    checkScope(3, 4, 4, 6, annotate("at least Three cats eat mice.")[2]);
    checkScope(3, 4, 4, 6, annotate("at least 3 cats have tails.")[2]);
  }

  @Test
  public void everyone_pp_verb_Y() {
    checkScope(1, 3, 3, 5, annotate("everyone at Stanford likes cats.")[0]);
    checkScope(1, 5, 5, 7, annotate("everyone who is at Stanford likes cats.")[0]);
  }

  @Test
  public void there_are_np() {
    // TODO: the parser is tagging "there" as RB and not EX
    //checkScope(2, 3, annotate("there are cats")[1]);
    checkScope(2, 3, annotate("There are cats")[1]);
  }

  @Test
  public void there_are_np_pp() {
    // TODO(gabor) this actually seems wrong...
    // TODO: the parser is tagging "there" as RB and not EX
    // checkScope(2, 6, annotate("there are cats who like dogs")[1]);
    checkScope(2, 6, annotate("There are cats who like dogs")[1]);
  }

  @Test
  public void one_of_the_X_Y() {
    checkScope(3, 4, 4, 6, annotate("one of the cats have tails")[2]);
  }

  @Test
  public void regressionStrangeComma() {
    Optional<OperatorSpec>[] operators = annotate("all cats, have tails.");
    checkScope(1, 2, 3, 5, operators[0]);  // though, unclear if this should even be true?
  }

  @Test
  public void unarySome() {
    checkScope(3, 4, annotate("Cats eat some mice")[2]);
  }

 
  @Test
  public void fracasSentencesWithAll() {
    checkScope("{ All } [ APCOM managers ] [ have company cars ]");
    checkScope("{ All } [ Canadian residents ] [ can travel freely within Europe ]");
    checkScope("{ All } [ Europeans ] [ are people ]");
    checkScope("{ All } [ Europeans ] [ can travel freely within Europe ]");
    checkScope("{ All } [ Europeans ] [ have the right to live in Europe ]");
    checkScope("{ All } [ Italian men ] [ want to be a great tenor ]");
    checkScope("{ All } [ committee members ] [ are people ]");
    checkScope("{ All } [ competent legal authorities ] [ are competent law lecturers ]");
    checkScope("{ All } [ elephants ] [ are large animals ]");
    checkScope("{ All } [ fat legal authorities ] [ are fat law lecturers ]");
    checkScope("{ All } [ law lecturers ] [ are legal authorities ]");
    checkScope("{ All } [ legal authorities ] [ are law lecturers ]");
    checkScope("{ All } [ mice ] [ are small animals ]");
    checkScope("{ All } [ people who are from Portugal ] [ are from southern Europe ]");
    checkScope("{ All } [ people who are from Sweden ] [ are from Scandinavia ]");
    checkScope("{ All } [ people who are resident in Europe ] [ can travel freely within Europe ]");
    checkScope("{ All } [ residents of major western countries ] [ are residents of western countries ]");
    checkScope("{ All } [ residents of member states ] [ are individuals ]");
    checkScope("{ All } [ residents of the North American continent ] [ can travel freely within Europe ]");
    checkScope("{ All } [ the people who were at the meeting ] [ voted for a new chairman ]");
  }
  

  @Test
  public void fracasSentencesWithEach() {
    checkScope("{ Each } [ Canadian resident ] [ can travel freely within Europe ]");
    checkScope("{ Each } [ European ] [ can travel freely within Europe ]");
    checkScope("{ Each } [ European ] [ has the right to live in Europe ]");
    checkScope("{ Each } [ Italian tenor ] [ wants to be great ]");
    checkScope("{ Each } [ department ] [ has a dedicated line ]");
    checkScope("{ Each of } [ the other 99 companies ] [ owns one computer ]");
    checkScope("{ Each } [ resident of the North American continent ] [ can travel freely within Europe ]");
  }

  @Test
  public void fracasSentencesWithEvery() {
    checkScope("{ Every } [ Ancient Greek ] [ was a noted philosopher ]");
    checkScope("{ Every } [ Canadian resident ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ Canadian resident ] [ is a resident of the North American continent ]");
    checkScope("{ Every } [ European ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ European ] [ has the right to live in Europe ]");
    checkScope("{ Every } [ European ] [ is a person ]");
    checkScope("{ Every } [ Italian man ] [ wants to be a great tenor ]");
    checkScope("{ Every } [ Swede ] [ is a Scandinavian ]");
    checkScope("{ Every } [ committee ] [ has a chairman ]");
    checkScope("{ Every } [ committee ] [ has a chairman appointed by members of the committee ]");
    // Current parser is mis-attaching the "has"
    // checkScope("{ Every } [ customer who owns a computer ] [ has a service contract for it ]");
    checkScope("{ Every } [ department ] [ rents a line from BT ]");
    checkScope("{ Every } [ executive who had a laptop computer ] [ brought it to take notes at the meeting ]");
    checkScope("{ Every } [ four - legged mammal ] [ is a four - legged animal ]");
    checkScope("{ Every } [ individual who has the right to live anywhere in Europe ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ individual who has the right to live in Europe ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ inhabitant of Cambridge ] [ voted for a Labour MP ]");
    checkScope("{ Every } [ mammal ] [ is an animal ]");
    checkScope("{ Every } [ person who has the right to live in Europe ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ report ] [ has a cover page ]");
    checkScope("{ Every } [ representative and client ] [ was at the meeting ]");
    checkScope("{ Every } [ representative and every client ] [ was at the meeting ]");
    checkScope("{ Every } [ representative ] [ has read this report ]");
    checkScope("{ Every } [ representative or client ] [ was at the meeting ]");
    checkScope("{ Every } [ representative ] [ was at the meeting ]");
    checkScope("{ Every } [ resident of the North American continent ] [ can travel freely within Europe ]");
    checkScope("{ Every } [ student ] [ used her workstation ]");
  }

  @Test
  public void fracasSentencesWithEveryone() {
    checkScope("{ Everyone } [ at the meeting ] [ voted for a new chairman ]");
    //checkScope("{ Everyone } [ who starts gambling seriously ] [ continues until he is broke ]");
    checkScope("{ Everyone } [ who starts gambling seriously ] [ stops the moment he is broke ]");
  }

  @Test
  public void fracasSentencesWithFew() {
    checkScope("{ Few } [ committee members ] [ are from Portugal ]");
    checkScope("{ Few } [ committee members ] [ are from southern Europe ]");
    checkScope("{ Few } [ female committee members ] [ are from southern Europe ]");
  }

  @Test
  public void fracasSentencesWithA() {
    checkScope("{ A } [ Scandinavian ] [ won a Nobel prize ]");
    checkScope("{ A } [ Swede ] [ won a Nobel prize ]");
    checkScope("{ A } [ company director ] [ awarded himself a large payrise ]");
    checkScope("{ A } [ company director ] [ has awarded and been awarded a payrise ]");
    checkScope("{ A } [ lawyer ] [ signed every report ]");

    checkScope("{ An } [ Irishman ] [ won a Nobel prize ]");
    checkScope("{ An } [ Irishman ] [ won the Nobel prize for literature ]");
    checkScope("{ An } [ Italian ] [ became the world 's greatest tenor ]");
  }

  @Test
  public void fracasSentencesWithAFew() {
    checkScope("{ A few } [ committee members ] [ are from Scandinavia ]");
    checkScope("{ A few } [ committee members ] [ are from Sweden ]");
    checkScope("{ A few } [ female committee members ] [ are from Scandinavia ]");
    checkScope("{ A few } [ great tenors ] [ sing popular music ]");
  }

  @Test
  public void fracasSentencesWithAtLeastAFew() {
    //checkScope("{ At least a few } [ committee members ] [ are from Scandinavia ]");
    //checkScope("{ At least a few } [ committee members ] [ are from Sweden ]");
    checkScope("{ At least a few } [ female committee members ] [ are from Scandinavia ]");
  }

  @Test
  public void fracasSentencesWithEither() {
    checkScope("{ Either } [ Smith Jones or Anderson ] [ signed the contract ]");
  }

  @Test
  public void fracasSentencesWithOneOfThe() {
    checkScope("{ One of } [ the commissioners ] [ spends a lot of time at home ]");
//    checkScope("{ One of } [ the leading tenors ] [ is Pavarotti ]");  // TODO(gabor) these are actually a bit tricky; [one of] and [the] are separate constituents
  }

  @Test
  public void fracasSentencesWithSeveral() {
    checkScope("{ Several } [ Portuguese delegates ] [ got the results published in major national newspapers ]");
    checkScope("{ Several } [ delegates ] [ got the results published ]");
    checkScope("{ Several } [ delegates ] [ got the results published in major national newspapers ]");
    checkScope("{ Several } [ great tenors ] [ are British ]");
  }

  @Test
  public void fracasSentencesWithSome() {
    checkScope("{ Some } [ Irish delegates ] [ finished the survey on time ]");
    checkScope("{ Some } [ Italian men ] [ are great tenors ]");
    checkScope("{ Some } [ Italian tenors ] [ are great ]");
    checkScope("{ Some } [ Scandinavian delegate ] [ finished the report on time ]");
    checkScope("{ Some } [ accountant ] [ attended the meeting ]");
    checkScope("{ Some } [ accountants ] [ attended the meeting ]");
    checkScope("{ Some } [ delegate ] [ finished the report on time ]");
    checkScope("{ Some } [ delegates ] [ finished the survey ]");
    checkScope("{ Some } [ delegates ] [ finished the survey on time ]");
    checkScope("{ Some } [ great tenors ] [ are Swedish ]");
    //    checkScope("{ Some } [ great tenors ] [ like popular music ]");  // parse error
    checkScope("{ Some } [ people ] [ discover that they have been asleep ]");
  }

  @Test
  public void fracasSentencesWithThe() {
    checkScope("{ The } [ Ancient Greeks ] [ were all noted philosophers ]");
    checkScope("{ The } [ Ancient Greeks ] [ were noted philosophers ]");
    checkScope("{ The } [ ITEL - XZ ] [ is fast ]");
    checkScope("{ The } [ ITEL - ZX ] [ is an ITEL computer ]");
    checkScope("{ The } [ ITEL - ZX ] [ is slower than 500 MIPS ]");
    checkScope("{ The } [ PC - 6082 ] [ is as fast as the ITEL - XZ ]");
    checkScope("{ The } [ PC - 6082 ] [ is fast ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than 500 MIPS ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than any ITEL computer ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than every ITEL computer ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than some ITEL computer ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than the ITEL - XZ ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than the ITEL - ZX ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than the ITEL - ZX and the ITEL - ZY ]");
    checkScope("{ The } [ PC - 6082 ] [ is faster than the ITEL - ZX or the ITEL - ZY ]");
    checkScope("{ The } [ PC - 6082 ] [ is slow ]");
    checkScope("{ The } [ PC - 6082 ] [ is slower than the ITEL - XZ ]");
    checkScope("{ The } [ chairman of the department ] [ is a person ]");
    checkScope("{ The } [ chairman ] [ read out every item on the agenda ]");
    checkScope("{ The } [ chairman ] [ read out the items on the agenda ]");
    checkScope("{ The } [ conference ] [ started on July 4th , 1994 ]");
    checkScope("{ The } [ conference ] [ was over on July 8th , 1994 ]");
    checkScope("{ The } [ inhabitants of Cambridge ] [ voted for a Labour MP ]");
    //    checkScope("{ The } [ people who were at the meeting ] [ all voted for a new chairman ]");  // TODO(gabor) Parse error on "meeting -dep-> all"
    checkScope("{ The } [ people who were at the meeting ] [ voted for a new chairman ]");
    checkScope("{ The } [ really ambitious tenors ] [ are Italian ]");
    checkScope("{ The } [ residents of major western countries ] [ can travel freely within Europe ]");
    checkScope("{ The } [ residents of major western countries ] [ have the right to live in Europe ]");
    checkScope("{ The } [ residents of member states ] [ can travel freely within Europe ]");
    checkScope("{ The } [ residents of member states ] [ have the right to live anywhere in Europe ]");
    checkScope("{ The } [ residents of member states ] [ have the right to live in Europe ]");
    checkScope("{ The } [ residents of western countries ] [ can travel freely within Europe ]");
    checkScope("{ The } [ residents of western countries ] [ have the right to live in Europe ]");
    checkScope("{ The } [ sales department ] [ rents a line from BT ]");
    checkScope("{ The } [ sales department ] [ rents it from BT ]");
    checkScope("{ The } [ students ] [ are going to Paris by train ]");
    checkScope("{ The } [ students ] [ have spoken to Mary ]");
    checkScope("{ The } [ system failure ] [ was blamed on one or more software faults ]");
  }

  @Test
  public void fracasSentencesWithThereAre() {
    checkScope("{ There are } [ 100 companies ]");
    checkScope("{ There are } [ Italian men who want to be a great tenor ]");
    checkScope("{ There are } [ Italian tenors who want to be great ]");
    checkScope("{ There are } [ few committee members from Portugal ]");
    checkScope("{ There are } [ few committee members from southern Europe ]");
    checkScope("{ There are } [ great tenors who are British ]");
    checkScope("{ There are } [ great tenors who are German ]");
    checkScope("{ There are } [ great tenors who are Italian ]");
    checkScope("{ There are } [ great tenors who are Swedish ]");
    checkScope("{ There are } [ great tenors who sing popular music ]");
    // checkScope("{ There are } [ really ambitious tenors who are Italian ]");  // TODO(gabor) parse error on are -advmod-> really
    // checkScope("{ There are } [ really great tenors who are modest ]");       // TODO(gabor) as above
    checkScope("{ There are } [ sixteen representatives ]");
    checkScope("{ There are } [ some reports from ITEL on Smith 's desk ]");
    checkScope("{ There are } [ tenors who will take part in the concert ]");

    checkScope("{ There is } [ a car that John and Bill own ]");
    checkScope("{ There is } [ someone whom Helen saw answer the phone ]");

    checkScope("{ There was } [ a group of people that met ]");
    checkScope("{ There was } [ an Italian who became the world 's greatest tenor ]");
    checkScope("{ There was } [ one auditor who signed all the reports ]");
  }

  @Test
  public void fracasSentencesWithProperNouns() {
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL ]");
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL has ]");
    checkScope("[ { APCOM } ] [ has a more important customer than ITEL is ]");
    checkScope("[ { APCOM } ] [ has been paying mortgage interest for a total of 15 years or more ]");
    checkScope("[ { APCOM } ] [ lost some orders ]");
    checkScope("[ { APCOM } ] [ lost ten orders ]");
    checkScope("[ { APCOM } ] [ signed the contract ]");
    checkScope("[ { APCOM } ] [ signed the contract ] Friday");
    checkScope("[ { APCOM } ] [ sold exactly 2500 computers ]");
    checkScope("[ { APCOM } ] [ won some orders ]");
    checkScope("[ { APCOM } ] [ won ten orders ]");

    checkScope("[ { Bill } ] [ bought a car ]");
    checkScope("[ { Bill } ] [ has spoken to Mary ]");
    checkScope("[ { Bill } ] [ is going to ]");
    checkScope("[ { Bill } ] [ knows why John had his paper accepted ]");
    checkScope("[ { Bill } ] [ owns a blue car ]");
    checkScope("[ { Bill } ] [ owns a blue one ]");
    checkScope("[ { Bill } ] [ owns a car ]");
    checkScope("[ { Bill } ] [ owns a fast car ]");
    checkScope("[ { Bill } ] [ owns a fast one ]");
    checkScope("[ { Bill } ] [ owns a fast red car ]");
    checkScope("[ { Bill } ] [ owns a red car ]");
    checkScope("[ { Bill } ] [ owns a slow one ]");
    checkScope("[ { Bill } ] [ owns a slow red car ]");
    checkScope("[ { Bill } ] [ said Mary wrote a report ]");
    checkScope("[ { Bill } ] [ said Peter wrote a report ]");
    checkScope("[ { Bill } ] [ spoke to Mary ]");
    checkScope("[ { Bill } ] [ spoke to Mary at five o'clock ]");
    checkScope("[ { Bill } ] [ spoke to Mary at four o'clock ]");
    checkScope("[ { Bill } ] [ spoke to Mary on Monday ]");
    checkScope("[ { Bill } ] [ spoke to everyone that John did ]");
    checkScope("[ { Bill } ] [ suggested to Frank 's boss that they should go to the meeting together , and Carl to Alan 's wife ]");
    checkScope("[ { Bill } ] [ went to Berlin by car ]");
    checkScope("[ { Bill } ] [ went to Berlin by train ]");
    checkScope("[ { Bill } ] [ went to Paris by train ]");
    checkScope("[ { Bill } ] [ will speak to Mary ]");
    checkScope("[ { Bill } ] [ wrote a report ]");

    checkScope("[ { Dumbo } ] [ is a four - legged animal ]");
    checkScope("[ { Dumbo } ] [ is a large animal ]");
    checkScope("[ { Dumbo } ] [ is a small animal ]");
    checkScope("[ { Dumbo } ] [ is a small elephant ]");
    checkScope("[ { Dumbo } ] [ is four - legged ]");
    checkScope("[ { Dumbo } ] [ is larger than Mickey ]");

    checkScope("[ { GFI } ] [ owns several computers ]");

    checkScope("[ { Helen } ] [ saw the chairman of the department answer the phone ]");

    checkScope("[ { ICM } ] [ is one of the companies and owns 150 computers ]");

    // checkScope("[ { ITEL } ] [ always delivers reports late ]");  // TODO(gabor) bad parse from ITEL -dep-> delivers
    checkScope("[ { ITEL } ] [ built MTALK in 1993 ]");
    // checkScope("[ { ITEL } ] [ currently has a factory in Birmingham ]");  // fix me (bad scope)
    checkScope("[ { ITEL } ] [ delivered reports late in 1993 ]");
    checkScope("[ { ITEL } ] [ developed a new editor in 1993 ]");
    checkScope("[ { ITEL } ] [ existed in 1992 ]");
    checkScope("[ { ITEL } ] [ expanded in 1993 ]");
    checkScope("[ { ITEL } ] [ finished MTALK in 1993 ]");
    checkScope("[ { ITEL } ] [ has a factory in Birmingham ]");
    checkScope("[ { ITEL } ] [ has developed a new editor since 1992 ]");
    checkScope("[ { ITEL } ] [ has expanded since 1992 ]");
    checkScope("[ { ITEL } ] [ has made a loss since 1992 ]");
    checkScope("[ { ITEL } ] [ has sent most of the reports which Smith needs ]");
    checkScope("[ { ITEL } ] [ made a loss in 1993 ]");
    checkScope("[ { ITEL } ] [ maintains all the computers that GFI owns ]");
    checkScope("[ { ITEL } ] [ maintains them ]");
    checkScope("[ { ITEL } ] [ managed to win the contract in 1992 ]");
    // checkScope("[ { ITEL } ] [ never delivers reports late ]");
    checkScope("[ { ITEL } ] [ owned APCOM from 1988 to 1992 ]");
    checkScope("[ { ITEL } ] [ owned APCOM in 1990 ]");
    checkScope("[ { ITEL } ] [ sent a progress report in July 1994 ]");
    checkScope("[ { ITEL } ] [ sold 3000 more computers than APCOM ]");
    checkScope("[ { ITEL } ] [ sold 5500 computers ]");
    checkScope("[ { ITEL } ] [ tried to win the contract in 1992 ]");
    checkScope("[ { ITEL } ] [ was building MTALK in 1993 ]");
    checkScope("[ { ITEL } ] [ was winning the contract from APCOM in 1993 ]");
    checkScope("[ { ITEL } ] [ won a contract in 1993 ]");
    checkScope("[ { ITEL } ] [ won at least eleven orders ]");
    checkScope("[ { ITEL } ] [ won more orders than APCOM ]");
    checkScope("[ { ITEL } ] [ won more orders than APCOM did ]");
    checkScope("[ { ITEL } ] [ won more orders than APCOM lost ]");
    checkScope("[ { ITEL } ] [ won more orders than the APCOM contract ]");
    checkScope("[ { ITEL } ] [ won more than one order ]");
    checkScope("[ { ITEL } ] [ won some orders ]");
    checkScope("[ { ITEL } ] [ won the APCOM contract ]");
    checkScope("[ { ITEL } ] [ won the contract from APCOM in 1993 ]");
    checkScope("[ { ITEL } ] [ won the contract in 1992 ]");
    checkScope("[ { ITEL } ] [ won twenty orders ]");
    checkScope("[ { ITEL } ] [ won twice as many orders than APCOM ]");
    checkScope("[ { Itel } ] [ was in Birmingham in 1993 ]");

    checkScope("[ { John } ] [ bought a car ]");
    checkScope("[ { John } ] [ found Mary before Bill ]");
    checkScope("[ { John } ] [ found Mary before Bill found Mary ]");
    checkScope("[ { John } ] [ found Mary before John found Bill ]");
    checkScope("[ { John } ] [ had his paper accepted ]");
    checkScope("[ { John } ] [ has a diamond ]");
    checkScope("[ { John } ] [ has a genuine diamond ]");
    checkScope("[ { John } ] [ has spoken to Mary ]");
    checkScope("[ { John } ] [ hated the meeting ]");
    checkScope("[ { John } ] [ is a cleverer politician than Bill ]");
    checkScope("[ { John } ] [ is a fatter politician than Bill ]");
    checkScope("[ { John } ] [ is a former successful university student ]");
    checkScope("[ { John } ] [ is a former university student ]");
    checkScope("[ { John } ] [ is a man and Mary is a woman ]");
    checkScope("[ { John } ] [ is a successful former university student ]");
    checkScope("[ { John } ] [ is a university student ]");
    checkScope("[ { John } ] [ is cleverer than Bill ]");
    checkScope("[ { John } ] [ is fatter than Bill ]");
    checkScope("[ { John } ] [ is going to Paris by car , and the students by train ]");
    checkScope("[ { John } ] [ is successful ]");
    // checkScope("[ { John } ] [ needed to buy a car ] and Bill did "); // TODO(gabor) interesting example; also, parse error
    checkScope("[ { John } ] [ owns a car ]");
    checkScope("[ { John } ] [ owns a fast red car ]");
    checkScope("[ { John } ] [ owns a red car ]");
    checkScope("[ { John } ] [ represents his company ] and so does Mary");
    checkScope("[ { John } ] [ said Bill had been hurt ]");
    checkScope("[ { John } ] [ said Bill had hurt himself ]");
    checkScope("[ { John } ] [ said Bill wrote a report ]");
    // FIXME this should work even if the parse changed some, right?
    //checkScope("[ { John } ] [ said Mary wrote a report , and Bill did too ]");  // interesting example
    // TODO(gabor) fix me (bad scope)
    //checkScope("[ { John } ] [ said that Mary wrote a report ] , and that Bill did too");
    checkScope("[ { John } ] [ spoke to Mary ]");
    checkScope("[ { John } ] [ spoke to Mary at four o'clock ]");
    checkScope("[ { John } ] [ spoke to Mary on Friday ]");
    checkScope("[ { John } ] [ spoke to Mary on Monday ]");
    checkScope("[ { John } ] [ spoke to Mary on Thursday ]");
    checkScope("[ { John } ] [ spoke to Sue ]");
    checkScope("[ { John } ] [ wanted to buy a car ] , and he did");
    checkScope("[ { John } ] [ wants to know how many men work part time ]");
    checkScope("[ { John } ] [ wants to know how many men work part time , and which ]");
    checkScope("[ { John } ] [ wants to know how many women work part time ]");
    checkScope("[ { John } ] [ wants to know which men work part time ]");
    checkScope("[ { John } ] [ went to Paris by car ]");
    // FIXME should this encompass "and Bill by train"?
    // checkScope("[ { John } ] [ went to Paris by car , and Bill by train ]");
    checkScope("[ { John } ] [ went to Paris by car , and Bill by train to Berlin ]");
    // FIXME should this encompass "and Bill to Berlin"?
    // checkScope("[ { John } ] [ went to Paris by car , and Bill to Berlin ]");
    checkScope("[ { John } ] [ wrote a report ]");
//    checkScope("[ { John } ] [ wrote a report ] , and Bill said Peter did too ]");  // TODO(gabor) fix me

    checkScope("[ { Jones } ] [ claimed Smith had costed Jones ' proposal ]");
    checkScope("[ { Jones } ] [ claimed Smith had costed Smith 's proposal ]");
    checkScope("[ { Jones } ] [ claimed he had costed Smith 's proposal ]");
    checkScope("[ { Jones } ] [ claimed he had costed his own proposal ]");
    checkScope("[ { Jones } ] [ graduated in March ] and has been employed ever since");
    checkScope("[ { Jones } ] [ has a company car ]");
    checkScope("[ { Jones } ] [ has been unemployed in the past ]");
    checkScope("[ { Jones } ] [ has more than one company car ]");
    checkScope("[ { Jones } ] [ is an APCOM manager ]");
    checkScope("[ { Jones } ] [ is the chairman of ITEL ]");
    checkScope("[ { Jones } ] [ left after Anderson left ]");
    checkScope("[ { Jones } ] [ left after Anderson was present ]");
    checkScope("[ { Jones } ] [ left after Smith left ]");
    // Tagger labels "before" as RB, leading to a very strange parse
    //checkScope("[ { Jones } ] [ left before Anderson left ]");
    checkScope("[ { Jones } ] [ left before Smith left ]");
    checkScope("[ { Jones } ] [ left the meeting ]");
    checkScope("[ { Jones } ] [ represents Jones 's company ]");
    checkScope("[ { Jones } ] [ represents Smith 's company ]");
    checkScope("[ { Jones } ] [ revised the contract ]");
    checkScope("[ { Jones } ] [ revised the contract after Smith did ]");
    checkScope("[ { Jones } ] [ revised the contract before Smith did ]");
    checkScope("[ { Jones } ] [ signed another contract ]");
    checkScope("[ { Jones } ] [ signed the contract ]");
    checkScope("[ { Jones } ] [ signed two contracts ]");
    //checkScope("[ { Jones } ] [ swam after Smith swam ]");
    checkScope("[ { Jones } ] [ swam to the shore ]");
    checkScope("[ { Jones } ] [ swam to the shore after Smith swam to the shore ]");
    checkScope("[ { Jones } ] [ was present ]");
    checkScope("[ { Jones } ] [ was present after Smith was present ]");
    checkScope("[ { Jones } ] [ was present before Smith was present ]");
    checkScope("[ { Jones } ] [ was unemployed at some time before he graduated ]");
    checkScope("[ { Jones } ] [ was writing a report ]");
    checkScope("[ { Jones } ] [ was writing a report after Smith was writing a report ]");
    checkScope("[ { Jones } ] [ was writing a report before Smith was writing a report ]");

    checkScope("[ { Kim } ] [ is a clever person ]");
    checkScope("[ { Kim } ] [ is a clever politician ]");
    checkScope("[ { Kim } ] [ is clever ]");

    checkScope("[ { MFI } ] [ has a service contract for all its computers ]");
    checkScope("[ { MFI } ] [ is a customer that owns exactly one computer ]");
    checkScope("[ { MFI } ] [ is a customer that owns several computers ]");

    checkScope("[ { Mary } ] [ has a workstation ]");
    checkScope("[ { Mary } ] [ is a student ]");
    checkScope("[ { Mary } ] [ is female ]");
    checkScope("[ { Mary } ] [ represents John 's company ]");
    checkScope("[ { Mary } ] [ represents her own company ]");
    checkScope("[ { Mary } ] [ used a workstation ]");
    checkScope("[ { Mary } ] [ used her workstation ]");

    checkScope("[ { Mickey } ] [ is a large animal ]");
    checkScope("[ { Mickey } ] [ is a large mouse ]");
    checkScope("[ { Mickey } ] [ is a small animal ]");
    checkScope("[ { Mickey } ] [ is larger than Dumbo ]");
    checkScope("[ { Mickey } ] [ is smaller than Dumbo ]");

    checkScope("[ { Pavarotti } ] [ is a leading tenor who comes cheap ]");

    checkScope("[ { Tuesday } ] [ is not good for me ] .");
  }

  @Test
  public void fracasSentencesWithAtMostAtLeast() {
    checkScope("{ At least three } [ commissioners ] [ spend a lot of time at home ]");
    checkScope("{ At least three } [ commissioners ] [ spend time at home ]");
    //checkScope("{ At least three } [ female commissioners ] [ spend time at home ]");
    //checkScope("{ At least three } [ male commissioners ] [ spend time at home ]");
    checkScope("{ At least three } [ tenors ] [ will take part in the concert ]");
    checkScope("{ At most ten } [ commissioners ] [ spend a lot of time at home ]");
    checkScope("{ At most ten } [ commissioners ] [ spend time at home ]");
    checkScope("{ At most ten } [ female commissioners ] [ spend time at home ]");

    // TODO: can't get the parser to recognize "Just one NN"
    //checkScope("{ Just one } [ accountant ] [ attended the meeting ]");
  }

  @Test
  public void fracasSentencesWithPureNumbers() {
    checkScope("{ Eight } [ machines ] [ have been removed ]");

    checkScope("{ Five } [ men ] [ work part time ]");
    checkScope("{ Forty five } [ women ] [ work part time ]");

    checkScope("{ Six } [ accountants ] [ signed the contract ]");
    checkScope("{ Six } [ lawyers ] [ signed the contract ]");

    checkScope("{ Ten } [ machines ] [ were here yesterday ]");
    checkScope("{ Twenty } [ men ] [ work in the Sales Department ]");
    checkScope("{ Two } [ machines ] [ have been removed ]");
    checkScope("{ Two } [ women ] [ work in the Sales Department ]");
  }

  @Test
  public void fracasSentencesWithBoth() {
    checkScope("{ Both } [ commissioners ] [ used to be businessmen ]");
    checkScope("{ Both } [ commissioners ] [ used to be leading businessmen ]");
    checkScope("{ Both } [ leading tenors ] [ are excellent ]");
    checkScope("{ Both } [ leading tenors ] [ are indispensable ]");
  }

  @Test
  public void fracasSentencesWithMany() {
    checkScope("{ Many } [ British delegates ] [ obtained interesting results from the survey ]");
    checkScope("{ Many } [ delegates ] [ obtained interesting results from the survey ]");
    checkScope("{ Many } [ delegates ] [ obtained results from the survey ]");
    checkScope("{ Many } [ great tenors ] [ are German ]");
  }

  @Test
  public void fracasSentencesWithMost() {
    checkScope("{ Most } [ Europeans ] [ can travel freely within Europe ]");
    checkScope("{ Most } [ Europeans who are resident in Europe ] [ can travel freely within Europe ]");
    checkScope("{ Most } [ Europeans who are resident outside Europe ] [ can travel freely within Europe ]");
    checkScope("{ Most } [ clients at the demonstration ] [ were impressed by the system 's performance ]");
    // TODO: latest parser is parsing this whole sentence as an NP
    // checkScope("{ Most } [ companies that own a computer ] [ have a service contract for it ]");
    checkScope("{ Most } [ great tenors ] [ are Italian ]");
  }

  @Test
  public void fracasSentencesWithNeither() {
    checkScope("{ Neither } [ commissioner ] [ spends a lot of time at home ]");
    checkScope("{ Neither } [ commissioner ] [ spends time at home ]");
    checkScope("{ Neither } [ leading tenor ] [ comes cheap ]");
  }

  @Test
  public void fracasSentencesWithBinaryNo() {
    checkScope("{ No } [ Scandinavian delegate ] [ finished the report on time ]");
    checkScope("{ No } [ accountant ] [ attended the meeting ]");
    checkScope("{ No } [ accountants ] [ attended the meeting ]");
    checkScope("{ No } [ delegate ] [ finished the report ]");
    checkScope("{ No } [ really great tenors ] [ are modest ]");
    checkScope("{ No } [ representative ] [ took less than half a day to read the report ]");
    checkScope("{ No } [ student ] [ used her workstation ]");
    checkScope("{ No } [ two representatives ] [ have read it at the same time ]");
    checkScope("{ No } [ delegate ] [ finished the report on time ]");
  }

  @Test
  public void fracasSentencesWithBinaryNoOne() {
    // Ignore "no one" for now.
//    checkScope("{ No one } [ can gamble ] [ when he is broke ]");  // interesting: subject object reversal (we of course don't actually get this...)
//    checkScope("{ No one } [ gambling seriously ] [ stops until he is broke ]");
//    checkScope("{ No one } [ who starts gambling seriously ] [ stops until he is broke ]");

    checkScope("{ Nobody } [ who is asleep ] [ ever knows that he is asleep ]");
  }


  @Test
  public void temporalRegressions() {
    checkScope("[ I ] can { not } [ make tomorrow ]");  // was missing the nmod:tmod on GEN_PREP
    checkScope("Anytime { but } [ next Tuesday ]");     // @see implicitNegationsBut
    checkScope("Anytime { except } [ next Tuesday ]");  // @see implicitNegationsExcept
    checkScope("{ not } [ on Tuesday ]");
  }


  @Test
  public void implicitNegationsBut() {
    checkScope("Anytime { but } [ next Tuesday ]");
    checkScope("food but { not } [ water ]");
  }


  @Test
  public void implicitNegationsExcept() {
    checkScope("I like everything , { except } [ cabbage ]");
    checkScope("Anytime { except } [ next Tuesday ]");
  }


  @Test
  public void doubleNegatives() {
    // TODO: the parse for this one looks correct, but "not" is marked the head.
    // Shouldn't "Tuesday" be the head?
    // checkScope("No, { not } [ Tuesday ]");

    // "No" is now marked as UH and excluded from the "I"
    checkScope("No , [ I ] can { not } [ do Tuesday ]");
    checkScope("No [ I ] can { not } [ do Tuesday ]");
  }


  @Test
  public void binaryNegationOutOfOrder() {
//    checkScope("Can { not } do [ Tuesday ]");  // TODO(gabor) This is a strange one...
    checkScope("[ Tuesday ] will { not } [ work ]");
    checkScope("[ Cats ] are { not } [ fluffy ]");
    checkScope("[ Tuesday ] is { not } [ good for me ] .");
  }


}
