package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.SerializableFunction;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.international.french.DybroFrenchHeadFinder;
import edu.stanford.nlp.trees.international.french.FrenchTreeReaderFactory;
import edu.stanford.nlp.trees.international.french.FrenchTreebankLanguagePack;
import edu.stanford.nlp.trees.international.french.FrenchXMLTreeReaderFactory;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * TreebankLangParserParams for the French Treebank corpus. This package assumes that the FTB
 * has been transformed into PTB-format trees encoded in UTF-8. The "-xmlFormat" option can
 * be used to read the raw FTB trees.
 *
 * @author Marie-Catherine de Marneffe
 * @author Spence Green
 *
 */
public class FrenchTreebankParserParams extends TregexPoweredTreebankParserParams  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FrenchTreebankParserParams.class);

  private static final long serialVersionUID = -6976724734594763986L;

  private final StringBuilder optionsString;

  private HeadFinder headFinder;

  //The treebank is distributed in XML format.
  //Use -xmlFormat below to enable reading the raw files.
  private boolean readPennFormat = true;

  private boolean collinizerRetainsPunctuation = false;

  //Controls the MW annotation feature
  private TwoDimensionalCounter<String, String> mwCounter;

  private MorphoFeatureSpecification morphoSpec;

  // For adding the CC tagset as annotations.
  private MorphoFeatureSpecification tagSpec;

  public FrenchTreebankParserParams() {
    super(new FrenchTreebankLanguagePack());

    setInputEncoding("UTF-8");

    optionsString = new StringBuilder();
    optionsString.append("FrenchTreebankParserParams\n");

    initializeAnnotationPatterns();
  }

  /**
   * Features which should be enabled by default.
   */
  @Override
  protected String[] baselineAnnotationFeatures() {
    return new String[0];
  }

  /**
   * Features to enable for the factored parser
   */
  private static final String[] factoredFeatures = new String[] {
    "-tagPAFr",

    "-markInf", "-markPart", "-markVN", "-coord1", "-de2", "-markP1",

    //MWE features...don't help overall parsing, but help MWE categories
    "-MWAdvS", "-MWADVSel1", "-MWADVSel2", "-MWNSel1", "-MWNSel2",

    // New features for CL submission
    "-splitPUNC",
  };

  @SuppressWarnings("unchecked")
  private void initializeAnnotationPatterns() {
    /***************************************************************************
     *                           BASELINE FEATURES
     ***************************************************************************/
    // Incremental delta improvements are over the previous feature (dev set, <= 40)
    //

    // POS Splitting for verbs
    annotations.put("-markInf", new Pair("@V > (@VN > @VPinf)",
                                         new SimpleStringFunction("-infinitive")));
    annotations.put("-markPart", new Pair("@V > (@VN > @VPpart)",
                                          new SimpleStringFunction("-participle")));
    annotations.put("-markVN", new Pair("__ << @VN", new SimpleStringFunction("-withVN")));

    // +1.45 F1  (Helps MWEs significantly)
    annotations.put("-tagPAFr", new Pair("!@PUNC < (__ !< __) > __=parent",
                                         new AddRelativeNodeFunction("-", "parent", true)));

    // +.14 F1
    annotations.put("-coord1", new Pair("@COORD <2 __=word",
                                        new AddRelativeNodeFunction("-","word", true)));

    // +.70 F1 -- de c-commands other stuff dominated by NP, PP, and COORD
    annotations.put("-de2", new Pair("@P < /^([Dd]es?|du|d')$/", new SimpleStringFunction("-de2")));
    annotations.put("-de3", new Pair("@NP|PP|COORD >+(@NP|PP) (@PP <, (@P < /^([Dd]es?|du|d')$/))",
                                     new SimpleStringFunction("-de3")));

    // +.31 F1
    annotations.put("-markP1",new Pair("@P > (@PP > @NP)", new SimpleStringFunction("-n")));

    //MWEs
    //(for MWADV 75.92 -> 77.16)
    annotations.put("-MWAdvS", new Pair("@MWADV > /S/", new SimpleStringFunction("-mwadv-s")));

    annotations.put("-MWADVSel1", new Pair("@MWADV <1 @P <2 @N !<3 __",
                                           new SimpleStringFunction("-mwadv1")));
    annotations.put("-MWADVSel2", new Pair("@MWADV <1 @P <2 @D <3 @N !<4 __",
                                           new SimpleStringFunction("-mwadv2")));

    annotations.put("-MWNSel1", new Pair("@MWN <1 @N <2 @A !<3 __",
                                         new SimpleStringFunction("-mwn1")));
    annotations.put("-MWNSel2", new Pair("@MWN <1 @N <2 @P <3 @N !<4 __",
                                         new SimpleStringFunction("-mwn2")));
    annotations.put("-MWNSel3", new Pair("@MWN <1 @N <2 @- <3 @N !<4 __",
                                         new SimpleStringFunction("-mwn3")));

    annotations.put("-splitPUNC",new Pair("@PUNC < __=" + AnnotatePunctuationFunction.key,
                                          new AnnotatePunctuationFunction()));

    /***************************************************************************
     *                          TEST FEATURES
     ***************************************************************************/

    // Mark MWE tags only
    annotations.put("-mweTag", new Pair("!@PUNC < (__ !< __) > /MW/=parent",
                                        new AddRelativeNodeFunction("-","parent", true)));


    annotations.put("-sq",new Pair("@SENT << /\\?/", new SimpleStringFunction("-Q")));

    //New phrasal splits
    annotations.put("-hasVP", new Pair("!@ROOT|SENT << /^VP/", new SimpleStringFunction("-hasVP")));
    annotations.put("-hasVP2", new Pair("__ << /^VP/", new SimpleStringFunction("-hasVP")));
    annotations.put("-npCOORD", new Pair("@NP < @COORD", new SimpleStringFunction("-coord")));
    annotations.put("-npVP", new Pair("@NP < /VP/", new SimpleStringFunction("-vp")));

    //NPs
    annotations.put("-baseNP1", new Pair("@NP <1 @D <2 @N !<3 __",
                                         new SimpleStringFunction("-np1")));
    annotations.put("-baseNP2", new Pair("@NP <1 @D <2 @MWN !<3 __",
                                         new SimpleStringFunction("-np2")));
    annotations.put("-baseNP3", new Pair("@NP <1 @MWD <2 @N !<3 __ ",
                                         new SimpleStringFunction("-np3")));


    //MWEs
    annotations.put("-npMWN1", new Pair("@NP < (@MWN < @A)", new SimpleStringFunction("-mwna")));
    annotations.put("-npMWN2", new Pair("@NP <1 @D <2 @MWN <3 @PP !<4 __",
                                        new SimpleStringFunction("-mwn2")));
    annotations.put("-npMWN3", new Pair("@NP <1 @D <2 (@MWN <1 @N <2 @A !<3 __) !<3 __",
                                        new SimpleStringFunction("-mwn3")));
    annotations.put("-npMWN4", new Pair(
      "@PP <, @P <2 (@NP <1 @D <2 (@MWN <1 @N <2 @A !<3 __) !<3 __) !<3 __",
      new SimpleStringFunction("-mwn3")));


    //The whopper....
    annotations.put("-MWNSel", new Pair("@MWN", new AddPOSSequenceFunction("-",600,true)));
    annotations.put("-MWADVSel", new Pair("@MWADV", new AddPOSSequenceFunction("-",500,true)));
    annotations.put("-MWASel", new Pair("@MWA", new AddPOSSequenceFunction("-",100,true)));
    annotations.put("-MWCSel", new Pair("@MWC", new AddPOSSequenceFunction("-",400,true)));
    annotations.put("-MWDSel", new Pair("@MWD", new AddPOSSequenceFunction("-",100,true)));
    annotations.put("-MWPSel", new Pair("@MWP", new AddPOSSequenceFunction("-",600,true)));
    annotations.put("-MWPROSel", new Pair("@MWPRO", new AddPOSSequenceFunction("-",60,true)));
    annotations.put("-MWVSel", new Pair("@MWV", new AddPOSSequenceFunction("-",200,true)));

    //MWN
    annotations.put("-mwn1", new Pair("@MWN <1 @N <2 @A !<3 __", new SimpleStringFunction("-na")));
    annotations.put("-mwn2", new Pair("@MWN <1 @N <2 @P <3 @N !<4 __",
                                      new SimpleStringFunction("-npn")));
    annotations.put("-mwn3", new Pair("@MWN <1 @N <2 @- <3 @N !<4 __",
                                      new SimpleStringFunction("-n-n")));
    annotations.put("-mwn4", new Pair("@MWN <1 @N <2 @N !<3 __", new SimpleStringFunction("-nn")));
    annotations.put("-mwn5", new Pair("@MWN <1 @D <2 @N !<3 __", new SimpleStringFunction("-dn")));

    //wh words
    annotations.put("-hasWH", new Pair(
      "__ < /^(qui|quoi|comment|quel|quelle|quels|quelles|où|combien|que|pourquoi|quand)$/",
      new SimpleStringFunction("-wh")));


    //POS splitting
    annotations.put("-markNNP2", new Pair("@N < /^[A-Z]/", new SimpleStringFunction("-nnp")));

    annotations.put("-markD1",new Pair("@D > (__ > @PP)", new SimpleStringFunction("-p")));
    annotations.put("-markD2",new Pair("@D > (__ > @NP)", new SimpleStringFunction("-n")));
    annotations.put("-markD3",new Pair("@D > (__ > /^VP/)", new SimpleStringFunction("-v")));
    annotations.put("-markD4",new Pair("@D > (__ > /^S/)", new SimpleStringFunction("-s")));
    annotations.put("-markD5",new Pair("@D > (__ > @COORD)", new SimpleStringFunction("-c")));

    //Appositives?
    annotations.put("-app1", new Pair("@NP < /[,]/", new SimpleStringFunction("-app1")));
    annotations.put("-app2", new Pair("/[^,\\-:;\"]/ > (@NP < /^[,]$/) $,, /^[,]$/",
                                      new SimpleStringFunction("-app2")));

    //COORD
    annotations.put("-coord2",new Pair("@COORD !< @C", new SimpleStringFunction("-nonC")));
    annotations.put("-hasCOORD",new Pair("__ < @COORD", new SimpleStringFunction("-hasCOORD")));
    annotations.put("-hasCOORDLS",new Pair("@SENT <, @COORD",
                                           new SimpleStringFunction("-hasCOORDLS")));
    annotations.put("-hasCOORDNonS",new Pair("__ < @COORD !<, @COORD",
                                             new SimpleStringFunction("-hasCOORDNonS")));


    // PP / VPInf
    annotations.put("-pp1",new Pair("@P < /^(du|des|au|aux)$/=word",
                                    new AddRelativeNodeFunction("-","word", false)));
    annotations.put("-vpinf1",new Pair("@VPinf <, __=word",
                                       new AddRelativeNodeFunction("-","word", false)));

    annotations.put("-vpinf2",new Pair("@VPinf <, __=word",
                                       new AddRelativeNodeFunction("-","word", true)));

    // PP splitting (subsumed by the de2-3 features)
    annotations.put("-splitIN",new Pair(
      "@PP <, (P < /^([Dd]e|[Dd]'|[Dd]es|[Dd]u|à|[Aa]u|[Aa]ux|[Ee]n|[Dd]ans|[Pp]ar|[Ss]ur|[Pp]our|[Aa]vec|[Ee]ntre)$/=word)",
      new AddRelativeNodeFunction("-","word", false,true)));

    annotations.put("-splitP",new Pair(
      "@P < /^([Dd]e|[Dd]'|[Dd]es|[Dd]u|à|[Aa]u|[Aa]ux|[Ee]n|[Dd]ans|[Pp]ar|[Ss]ur|[Pp]our|[Aa]vec|[Ee]ntre)$/=word",
      new AddRelativeNodeFunction("-","word", false,true)));

    //de features
    annotations.put("-hasde", new Pair("@NP|PP <+(@NP|PP) (P < de)",
                                       new SimpleStringFunction("-hasDE")));
    annotations.put("-hasde2", new Pair("@PP < de", new SimpleStringFunction("-hasDE2")));

    //NPs
    annotations.put("-np1", new Pair("@NP < /^,$/", new SimpleStringFunction("-np1")));
    annotations.put("-np2", new Pair("@NP <, (@D < le|la|les)", new SimpleStringFunction("-np2")));
    annotations.put("-np3", new Pair("@D < le|la|les", new SimpleStringFunction("-def")));


    annotations.put("-baseNP", new Pair("@NP <, @D <- (@N , @D)", new SimpleStringFunction("-baseNP")));

    // PP environment
    annotations.put("-markP2",new Pair("@P > (@PP > @AP)", new SimpleStringFunction("-a")));
    annotations.put("-markP3",new Pair("@P > (@PP > @SENT|Ssub|VPinf|VPpart)",
                                       new SimpleStringFunction("-v")));
    annotations.put("-markP4",new Pair("@P > (@PP > @Srel)", new SimpleStringFunction("-r")));
    annotations.put("-markP5",new Pair("@P > (@PP > @COORD)", new SimpleStringFunction("-c")));
    annotations.put("-markP6",new Pair("@P > @VPinf", new SimpleStringFunction("-b")));
    annotations.put("-markP7",new Pair("@P > @VPpart", new SimpleStringFunction("-b")));
    annotations.put("-markP8",new Pair("@P > /^MW|NP/", new SimpleStringFunction("-internal")));
    annotations.put("-markP9",new Pair("@P > @COORD", new SimpleStringFunction("-c")));


    /***************************************************************************
     *                           DIDN'T WORK
     ***************************************************************************/
    //MWEs
    annotations.put("-hasMWP", new Pair("!/S/ < @MWP", new SimpleStringFunction("-mwp")));
    annotations.put("-hasMWP2", new Pair("@PP < @MWP", new SimpleStringFunction("-mwp2")));
    annotations.put("-hasMWN2", new Pair("@PP <+(@NP) @MWN", new SimpleStringFunction("-hasMWN2")));
    annotations.put("-hasMWN3", new Pair("@NP < @MWN", new SimpleStringFunction("-hasMWN3")));

    annotations.put("-hasMWADV", new Pair("/^A/ < @MWADV", new SimpleStringFunction("-hasmwadv")));
    annotations.put("-hasC1", new Pair("__ < @MWC", new SimpleStringFunction("-hasc1")));
    annotations.put("-hasC2", new Pair("@MWC > /S/", new SimpleStringFunction("-hasc2")));
    annotations.put("-hasC3", new Pair("@COORD < @MWC", new SimpleStringFunction("-hasc3")));
    annotations.put("-uMWN", new Pair("@NP <: @MWN", new SimpleStringFunction("-umwn")));

    //POS splitting
    annotations.put("-splitC", new Pair("@C < __=word",
                                        new AddRelativeNodeFunction("-","word", false)));
    annotations.put("-splitD",new Pair("@D < /^[^\\d+]{1,4}$/=word",
                                       new AddRelativeNodeFunction("-","word", false)));
    annotations.put("-de1", new Pair("@D < /^([Dd]es?|du|d')$/",
                                     new SimpleStringFunction("-de1")));

    annotations.put("-markNNP1", new Pair("@NP < (N < /^[A-Z]/) !< /^[^NA]/",
                                          new SimpleStringFunction("-nnp")));

    //PP environment
    annotations.put("-markPP1",new Pair("@PP > @NP", new SimpleStringFunction("-n")));
    annotations.put("-markPP2",new Pair("@PP > @AP", new SimpleStringFunction("-a")));
    annotations.put("-markPP3",new Pair("@PP > @SENT|Ssub|VPinf|VPpart",
                                        new SimpleStringFunction("-v")));
    annotations.put("-markPP4",new Pair("@PP > @Srel", new SimpleStringFunction("-r")));
    annotations.put("-markPP5",new Pair("@PP > @COORD", new SimpleStringFunction("-c")));

    annotations.put("-dominateCC",new Pair("__ << @COORD", new SimpleStringFunction("-withCC")));
    annotations.put("-dominateIN",new Pair("__ << @PP", new SimpleStringFunction("-withPP")));

    //Klein and Manning style features
    annotations.put("-markContainsVP", new Pair("__ << /^VP/",
                                                new SimpleStringFunction("-hasV")));
    annotations.put("-markContainsVP2",new Pair("__ << /^VP/=word",
                                                new AddRelativeNodeFunction("-hasV-","word", false)));

    annotations.put("-markVNArgs",new Pair("@VN $+ __=word1",
                                           new AddRelativeNodeFunction("-","word1", false)));
    annotations.put("-markVNArgs2",new Pair("@VN > __=word1 $+ __=word2",
                                            new AddRelativeNodeFunction("-","word1","word2", false)));

    annotations.put("-markContainsMW", new Pair("__ << /^MW/", new SimpleStringFunction("-hasMW")));
    annotations.put("-markContainsMW2",new Pair("__ << /^MW/=word",
                                                new AddRelativeNodeFunction("-has-","word", false)));

    //MWE Sequence features
    annotations.put("-mwStart", new Pair("__ >, /^MW/", new SimpleStringFunction("-mwStart")));
    annotations.put("-mwMiddle", new Pair("__ !>- /^MW/ !>, /^MW/ > /^MW/",
                                          new SimpleStringFunction("-mwMid")));
    annotations.put("-mwMiddle2", new Pair("__ !>- /^MW/ !>, /^MW/ > /^MW/ , __=pos",
                                           new AddRelativeNodeFunction("-","pos", true)));
    annotations.put("-mwEnd", new Pair("__ >- /^MW/", new SimpleStringFunction("-mwEnd")));

    //AP Features
    annotations.put("-nonNAP",new Pair("@AP !$, @N|AP", new SimpleStringFunction("-nap")));

    //Phrasal splitting
    annotations.put("-markNPTMP", new Pair(
      "@NP < (@N < /^(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche|janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre|Janvier|Février|Mars|Avril|Mai|Juin|Juillet|Août|Septembre|Octobre|Novembre|Décembre)$/)",
      new SimpleStringFunction("-tmp")));

    //Singular
    annotations.put("-markSing1", new Pair("@NP < (D < /^(ce|cette|une|la|le|un|sa|son|ma|mon|ta|ton)$/)",
                                           new SimpleStringFunction("-sing")));
    annotations.put("-markSing2", new Pair("@AP < (A < (/[^sx]$/ !< __))",
                                           new SimpleStringFunction("-sing")));
    annotations.put("-markSing3", new Pair("@VPpart < (V < /(e|é)$/)",
                                           new SimpleStringFunction("-sing")));

    //Plural
    annotations.put("-markPl1", new Pair("@NP < (D < /s$/)", new SimpleStringFunction("-pl")));
    annotations.put("-markPl2", new Pair("@AP < (A < /[sx]$/)", new SimpleStringFunction("-pl")));
    annotations.put("-markPl3", new Pair("@VPpart < (V < /(es|és)$/)",
                                         new SimpleStringFunction("-pl")));


    compileAnnotations(headFinder());
  }

  private static class AnnotatePunctuationFunction implements SerializableFunction<TregexMatcher,String> {
    static final String key = "term";

    public String apply(TregexMatcher m) {

      final String punc = m.getNode(key).value();

      switch (punc) {
        case ".":
          return "-fs";
        case "?":
          return "-quest";
        case ",":
          return "-comma";
        case ":":
        case ";":
          return "-colon";
      }
//      else if (punc.equals("-LRB-"))
//        return "-lrb";
//      else if (punc.equals("-RRB-"))
//        return "-rrb";
//      else if (punc.equals("-"))
//        return "-dash";
//      else if (quote.matcher(punc).matches())
//        return "-quote";
      //      else if(punc.equals("/"))
      //        return "-slash";
      //      else if(punc.equals("%"))
      //        return "-perc";
      //      else if(punc.contains(".."))
      //        return "-ellipses";
      return "";
    }

    @Override
    public String toString() { return "AnnotatePunctuationFunction"; }

    private static final long serialVersionUID = 1L;
  }


  /**
   * Annotates all nodes that match the tregex query with annotationMark + key1
   * Usually annotationMark = "-"
   * Optionally, you can use a second key in the tregex expression.
   *
   */
  private class AddRelativeNodeFunction implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;
    private String key;
    private String key2;
    private boolean doBasicCat = false;
    private boolean toLower = false;

    public AddRelativeNodeFunction(String annotationMark, String key, boolean basicCategory) {
      this.annotationMark = annotationMark;
      this.key = key;
      this.key2 = null;
      doBasicCat = basicCategory;
    }

    public AddRelativeNodeFunction(String annotationMark, String key1, String key2, boolean basicCategory) {
      this(annotationMark,key1,basicCategory);
      this.key2 = key2;
    }

    public AddRelativeNodeFunction(String annotationMark, String key1, boolean basicCategory, boolean toLower) {
      this(annotationMark,key1,basicCategory);
      this.toLower = toLower;
    }

    public String apply(TregexMatcher m) {
      String tag;
      if(key2 == null)
        tag = annotationMark + ((doBasicCat) ? tlp.basicCategory(m.getNode(key).label().value()) : m.getNode(key).label().value());
      else {
        String annot1 = (doBasicCat) ? tlp.basicCategory(m.getNode(key).label().value()) : m.getNode(key).label().value();
        String annot2 = (doBasicCat) ? tlp.basicCategory(m.getNode(key2).label().value()) : m.getNode(key2).label().value();
        tag = annotationMark + annot1 + annotationMark + annot2;
      }

      return (toLower) ? tag.toLowerCase() : tag;
    }

    @Override
    public String toString() {
      if(key2 == null)
        return "AddRelativeNodeFunction[" + annotationMark + ',' + key + ']';
      else
        return "AddRelativeNodeFunction[" + annotationMark + ',' + key + ',' + key2 + ']';
    }

    private static final long serialVersionUID = 1L;

  }

  private class AddPOSSequenceFunction implements SerializableFunction<TregexMatcher,String> {

    private final String annotationMark;
    private final boolean doBasicCat;
    private final double cutoff;

    public AddPOSSequenceFunction(String annotationMark, int cutoff, boolean basicCategory) {
      this.annotationMark = annotationMark;
      doBasicCat = basicCategory;
      this.cutoff = cutoff;
    }


    public String apply(TregexMatcher m) {
      if(mwCounter == null)
        throw new RuntimeException("Cannot enable POSSequence features without POS sequence map. Use option -frenchMWMap.");

      Tree t = m.getMatch();
      StringBuilder sb = new StringBuilder();
      for(Tree kid : t.children()) {
        if( ! kid.isPreTerminal())
          throw new RuntimeException("Not POS sequence for tree: " + t.toString());
        String tag = doBasicCat ? tlp.basicCategory(kid.value()) : kid.value();
        sb.append(tag).append(" ");
      }

      if(mwCounter.getCount(t.value(), sb.toString().trim()) > cutoff)
        return annotationMark + sb.toString().replaceAll("\\s+", "").toLowerCase();
      else
        return "";
    }

    @Override
    public String toString() {
      return "AddPOSSequenceFunction[" + annotationMark + ',' + cutoff + ',' + doBasicCat + ']';
    }

    private static final long serialVersionUID = 1L;
  }


  @Override
  public HeadFinder headFinder() {
    if(headFinder == null)
      headFinder = new DybroFrenchHeadFinder(treebankLanguagePack()); //Superior for vanilla PCFG over Arun's headfinding rules
    return headFinder;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return headFinder();
  }

  private void setHeadFinder(HeadFinder hf) {
    if(hf == null)
      throw new IllegalArgumentException();

    headFinder = hf;

    compileAnnotations(hf);
  }


  /**
   *
   * @param op Lexicon options
   * @return A Lexicon
   */
  @Override
  public Lexicon lex(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    if(op.lexOptions.uwModelTrainer == null)
      op.lexOptions.uwModelTrainer = "edu.stanford.nlp.parser.lexparser.FrenchUnknownWordModelTrainer";
    if(morphoSpec != null) {
      return new FactoredLexicon(op, morphoSpec, wordIndex, tagIndex);
    }

    return new BaseLexicon(op, wordIndex, tagIndex);
  }

  @Override
  public String[] sisterSplitters() {
    return new String[0];
  }

  @Override
  public AbstractCollinizer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    return new TreeCollinizer(treebankLanguagePack(),collinizerRetainsPunctuation,false);
  }

  @Override
  public DiskTreebank diskTreebank() {
    return new DiskTreebank(treeReaderFactory(), inputEncoding);
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    return new MemoryTreebank(treeReaderFactory(), inputEncoding);
  }

  public TreeReaderFactory treeReaderFactory() {
    return (readPennFormat) ? new FrenchTreeReaderFactory() : new FrenchXMLTreeReaderFactory(false);
  }

  public List<HasWord> defaultTestSentence() {
    String[] sent = {"Ceci", "est", "seulement", "un", "test", "."};
    return SentenceUtils.toWordList(sent);
  }

  @Override
  public Tree transformTree(Tree t, Tree root) {
    // Perform tregex-powered annotations
    t = super.transformTree(t, root);

    String cat = t.value();

    //Add morphosyntactic features if this is a POS tag
    if(t.isPreTerminal() && tagSpec != null) {
      if( !(t.firstChild().label() instanceof CoreLabel) || ((CoreLabel) t.firstChild().label()).originalText() == null )
        throw new RuntimeException(String.format("%s: Term lacks morpho analysis: %s",this.getClass().getName(),t.toString()));

      String morphoStr = ((CoreLabel) t.firstChild().label()).originalText();
      Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString("", morphoStr);
      MorphoFeatures feats = tagSpec.strToFeatures(lemmaMorph.second());
      cat = feats.getTag(cat);
    }

    //Update the label(s)
    t.setValue(cat);
    if (t.isPreTerminal() && t.label() instanceof HasTag)
      ((HasTag) t.label()).setTag(cat);

    return t;
  }


  private void loadMWMap(String filename) {
    mwCounter = new TwoDimensionalCounter<>();

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename)), "UTF-8"));

      int nLines = 0;
      for(String line; (line = br.readLine()) != null; nLines++) {
        String[] toks = line.split("\t");
        assert toks.length == 3;
        mwCounter.setCount(toks[0].trim(), toks[1].trim(), Double.parseDouble(toks[2].trim()));
      }
      br.close();

      System.err.printf("%s: Loaded %d lines from %s into MWE counter%n", this.getClass().getName(),nLines,filename);

    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Configures morpho-syntactic annotations for POS tags.
   *
   * @param activeFeats A comma-separated list of feature values with names according
   * to MorphoFeatureType.
   *
   */
  private String setupMorphoFeatures(String activeFeats) {
    String[] feats = activeFeats.split(",");
    morphoSpec = tlp.morphFeatureSpec();
    for(String feat : feats) {
      MorphoFeatureType fType = MorphoFeatureType.valueOf(feat.trim());
      morphoSpec.activate(fType);
    }
    return morphoSpec.toString();
  }

  @Override
  public void display() {
    log.info(optionsString.toString());
  }

  @Override
  public int setOptionFlag(String[] args, int i) {
    if (annotations.containsKey(args[i])) {
      addFeature(args[i]);
      i++;
    } else if (args[i].equals("-collinizerRetainsPunctuation")) {
      optionsString.append("Collinizer retains punctuation.\n");
      collinizerRetainsPunctuation = true;
      i++;

    } else if (args[i].equalsIgnoreCase("-headFinder") && (i + 1 < args.length)) {
      try {
        HeadFinder hf = (HeadFinder) Class.forName(args[i + 1]).newInstance();
        setHeadFinder(hf);
        optionsString.append("HeadFinder: " + args[i + 1] + "\n");

      } catch (Exception e) {
        log.info(e);
        log.info(this.getClass().getName() + ": Could not load head finder " + args[i + 1]);
      }
      i += 2;

    } else if(args[i].equals("-xmlFormat")) {
      optionsString.append("Reading trees in XML format.\n");
      readPennFormat = false;
      setInputEncoding(tlp.getEncoding());
      i++;

    } else if (args[i].equals("-frenchFactored")) {
      for(String feature : factoredFeatures)
        addFeature(feature);
      i++;

    } else if(args[i].equals("-frenchMWMap")) {
      loadMWMap(args[i+1]);
      i+=2;

    } else if(args[i].equals("-tsg")) {
      //wsg2011: These features should be removed for TSG extraction.
      //If they are retained, the resulting grammar seems to be too brittle....
      optionsString.append("Removing baseline features: -markVN, -coord1");

      removeFeature("-markVN");
      optionsString.append(" (removed -markVN)");

      removeFeature("-coord1");
      optionsString.append(" (removed -coord1)\n");

      i++;

    } else if(args[i].equals("-factlex") && (i + 1 < args.length)) {
      String activeFeats = setupMorphoFeatures(args[i+1]);
      optionsString.append("Factored Lexicon: active features: ").append(activeFeats);

      // WSGDEBUG Maybe add -mweTag in place of -tagPAFr?
      removeFeature("-tagPAFr");
      optionsString.append(" (removed -tagPAFr)\n");

      // Add -mweTag
      String[] option = {"-mweTag"};
      setOptionFlag(option, 0);

      i+=2;
    } else if(args[i].equals("-noFeatures")) {
      for (String feature : annotations.keySet())
        removeFeature(feature);
      optionsString.append("Removed all manual features.\n");

      i++;
    } else if(args[i].equals("-ccTagsetAnnotations")) {
      tagSpec = new FrenchMorphoFeatureSpecification();
      tagSpec.activate(MorphoFeatureType.OTHER);
      optionsString.append("Adding CC tagset as POS state splits.\n");
      ++i;
    }

    return i;
  }
}
