package edu.stanford.nlp.international.spanish.pipeline;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.international.spanish.SpanishSplitTreeNormalizer;
import edu.stanford.nlp.util.Pair;

/**
 * @author Jon Gauthier
 */
public class AnCoraProcessorITest {

  private static final TreeReaderFactory treeReaderFactory = new LabeledScoredTreeReaderFactory(
        new SpanishSplitTreeNormalizer());

  private Tree t1;
  private Tree t1First, t1Second;

  private Tree t2;
  private Tree t2First, t2Intermediate, t2Second, t2Third;

  private Tree t3;
  private Tree t3First, t3Intermediate, t3Second, t3Third;

  @Before
  public void setUp() {
    t1 = readTree("(ROOT\n" +
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

    t1First = readTree("(ROOT\n" +
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

    t1Second = readTree("  (ROOT\n" +
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

    t2 = readTree("(ROOT\n" +
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

    t2First = readTree("(ROOT\n" +
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

    t2Intermediate = readTree("(ROOT\n" +
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

    t2Second = readTree("  (ROOT\n" +
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

    t2Third = readTree("(ROOT\n" +
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

    t3 = readTree("(ROOT\n" +
                    "  (sentence\n" +
                    "    (S (fe \")\n" +
                    "      (S\n" +
                    "        (S\n" +
                    "          (conj (cs Si))\n" +
                    "          (grup.verb (vmip000 quieren))\n" +
                    "          (sp\n" +
                    "            (prep (sp000 a))\n" +
                    "            (sn\n" +
                    "              (grup.nom (pi000000 alguien)\n" +
                    "                (S\n" +
                    "                  (S\n" +
                    "                    (relatiu (pr000000 que))\n" +
                    "                    (grup.verb (vmsp000 dé))\n" +
                    "                    (sn\n" +
                    "                      (grup.nom (nc0p000 rodeos)\n" +
                    "                        (sp\n" +
                    "                          (prep (sp000 para))\n" +
                    "                          (S\n" +
                    "                            (infinitiu (vmn0000 decir))\n" +
                    "                            (sn\n" +
                    "                              (spec (da0000 las))\n" +
                    "                              (grup.nom (nc0p000 cosas))))))))\n" +
                    "                  (conj (cc y))\n" +
                    "                  (S\n" +
                    "                    (relatiu (pr000000 que))\n" +
                    "                    (grup.verb (vmip000 apoya))\n" +
                    "                    (sp\n" +
                    "                      (prep (sp000 a))\n" +
                    "                      (sn\n" +
                    "                        (spec (da0000 las))\n" +
                    "                        (grup.nom\n" +
                    "                          (s.a\n" +
                    "                            (grup.a (aq0000 grandes)))\n" +
                    "                          (s.a\n" +
                    "                            (grup.a (aq0000 farmacéuticas)))))))))))\n" +
                    "          (fc ,))\n" +
                    "        (sn\n" +
                    "          (grup.nom (pd000000 este)))\n" +
                    "        (grup.verb (vsip000 es))\n" +
                    "        (sn\n" +
                    "          (spec (dp0000 su))\n" +
                    "          (grup.nom (nc0s000 hombre))))\n" +
                    "      (conj (fp .))\n" +
                    "      (S\n" +
                    "        (S\n" +
                    "          (conj (cs Si))\n" +
                    "          (grup.verb (vmip000 quieren))\n" +
                    "          (sn\n" +
                    "            (grup.nom (pi000000 alguien)\n" +
                    "              (S\n" +
                    "                (relatiu (pr000000 que))\n" +
                    "                (grup.verb (vmsp000 defienda))\n" +
                    "                (sn\n" +
                    "                  (spec (dp0000 sus))\n" +
                    "                  (grup.nom (nc0p000 derechos))))))\n" +
                    "          (fd :))\n" +
                    "        (sadv\n" +
                    "          (grup.adv (rg aquí)))\n" +
                    "        (sn\n" +
                    "          (grup.nom (pp000000 me)))\n" +
                    "        (grup.verb (vmip000 tienen)))\n" +
                    "      (conj (fp .))\n" +
                    "      (S\n" +
                    "        (sn\n" +
                    "          (grup.nom (pp000000 Yo)))\n" +
                    "        (grup.verb (vmip000 estoy))\n" +
                    "        (S\n" +
                    "          (participi (aq0000 dispuesto))\n" +
                    "          (sp\n" +
                    "            (prep (sp000 a))\n" +
                    "            (S\n" +
                    "              (infinitiu (vmn0000 hacer))\n" +
                    "              (sn\n" +
                    "                (grup.nom (pp000000 lo)))))))\n" +
                    "      (fe \"))\n" +
                    "    (fc ,)\n" +
                    "    (grup.verb (vmis000 afirmó))\n" +
                    "    (sn\n" +
                    "      (grup.nom (np00000 Al) (np00000 Gore)))\n" +
                    "    (fp .)))");

    t3First = readTree("(ROOT\n" +
                         "  (sentence\n" +
                         "    (S (fe \")\n" +
                         "      (S\n" +
                         "        (S\n" +
                         "          (conj (cs Si))\n" +
                         "          (grup.verb (vmip000 quieren))\n" +
                         "          (sp\n" +
                         "            (prep (sp000 a))\n" +
                         "            (sn\n" +
                         "              (grup.nom (pi000000 alguien)\n" +
                         "                (S\n" +
                         "                  (S\n" +
                         "                    (relatiu (pr000000 que))\n" +
                         "                    (grup.verb (vmsp000 dé))\n" +
                         "                    (sn\n" +
                         "                      (grup.nom (nc0p000 rodeos)\n" +
                         "                        (sp\n" +
                         "                          (prep (sp000 para))\n" +
                         "                          (S\n" +
                         "                            (infinitiu (vmn0000 decir))\n" +
                         "                            (sn\n" +
                         "                              (spec (da0000 las))\n" +
                         "                              (grup.nom (nc0p000 cosas))))))))\n" +
                         "                  (conj (cc y))\n" +
                         "                  (S\n" +
                         "                    (relatiu (pr000000 que))\n" +
                         "                    (grup.verb (vmip000 apoya))\n" +
                         "                    (sp\n" +
                         "                      (prep (sp000 a))\n" +
                         "                      (sn\n" +
                         "                        (spec (da0000 las))\n" +
                         "                        (grup.nom\n" +
                         "                          (s.a\n" +
                         "                            (grup.a (aq0000 grandes)))\n" +
                         "                          (s.a\n" +
                         "                            (grup.a (aq0000 farmacéuticas)))))))))))\n" +
                         "          (fc ,))\n" +
                         "        (sn\n" +
                         "          (grup.nom (pd000000 este)))\n" +
                         "        (grup.verb (vsip000 es))\n" +
                         "        (sn\n" +
                         "          (spec (dp0000 su))\n" +
                         "          (grup.nom (nc0s000 hombre)))))\n" +
                         "    (fp .)))");

    t3Intermediate = readTree("(ROOT\n" +
                                "  (sentence\n" +
                                "    (S\n" +
                                "      (S\n" +
                                "        (S\n" +
                                "          (conj (cs Si))\n" +
                                "          (grup.verb (vmip000 quieren))\n" +
                                "          (sn\n" +
                                "            (grup.nom (pi000000 alguien)\n" +
                                "              (S\n" +
                                "                (relatiu (pr000000 que))\n" +
                                "                (grup.verb (vmsp000 defienda))\n" +
                                "                (sn\n" +
                                "                  (spec (dp0000 sus))\n" +
                                "                  (grup.nom (nc0p000 derechos))))))\n" +
                                "          (fd :))\n" +
                                "        (sadv\n" +
                                "          (grup.adv (rg aquí)))\n" +
                                "        (sn\n" +
                                "          (grup.nom (pp000000 me)))\n" +
                                "        (grup.verb (vmip000 tienen)))\n" +
                                "      (fp .)\n" +
                                "      (S\n" +
                                "        (sn\n" +
                                "          (grup.nom (pp000000 Yo)))\n" +
                                "        (grup.verb (vmip000 estoy))\n" +
                                "        (S\n" +
                                "          (participi (aq0000 dispuesto))\n" +
                                "          (sp\n" +
                                "            (prep (sp000 a))\n" +
                                "            (S\n" +
                                "              (infinitiu (vmn0000 hacer))\n" +
                                "              (sn\n" +
                                "                (grup.nom (pp000000 lo)))))))\n" +
                                "      (fe \"))\n" +
                                "    (fc ,)\n" +
                                "    (grup.verb (vmis000 afirmó))\n" +
                                "    (sn\n" +
                                "      (grup.nom (np00000 Al) (np00000 Gore)))\n" +
                                "    (fp .)))");

    t3Second = readTree("    (ROOT\n" +
                          "      (sentence\n" +
                          "        (S\n" +
                          "          (conj (cs Si))\n" +
                          "          (grup.verb (vmip000 quieren))\n" +
                          "          (sn\n" +
                          "            (grup.nom (pi000000 alguien)\n" +
                          "              (S\n" +
                          "                (relatiu (pr000000 que))\n" +
                          "                (grup.verb (vmsp000 defienda))\n" +
                          "                (sn\n" +
                          "                  (spec (dp0000 sus))\n" +
                          "                  (grup.nom (nc0p000 derechos))))))\n" +
                          "          (fd :))\n" +
                          "        (sadv\n" +
                          "          (grup.adv (rg aquí)))\n" +
                          "        (sn\n" +
                          "          (grup.nom (pp000000 me)))\n" +
                          "        (grup.verb (vmip000 tienen))\n" +
                          "      (fp .)))");

    t3Third = Trees.readTree("(ROOT\n" +
                               "  (sentence\n" +
                               "    (S\n" +
                               "      (S\n" +
                               "        (sn\n" +
                               "          (grup.nom (pp000000 Yo)))\n" +
                               "        (grup.verb (vmip000 estoy))\n" +
                               "        (S\n" +
                               "          (participi (aq0000 dispuesto))\n" +
                               "          (sp\n" +
                               "            (prep (sp000 a))\n" +
                               "            (S\n" +
                               "              (infinitiu (vmn0000 hacer))\n" +
                               "              (sn\n" +
                               "                (grup.nom (pp000000 lo)))))))\n" +
                               "      (fe \"))\n" +
                               "    (fc ,)\n" +
                               "    (grup.verb (vmis000 afirmó))\n" +
                               "    (sn\n" +
                               "      (grup.nom (np00000 Al) (np00000 Gore)))\n" +
                               "    (fp .)))\n");
  }

  @Test
  public void testFindSplitPoint() {
    Tree splitPoint = AnCoraProcessor.findSplitPoint(t1);
    Assert.assertNotNull(splitPoint);
    Assert.assertEquals(splitPoint.toString(), "(fp .)");
    Assert.assertEquals("Hay tres ciclistas que están por encima de los demás . Son Armstrong , " +
                    "Pantani y Ulrich ,",
            SentenceUtils.listToString(splitPoint.parent(t1).yield()));
  }

  @Test
  public void testSplitTrivial() {
    Tree temp = t1.deepCopy();
    Assert.assertEquals(new Pair<Tree, Tree>(t1, null), AnCoraProcessor.split(temp, null));
  }

  @Test
  public void testSplit() {
    Tree temp = t1.deepCopy();
    Tree splitPoint = AnCoraProcessor.findSplitPoint(temp);
    Pair<Tree, Tree> expectedSplit = new Pair<>(t1First, t1Second);
    Pair<Tree, Tree> split = AnCoraProcessor.split(temp, splitPoint);

    // Easier debugging if we separate these assertions
    Assert.assertEquals(expectedSplit.first(), split.first());
    Assert.assertEquals(expectedSplit.second(), split.second());
  }

  @Test
  public void testSplitThreeSentences() {
    // Split into 1, 2+3, then split 2+3 into 2, 3
    Tree temp = t2.deepCopy();
    Tree splitPoint = AnCoraProcessor.findSplitPoint(temp);
    Pair<Tree, Tree> expectedSplit = new Pair<>(t2First, t2Intermediate);
    Pair<Tree, Tree> split = AnCoraProcessor.split(temp, splitPoint);

    Assert.assertEquals(expectedSplit.first(), split.first());
    Assert.assertEquals(expectedSplit.second(), split.second());

    temp = t2Intermediate.deepCopy();
    splitPoint = AnCoraProcessor.findSplitPoint(temp);
    expectedSplit = new Pair<>(t2Second, t2Third);
    split = AnCoraProcessor.split(temp, splitPoint);

    Assert.assertEquals(expectedSplit.first(), split.first());
    Assert.assertEquals(expectedSplit.second(), split.second());
  }

  // Some AnCora trees hold periods underneath "conj" constituents.. I guess this makes some sense
  @Test
  public void testSplitThreeSentencesWithConj() {
    Tree temp = t3.deepCopy();
    Tree splitPoint = AnCoraProcessor.findSplitPoint(temp);
    Pair<Tree, Tree> expectedSplit = new Pair<>(t3First, t3Intermediate);
    Pair<Tree, Tree> split = AnCoraProcessor.split(temp, splitPoint);

    Assert.assertEquals(expectedSplit.first(), split.first());
    Assert.assertEquals(expectedSplit.second(), split.second());

    temp = t3Intermediate.deepCopy();
    splitPoint = AnCoraProcessor.findSplitPoint(temp);
    expectedSplit = new Pair<>(t3Second, t3Third);
    split = AnCoraProcessor.split(temp, splitPoint);

    Assert.assertEquals(expectedSplit.first(), split.first());
    Assert.assertEquals(expectedSplit.second(), split.second());
  }

  private static Tree readTree(String treeString) {
    return Tree.valueOf(treeString, treeReaderFactory);
  }

}