package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;

import java.util.*;

/**
 * A utility for preprocessing the AnCora Spanish corpus.
 *
 * Attempts to disambiguate Spanish personal pronouns which have
 * multiple senses:
 *
 *     <em>me, te, se, nos, os</em>
 *
 * Each of these can be used as 1) an indirect object pronoun or as
 * 2) a reflexive pronoun. (<em>me, te, nos,</em> and <em>os</em> can
 * also be used as direct object pronouns.)
 *
 * For the purposes of corpus preprocessing, all we need is to
 * distinguish between the object- and reflexive-pronoun cases.
 *
 * Disambiguation is done first by (dictionary-powered) heuristics, and
 * then by brute force. The brute-force decisions are manual tags for
 * verbs with clitic pronouns which appear in the AnCora corpus.
 *
 * @author Jon Gauthier
 * @see edu.stanford.nlp.trees.international.spanish.SpanishTreeNormalizer
 */
public class SpanishPronounDisambiguator {

  public static enum PersonalPronounType {OBJECT, REFLEXIVE, UNKNOWN}

  private static final Set<String> ambiguousPersonalPronouns = new HashSet<String>() {{
    add("me"); add("te"); add("se"); add("nos"); add("os");
  }};

  /**
   * The following verbs always use ambiguous pronouns in a reflexive
   * sense in the corpus.
   */
  private static final Set<String> alwaysReflexiveVerbs = new HashSet<String>() {{
    add("acercar");
    add("acostumbrar");
    add("afeitar");
    add("ahincar");
    add("Anticipar");
    add("aplicar");
    add("aprovechar");
    add("Atreve");
    add("callar");
    add("casar");
    add("colocar");
    add("comportar");
    add("comprar");
    add("deber");
    add("desplazar");
    add("detectar");
    add("divirtiendo");
    add("echar");
    add("encontrar");
    add("enfrentar");
    add("entender");
    add("enterar");
    add("equivocar");
    add("esconder");
    add("esforzando");
    add("felicitar");
    add("fija");
    add("ganar");
    add("Habituar");
    add("hacer");
    add("imaginar");
    add("iniciar");
    add("ir");
    add("jugar");
    add("Levantar");
    add("Manifestar");
    add("mantener");
    add("marchar");
    add("Negar");
    add("obsesionar");
    add("Olvidar");
    add("olvidar");
    add("oponer");
    add("Para");
    add("pasar");
    add("plantear");
    add("poner");
    add("quedar");
    add("querer");
    add("reciclar");
    add("reconoce");
    add("reconstruir");
    add("recuperar");
    add("referir");
    add("rendir");
    add("reservar");
    add("reunir");
    add("sentar");
    add("sentir");
    add("someter");
    add("tirando");
    add("tomar");
    add("unir");
    add("Ve");
    add("vestir");
  }};

  /**
   * The following verbs always use ambiguous clitic pronouns in an
   * object sense in the corpus.
   */
  private static final Set<String> neverReflexiveVerbs = new HashSet<String>() {{
    add("aguar");
    add("anunciar");
    add("arrebatando");
    add("arruinar");
    add("clasificar");
    add("concretar");
    add("contar");
    add("crea");
    add("Cuente");
    add("Decir");
    add("devolver");
    add("devuelve");
    add("dirigiendo");
    add("distraer");
    add("exigiendo");
    add("exigir");
    add("haz");
    add("ignorar");
    add("impedir");
    add("llevar");
    add("mirar");
    add("multar");
    add("negar");
    add("ocultando");
    add("pidiendo");
    add("prevenir");
    add("quitar");
    add("resultar");
    add("saludar");
    add("servir");
    add("situar");
    add("tutear");
    add("utilizar");
    add("vender");
    add("ver");
    add("visitar");
  }};

