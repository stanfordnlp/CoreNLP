package edu.stanford.nlp.quoteattribution.Sieves.MSSieves;

import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.quoteattribution.Person;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mention to Speaker mapping Sieve.
 *
 *  @author mjfang on 7/8/16.
 */
public abstract class MSSieve extends Sieve {

    public MSSieve(Annotation doc,
                   Map<String, List<Person>> characterMap,
                   Map<Integer,String> pronounCorefMap,
                   Set<String> animacyList) {
        super(doc, characterMap, pronounCorefMap, animacyList);
    }

    public abstract void doMentionToSpeaker(Annotation doc);

}
