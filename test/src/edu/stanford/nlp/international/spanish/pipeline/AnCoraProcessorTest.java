package edu.stanford.nlp.international.spanish.pipeline;

import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.Pair;
import junit.framework.TestCase;

public class AnCoraProcessorTest extends TestCase {

  private Tree t1;
  private Tree t1First, t1Second;

  private Tree t2;
  private Tree t2First, t2Intermediate, t2Second, t2Third;

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
                               "          (grup.verb (vaip000 Hay))\n" +
                               "          (sn\n" +
                               "            (spec (dn0000 tres))\n" +
                               "            (grup.nom (nc0p000 ciclistas)\n" +
                               "              (S\n" +
                               "                (relatiu (pr000000 que))\n" +
                               "                (grup.verb (vmip000 están))\n" +
                               "                (sp\n" +
                               "                  (prep\n" +
                               "                    (grup.prep (sp000 por) (rg encima) (sp000 de)))\n" +
                               "                  (sn\n" +
                               "                    (spec (da0000 los))\n" +
                               "                    (grup.nom (pi000000 demás))))))))))\n" +
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

    t2 = Trees.readTree("(ROOT\n" +
                          "  (sentence\n" +
                          "    (S (fe \")\n" +
                          "      (S\n" +
                          "        (neg (rn No))\n" +
                          "        (grup.verb (vmip000 quiero))\n" +
                          "        (S\n" +
                          "          (sadv\n" +
                          "            (grup.adv (rg ni)))\n" +
                          "          (infinitiu (vmn0000 pensar))\n" +
                          "          (sp\n" +
                          "            (prep (sp000 en))\n" +
                          "            (sn\n" +
                          "              (grup.nom (pd000000 eso))))))\n" +
                          "      (fp .)\n" +
                          "      (S\n" +
                          "        (S\n" +
                          "          (sn\n" +
                          "            (spec (da0000 Lo))\n" +
                          "            (grup.nom\n" +
                          "              (s.a\n" +
                          "                (grup.a (aq0000 lógico)))))\n" +
                          "          (grup.verb (vsic000 sería))\n" +
                          "          (S\n" +
                          "            (conj (cs que))\n" +
                          "            (grup.verb (vmsi000 estuviera))\n" +
                          "            (sadv\n" +
                          "              (grup.adv (rg cerca)\n" +
                          "                (sp\n" +
                          "                  (prep (sp000 de))\n" +
                          "                  (sn\n" +
                          "                    (spec (da0000 el))\n" +
                          "                    (grup.nom (nc0s000 hospital)))))))\n" +
                          "          (fc ,))\n" +
                          "        (conj (cc pero))\n" +
                          "        (S\n" +
                          "          (sadv\n" +
                          "            (grup.adv (rg también)))\n" +
                          "          (grup.verb (vaip000 hay) (cs que)\n" +
                          "            (infinitiu (vmn0000 mirar)))\n" +
                          "          (sn\n" +
                          "            (spec (da0000 el))\n" +
                          "            (grup.nom (nc0s000 lado)\n" +
                          "              (s.a\n" +
                          "                (grup.a (aq0000 humano)))))))\n" +
                          "      (fp .)\n" +
                          "      (S\n" +
                          "        (S\n" +
                          "          (sn\n" +
                          "            (grup.nom (pp000000 Él)))\n" +
                          "          (sadv\n" +
                          "            (grup.adv (rg aquí)))\n" +
                          "          (grup.verb (vmip000 está))\n" +
                          "          (S\n" +
                          "            (participi (aq0000 rodeado))\n" +
                          "            (sp\n" +
                          "              (prep (sp000 de))\n" +
                          "              (sn\n" +
                          "                (spec (dp0000 su))\n" +
                          "                (grup.nom (nc0s000 gente))))))\n" +
                          "        (fc ,)\n" +
                          "        (S\n" +
                          "          (sn\n" +
                          "            (spec (da0000 el))\n" +
                          "            (grup.nom (nc0s000 aire)))\n" +
                          "          (grup.verb (vsip000 es))\n" +
                          "          (s.a\n" +
                          "            (grup.a (aq0000 bueno))))\n" +
                          "        (conj (cc y))\n" +
                          "        (S\n" +
                          "          (morfema.verbal (p0000000 se))\n" +
                          "          (grup.verb (vmip000 respira))\n" +
                          "          (sn\n" +
                          "            (grup.nom (nc0s000 tranquilidad)))))\n" +
                          "      (fe \") (fc ,))\n" +
                          "    (grup.verb (vmis000 dijo))\n" +
                          "    (fp .)))");

