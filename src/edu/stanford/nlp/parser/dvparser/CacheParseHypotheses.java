package edu.stanford.nlp.parser.dvparser; 

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.LabeledScoredTreeReaderFactory;
import edu.stanford.nlp.trees.SynchronizedTreeTransformer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreeNormalizer;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;

public class CacheParseHypotheses  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CacheParseHypotheses.class);

  private static final TreeReaderFactory trf = new LabeledScoredTreeReaderFactory(CoreLabel.factory(), new TreeNormalizer());

  final BasicCategoryTreeTransformer treeBasicCategories;
  public final Predicate<Tree> treeFilter;

  public CacheParseHypotheses(LexicalizedParser parser) {
    treeBasicCategories = new BasicCategoryTreeTransformer(parser.treebankLanguagePack());
    treeFilter = new FilterConfusingRules(parser);
  }

  public byte[] convertToBytes(List<Tree> input) {
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      GZIPOutputStream gos = new GZIPOutputStream(bos);
      ObjectOutputStream oos = new ObjectOutputStream(gos);
      List<Tree> transformed = CollectionUtils.transformAsList(input, treeBasicCategories);
      List<Tree> filtered = CollectionUtils.filterAsList(transformed, treeFilter);
      oos.writeObject(filtered.size());
      for (Tree tree : filtered) {
        oos.writeObject(tree.toString());
      }
      oos.close();
      gos.close();
      bos.close();
      return bos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public IdentityHashMap<Tree, byte[]> convertToBytes(IdentityHashMap<Tree, List<Tree>> uncompressed) {
    IdentityHashMap<Tree, byte[]> compressed = Generics.newIdentityHashMap();
    for (Map.Entry<Tree, List<Tree>> entry : uncompressed.entrySet()) {
      compressed.put(entry.getKey(), convertToBytes(entry.getValue()));
    }
    return compressed;
  }

  public static List<Tree> convertToTrees(byte[] input) {
    try {
      List<Tree> output = new ArrayList<>();
      ByteArrayInputStream bis = new ByteArrayInputStream(input);
      GZIPInputStream gis = new GZIPInputStream(bis);
      ObjectInputStream ois = new ObjectInputStream(gis);
      int size = ErasureUtils.<Integer>uncheckedCast(ois.readObject());
      for (int i = 0; i < size; ++i) {
        String rawTree = ErasureUtils.uncheckedCast(ois.readObject());
        Tree tree = Tree.valueOf(rawTree, trf);
        tree.setSpans();
        output.add(tree);
      }
      ois.close();
      gis.close();
      bis.close();
      return output;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public static IdentityHashMap<Tree, List<Tree>> convertToTrees(IdentityHashMap<Tree, byte[]> compressed, int numThreads) {
    return convertToTrees(compressed.keySet(), compressed, numThreads);
  }

  static class DecompressionProcessor implements ThreadsafeProcessor<byte[], List<Tree>> {
    @Override
    public List<Tree> process(byte[] input) {
      return convertToTrees(input);
    }

    @Override
    public ThreadsafeProcessor<byte[], List<Tree>> newInstance() {
      // should be threadsafe
      return this;
    }
  }

  public static IdentityHashMap<Tree, List<Tree>> convertToTrees(Collection<Tree> keys, IdentityHashMap<Tree, byte[]> compressed,
                                                                 int numThreads) {
    IdentityHashMap<Tree, List<Tree>> uncompressed = Generics.newIdentityHashMap();
    MulticoreWrapper<byte[], List<Tree>> wrapper = new MulticoreWrapper<>(numThreads, new DecompressionProcessor());
    for (Tree tree : keys) {
      wrapper.put(compressed.get(tree));
    }
    for (Tree tree : keys) {
      if (!wrapper.peek()) {
        wrapper.join();
      }
      uncompressed.put(tree, wrapper.poll());
    }
    return uncompressed;
  }


  static class CacheProcessor implements ThreadsafeProcessor<Tree, Pair<Tree, byte[]>> {
    CacheParseHypotheses cacher;
    LexicalizedParser parser;
    int dvKBest;
    TreeTransformer transformer;

    public CacheProcessor(CacheParseHypotheses cacher, LexicalizedParser parser, int dvKBest, TreeTransformer transformer) {
      this.cacher = cacher;
      this.parser = parser;
      this.dvKBest = dvKBest;
      this.transformer = transformer;
    }

    @Override
    public Pair<Tree, byte[]> process(Tree tree) {
      List<Tree> topParses = DVParser.getTopParsesForOneTree(parser, dvKBest, tree, transformer);
      // this block is a test to make sure the conversion code is working...
      List<Tree> converted = cacher.convertToTrees(cacher.convertToBytes(topParses));
      List<Tree> simplified = CollectionUtils.transformAsList(topParses, cacher.treeBasicCategories);
      simplified = CollectionUtils.filterAsList(simplified, cacher.treeFilter);
      if (simplified.size() != topParses.size()) {
        log.info("Filtered " + (topParses.size() - simplified.size()) + " trees");
        if (simplified.size() == 0) {
          log.info(" WARNING: filtered all trees for " + tree);
        }
      }
      if (!simplified.equals(converted)) {
        if (converted.size() != simplified.size()) {
          throw new AssertionError("horrible error: tree sizes not equal, " + converted.size() + " vs " + simplified.size());
        }
        for (int i = 0; i < converted.size(); ++i) {
          if (!simplified.get(i).equals(converted.get(i))) {
            System.out.println("=============================");
            System.out.println(simplified.get(i));
            System.out.println("=============================");
            System.out.println(converted.get(i));
            System.out.println("=============================");
            throw new AssertionError("horrible error: tree " + i + " not equal for base tree " + tree);
          }
        }
      }
      return Pair.makePair(tree, cacher.convertToBytes(topParses));
    }

    @Override
    public ThreadsafeProcessor<Tree, Pair<Tree, byte[]>> newInstance() {
      // should be threadsafe
      return this;
    }
  }


  /**
   * An example of a command line is
   * <br>
   * java -mx1g edu.stanford.nlp.parser.dvparser.CacheParseHypotheses -model /scr/horatio/dvparser/wsjPCFG.nocompact.simple.ser.gz -output cached9.simple.ser.gz  -treebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-202
   * <br>
   * java -mx4g edu.stanford.nlp.parser.dvparser.CacheParseHypotheses -model ~/scr/dvparser/wsjPCFG.nocompact.simple.ser.gz -output cached.train.simple.ser.gz -treebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -numThreads 6
   * <br>
   * java -mx4g edu.stanford.nlp.parser.dvparser.CacheParseHypotheses -model ~/scr/dvparser/chinese/xinhuaPCFG.ser.gz -output cached.xinhua.train.ser.gz -treebank /afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed  026-270,301-499,600-999
   */
  public static void main(String[] args) throws IOException {
    String parserModel = null;
    String output = null;
    List<Pair<String, FileFilter>> treebanks = Generics.newArrayList();
    int dvKBest = 200;
    int numThreads = 1;
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-dvKBest")) {
        dvKBest = Integer.parseInt(args[argIndex + 1]);
        argIndex += 2;
        continue;
      }
      if (args[argIndex].equalsIgnoreCase("-parser") || args[argIndex].equals("-model")) {
        parserModel = args[argIndex + 1];
        argIndex += 2;
        continue;
      }
      if (args[argIndex].equalsIgnoreCase("-output")) {
        output = args[argIndex + 1];
        argIndex += 2;
        continue;
      }
      if (args[argIndex].equalsIgnoreCase("-treebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-treebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        treebanks.add(treebankDescription);
        continue;
      }
      if (args[argIndex].equalsIgnoreCase("-numThreads")) {
        numThreads = Integer.parseInt(args[argIndex + 1]);
        argIndex += 2;
        continue;
      }
      throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
    }

    if (parserModel == null) {
      throw new IllegalArgumentException("Need to supply a parser model with -model");
    }
    if (output == null) {
      throw new IllegalArgumentException("Need to supply an output filename with -output");
    }
    if (treebanks.isEmpty()) {
      throw new IllegalArgumentException("Need to supply a treebank with -treebank");
    }

    log.info("Writing output to " + output);
    log.info("Loading parser model " + parserModel);
    log.info("Writing " + dvKBest + " hypothesis trees for each tree");

    LexicalizedParser parser = LexicalizedParser.loadModel(parserModel, "-dvKBest", Integer.toString(dvKBest));
    CacheParseHypotheses cacher = new CacheParseHypotheses(parser);
    TreeTransformer transformer = DVParser.buildTrainTransformer(parser.getOp());
    List<Tree> sentences = new ArrayList<>();
    for (Pair<String, FileFilter> description : treebanks) {
      log.info("Reading trees from " + description.first);
      Treebank treebank = parser.getOp().tlpParams.memoryTreebank();
      treebank.loadPath(description.first, description.second);

      treebank = treebank.transform(transformer);
      sentences.addAll(treebank);
    }

    log.info("Processing " + sentences.size() + " trees");

    List<Pair<Tree, byte[]>> cache = Generics.newArrayList();
    transformer = new SynchronizedTreeTransformer(transformer);
    MulticoreWrapper<Tree, Pair<Tree, byte[]>> wrapper = new MulticoreWrapper<>(numThreads, new CacheProcessor(cacher, parser, dvKBest, transformer));
    for (Tree tree : sentences) {
      wrapper.put(tree);
      while (wrapper.peek()) {
        cache.add(wrapper.poll());
        if (cache.size() % 10 == 0) {
          System.out.println("Processed " + cache.size() + " trees");
        }
      }
    }
    wrapper.join();
    while (wrapper.peek()) {
      cache.add(wrapper.poll());
      if (cache.size() % 10 == 0) {
        System.out.println("Processed " + cache.size() + " trees");
      }
    }

    System.out.println("Finished processing " + cache.size() + " trees");

    IOUtils.writeObjectToFile(cache, output);
  }
}
