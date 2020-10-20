package edu.stanford.nlp.trees.ud;

/**
 * Set of Unviversal Dependencies Relations v2.
 *
 * @author Sebastian Schuster
 */

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.trees.GrammaticalRelation;


import java.util.HashMap;

import static edu.stanford.nlp.trees.GrammaticalRelation.*;


public class UniversalGrammaticalRelations {


    private UniversalGrammaticalRelations() {}



    public static final GrammaticalRelation NOMINAL_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj", "nominal subject", DEPENDENT);

    public static final GrammaticalRelation CLAUSAL_SUBJECT =
            new GrammaticalRelation(Language.Any, "csubj", "clausal subject", DEPENDENT);

    public static final GrammaticalRelation DIRECT_OBJECT =
            new GrammaticalRelation(Language.Any, "obj", "object", DEPENDENT);

    public static final GrammaticalRelation INDIRECT_OBJECT =
            new GrammaticalRelation(Language.Any, "iobj", "indirect object", DEPENDENT);

    public static final GrammaticalRelation CLAUSAL_COMPLEMENT =
            new GrammaticalRelation(Language.Any, "ccomp", "clausal complement", DEPENDENT);

    public static final GrammaticalRelation XCLAUSAL_COMPLEMENT =
            new GrammaticalRelation(Language.Any, "xcomp", "open clausal complement", DEPENDENT);

    public static final GrammaticalRelation OBLIQUE_MODIFIER =
            new GrammaticalRelation(Language.Any, "obl", "oblique modifier", DEPENDENT);

    public static final GrammaticalRelation VOCATIVE =
            new GrammaticalRelation(Language.Any, "vocative", "vocative", DEPENDENT);

    public static final GrammaticalRelation  EXPLETIVE =
            new GrammaticalRelation(Language.Any, "expl", "expletive", DEPENDENT);

    public static final GrammaticalRelation  DISLOCATED =
            new GrammaticalRelation(Language.Any, "dislocated", "dislocated", DEPENDENT);

    public static final GrammaticalRelation  ADV_CLAUSE_MODIFIER =
            new GrammaticalRelation(Language.Any, "advcl", "adverbial clause modifier", DEPENDENT);

    public static final GrammaticalRelation  ADVERBIAL_MODIFIER =
            new GrammaticalRelation(Language.Any, "advmod", "adverbial modifier", DEPENDENT);

    public static final GrammaticalRelation DISCOURSE_ELEMENT =
            new GrammaticalRelation(Language.Any, "discourse", "discourse element", DEPENDENT);

    public static final GrammaticalRelation AUX_MODIFIER =
            new GrammaticalRelation(Language.Any, "auxiliary", "auxiliary element", DEPENDENT);

    public static final GrammaticalRelation COPULA =
            new GrammaticalRelation(Language.Any, "cop", "copula", DEPENDENT);

    public static final GrammaticalRelation MARKER =
            new GrammaticalRelation(Language.Any, "mark", "marker", DEPENDENT);

    public static final GrammaticalRelation NOMINAL_MODIFIER =
            new GrammaticalRelation(Language.Any, "nmod", "nominal modifier", DEPENDENT);

    public static final GrammaticalRelation APPOSITIONAL_MODIFIER =
            new GrammaticalRelation(Language.Any, "appos", "appositional modifier", DEPENDENT);

    public static final GrammaticalRelation NUMERIC_MODIFIER =
            new GrammaticalRelation(Language.Any, "nummod", "numerical modifier", DEPENDENT);

    public static final GrammaticalRelation CLAUSAL_MODIFIER =
            new GrammaticalRelation(Language.Any, "acl", "clausal modifier of a noun (adjectival clause)", DEPENDENT);

    public static final GrammaticalRelation ADJECTIVAL_MODIFIER =
            new GrammaticalRelation(Language.Any, "amod", "adjectival modifier", DEPENDENT);

    public static final GrammaticalRelation DETERMINER =
            new GrammaticalRelation(Language.Any, "det", "determiner", DEPENDENT);

    public static final GrammaticalRelation CLASSIFIER =
            new GrammaticalRelation(Language.Any, "clf", "classifier", DEPENDENT);

    public static final GrammaticalRelation CASE_MARKER =
            new GrammaticalRelation(Language.Any, "case", "case marker", DEPENDENT);

    public static final GrammaticalRelation CONJUNCT =
            new GrammaticalRelation(Language.Any, "conj", "conjunct", DEPENDENT);

    public static final GrammaticalRelation COORDINATION =
            new GrammaticalRelation(Language.Any, "cc", "coordinating conjunction", DEPENDENT);

    public static final GrammaticalRelation FIXED =
            new GrammaticalRelation(Language.Any, "fixed", "fixed multiword expression", DEPENDENT);

    public static final GrammaticalRelation FLAT =
            new GrammaticalRelation(Language.Any, "flat", "flat multiword expression", DEPENDENT);

