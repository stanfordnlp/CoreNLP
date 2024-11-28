package edu.stanford.nlp.trees.ud;

import static org.junit.Assert.*;
import org.junit.Test;

import edu.stanford.nlp.semgraph.SemanticGraph;

public class EnglishMWTCombinerTest {

  static final String newline = System.getProperty("line.separator");
  public static final String expectedITS = String.join(newline,
                                                       "1-2	it's	_	_	_	_	_	_	_	_",
                                                       "1	it	_	_	_	_	4	nsubj	_	_",
                                                       "2	's	_	_	_	_	4	cop	_	_",
                                                       "3	yours	_	_	_	_	4	advmod	_	_",
                                                       "4	yours	_	_	_	_	0	root	_	_",
                                                       "5	!	_	_	_	_	4	punct	_	_");

  public static final String expectedCANNOT = String.join(newline,
                                                          "1	I	_	_	_	_	4	nsubj	_	_",
                                                          "2-3	CANNOT	_	_	_	_	_	_	_	_",
                                                          "2	CAN	_	_	_	_	4	aux	_	_",
                                                          "3	NOT	_	_	_	_	4	advmod	_	_",
                                                          "4	believe	_	_	_	_	0	root	_	_",
                                                          "5	it	_	_	_	_	4	obj	_	_",
                                                          "6	!	_	_	_	_	4	punct	_	_");

  public static final String expectedWANNA = String.join(newline,
                                                         "1	I	_	_	_	_	2	nsubj	_	_",
                                                         "2-3	wanna	_	_	_	_	_	_	_	_",
                                                         "2	wan	want	_	_	_	0	root	_	_",
                                                         "3	na	to	_	_	_	4	mark	_	_",
                                                         "4	fix	_	_	_	_	2	xcomp	_	_",
                                                         "5	this	_	_	_	_	4	obj	_	_");

  @Test
  public void testMWT() {
    CoNLLUDocumentWriter writer = new CoNLLUDocumentWriter();
    EnglishMWTCombiner combiner = new EnglishMWTCombiner();

    SemanticGraph sg = SemanticGraph.valueOf("[yours-4 nsubj> it-1 cop> 's-2 advmod> yours-3 punct> !-5]");
    sg.getNodeByIndexSafe(1).setAfter("");
    sg = combiner.combineMWTs(sg);
    String result = writer.printSemanticGraph(sg);
    assertEquals(expectedITS, result.trim());

    sg = SemanticGraph.valueOf("[believe-4 nsubj> I-1 aux> CAN-2 advmod> NOT-3 obj> it-5 punct> !-6]");
    sg.getNodeByIndexSafe(2).setAfter("");
    sg = combiner.combineMWTs(sg);
    result = writer.printSemanticGraph(sg);
    assertEquals(expectedCANNOT, result.trim());

    sg = SemanticGraph.valueOf("[wan-2 nsubj> I-1 xcomp> [fix-4 mark> na-3 obj> this-5]]");
    sg.getNodeByIndexSafe(2).setAfter("");
    sg = combiner.combineMWTs(sg);
    result = writer.printSemanticGraph(sg);
    assertEquals(expectedWANNA, result.trim());
  }
}
