package edu.stanford.nlp.semgraph.semgrex;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import junit.framework.Assert;
import junit.framework.TestCase;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;


public class SemgrexPatternTest extends TestCase {

  /*
   * Test method for 'edu.stanford.nlp.semgraph.semgrex.SemgrexPattern.prettyPrint()'
   */
  public void testPrettyPrint() {
    // SemgrexPattern pat = SemgrexPattern.compile("{} >sub {} & ?>ss {w:w}");
    // SemgrexPattern pat =
    // SemgrexPattern.compile("({} </nsubj|agent/ {pos:/VB.*/}=hypVerb) @ ({#} </nsubj|agent/ {pos:/VB.*/}=txtVerb)");

    // SemgrexPattern pat =
    // SemgrexPattern.compile("({sentIndex:4} <=hypReln {sentIndex:2}=hypGov) @ ({} <=hypReln ({} >conj_and ({} @ {}=hypGov)))");
    SemgrexPattern pat = SemgrexPattern
        .compile("({}=partnerOne [[<prep_to ({word:/married/} >nsubjpass {}=partnerTwo)] | [<nsubjpass ({word:married} >prep_to {}=partnerTwo)]]) @ ({} [[>/nn|appos/ {lemma:/wife|husband/} >poss ({}=txtPartner @ {}=partnerTwo)] | [<poss (({}=txtPartner @ {}=partnerTwo) >/appos|nn/ {lemma:/wife|husband/})]])");
    // SemgrexPattern pat =
    // SemgrexPattern.compile("({pos:/VB.*/}=hVerb @ {pos:/VB.*/}=tVerb) >/nsubj|nsubjpass|dobj|iobj|prep.*/=hReln ({}=hWord @ ({}=tWord [ [ >/nsubj|nsubjpass|dobj|iobj|prep.*/=tReln {}=tVerb] | [ >appos ({} >/nsubj|nsubjpass|dobj|iobj|prep.*/=tReln {}=tVerb) ] | [ <appos ({} >/nsubj|nsubjpass|dobj|iobj|prep.*/=tReln {}=tVerb)] | ![> {}=tVerb]]))");
    // SemgrexPattern pat =
    // SemgrexPattern.compile("({}=partnerOne [[<prep_to ({word:married} >nsubjpass {}=partnerTwo)] | [<nsubjpass ({word:married} >prep_to {}=partnerTwo)]]) @ ({} [[>nn {lemma:/wife|husband/} >poss {}=txtPartner] | [<poss ({}=txtPartner >nn {lemma:/wife|husband/})]])");
    // @ ({} </nsubj|agent/ {pos:/VB.*/}=txtVerb)
    pat.prettyPrint();
  }

  public void testFind() throws Exception {
    SemanticGraph h = SemanticGraph.valueOf("[married/VBN nsubjpass:Hughes/NNP auxpass:was/VBD prep_to:Gracia/NNP]");
    SemanticGraph t = SemanticGraph
        .valueOf("[loved/VBD\nnsubj:Hughes/NNP\ndobj:[wife/NN poss:his/PRP$ appos:Gracia/NNP]\nconj_and:[obsessed/JJ\ncop:was/VBD\nadvmod:absolutely/RB\nprep_with:[Elicia/NN poss:his/PRP$ amod:little/JJ nn:daughter/NN]]]");
    String s =
        "(ROOT\n(S\n(NP (DT The) (NN chimney) (NNS sweeps))\n(VP (VBP do) (RB not)\n(VP (VB like)\n(S\n(VP (VBG working)\n(PP (IN on)\n(NP (DT an) (JJ empty) (NN stomach)))))))\n(. .)))";
    Tree tree = Tree.valueOf(s);
    SemanticGraph sg = SemanticGraphFactory.allTypedDependencies(tree, true);
    SemgrexPattern pat = SemgrexPattern.compile("{}=gov ![>det {}] & > {word:/^(?!not).*$/}=dep");
    sg.prettyPrint();
    // SemgrexPattern pat =
    // SemgrexPattern.compile("{} [[<prep_to ({word:married} >nsubjpass {})] | [<nsubjpass ({word:married} >prep_to {})]]");
    pat.prettyPrint();
    SemgrexMatcher mat = pat.matcher(sg);
    while (mat.find()) {
      // String match = mat.getMatch().word();
      String gov = mat.getNode("gov").word();
      // String reln = mat.getRelnString("reln");
      String dep = mat.getNode("dep").word();
      // System.out.println(match);
      System.out.println(dep + ' ' + gov);
    }

    SemgrexPattern pat2 = SemgrexPattern
        .compile("{} [[>/nn|appos/ ({lemma:/wife|husband|partner/} >/poss/ {}=txtPartner)] | [<poss ({}=txtPartner >/nn|appos/ {lemma:/wife|husband|partner/})]"
            + "| [<nsubj ({$} >> ({word:/wife|husband|partner/} >poss {word:/his|her/} >/nn|appos/ {}))]]");
    SemgrexMatcher mat2 = pat2.matcher(t);
    while (mat2.find()) {
      String match = mat2.getMatch().word();
      // String gov = mat.getNode("gov").word();
      // String reln = mat.getRelnString("reln");
      // String dep = mat.getNode("dep").word();
      System.out.println(match);
      // System.out.println(dep + " " + gov);
    }

    HashMap<IndexedWord, IndexedWord> map = new HashMap<IndexedWord, IndexedWord>();
    map.put(h.getNodeByWordPattern("Hughes"), t.getNodeByWordPattern("Hughes"));
    map.put(h.getNodeByWordPattern("Gracia"), t.getNodeByWordPattern("Gracia"));
    Alignment alignment = new Alignment(map, 0, "");

    SemgrexPattern fullPat = SemgrexPattern
        .compile("({}=partnerOne [[<prep_to ({word:married} >nsubjpass {}=partnerTwo)] | [<nsubjpass ({word:married} >prep_to {}=partnerTwo)]]) @ ({} [[>/nn|appos/ ({lemma:/wife|husband|partner/} >/poss/ {}=txtPartner)] | [<poss ({}=txtPartner >/nn|appos/ {lemma:/wife|husband|partner/})]"
            + "| [<nsubj ({$} >> ({word:/wife|husband|partner/} >poss {word:/his|her/} >/nn|appos/ {}=txtPartner))]])");
    fullPat.prettyPrint();

    SemgrexMatcher fullMat = fullPat.matcher(h, alignment, t);
    if (fullMat.find()) {
      System.out.println("woo: " + fullMat.getMatch().word());
      System.out.println(fullMat.getNode("txtPartner"));
      System.out.println(fullMat.getNode("partnerOne"));
      System.out.println(fullMat.getNode("partnerTwo"));

    } else {
      System.out.println("boo");
    }

    SemgrexPattern pat3 = SemgrexPattern
        .compile("({word:LIKE}=parent >>/aux.*/ {word:/do/}=node)");
    System.out.println("pattern is ");
    pat3.prettyPrint();
    System.out.println("tree is ");
    sg.prettyPrint();

    //checking if ignoring case or not
    SemgrexMatcher mat3 = pat3.matcher(sg, true);
    if (mat3.find()) {
      String parent = mat3.getNode("parent").word();
      String node = mat3.getNode("node").word();
      System.out.println("Result: parent is " + parent + " and node is "  + node);
      Assert.assertEquals(parent, "like");
      Assert.assertEquals(node, "do");

    } else {
      Assert.fail();
    }
  }

