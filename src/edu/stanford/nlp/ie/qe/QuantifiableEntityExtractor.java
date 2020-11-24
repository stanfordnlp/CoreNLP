package edu.stanford.nlp.ie.qe;

import edu.stanford.nlp.ie.NumberNormalizer;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.tokensregex.CoreMapExpressionExtractor;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Extracts quantifiable entities using rules.
 * The rules can be found in: {@code src/edu/stanford/nlp/ie/qe/rules/}
 *
 * @author Angel Chang
 */
public class QuantifiableEntityExtractor {

  private Env env;
  private Options options;
  private CoreMapExpressionExtractor<MatchedExpression> extractor;

  public SimpleQuantifiableEntity get(double amount, String unitName) {
    return new SimpleQuantifiableEntity(amount, (Unit) env.get(unitName));
  }

  public List<MatchedExpression> extract(CoreMap annotation) {
    if (!annotation.containsKey(CoreAnnotations.NumerizedTokensAnnotation.class)) {
      List<CoreMap> mergedNumbers = NumberNormalizer.findAndMergeNumbers(annotation);
      annotation.set(CoreAnnotations.NumerizedTokensAnnotation.class, mergedNumbers);
    }
    return extractor.extractExpressions(annotation);
  }

  // Initializing
  public void init(String name, Properties props)
  {
    init(new Options(name, props));
  }

  public void init(Options options) {
    this.options = options;
    initEnv();
    extractor = createExtractor();
  }

  private CoreMapExpressionExtractor<MatchedExpression> createExtractor() {
    List<String> filenames = StringUtils.split(options.grammarFilename, "\\s*[,;]\\s*");
    return CoreMapExpressionExtractor.createExtractorFromFiles(env, filenames);
  }

  private void initEnv() {
    env = TokenSequencePattern.getNewEnv();
    env.setDefaultTokensAnnotationKey(CoreAnnotations.NumerizedTokensAnnotation.class);

    // Do case insensitive matching
    env.setDefaultStringMatchFlags(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    env.setDefaultStringPatternFlags(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    try {
      Units.registerUnits(env, options.unitsFilename);
    } catch (IOException ex)  {
      throw new RuntimeException("Error loading units from " + options.unitsFilename, ex);
    }
    try {
      UnitPrefix.registerPrefixes(env, options.prefixFilename);
    } catch (IOException ex)  {
      throw new RuntimeException("Error loading prefixes from " + options.prefixFilename, ex);
    }
    env.bind("options", options);

    env.bind("numcomptype", CoreAnnotations.NumericCompositeTypeAnnotation.class);
    env.bind("numcompvalue", CoreAnnotations.NumericCompositeValueAnnotation.class);
  }

  private static void generatePrefixDefs(String infile, String outfile) throws IOException {
    List<UnitPrefix> prefixes = UnitPrefix.loadPrefixes(infile);
    PrintWriter pw = IOUtils.getPrintWriter(outfile);
    pw.println("SI_PREFIX_MAP = {");
    List<String> items = new ArrayList<>();
    for (UnitPrefix prefix : prefixes) {
      if ("SI".equals(prefix.system)) {
        items.add("\"" + prefix.name + "\": " + prefix.getName().toUpperCase());
      }
    }
    pw.println(StringUtils.join(items, ",\n"));
    pw.println("}");
    pw.println("$SiPrefixes = CreateRegex(Keys(SI_PREFIX_MAP))");
    pw.println();
    pw.println("SI_SYM_PREFIX_MAP = {");
    items.clear();
    for (UnitPrefix prefix:prefixes) {
      if ("SI".equals(prefix.system)) {
        items.add("\"" + prefix.symbol + "\": " + prefix.getName().toUpperCase());
      }
    }
    pw.println(StringUtils.join(items, ",\n"));
    pw.println("}");
    pw.println("$SiSymPrefixes = CreateRegex(Keys(SI_SYM_PREFIX_MAP))");
    pw.close();
  }

  private static void generateUnitsStage0Rules(String unitsFiles, String infile, String outfile) throws IOException {
    Pattern tabPattern = Pattern.compile("\t");
    PrintWriter pw = IOUtils.getPrintWriter(outfile);

    List<Unit> units = Units.loadUnits(unitsFiles);
    pw.println("SI_UNIT_MAP = {");
    List<String> items = new ArrayList<>();
    for (Unit unit:units) {
      if ("SI".equals(unit.prefixSystem)) {
        items.add("\"" + unit.name + "\": " + (unit.getType() + "_" + unit.getName()).toUpperCase());
      }
    }
    pw.println(StringUtils.join(items, ",\n"));
    pw.println("}");
    pw.println("$SiUnits = CreateRegex(Keys(SI_UNIT_MAP))");
    pw.println();

    pw.println("SI_SYM_UNIT_MAP = {");
    items.clear();
    for (Unit unit:units) {
      if ("SI".equals(unit.prefixSystem)) {
        items.add("\"" + unit.symbol + "\": " + (unit.getType() + "_" + unit.getName()).toUpperCase());
      }
    }
    pw.println(StringUtils.join(items, ",\n"));
    pw.println("}");
    pw.println("$SiSymUnits = CreateRegex(Keys(SI_SYM_UNIT_MAP))");
    pw.println();

    pw.println("SYM_UNIT_MAP = {");
    items.clear();
    for (Unit unit:units) {
      items.add("\"" + unit.symbol + "\": " + (unit.getType() + "_" + unit.getName()).toUpperCase());
    }
    pw.println(StringUtils.join(items, ",\n"));
    pw.println("}");
    pw.println("$SymUnits = CreateRegex(Keys(SYM_UNIT_MAP))");
    pw.println();

    BufferedReader br = IOUtils.readerFromString(infile);
    String line;
    pw.println("ENV.defaults[\"stage\"] = 0");
    while ((line = br.readLine()) != null) {
      String[] fields = tabPattern.split(line);
      pw.println(String.format("{ pattern: ( %s ), action: Tag($0, \"Unit\", %s) }", fields[0], fields[1]));
    }
    br.close();
    pw.close();
  }

  public static void main(String[] args) throws Exception {
    // Generate rules files
    Properties props = StringUtils.argsToProperties(args);
    Options options = new Options("qe", props);
    generatePrefixDefs(options.prefixFilename, options.prefixRulesFilename);
    generateUnitsStage0Rules(options.unitsFilename, options.text2UnitMapping, options.unitsRulesFilename);
  }

}
