package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.util.IETestUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * A bunch of tests of the question to statement translator, taken from the
 * webquestions training set (http://www-nlp.stanford.edu/software/sempre/).
 *
 * @author Gabor Angeli
 */
public class QuestionToStatementTranslatorTest {

  private final QuestionToStatementTranslator instance;

  public QuestionToStatementTranslatorTest() {
    instance = new QuestionToStatementTranslator();
  }

  @Test
  public void canInitialize() { }

  private void check(String input, String output) {
    List<List<CoreLabel>> results = instance.toStatement(IETestUtils.parseSentence(input));
    assertTrue(results.size() > 0);
    assertEquals(output,
        StringUtils.join(results.get(0).stream().map(CoreLabel::word), " "));
  }

  @Test
  public void parseWhatIs() {
    check(
        "what/WP is/VBZ the/DT name/NN of/PRP justin/NNP bieber/NNP brother/NN ?",
        "justin bieber brother is thing");
    check(
        "what/WP is/VBZ nina/NNP dobrev/NNP nationality/NN ?",
        "nina dobrev nationality is thing");
    check(
        "what/WP is/VBZ the/DT president/NN of/PRP brazil/NNP ?",
        "the president of brazil is thing");
    check(
        "what/WP is/VBZ saint/NNP nicholas/NNP known/VBN for/PRP ?",
        "saint nicholas is known for thing");
    check(
        "what/WP is/VBZ cher/NNP 's/POS son/NN 's/POS name/NN ?",
        "cher 's son 's name is thing");
    check(
        "what/WP is/VBZ martin/NNP cooper/NNP doing/VBZ now/RB ?",
        "martin cooper is now doing thing");
    check(
        "what/WP is/VBZ medicare/NN a/NN ?",
        "medicare a is thing");
    check(
        "what/WP is/VBZ the/DT name/NN of/PRP the first harry potter novel ?",
        "the first harry potter novel is thing");
    check(
        "what/WP is/VBZ the/DT first book sherlock holmes appeared in ?",
        "the first book sherlock holmes appeared in is thing");
    check(
        "what/WP is/VBZ charles/NN darwin/NN famous/JJ for/PRP ?",
        "charles darwin is famous for thing");
    check(
        "what/WP is/VBZ henry/NNP clay/NNP known/VBN for/PRP ?",
        "henry clay is known for thing");
    check(
        "what/WP is/VBZ the/DT money/NN in/IN spain/NNP called/VBN ?",
        "the money in spain is called thing");
    check(
        "what/WP is/VBZ the/DT name/NN of/PRP the pittsburgh steelers head coach ?",
        "the pittsburgh steelers head coach is thing");
    check(
        "what/WP is/VBZ james/NNP madison/NNP most/RBS famous/JJ for/PRP ?",
        "james madison is most famous for thing");
    check(
        "what/WP is/VBZ the/DT china/NNP money/NN called/VBN ?",
        "the china money is called thing");
    check(
        "what/WP is/VBZ john/NNP steinbeck/NNP best/RB known/VBN for/IN ?",
        "john steinbeck is best known for thing");
    check(
        "what/WP is/VBZ st/NNP francis/NNP the/DT patron/JJ saint/NN of/IN ?",
        "st francis is the patron saint of thing");
    check(
        "what/WP is/VBZ daniel/NNP radcliffe/NNP name/NN in/IN the/DT woman/NN in/IN black/NN ?",
        "daniel radcliffe name in the woman in black is thing");
    check(
        "what/WP island/NN is/VBZ bethany/NNP hamilton/NNP from/IN ?",
        "bethany hamilton is from island");
    check(
        "what/WP is/VBZ the/DT senate/NN responsible/JJ for/IN ?",
        "the senate is responsible for thing");
  }

  @Test
  public void parseWhatIsThere() {
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN laredo/NNP tx/NNP ?",
        "there is thing to do in laredo tx");
    check(
        "what/WP is/VBZ there/RB to/TO see/VB in/IN barcelona/NNP ?",
        "there is thing to see in barcelona");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN gatlinburg/NNP in/IN december/NNP ?",
        "there is thing to do in gatlinburg in december");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN mt/NNP baldy/NNP california/NNP ?",
        "there is thing to do in mt baldy california");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB around/IN austin/NNP texas/NNP ?",
        "there is thing to do around austin texas");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN niagara/NNP falls/NNP new/NNP york/NNP ?",
        "there is thing to do in niagara falls new york");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN palm/NNP springs/NNP ?",
        "there is thing to do in palm springs");
    check(
        "what/WP is/VBZ there/RB to/TO see/VB near/IN the/DT grand/NNP canyon/NNP ?",
        "there is thing to see near the grand canyon");
    check(
        "what/WP is/VBZ there/RB for/IN kids/NN to/TO do/VB in/IN miami/NNP ?",
        "there is thing for kids to do in miami");
    check(
        "what/WP is/VBZ there/RB fun/NN to/TO do/VB in/IN san/NNP diego/NNP ?",
        "there is fun thing to do in san diego");
    check(
        "what/WP is/VBZ there/RB to/TO do/VB in/IN montpelier/NNP vt/NNP ?",
        "there is thing to do in montpelier vt");
  }

  @Test
  public void parseWhereDo() {
    check(
        "where/WRB did/VBD saki/NNP live/VB ?",
        "saki live at location");
    check(
        "where/WRB did/VBD dmitri/NNP mendeleev/NNP study/VB science/NN ?",
        "dmitri mendeleev study science at location");
    check(
        "where/WRB did/VBD boston/NNP terriers/NNP come/VBP from/IN ?",
        "boston terriers come from location");
    check(
        "where/WRB did/VBD madoff/NNP live/VP in/IN nyc/NNP ?",
        "madoff live in location , nyc");
    check(
        "where/WRB did/VBD kaiser/NNP wilhelm/NNP fled/VBD to/IN ?",
        "kaiser wilhelm fled to location");
  }

  @Test
  public void parseWhereIs() {
    check(
        "where/WRB is/VBD jack/NNP daniels/NNP factory/NN ?",
        "jack daniels factory is at location");
    check(
        "where/WRB is/VBD rome/NNP italy/VB located/VBD on/IN a/DT map/NN ?",
        "rome italy is at location");
  }
}
