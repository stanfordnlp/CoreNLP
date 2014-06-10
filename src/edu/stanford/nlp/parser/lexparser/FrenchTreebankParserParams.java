package edu.stanford.nlp.parser.lexparser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
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
import edu.stanford.nlp.trees.tregex.TregexParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.TregexPatternCompiler;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * TreebankLangParserParams for the Frenck Treebank corpus. This package assumes that the FTB
 * has been transformed into PTB-format trees encoded in UTF-8. The "-xmlFormat" option can
 * be used to read the raw FTB trees.
 *
 * @author Marie-Catherine de Marneffe
 * @author Spence Green
 *
 */
public class FrenchTreebankParserParams extends AbstractTreebankParserParams {

  private static final long serialVersionUID = -6976724734594763986L;

  private final StringBuilder optionsString;

  private HeadFinder headFinder;
  private final Map<String,Pair<TregexPattern,Function<TregexMatcher,String>>> annotationPatterns;
  private final List<Pair<TregexPattern,Function<TregexMatcher,String>>> activeAnnotations;

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

    annotationPatterns = new HashMap<String,Pair<TregexPattern,Function<TregexMatcher,String>>>();
    activeAnnotations = new ArrayList<Pair<TregexPattern,Function<TregexMatcher,String>>>();

    initializeAnnotationPatterns();
  }

  private final List<String> baselineFeatures = new ArrayList<String>();
  {
    baselineFeatures.add("-tagPAFr");
    
    baselineFeatures.add("-markInf");
    baselineFeatures.add("-markPart");
    baselineFeatures.add("-markVN");
    baselineFeatures.add("-coord1");
    baselineFeatures.add("-de2");
    baselineFeatures.add("-markP1");

    //MWE features...don't help overall parsing, but help MWE categories
    baselineFeatures.add("-MWAdvS");
    baselineFeatures.add("-MWADVSel1");
    baselineFeatures.add("-MWADVSel2");
    baselineFeatures.add("-MWNSel1");
    baselineFeatures.add("-MWNSel2");
    
    // New features for CL submission
    baselineFeatures.add("-splitPUNC");
  }
  private final List<String> additionalFeatures = new ArrayList<String>();


  private void initializeAnnotationPatterns() {
    try {

      TregexPatternCompiler tregexPatternCompiler = new TregexPatternCompiler(headFinder());

      /***************************************************************************
       *                           BASELINE FEATURES
       ***************************************************************************/
      // Incremental delta improvements are over the previous feature (dev set, <= 40)
      //

      // POS Splitting for verbs
      annotationPatterns.put("-markInf",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@V > (@VN > @VPinf)"),new SimpleStringFunction("-infinitive")));
      annotationPatterns.put("-markPart",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@V > (@VN > @VPpart)"),new SimpleStringFunction("-participle")));
      annotationPatterns.put("-markVN",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << @VN"),new SimpleStringFunction("-withVN")));

      // +1.45 F1  (Helps MWEs significantly)
      annotationPatterns.put("-tagPAFr", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("!@PUNC < (__ !< __) > __=parent"),new AddRelativeNodeFunction("-","parent", true)));
      
      // +.14 F1
      annotationPatterns.put("-coord1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@COORD <2 __=word"), new AddRelativeNodeFunction("-","word", true)));

      // +.70 F1 -- de c-commands other stuff dominated by NP, PP, and COORD
      annotationPatterns.put("-de2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P < /^([Dd]es?|du|d')$/"),new SimpleStringFunction("-de2")));
      annotationPatterns.put("-de3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP|PP|COORD >+(@NP|PP) (@PP <, (@P < /^([Dd]es?|du|d')$/))"),new SimpleStringFunction("-de3")));

      // +.31 F1
      annotationPatterns.put("-markP1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > (@PP > @NP)"),new SimpleStringFunction("-n")));

      //MWEs
      //(for MWADV 75.92 -> 77.16)
      annotationPatterns.put("-MWAdvS", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWADV > /S/"),new SimpleStringFunction("-mwadv-s")));

      annotationPatterns.put("-MWADVSel1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWADV <1 @P <2 @N !<3 __"),new SimpleStringFunction("-mwadv1")));
      annotationPatterns.put("-MWADVSel2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWADV <1 @P <2 @D <3 @N !<4 __"),new SimpleStringFunction("-mwadv2")));

      annotationPatterns.put("-MWNSel1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @A !<3 __"),new SimpleStringFunction("-mwn1")));
      annotationPatterns.put("-MWNSel2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @P <3 @N !<4 __"),new SimpleStringFunction("-mwn2")));
      annotationPatterns.put("-MWNSel3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @- <3 @N !<4 __"),new SimpleStringFunction("-mwn3")));

      annotationPatterns.put("-splitPUNC",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PUNC < __=" + AnnotatePunctuationFunction.key),new AnnotatePunctuationFunction()));
      
      /***************************************************************************
       *                          TEST FEATURES
       ***************************************************************************/

      // Mark MWE tags only
      annotationPatterns.put("-mweTag", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("!@PUNC < (__ !< __) > /MW/=parent"),new AddRelativeNodeFunction("-","parent", true)));
      
      
      annotationPatterns.put("-sq",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@SENT << /\\?/"),new SimpleStringFunction("-Q")));

      //New phrasal splits
      annotationPatterns.put("-hasVP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("!@ROOT|SENT << /^VP/"),new SimpleStringFunction("-hasVP")));
      annotationPatterns.put("-hasVP2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << /^VP/"),new SimpleStringFunction("-hasVP")));
      annotationPatterns.put("-npCOORD", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < @COORD"),new SimpleStringFunction("-coord")));
      annotationPatterns.put("-npVP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < /VP/"),new SimpleStringFunction("-vp")));

      //NPs
      annotationPatterns.put("-baseNP1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <1 @D <2 @N !<3 __"),new SimpleStringFunction("-np1")));
      annotationPatterns.put("-baseNP2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <1 @D <2 @MWN !<3 __"),new SimpleStringFunction("-np2")));
      annotationPatterns.put("-baseNP3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <1 @MWD <2 @N !<3 __ "),new SimpleStringFunction("-np3")));


      //MWEs
      annotationPatterns.put("-npMWN1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < (@MWN < @A)"),new SimpleStringFunction("-mwna")));
      annotationPatterns.put("-npMWN2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <1 @D <2 @MWN <3 @PP !<4 __"),new SimpleStringFunction("-mwn2")));
      annotationPatterns.put("-npMWN3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <1 @D <2 (@MWN <1 @N <2 @A !<3 __) !<3 __"),new SimpleStringFunction("-mwn3")));
      annotationPatterns.put("-npMWN4", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP <, @P <2 (@NP <1 @D <2 (@MWN <1 @N <2 @A !<3 __) !<3 __) !<3 __"),new SimpleStringFunction("-mwn3")));


      //The whopper....
      annotationPatterns.put("-MWNSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN"),new AddPOSSequenceFunction("-",600,true)));
      annotationPatterns.put("-MWADVSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWADV"),new AddPOSSequenceFunction("-",500,true)));
      annotationPatterns.put("-MWASel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWA"),new AddPOSSequenceFunction("-",100,true)));
      annotationPatterns.put("-MWCSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWC"),new AddPOSSequenceFunction("-",400,true)));
      annotationPatterns.put("-MWDSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWD"),new AddPOSSequenceFunction("-",100,true)));
      annotationPatterns.put("-MWPSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWP"),new AddPOSSequenceFunction("-",600,true)));
      annotationPatterns.put("-MWPROSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWPRO"),new AddPOSSequenceFunction("-",60,true)));
      annotationPatterns.put("-MWVSel", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWV"),new AddPOSSequenceFunction("-",200,true)));

      //MWN
      annotationPatterns.put("-mwn1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @A !<3 __"),new SimpleStringFunction("-na")));
      annotationPatterns.put("-mwn2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @P <3 @N !<4 __"),new SimpleStringFunction("-npn")));
      annotationPatterns.put("-mwn3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @- <3 @N !<4 __"),new SimpleStringFunction("-n-n")));
      annotationPatterns.put("-mwn4", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @N <2 @N !<3 __"),new SimpleStringFunction("-nn")));
      annotationPatterns.put("-mwn5", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWN <1 @D <2 @N !<3 __"),new SimpleStringFunction("-dn")));

      //wh words
      annotationPatterns.put("-hasWH", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ < /^(qui|quoi|comment|quel|quelle|quels|quelles|où|combien|que|pourquoi|quand)$/"),new SimpleStringFunction("-wh")));


      //POS splitting
      annotationPatterns.put("-markNNP2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@N < /^[A-Z]/"),new SimpleStringFunction("-nnp")));

      annotationPatterns.put("-markD1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D > (__ > @PP)"),new SimpleStringFunction("-p")));
      annotationPatterns.put("-markD2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D > (__ > @NP)"),new SimpleStringFunction("-n")));
      annotationPatterns.put("-markD3",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D > (__ > /^VP/)"),new SimpleStringFunction("-v")));
      annotationPatterns.put("-markD4",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D > (__ > /^S/)"),new SimpleStringFunction("-s")));
      annotationPatterns.put("-markD5",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D > (__ > @COORD)"),new SimpleStringFunction("-c")));

      //Appositives?
      annotationPatterns.put("-app1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < /[,]/"),new SimpleStringFunction("-app1")));
      annotationPatterns.put("-app2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("/[^,\\-:;\"]/ > (@NP < /^[,]$/) $,, /^[,]$/"),new SimpleStringFunction("-app2")));

      //COORD
      annotationPatterns.put("-coord2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@COORD !< @C"), new SimpleStringFunction("-nonC")));
      annotationPatterns.put("-hasCOORD",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ < @COORD"), new SimpleStringFunction("-hasCOORD")));
      annotationPatterns.put("-hasCOORDLS",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@SENT <, @COORD"), new SimpleStringFunction("-hasCOORDLS")));
      annotationPatterns.put("-hasCOORDNonS",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ < @COORD !<, @COORD"), new SimpleStringFunction("-hasCOORDNonS")));


      // PP / VPInf
      annotationPatterns.put("-pp1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P < /^(du|des|au|aux)$/=word"), new AddRelativeNodeFunction("-","word", false)));
      annotationPatterns.put("-vpinf1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VPinf <, __=word"), new AddRelativeNodeFunction("-","word", false)));

      annotationPatterns.put("-vpinf2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VPinf <, __=word"), new AddRelativeNodeFunction("-","word", true)));

      // PP splitting (subsumed by the de2-3 features)
      annotationPatterns.put("-splitIN",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP <, (P < /^([Dd]e|[Dd]'|[Dd]es|[Dd]u|à|[Aa]u|[Aa]ux|[Ee]n|[Dd]ans|[Pp]ar|[Ss]ur|[Pp]our|[Aa]vec|[Ee]ntre)$/=word)"), new AddRelativeNodeFunction("-","word", false,true)));

      annotationPatterns.put("-splitP",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P < /^([Dd]e|[Dd]'|[Dd]es|[Dd]u|à|[Aa]u|[Aa]ux|[Ee]n|[Dd]ans|[Pp]ar|[Ss]ur|[Pp]our|[Aa]vec|[Ee]ntre)$/=word"), new AddRelativeNodeFunction("-","word", false,true)));

      //de features
      annotationPatterns.put("-hasde", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP|PP <+(@NP|PP) (P < de)"),new SimpleStringFunction("-hasDE")));
      annotationPatterns.put("-hasde2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP < de"),new SimpleStringFunction("-hasDE2")));

      //NPs
      annotationPatterns.put("-np1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < /^,$/"),new SimpleStringFunction("-np1")));
      annotationPatterns.put("-np2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <, (@D < le|la|les)"),new SimpleStringFunction("-np2")));
      annotationPatterns.put("-np3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D < le|la|les"),new SimpleStringFunction("-def")));


      annotationPatterns.put("-baseNP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <, @D <- (@N , @D)"),new SimpleStringFunction("-baseNP")));

      // PP environment
      annotationPatterns.put("-markP2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > (@PP > @AP)"),new SimpleStringFunction("-a")));
      annotationPatterns.put("-markP3",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > (@PP > @SENT|Ssub|VPinf|VPpart)"),new SimpleStringFunction("-v")));
      annotationPatterns.put("-markP4",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > (@PP > @Srel)"),new SimpleStringFunction("-r")));
      annotationPatterns.put("-markP5",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > (@PP > @COORD)"),new SimpleStringFunction("-c")));
      annotationPatterns.put("-markP6",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > @VPinf"),new SimpleStringFunction("-b")));
      annotationPatterns.put("-markP7",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > @VPpart"),new SimpleStringFunction("-b")));
      annotationPatterns.put("-markP8",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > /^MW|NP/"),new SimpleStringFunction("-internal")));
      annotationPatterns.put("-markP9",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@P > @COORD"),new SimpleStringFunction("-c")));


      /***************************************************************************
       *                           DIDN'T WORK
       ***************************************************************************/
      //MWEs
      annotationPatterns.put("-hasMWP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("!/S/ < @MWP"),new SimpleStringFunction("-mwp")));
      annotationPatterns.put("-hasMWP2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP < @MWP"),new SimpleStringFunction("-mwp2")));
      annotationPatterns.put("-hasMWN2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP <+(@NP) @MWN"),new SimpleStringFunction("-hasMWN2")));
      annotationPatterns.put("-hasMWN3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < @MWN"),new SimpleStringFunction("-hasMWN3")));

      annotationPatterns.put("-hasMWADV", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("/^A/ < @MWADV"),new SimpleStringFunction("-hasmwadv")));
      annotationPatterns.put("-hasC1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ < @MWC"),new SimpleStringFunction("-hasc1")));
      annotationPatterns.put("-hasC2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@MWC > /S/"),new SimpleStringFunction("-hasc2")));
      annotationPatterns.put("-hasC3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@COORD < @MWC"),new SimpleStringFunction("-hasc3")));
      annotationPatterns.put("-uMWN", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP <: @MWN"),new SimpleStringFunction("-umwn")));

      //POS splitting
      annotationPatterns.put("-splitC", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@C < __=word"),new AddRelativeNodeFunction("-","word", false)));
      annotationPatterns.put("-splitD",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D < /^[^\\d+]{1,4}$/=word"), new AddRelativeNodeFunction("-","word", false)));
      annotationPatterns.put("-de1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@D < /^([Dd]es?|du|d')$/"),new SimpleStringFunction("-de1")));

      annotationPatterns.put("-markNNP1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < (N < /^[A-Z]/) !< /^[^NA]/"),new SimpleStringFunction("-nnp")));

      //PP environment
      annotationPatterns.put("-markPP1",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP > @NP"),new SimpleStringFunction("-n")));
      annotationPatterns.put("-markPP2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP > @AP"),new SimpleStringFunction("-a")));
      annotationPatterns.put("-markPP3",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP > @SENT|Ssub|VPinf|VPpart"),new SimpleStringFunction("-v")));
      annotationPatterns.put("-markPP4",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP > @Srel"),new SimpleStringFunction("-r")));
      annotationPatterns.put("-markPP5",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@PP > @COORD"),new SimpleStringFunction("-c")));

      annotationPatterns.put("-dominateCC",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << @COORD"),new SimpleStringFunction("-withCC")));
      annotationPatterns.put("-dominateIN",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << @PP"),new SimpleStringFunction("-withPP")));

      //Klein and Manning style features
      annotationPatterns.put("-markContainsVP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << /^VP/"),new SimpleStringFunction("-hasV")));
      annotationPatterns.put("-markContainsVP2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << /^VP/=word"), new AddRelativeNodeFunction("-hasV-","word", false)));

      annotationPatterns.put("-markVNArgs",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VN $+ __=word1"), new AddRelativeNodeFunction("-","word1", false)));
      annotationPatterns.put("-markVNArgs2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VN > __=word1 $+ __=word2"), new AddRelativeNodeFunction("-","word1","word2", false)));

      annotationPatterns.put("-markContainsMW", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << /^MW/"),new SimpleStringFunction("-hasMW")));
      annotationPatterns.put("-markContainsMW2",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ << /^MW/=word"), new AddRelativeNodeFunction("-has-","word", false)));

      //MWE Sequence features
      annotationPatterns.put("-mwStart", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ >, /^MW/"),new SimpleStringFunction("-mwStart")));
      annotationPatterns.put("-mwMiddle", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ !>- /^MW/ !>, /^MW/ > /^MW/"),new SimpleStringFunction("-mwMid")));
      annotationPatterns.put("-mwMiddle2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ !>- /^MW/ !>, /^MW/ > /^MW/ , __=pos"),new AddRelativeNodeFunction("-","pos", true)));
      annotationPatterns.put("-mwEnd", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("__ >- /^MW/"),new SimpleStringFunction("-mwEnd")));

      //AP Features
      annotationPatterns.put("-nonNAP",new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@AP !$, @N|AP"), new SimpleStringFunction("-nap")));

      //Phrasal splitting
      annotationPatterns.put("-markNPTMP", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < (@N < /^(lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche|Lundi|Mardi|Mercredi|Jeudi|Vendredi|Samedi|Dimanche|janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre|Janvier|Février|Mars|Avril|Mai|Juin|Juillet|Août|Septembre|Octobre|Novembre|Décembre)$/)"),new SimpleStringFunction("-tmp")));

      //Singular
      annotationPatterns.put("-markSing1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < (D < /^(ce|cette|une|la|le|un|sa|son|ma|mon|ta|ton)$/)"),new SimpleStringFunction("-sing")));
      annotationPatterns.put("-markSing2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@AP < (A < (/[^sx]$/ !< __))"),new SimpleStringFunction("-sing")));
      annotationPatterns.put("-markSing3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VPpart < (V < /(e|é)$/)"),new SimpleStringFunction("-sing")));

      //Plural
      annotationPatterns.put("-markPl1", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@NP < (D < /s$/)"),new SimpleStringFunction("-pl")));
      annotationPatterns.put("-markPl2", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@AP < (A < /[sx]$/)"),new SimpleStringFunction("-pl")));
      annotationPatterns.put("-markPl3", new Pair<TregexPattern,Function<TregexMatcher,String>>(tregexPatternCompiler.compile("@VPpart < (V < /(es|és)$/)"),new SimpleStringFunction("-pl")));

    } catch (TregexParseException e) {
      int nth = annotationPatterns.size() + 1;
      String nthStr = (nth == 1) ? "1st": ((nth == 2) ? "2nd": nth + "th");
      System.err.println("Parse exception on " + nthStr + " annotation pattern initialization:" + e);
    }
  }

  private static class AnnotatePunctuationFunction implements SerializableFunction<TregexMatcher,String> {
    static final String key = "term";

    private static final Pattern quote = Pattern.compile("^\"$");

    public String apply(TregexMatcher m) {

      final String punc = m.getNode(key).value();

      if (punc.equals("."))
        return "-fs";
      else if (punc.equals("?"))
        return "-quest";
      else if (punc.equals(","))
        return "-comma";
      else if (punc.equals(":") || punc.equals(";"))
        return "-colon";
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
   * Annotates all nodes that match the tregex query with annotationMark.
   *
   */
  private static class SimpleStringFunction implements SerializableFunction<TregexMatcher,String> {

    private String annotationMark;

    public SimpleStringFunction(String annotationMark) {
      this.annotationMark = annotationMark;
    }


    public String apply(TregexMatcher tregexMatcher) {
      return annotationMark;
    }

    @Override
    public String toString() { return "SimpleStringFunction[" + annotationMark + ']'; }

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

    // Need to re-initialize all patterns due to the new headFinder
    initializeAnnotationPatterns();

    activeAnnotations.clear();

    for(String key : baselineFeatures) {
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(key);
      activeAnnotations.add(p);
    }
    for(String key : additionalFeatures) {
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(key);
      activeAnnotations.add(p);
    }
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
  public TreeTransformer collinizer() {
    return new TreeCollinizer(treebankLanguagePack());
  }

  @Override
  public TreeTransformer collinizerEvalb() {
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
    return new FrenchTreeReaderFactory(readPennFormat);
  }

  public List<HasWord> defaultTestSentence() {
    String[] sent = {"Ceci", "est", "seulement", "un", "test", "."};
    return Sentence.toWordList(sent);
  }
  
  @Override
  public Tree transformTree(Tree t, Tree root) {

    String baseCat = t.value();
    StringBuilder newCategory = new StringBuilder();

    //Add manual state splits
    for (Pair<TregexPattern,Function<TregexMatcher,String>> e : activeAnnotations) {
      TregexMatcher m = e.first().matcher(root);
      if (m.matchesAt(t))
        newCategory.append(e.second().apply(m));
    }

    //Add morphosyntactic features if this is a POS tag
    if(t.isPreTerminal() && tagSpec != null) {
      if( !(t.firstChild().label() instanceof CoreLabel) || ((CoreLabel) t.firstChild().label()).originalText() == null )
        throw new RuntimeException(String.format("%s: Term lacks morpho analysis: %s",this.getClass().getName(),t.toString()));

      String morphoStr = ((CoreLabel) t.firstChild().label()).originalText();
      Pair<String,String> lemmaMorph = MorphoFeatureSpecification.splitMorphString("", morphoStr);
      MorphoFeatures feats = tagSpec.strToFeatures(lemmaMorph.second());
      baseCat = feats.getTag(baseCat);
    }

    //Update the label(s)
    String newCat = baseCat + newCategory.toString();
    t.setValue(newCat);
    if (t.isPreTerminal() && t.label() instanceof HasTag)
      ((HasTag) t.label()).setTag(newCat);

    return t;
  }


  private void loadMWMap(String filename) {
    mwCounter = new TwoDimensionalCounter<String,String>();

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


  private void removeBaselineFeature(String featName) {
    if(baselineFeatures.contains(featName)) {
      baselineFeatures.remove(featName);
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(featName);
      activeAnnotations.remove(p);
    }
  }

  @Override
  public void display() {
    System.err.println(optionsString.toString());
  }

  @Override
  public int setOptionFlag(String[] args, int i) {
    if (annotationPatterns.keySet().contains(args[i])) {
      if(!baselineFeatures.contains(args[i])) additionalFeatures.add(args[i]);
      Pair<TregexPattern,Function<TregexMatcher,String>> p = annotationPatterns.get(args[i]);
      activeAnnotations.add(p);
      optionsString.append("Option " + args[i] + " added annotation pattern " + p.first() + " with annotation " + p.second() + '\n');
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
        System.err.println(e);
        System.err.println(this.getClass().getName() + ": Could not load head finder " + args[i + 1]);
      }
      i += 2;

    } else if(args[i].equals("-xmlFormat")) {
      optionsString.append("Reading trees in XML format.\n");
      readPennFormat = false;
      setInputEncoding(tlp.getEncoding());
      i++;

    } else if (args[i].equals("-frenchFactored")) {
      for(String annotation : baselineFeatures) {
        String[] a = {annotation};
        setOptionFlag(a,0);
      }
      i++;

    } else if(args[i].equals("-frenchMWMap")) {
      loadMWMap(args[i+1]);
      i+=2;

    } else if(args[i].equals("-tsg")) {
      //wsg2011: These features should be removed for TSG extraction.
      //If they are retained, the resulting grammar seems to be too brittle....
      optionsString.append("Removing baseline features: ");

      removeBaselineFeature("-markVN");
      optionsString.append(" (removed -markVN)");

      removeBaselineFeature("-coord1");
      optionsString.append(" (removed -coord1)\n");

      i++;

    } else if(args[i].equals("-factlex") && (i + 1 < args.length)) {
      String activeFeats = setupMorphoFeatures(args[i+1]);
      optionsString.append("Factored Lexicon: active features: ").append(activeFeats);

      // WSGDEBUG Maybe add -mweTag in place of -tagPAFr?
      removeBaselineFeature("-tagPAFr");
      optionsString.append(" (removed -tagPAFr)\n");
      
      // Add -mweTag
      String[] option = {"-mweTag"};
      setOptionFlag(option, 0);

      i+=2;
    } else if(args[i].equals("-noFeatures")) {
      activeAnnotations.clear();
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