    public static final GrammaticalRelation COMPOUND_MODIFIER =
            new GrammaticalRelation(Language.Any, "compound", "compound", DEPENDENT);

    public static final GrammaticalRelation LIST =
            new GrammaticalRelation(Language.Any, "list", "list", DEPENDENT);

    public static final GrammaticalRelation PARATAXIS =
            new GrammaticalRelation(Language.Any, "parataxis", "parataxis", DEPENDENT);

    public static final GrammaticalRelation ORPHAN =
            new GrammaticalRelation(Language.Any, "orphan", "orphan", DEPENDENT);

    public static final GrammaticalRelation GOES_WITH =
            new GrammaticalRelation(Language.Any, "goeswith", "goes with", DEPENDENT);

    public static final GrammaticalRelation REPARANDUM =
            new GrammaticalRelation(Language.Any, "reparandum", "reparandum", DEPENDENT);

    public static final GrammaticalRelation PUNCTUATION =
            new GrammaticalRelation(Language.Any, "punct", "punctuation", DEPENDENT);

    public static final GrammaticalRelation NOMINAL_PASSIVE_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj:pass", "nominal passive subject", DEPENDENT);

    public static final GrammaticalRelation CLAUSAL_PASSIVE_SUBJECT =
            new GrammaticalRelation(Language.Any, "csubj:pass", "clausal passive subject", DEPENDENT);

    public static final GrammaticalRelation RELATIVE_CLAUSE =
            new GrammaticalRelation(Language.Any, "acl:relcl", "relative clause", DEPENDENT);

    public static final GrammaticalRelation PHRASAL_VERB_PARTICLE =
            new GrammaticalRelation(Language.Any, "compound:prt", "phrasal verb particle", DEPENDENT);

    public static final GrammaticalRelation AGENT =
            new GrammaticalRelation(Language.Any, "obl:agent", "agent", DEPENDENT);

    public static final GrammaticalRelation POSSESSOR =
            new GrammaticalRelation(Language.Any, "nmod:poss", "possessor", DEPENDENT);

    public static final GrammaticalRelation REFERENT =
            new GrammaticalRelation(Language.Any, "ref", "pronominal referent", DEPENDENT);


    public static final GrammaticalRelation CONTROLLING_NOMINAL_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj:xsubj", "controlling nominal subject", DEPENDENT);

    public static final GrammaticalRelation CONTROLLING_NOMINAL_PASSIVE_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj:pass:xsubj", "controlling nominal passive subject", DEPENDENT);


    public static final GrammaticalRelation RELATIVE_NOMINAL_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj:relsubj", "relative nominal subject", DEPENDENT);


    public static final GrammaticalRelation RELATIVE_NOMINAL_PASSIVE_SUBJECT =
            new GrammaticalRelation(Language.Any, "nsubj:pass:relsubj", "relative nominal passive subject", DEPENDENT);


    public static final GrammaticalRelation RELATIVE_OBJECT =
            new GrammaticalRelation(Language.Any, "obl:relobj", "relative object", DEPENDENT);


    private static HashMap<String,GrammaticalRelation> nmodRelations = new HashMap<>();
    private static HashMap<String,GrammaticalRelation> oblRelations = new HashMap<>();
    private static HashMap<String,GrammaticalRelation> aclRelations = new HashMap<>();
    private static HashMap<String,GrammaticalRelation> advclRelations = new HashMap<>();
    private static HashMap<String,GrammaticalRelation> conjRelations = new HashMap<>();

    public static final GrammaticalRelation getConj(String subtype) {
        return getSpecificReln(conjRelations, subtype, CONJUNCT);
    }

    public static final GrammaticalRelation getNmod(String subtype) {
        return getSpecificReln(nmodRelations, subtype, NOMINAL_MODIFIER);
    }

    public static final GrammaticalRelation getObl(String subtype) {
        return getSpecificReln(oblRelations, subtype, OBLIQUE_MODIFIER);
    }


    public static final GrammaticalRelation getAcl(String subtype) {
        return getSpecificReln(aclRelations, subtype, CLAUSAL_MODIFIER);
    }


    public static final GrammaticalRelation getAdvcl(String subtype) {
        return getSpecificReln(advclRelations, subtype, ADV_CLAUSE_MODIFIER);
    }

    private static final GrammaticalRelation getSpecificReln(HashMap<String,GrammaticalRelation> existingRelations,
                                                             String subtype, GrammaticalRelation parentRelation) {

        if ( ! existingRelations.containsKey(subtype)) {
            GrammaticalRelation reln = new GrammaticalRelation(Language.Any, parentRelation.getShortName(),
                    "subtyped " + parentRelation.getLongName(), parentRelation, subtype);
            existingRelations.put(subtype, reln);
        }

        return existingRelations.get(subtype);

    }






}
