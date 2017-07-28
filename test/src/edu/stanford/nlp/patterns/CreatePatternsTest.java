package edu.stanford.nlp.patterns;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.dep.DepPattern;
import edu.stanford.nlp.patterns.surface.CreatePatterns;
import edu.stanford.nlp.patterns.surface.PatternsForEachToken;
import edu.stanford.nlp.patterns.surface.PatternsForEachTokenInMemory;
import edu.stanford.nlp.patterns.surface.SurfacePattern;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;
import org.junit.Test;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by sonalg on 11/2/14.
 */
public class CreatePatternsTest {


  @Test
  public void test() throws IOException {
    Properties props = new Properties();
    props.setProperty("patternType","DEP");
    ConstantsAndVariables constvars = new ConstantsAndVariables(props, new HashSet<String>(), new HashMap<String, Class<? extends TypesafeMap.Key<String>>>());
    CreatePatterns<DepPattern> createPatterns = new CreatePatterns<>(props, constvars);
    Map<String, DataInstance> sents = new HashMap<>();
    CoreMap m = new ArrayCoreMap();
    String text = "We present a paper that focuses on semantic graphs applied to language.";

    String graphString="[present/VBP-2 nsubj>We/PRP-1 dobj>[paper/NN-4 det>a/DT-3] ccomp>[applied/VBN-10 mark>that/IN-5 nsubj>[focuses/NN-6 nmod:on>[graphs/NNS-9 amod>semantic/JJ-8]] nmod:to>language/NN-12]]";
    SemanticGraph graph = SemanticGraph.valueOf(graphString);

    //String phrase = "semantic graphs";
    List<String> tokens = Arrays.asList(new String[]{"We", "present", "a", "paper", "that", "focuses", "on", "semantic", "graphs", "applied", "to", "language"});
    m.set(CoreAnnotations.TokensAnnotation.class, tokens.stream().map(x -> {
      CoreLabel t = new CoreLabel();
      t.setWord(x);
      return t;
    }).collect(Collectors.toList()));
    m.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
    sents.put("sent1", DataInstance.getNewInstance(PatternFactory.PatternType.DEP, m));
    createPatterns.getAllPatterns(sents, props, ConstantsAndVariables.PatternForEachTokenWay.MEMORY);
    System.out.println("graph is " + graph);
    System.out.println(PatternsForEachTokenInMemory.patternsForEachToken);
  }
}