  /**
   * Brute-force: based on clauses which we recognize from AnCora,
   * dictate the type of pronoun being used
   *
   * Map from pair (verb, containing clause) to personal pronoun type
   */
  @SuppressWarnings("unchecked")
  private static final Map<Pair<String, String>, PersonalPronounType> bruteForceDecisions =
    new HashMap<Pair<String, String>, PersonalPronounType>() {{
      put(new Pair("contar", "No contarte mi vida nunca más"), PersonalPronounType.OBJECT);
      put(new Pair("Creer", "Creerselo todo"), PersonalPronounType.REFLEXIVE);
      put(new Pair("creer", "creérselo todo ..."), PersonalPronounType.REFLEXIVE);
      put(new Pair("dar", "darme la enhorabuena"), PersonalPronounType.OBJECT);
      put(new Pair("dar", "darnos cuenta"), PersonalPronounType.REFLEXIVE);
      put(new Pair("dar", "podría darnos"), PersonalPronounType.OBJECT);
      put(new Pair("dar", "puede darnos"), PersonalPronounType.OBJECT);
      put(new Pair("decir", "suele decirnos"), PersonalPronounType.OBJECT);
      put(new Pair("decir", "suelo decírmelo"), PersonalPronounType.REFLEXIVE);
      put(new Pair("dejar", "debería dejarnos faenar"), PersonalPronounType.OBJECT);
      put(new Pair("dejar", "dejarme un intermitente encendido"), PersonalPronounType.REFLEXIVE);
      put(new Pair("dejar", ": dejarnos un país tan limpio en su gobierno como el cielo claro después de las tormentas mediterráneas , que inundan nuestras obras públicas sin encontrar nunca ni un solo responsable político de tanta mala gestión , ya sea la plaza de Cerdà socialista o los incendios forestales de la Generalitat"), PersonalPronounType.OBJECT);
      put(new Pair("dejar", "podemos dejarnos adormecer"), PersonalPronounType.REFLEXIVE);
      put(new Pair("engañar", "engañarnos"), PersonalPronounType.OBJECT);
      put(new Pair("explicar", "deberá explicarnos"), PersonalPronounType.OBJECT);
      put(new Pair("liar", "liarme a tiros"), PersonalPronounType.REFLEXIVE);
      put(new Pair("llevar", "llevarnos a una trampa en esta elección"), PersonalPronounType.OBJECT);
      put(new Pair("manifestar", "manifestarme su solidaridad"), PersonalPronounType.OBJECT);
      put(new Pair("manifestar", "manifestarnos sobre las circunstancias que mantienen en vilo la vida y obra de los colombianos"), PersonalPronounType.REFLEXIVE);
      put(new Pair("mirando", "estábamos mirándonos"), PersonalPronounType.REFLEXIVE);
      put(new Pair("poner", "ponerme en ascuas"), PersonalPronounType.OBJECT);
      put(new Pair("servir", "servirme de guía"), PersonalPronounType.OBJECT);
      put(new Pair("volver", "debe volvernos"), PersonalPronounType.OBJECT);
      put(new Pair("volver", "deja de volverme"), PersonalPronounType.OBJECT);
      put(new Pair("volver", "volvernos"), PersonalPronounType.REFLEXIVE);
    }};

  /**
   * Determine if the given pronoun can have multiple senses.
   */
  public static boolean isAmbiguous(String pronoun) {
    return ambiguousPersonalPronouns.contains(pronoun);
  }

  /**
   * Determine whether the given clitic pronoun is an indirect object
   * pronoun or a reflexive pronoun.
   *
   * This method is only defined when the pronoun is one of
   *
   *     me, te, se, nos, os
   *
   * i.e., those in which the meaning is actually ambiguous.
   *
   * @param splitVerb The verb with clitics split off, as returned by
   *                  {@link edu.stanford.nlp.international.spanish.SpanishVerbStripper#separatePronouns(String)}.
   * @param pronounIdx The index of the pronoun within
   *                   {@code splitVerb.second()} which should be
   *                   disambiguated.
   * @param clauseYield A string representing the yield of the
   *                    clause which contains the given verb
   * @throws java.lang.IllegalArgumentException If the given pronoun is
   *         not ambiguous, or its disambiguation is not supported.
   */
  public static PersonalPronounType disambiguatePersonalPronoun(Pair<String, List<String>> splitVerb, int pronounIdx,
                                                                String clauseYield) {
    List<String> pronouns = splitVerb.second();
    String pronoun = pronouns.get(pronounIdx).toLowerCase();
    if (!ambiguousPersonalPronouns.contains(pronoun))
      throw new IllegalArgumentException("We don't support disambiguating pronoun '" + pronoun + "'");

    if (pronouns.size() == 1 && pronoun.equalsIgnoreCase("se"))
      return PersonalPronounType.REFLEXIVE;

    String verb = splitVerb.first();
    if (alwaysReflexiveVerbs.contains(verb))
      return PersonalPronounType.REFLEXIVE;
    else if (neverReflexiveVerbs.contains(verb))
      return PersonalPronounType.OBJECT;

    Pair<String, String> bruteForceKey = new Pair<String, String>(verb, clauseYield);
    if (bruteForceDecisions.containsKey(bruteForceKey))
      return bruteForceDecisions.get(bruteForceKey);

    // DEV
    System.err.println("DISAMB " + splitVerb.first() + "\nDISAMB\t" + clauseYield);

    return PersonalPronounType.UNKNOWN;
  }

}