  public void testMacro() throws IOException {
    SemanticGraph h = SemanticGraph.valueOf("[married/VBN nsubjpass:Hughes/NNP auxpass:was/VBD prep_to:Gracia/NNP]");
    String macro = "macro WORD = married";
    SemgrexBatchParser parser = new SemgrexBatchParser();
    String pattern = "({word:${WORD}}=parent >>nsubjpass {}=node)";
    List<SemgrexPattern> pats = parser.compileStream(new ByteArrayInputStream((macro + "\n" + pattern).getBytes(StandardCharsets.UTF_8)));
    SemgrexPattern pat3 = pats.get(0);
    boolean ignoreCase = true;
    SemgrexMatcher mat3 = pat3.matcher(h, ignoreCase);
    if (mat3.find()) {
      String parent = mat3.getNode("parent").word();
      String node = mat3.getNode("node").word();
      System.out.println("Result: parent is " + parent + " and node is " + node);
      Assert.assertEquals(parent, "married");
      Assert.assertEquals(node, "Hughes");
    } else
      throw new RuntimeException("failed!");
  }

  public void testEnv() throws IOException {
    SemanticGraph h = SemanticGraph.valueOf("[married/VBN nsubjpass:Hughes/NNP auxpass:was/VBD prep_to:Gracia/NNP]");
    h.getFirstRoot().set(PatternsAnnotations.PatternLabel1.class,"YES");
    //SemanticGraph t = SemanticGraph
    //  .valueOf("[loved/VBD\nnsubj:Hughes/NNP\ndobj:[wife/NN poss:his/PRP$ appos:Gracia/NNP]\nconj_and:[obsessed/JJ\ncop:was/VBD\nadvmod:absolutely/RB\nprep_with:[Elicia/NN poss:his/PRP$ amod:little/JJ nn:daughter/NN]]]");
    String macro = "macro WORD = married";
    SemgrexBatchParser parser = new SemgrexBatchParser();
    Env env = new Env();
    env.bind("pattern1",PatternsAnnotations.PatternLabel1.class);
    String pattern = "({pattern1:YES}=parent >>nsubjpass {}=node)";
    List<SemgrexPattern> pats = parser.compileStream(new ByteArrayInputStream((macro + "\n" + pattern).getBytes(StandardCharsets.UTF_8)), env);
    SemgrexPattern pat3 = pats.get(0);
    boolean ignoreCase = true;
    SemgrexMatcher mat3 = pat3.matcher(h, ignoreCase);
    if (mat3.find()) {
      String parent = mat3.getNode("parent").word();
      String node = mat3.getNode("node").word();
      System.out.println("Result: parent is " + parent + " and node is " + node);
      Assert.assertEquals(parent, "married");
      Assert.assertEquals(node, "Hughes");
    } else
      throw new RuntimeException("failed!");
  }

  public void testSerialization() throws IOException, ClassNotFoundException {
    SemgrexPattern pat3 = SemgrexPattern
      .compile("({word:LIKE}=parent >>nn {word:/do/}=node)");
    File tempfile = File.createTempFile("temp","file");
    tempfile.deleteOnExit();
    IOUtils.writeObjectToFile(pat3, tempfile);
    SemgrexPattern pat4 = IOUtils.readObjectFromFile(tempfile);
    Assert.assertEquals(pat3, pat4);
  }

}
