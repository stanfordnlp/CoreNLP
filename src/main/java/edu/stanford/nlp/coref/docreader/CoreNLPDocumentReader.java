package edu.stanford.nlp.coref.docreader;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.CorefUtils;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.InputDoc;
import edu.stanford.nlp.coref.data.Mention;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Coref doc reader where annotations are stored as CoreNLP annotations
 *
 * @author <a href="mailto:*angel@eloquent.ai">Angel Chang</a>
 */
public class CoreNLPDocumentReader implements DocReader {
    /**
     * A logger for this class
     */
    protected static final Redwood.RedwoodChannels logger = Redwood.channels(CoreNLPDocumentReader.class);

    private AnnotationIterator docIterator;
    //  private String filepath;
    protected final List<File> fileList;
    private int curFileIndex;
    private final CoreNLPDocumentReader.Options options;
    private final Predicate<Pair<CorefChain.CorefMention, List<CoreLabel>>> filterCorefMentions;

    public CoreNLPDocumentReader(String filepath) {
        this(filepath, new CoreNLPDocumentReader.Options());
    }

    public CoreNLPDocumentReader(String filepath, CoreNLPDocumentReader.Options options) {
//    this.filepath = filepath;
        if (filepath != null && new File(filepath).exists()) {
            this.fileList = getFiles(filepath, options.filePattern);
        } else {
            this.fileList = Collections.EMPTY_LIST;
        }
        this.options = options;
        filterCorefMentions = options.filterCorefMentions;
        if (options.sortFiles) {
            Collections.sort(this.fileList);
        }
        curFileIndex = 0;
        if (filepath != null && new File(filepath).exists() && options.printLoadingMessage) {
            logger.info("Reading " + fileList.size() + " CoreNLP files from " + filepath);
        }
    }

    /** Flags **/
    public static class Options {

        public boolean printLoadingMessage = true;

        public Locale lang = Locale.ENGLISH;
        public Predicate<Pair<CorefChain.CorefMention, List<CoreLabel>>> filterCorefMentions;

        protected String fileFilter;
        protected Pattern filePattern;
        protected boolean sortFiles;

        public Options() {
            this(".json$");
        }

        public Options(String filter) {
            fileFilter = filter;
            filePattern = Pattern.compile(fileFilter);
        }

        public void setFilter(String filter) {
            fileFilter = filter;
            filePattern = Pattern.compile(fileFilter);
        }
    }

    private static List<File> getFiles(String filepath, Pattern filter) {
        Iterable<File> iter = IOUtils.iterFilesRecursive(new File(filepath), filter);
        List<File> fileList = new ArrayList<>();
        for (File f : iter) {
            fileList.add(f);
        }
        Collections.sort(fileList);
        return fileList;
    }

    public Annotation getNextDocument() {
        try {
            if (curFileIndex >= fileList.size()) return null;  // DONE!
            File curFile = fileList.get(curFileIndex);
            if (docIterator == null) {
                docIterator = new AnnotationIterator(curFile.getAbsolutePath());
            }
            while ( ! docIterator.hasNext()) {
                Redwood.log("debug-docreader", "Processed " + docIterator.getDocCnt() + " documents in " + curFile.getAbsolutePath());
                docIterator.close();
                curFileIndex++;
                if (curFileIndex >= fileList.size()) {
                    return null;  // DONE!
                }
                curFile = fileList.get(curFileIndex);
                docIterator = new AnnotationIterator(curFile.getAbsolutePath());
            }
            Annotation next = docIterator.next();
            Redwood.log("debug-docreader", "Reading document: " + next.get(CoreAnnotations.DocIDAnnotation.class));
            return next;
        } catch (IOException ex) {
            throw new RuntimeIOException(ex);
        }
    }

    @Override
    public InputDoc nextDoc() {
        Annotation annotation = getNextDocument();
        if (annotation == null) return null;

        Set<Triple<Integer,Integer,Integer>> filterMentionSpans = null;
        if (filterCorefMentions != null) {
//            filterMentionSpans = CorefUtils.getMatchingMentionsSpans(
//                    annotation, annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class).values(), filterCorefMentions, true);
            filterMentionSpans = CorefUtils.getMatchingSpans(annotation);
            final Set<Triple<Integer,Integer,Integer>> f = filterMentionSpans;
            Map<Integer,CorefChain> filtered = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class)
                    .values().stream().filter(x -> CorefUtils.filterCorefChainWithMentionSpans(x, f))
                    .collect(Collectors.toMap(x -> x.getChainID(), x -> x));
            annotation.set(CorefCoreAnnotations.CorefChainAnnotation.class, filtered);
        }

        // store some useful information in docInfo for later
        Map<String, String> docInfo = makeDocInfo(annotation);

        List<List<Mention>> allGoldMentions = extractGoldMentions(annotation);

        InputDoc doc = new InputDoc(annotation, docInfo, allGoldMentions, null);
        doc.filterMentionSet = filterMentionSpans;
        return doc;
    }

    public List<List<Mention>> extractGoldMentions(Annotation annotation) {
        // These are mentions that are in the annotation, may not actually be "gold" mentions
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        Map<Integer, CorefChain> corefChains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
        List<List<Mention>> mentionsBySentence = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            mentionsBySentence.add(new ArrayList<>());
        }
        for (CorefChain chain : corefChains.values()) {
            for (CorefChain.CorefMention mention : chain.getMentionsInTextualOrder()) {
                Mention m = toMention(mention, sentences);
                mentionsBySentence.get(m.sentNum).add(m);
            }
        }
        return mentionsBySentence;
    }

    public Mention toMention(CorefChain.CorefMention cm, List<CoreMap> sentences) {
        Mention m = new Mention();
//        m.mentionType = cm.mentionType;
//        m.number = cm.number;
//        m.gender = cm.gender;
//        m.animacy = cm.animacy;
//        m.mentionID = cm.mentionID;
        m.goldCorefClusterID = cm.corefClusterID;
        m.startIndex = cm.startIndex - 1;
        m.endIndex = cm.endIndex - 1;
        m.sentNum = cm.sentNum - 1;

        CoreMap sent = sentences.get(m.sentNum);
        m.originalSpan = sent.get(CoreAnnotations.TokensAnnotation.class).subList(m.startIndex, m.endIndex);
        return m;
    }

    // store any useful information for later (as features, debug, etc)
    private static Map<String, String> makeDocInfo(Annotation annotation) {
        Map<String, String> docInfo = Generics.newHashMap();
        docInfo.put("DOC_ID", annotation.get(CoreAnnotations.DocIDAnnotation.class));

        return docInfo;
    }


    @Override
    public void reset() {
        curFileIndex = 0;
        if (docIterator != null) {
            docIterator.close();
            docIterator = null;
        }
    }

}
