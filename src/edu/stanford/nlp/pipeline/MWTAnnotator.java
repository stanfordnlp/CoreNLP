package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;

import java.util.*;

/**
 * An Annotator for splitting tokens into words based on a dictionary, rules, or a statistical model.
 */

public class MWTAnnotator {

    /** mapping from an original token to a list of words **/
    public HashMap<String, List<String>> multiWordTokenMapping;

    public MWTAnnotator(String name, Properties props) {
        String prefix = (name != null && !name.equals("")) ? name+".mwt." : "mwt.";
        //System.out.println(prefix+"mappingFile");
        //System.out.println(props.getProperty(prefix+"mappingFile"));
        loadMultiWordTokenMappings(props.getProperty(prefix+"mappingFile"));
    }

    public void loadMultiWordTokenMappings(String mapFilePath) {
        // set up the HashMap
        multiWordTokenMapping = new HashMap<String, List<String>>();
        // read in entries from mapping file
        List<String> mapEntries = IOUtils.linesFromFile(mapFilePath);
        // load entries into the HashMap
        for (String mapEntry : mapEntries) {
            String originalWord = mapEntry.split("\t")[0];
            String[] finalWords = mapEntry.split("\t")[1].split(",");
            multiWordTokenMapping.put(originalWord, Arrays.asList(finalWords));
        }
    }

    public void annotate(Annotation annotation) {
        List<CoreLabel> finalTokens = new ArrayList<CoreLabel>();
        for (CoreLabel token : annotation.get(CoreAnnotations.TokensAnnotation.class)) {
            // check if token text is in the mapping
            if (multiWordTokenMapping.containsKey(token.word())) {
                //System.err.println("found match: "+token.word());
                int numWordsForToken = multiWordTokenMapping.get(token.word()).size();
                List<CoreLabel> newTokens = new ArrayList<CoreLabel>();
                for (String word : multiWordTokenMapping.get(token.word())) {
                    //System.err.println("splitting into: "+word);
                    CoreLabel newToken = new CoreLabel();
                    newToken.setWord(word);
                    newToken.setValue(word);
                    newToken.setOriginalText(word);
                    newToken.setIsNewline(false);
                    newToken.set(CoreAnnotations.ParentAnnotation.class,
                            token.get(CoreAnnotations.ParentAnnotation.class));
                    newToken.set(CoreAnnotations.TokenBeginAnnotation.class, finalTokens.size());
                    newToken.set(CoreAnnotations.TokenEndAnnotation.class, finalTokens.size() + 1);
                    newToken.setBeginPosition(token.beginPosition());
                    newToken.setEndPosition(token.endPosition());
                    newToken.set(CoreAnnotations.MWTTokenTextAnnotation.class, token.word());
                    newToken.set(CoreAnnotations.MWTTokenCharacterOffsetBeginAnnotation.class, token.beginPosition());
                    newToken.set(CoreAnnotations.MWTTokenCharacterOffsetEndAnnotation.class, token.endPosition());
                    newToken.setIsMWT(true);
                    finalTokens.add(newToken);
                }
            } else {
                CoreLabel newToken = new CoreLabel(token);
                newToken.set(CoreAnnotations.TokenBeginAnnotation.class, finalTokens.size());
                newToken.set(CoreAnnotations.TokenEndAnnotation.class, finalTokens.size() + 1);
                finalTokens.add(newToken);
            }
        }
        annotation.set(CoreAnnotations.TokensAnnotation.class, finalTokens);
    }
}