    t2First = Trees.readTree("(ROOT\n" +
                               "  (sentence\n" +
                               "    (S (fe \")\n" +
                               "      (S\n" +
                               "        (neg (rn No))\n" +
                               "        (grup.verb (vmip000 quiero))\n" +
                               "        (S\n" +
                               "          (sadv\n" +
                               "            (grup.adv (rg ni)))\n" +
                               "          (infinitiu (vmn0000 pensar))\n" +
                               "          (sp\n" +
                               "            (prep (sp000 en))\n" +
                               "            (sn\n" +
                               "              (grup.nom (pd000000 eso)))))))\n" +
                               "    (fp .)))");

    t2Intermediate = Trees.readTree("(ROOT\n" +
                                      "  (sentence\n" +
                                      "    (S\n" +
                                      "      (S\n" +
                                      "        (S\n" +
                                      "          (sn\n" +
                                      "            (spec (da0000 Lo))\n" +
                                      "            (grup.nom\n" +
                                      "              (s.a\n" +
                                      "                (grup.a (aq0000 lógico)))))\n" +
                                      "          (grup.verb (vsic000 sería))\n" +
                                      "          (S\n" +
                                      "            (conj (cs que))\n" +
                                      "            (grup.verb (vmsi000 estuviera))\n" +
                                      "            (sadv\n" +
                                      "              (grup.adv (rg cerca)\n" +
                                      "                (sp\n" +
                                      "                  (prep (sp000 de))\n" +
                                      "                  (sn\n" +
                                      "                    (spec (da0000 el))\n" +
                                      "                    (grup.nom (nc0s000 hospital)))))))\n" +
                                      "          (fc ,))\n" +
                                      "        (conj (cc pero))\n" +
                                      "        (S\n" +
                                      "          (sadv\n" +
                                      "            (grup.adv (rg también)))\n" +
                                      "          (grup.verb (vaip000 hay) (cs que)\n" +
                                      "            (infinitiu (vmn0000 mirar)))\n" +
                                      "          (sn\n" +
                                      "            (spec (da0000 el))\n" +
                                      "            (grup.nom (nc0s000 lado)\n" +
                                      "              (s.a\n" +
                                      "                (grup.a (aq0000 humano)))))))\n" +
                                      "      (fp .)\n" +
                                      "      (S\n" +
                                      "        (S\n" +
                                      "          (sn\n" +
                                      "            (grup.nom (pp000000 Él)))\n" +
                                      "          (sadv\n" +
                                      "            (grup.adv (rg aquí)))\n" +
                                      "          (grup.verb (vmip000 está))\n" +
                                      "          (S\n" +
                                      "            (participi (aq0000 rodeado))\n" +
                                      "            (sp\n" +
                                      "              (prep (sp000 de))\n" +
                                      "              (sn\n" +
                                      "                (spec (dp0000 su))\n" +
                                      "                (grup.nom (nc0s000 gente))))))\n" +
                                      "        (fc ,)\n" +
                                      "        (S\n" +
                                      "          (sn\n" +
                                      "            (spec (da0000 el))\n" +
                                      "            (grup.nom (nc0s000 aire)))\n" +
                                      "          (grup.verb (vsip000 es))\n" +
                                      "          (s.a\n" +
                                      "            (grup.a (aq0000 bueno))))\n" +
                                      "        (conj (cc y))\n" +
                                      "        (S\n" +
                                      "          (morfema.verbal (p0000000 se))\n" +
                                      "          (grup.verb (vmip000 respira))\n" +
                                      "          (sn\n" +
                                      "            (grup.nom (nc0s000 tranquilidad)))))\n" +
                                      "      (fe \") (fc ,))\n" +
                                      "    (grup.verb (vmis000 dijo))\n" +
                                      "    (fp .)))");

