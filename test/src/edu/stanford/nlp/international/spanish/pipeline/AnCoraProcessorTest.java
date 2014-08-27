package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;

public class AnCoraProcessorTest extends TestCase {

  private Tree t1;
  private Tree t1First, t1Second;

  @Override
  public void setUp() {
    t1 = Trees.readTree("(ROOT\n" +
                          "  (sentence\n" +
                          "    (S\n" +
                          "      (sn\n" +
                          "        (grup.nom\n" +
                          "          (grup.nom (np00000 Walter) (np00000 Goodefrot))\n" +
                          "          (sn (fc ,)\n" +
                          "            (spec (dp0000 su))\n" +
                          "            (grup.nom (nc0s000 director)\n" +
                          "              (sp\n" +
                          "                (prep (sp000 en))\n" +
                          "                (sn\n" +
                          "                  (spec (da0000 el))\n" +
                          "                  (grup.nom (np00000 Telekom)))))\n" +
                          "            (fc ,))))\n" +
                          "      (morfema.pronominal (p0000000 se))\n" +
                          "      (grup.verb (vmii000 mostraba))\n" +
                          "      (s.a\n" +
                          "        (grup.a (aq0000 optimista)\n" +
                          "          (sp\n" +
                          "            (prep (sp000 por))\n" +
                          "            (sn\n" +
                          "              (spec (da0000 la))\n" +
                          "              (grup.nom (nc0s000 forma)\n" +
                          "                (sp\n" +
                          "                  (prep (sp000 de))\n" +
                          "                  (sn\n" +
                          "                    (spec (dp0000 su))\n" +
                          "                    (grup.nom (nc0s000 pupilo)))))))))\n" +
                          "      (fc ,))\n" +
                          "    (conj (cc pero))\n" +
                          "    (S\n" +
                          "      (S\n" +
                          "        (sadv\n" +
                          "          (grup.adv\n" +
                          "            (sp\n" +
                          "              (prep (sp000 a))\n" +
                          "              (sn\n" +
                          "                (spec (da0000 la))\n" +
                          "                (grup.nom (nc0s000 vez))))))\n" +
                          "        (grup.verb (vmii000 afirmaba))\n" +
                          "        (S\n" +
                          "          (conj (cs que))\n" +
                          "          (sn\n" +
                          "            (spec (da0000 la))\n" +
                          "            (grup.nom (nc0s000 competencia)))\n" +
                          "          (grup.verb (vmii000 iba) (sp000 a)\n" +
                          "            (infinitiu (vsn0000 ser)))\n" +
                          "          (S\n" +
                          "            (sadv\n" +
                          "              (grup.adv (rg muy)))\n" +
                          "            (participi (aq0000 reñida)))))\n" +
                          "      (S (fd :) (fe \")\n" +
                          "        (S\n" +
                          "          (S\n" +
                          "            (grup.verb (vaip000 Hay))\n" +
                          "            (sn\n" +
                          "              (spec (dn0000 tres))\n" +
                          "              (grup.nom (nc0p000 ciclistas)\n" +
                          "                (S\n" +
                          "                  (relatiu (pr000000 que))\n" +
                          "                  (grup.verb (vmip000 están))\n" +
                          "                  (sp\n" +
                          "                    (prep\n" +
                          "                      (grup.prep (sp000 por) (rg encima) (sp000 de)))\n" +
                          "                    (sn\n" +
                          "                      (spec (da0000 los))\n" +
                          "                      (grup.nom (pi000000 demás))))))))\n" +
                          "          (fp .)\n" +
                          "          (S\n" +
                          "            (grup.verb (vsip000 Son))\n" +
                          "            (sn\n" +
                          "              (grup.nom\n" +
                          "                (grup.nom (np00000 Armstrong))\n" +
                          "                (fc ,)\n" +
                          "                (grup.nom (np00000 Pantani))\n" +
                          "                (conj (cc y))\n" +
                          "                (grup.nom (np00000 Ulrich)))))\n" +
                          "          (fc ,))\n" +
                          "        (conj (cc pero))\n" +
                          "        (S\n" +
                          "          (sn\n" +
                          "            (spec (da0000 las))\n" +
                          "            (grup.nom (nc0p000 fuerzas)))\n" +
                          "          (grup.verb (vmif000 estarán))\n" +
                          "          (S\n" +
                          "            (participi (aq0000 igualadas))))\n" +
                          "        (fe \")))\n" +
                          "    (fp .)))");

    t1First = Trees.readTree("(ROOT\n" +
                               "  (sentence\n" +
                               "    (S\n" +
                               "      (sn\n" +
                               "        (grup.nom\n" +
                               "          (grup.nom (np00000 Walter) (np00000 Goodefrot))\n" +
                               "          (sn (fc ,)\n" +
                               "            (spec (dp0000 su))\n" +
                               "            (grup.nom (nc0s000 director)\n" +
                               "              (sp\n" +
                               "                (prep (sp000 en))\n" +
                               "                (sn\n" +
                               "                  (spec (da0000 el))\n" +
                               "                  (grup.nom (np00000 Telekom)))))\n" +
                               "            (fc ,))))\n" +
                               "      (morfema.pronominal (p0000000 se))\n" +
                               "      (grup.verb (vmii000 mostraba))\n" +
                               "      (s.a\n" +
                               "        (grup.a (aq0000 optimista)\n" +
                               "          (sp\n" +
                               "            (prep (sp000 por))\n" +
                               "            (sn\n" +
                               "              (spec (da0000 la))\n" +
                               "              (grup.nom (nc0s000 forma)\n" +
                               "                (sp\n" +
                               "                  (prep (sp000 de))\n" +
                               "                  (sn\n" +
                               "                    (spec (dp0000 su))\n" +
                               "                    (grup.nom (nc0s000 pupilo)))))))))\n" +
                               "      (fc ,))\n" +
                               "    (conj (cc pero))\n" +
                               "    (S\n" +
                               "      (S\n" +
                               "        (sadv\n" +
                               "          (grup.adv\n" +
                               "            (sp\n" +
                               "              (prep (sp000 a))\n" +
                               "              (sn\n" +
                               "                (spec (da0000 la))\n" +
                               "                (grup.nom (nc0s000 vez))))))\n" +
                               "        (grup.verb (vmii000 afirmaba))\n" +
                               "        (S\n" +
                               "          (conj (cs que))\n" +
                               "          (sn\n" +
                               "            (spec (da0000 la))\n" +
                               "            (grup.nom (nc0s000 competencia)))\n" +
                               "          (grup.verb (vmii000 iba) (sp000 a)\n" +
                               "            (infinitiu (vsn0000 ser)))\n" +
                               "          (S\n" +
                               "            (sadv\n" +
                               "              (grup.adv (rg muy)))\n" +
                               "            (participi (aq0000 reñida)))))\n" +
                               "      (S (fd :) (fe \")\n" +
                               "        (S\n" +
                               "          (S\n" +
                               "            (grup.verb (vaip000 Hay))\n" +
                               "            (sn\n" +
                               "              (spec (dn0000 tres))\n" +
                               "              (grup.nom (nc0p000 ciclistas)\n" +
                               "                (S\n" +
                               "                  (relatiu (pr000000 que))\n" +
                               "                  (grup.verb (vmip000 están))\n" +
                               "                  (sp\n" +
                               "                    (prep\n" +
                               "                      (grup.prep (sp000 por) (rg encima) (sp000 de)))\n" +
                               "                    (sn\n" +
                               "                      (spec (da0000 los))\n" +
                               "                      (grup.nom (pi000000 demás)))))))))))\n" +
                               "    (fp .)))");

    t1Second = Trees.readTree("  (ROOT\n" +
                                "    (sentence\n" +
                                "      (S\n" +
                                "        (S\n" +
                                "          (S\n" +
                                "            (grup.verb (vsip000 Son))\n" +
                                "            (sn\n" +
                                "              (grup.nom\n" +
                                "                (grup.nom (np00000 Armstrong))\n" +
                                "                (fc ,)\n" +
                                "                (grup.nom (np00000 Pantani))\n" +
                                "                (conj (cc y))\n" +
                                "                (grup.nom (np00000 Ulrich)))))\n" +
                                "          (fc ,))\n" +
                                "        (conj (cc pero))\n" +
                                "        (S\n" +
                                "          (sn\n" +
                                "            (spec (da0000 las))\n" +
                                "            (grup.nom (nc0p000 fuerzas)))\n" +
                                "          (grup.verb (vmif000 estarán))\n" +
                                "          (S\n" +
                                "            (participi (aq0000 igualadas))))\n" +
                                "        (fe \"))\n" +
                                "    (fp .)))");
  }

  public void testFindSplitPoint() {
    Tree splitPoint = AnCoraProcessor.findSplitPoint(t1);
    assertNotNull(splitPoint);
    assertEquals(splitPoint.toString(), "(fp .)");
    assertEquals("Hay tres ciclistas que están por encima de los demás . Son Armstrong , " +
                   "Pantani y Ulrich ,",
                 Sentence.listToString(splitPoint.parent(t1).yield()));
  }

  public void testSplitTrivial() {
    Tree temp = t1.deepCopy();
    assertEquals(new Pair<Tree, Tree>(null, t1), AnCoraProcessor.split(temp, null));
  }

  public void testSplit() {
    Tree temp = t1.deepCopy();
    Tree splitPoint = AnCoraProcessor.findSplitPoint(temp);
    Pair<Tree, Tree> expectedSplit = new Pair<Tree, Tree>(t1First, t1Second);
    Pair<Tree, Tree> split = AnCoraProcessor.split(temp, splitPoint);

    // Easier debugging if we separate these assertions
    assertEquals(expectedSplit.first(), split.first());
    assertEquals(expectedSplit.second(), split.second());
  }
}