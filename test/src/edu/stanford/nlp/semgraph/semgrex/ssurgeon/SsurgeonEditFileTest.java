package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.semgraph.SemanticGraph;

/** Tests reading Ssurgeon patterns from the plain text edit format, {@link Ssurgeon#readFromEditText} */
public class SsurgeonEditFileTest {

  @Test
  public void readEditText() {
    String text = String.join("\n",
                              "",
                              "# only a comment, so this block is skipped",
                              "",
                              "",
                              "# que as a subordinating conjunction",
                              "{word:/(?i:que)/;cpos:CCONJ}=que <mark {}",
                              "EditNode -node que -cpos SCONJ",
                              "   ",
                              "{word:/(?i:que)/;cpos:CCONJ}=que <obj {}",
                              "# a comment in the middle of a block",
                              "  EditNode -node que -cpos PRON",
                              "EditNode -node que -morphofeatures PronType=Rel",
                              "");
    List<SsurgeonPattern> patterns = Ssurgeon.readFromEditText(text, "UniversalEnglish");
    assertEquals(2, patterns.size());

    // UIDs count the skipped block
    assertEquals("2", patterns.get(0).getUID());
    assertEquals("que as a subordinating conjunction", patterns.get(0).getNotes());
    assertEquals(Language.UniversalEnglish, patterns.get(0).getLanguage());
    assertEquals(1, patterns.get(0).getEditScript().size());

    assertEquals("3", patterns.get(1).getUID());
    assertEquals("a comment in the middle of a block", patterns.get(1).getNotes());
    assertEquals(2, patterns.get(1).getEditScript().size());
  }

  @Test
  public void readEditTextWindowsLineEndings() {
    String text = "{}=a >obj=e {}\r\nRelabelNamedEdge -edge e -reln nsubj\r\n\r\n{}=b\r\nEditNode -node b -lemma foo\r\n";
    List<SsurgeonPattern> patterns = Ssurgeon.readFromEditText(text, "UniversalEnglish");
    assertEquals(2, patterns.size());
    assertEquals(1, patterns.get(0).getEditScript().size());
    assertEquals(1, patterns.get(1).getEditScript().size());
  }

  @Test
  public void readEditTextErrors() {
    // unknown language
    assertThrows(SsurgeonParseException.class, () -> Ssurgeon.readFromEditText("{}=a\nEditNode -node a -lemma foo", "Klingon"));
    // the semgrex is not a semgrex
    assertThrows(SsurgeonParseException.class, () -> Ssurgeon.readFromEditText("{}=a >obl:tmod {}", "UniversalEnglish"));
    // RelabelNamedEdge with an edge the semgrex never names
    assertThrows(SsurgeonParseException.class, () -> Ssurgeon.readFromEditText("{}=a >obj {}\nRelabelNamedEdge -edge e -reln nsubj", "UniversalEnglish"));
  }

  @Test
  public void applyEditText() {
    List<SsurgeonPattern> patterns = Ssurgeon.readFromEditText("{}=a >obj=e {}\nRelabelNamedEdge -edge e -reln nsubj", "UniversalEnglish");
    SemanticGraph graph = SemanticGraph.valueOf("[ate-2 nsubj> Bill-1 obj> muffins-3]");
    SemanticGraph edited = patterns.get(0).iterate(graph).first();
    assertEquals(SemanticGraph.valueOf("[ate-2 nsubj> Bill-1 nsubj> muffins-3]"), edited);
  }
}