    t2Second = Trees.readTree("  (ROOT\n" +
                                "    (sentence\n" +
                                "      (S\n" +
                                "        (S\n" +
                                "          (sn\n" +
                                "            (spec (da0000 Lo))\n" +
                                "            (grup.nom\n" +
                                "              (s.a\n" +
                                "                (grup.a (aq0000 lógico)))))\n" +
                                "          (grup.verb (vsic000 sería))\n" +
                                "          (S\n" +
                                "            (conj (cs que))\n" +
                                "            (grup.verb (vmsi000 estuviera))\n" +
                                "            (sadv\n" +
                                "              (grup.adv (rg cerca)\n" +
                                "                (sp\n" +
                                "                  (prep (sp000 de))\n" +
                                "                  (sn\n" +
                                "                    (spec (da0000 el))\n" +
                                "                    (grup.nom (nc0s000 hospital)))))))\n" +
                                "          (fc ,))\n" +
                                "        (conj (cc pero))\n" +
                                "        (S\n" +
                                "          (sadv\n" +
                                "            (grup.adv (rg también)))\n" +
                                "          (grup.verb (vaip000 hay) (cs que)\n" +
                                "            (infinitiu (vmn0000 mirar)))\n" +
                                "          (sn\n" +
                                "            (spec (da0000 el))\n" +
                                "            (grup.nom (nc0s000 lado)\n" +
                                "              (s.a\n" +
                                "                (grup.a (aq0000 humano)))))))\n" +
                                "      (fp .)))");

    t2Third = Trees.readTree("(ROOT\n" +
                               "  (sentence\n" +
                               "    (S\n" +
                               "      (S\n" +
                               "        (S\n" +
                               "          (sn\n" +
                               "            (grup.nom (pp000000 Él)))\n" +
                               "          (sadv\n" +
                               "            (grup.adv (rg aquí)))\n" +
                               "          (grup.verb (vmip000 está))\n" +
                               "          (S\n" +
                               "            (participi (aq0000 rodeado))\n" +
                               "            (sp\n" +
                               "              (prep (sp000 de))\n" +
                               "              (sn\n" +
                               "                (spec (dp0000 su))\n" +
                               "                (grup.nom (nc0s000 gente))))))\n" +
                               "        (fc ,)\n" +
                               "        (S\n" +
                               "          (sn\n" +
                               "            (spec (da0000 el))\n" +
                               "            (grup.nom (nc0s000 aire)))\n" +
                               "          (grup.verb (vsip000 es))\n" +
                               "          (s.a\n" +
                               "            (grup.a (aq0000 bueno))))\n" +
                               "        (conj (cc y))\n" +
                               "        (S\n" +
                               "          (morfema.verbal (p0000000 se))\n" +
                               "          (grup.verb (vmip000 respira))\n" +
                               "          (sn\n" +
                               "            (grup.nom (nc0s000 tranquilidad)))))\n" +
                               "      (fe \") (fc ,))\n" +
                               "    (grup.verb (vmis000 dijo))\n" +
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
    assertEquals(new Pair<Tree, Tree>(t1, null), AnCoraProcessor.split(temp, null));
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

  public void testSplitThreeSentences() {
    // Split into 1, 2+3, then split 2+3 into 2, 3
    Tree temp = t2.deepCopy();
    Tree splitPoint = AnCoraProcessor.findSplitPoint(temp);
    Pair<Tree, Tree> expectedSplit = new Pair<Tree, Tree>(t2First, t2Intermediate);
    Pair<Tree, Tree> split = AnCoraProcessor.split(temp, splitPoint);

    assertEquals(expectedSplit.first(), split.first());
    assertEquals(expectedSplit.second(), split.second());

    temp = t2Intermediate.deepCopy();
    splitPoint = AnCoraProcessor.findSplitPoint(temp);
    expectedSplit = new Pair<Tree, Tree>(t2Second, t2Third);
    split = AnCoraProcessor.split(temp, splitPoint);

    assertEquals(expectedSplit.first(), split.first());
    assertEquals(expectedSplit.second(), split.second());
  }
}