package edu.stanford.nlp.international.spanish.process;

import edu.stanford.nlp.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class AnCoraPronounDisambiguator {

  public static enum PersonalPronounType {OBJECT, REFLEXIVE, UNKNOWN}

  private static final Set<String> ambiguousPersonalPronouns = new HashSet<String>(Arrays.asList(
    "me", "te", "se", "nos", "os"
  ));

  /**
   * The following verbs always use ambiguous pronouns in a reflexive
   * sense in the corpus.
   */
  private static final Set<String> alwaysReflexiveVerbs = new HashSet<String>(Arrays.asList(
    "acercar",
    "acostumbrar",
    "afeitar",
    "ahincar",
    "Anticipar",
    "aplicar",
    "aprovechar",
    "Atreve",
    "callar",
    "casar",
    "colocar",
    "comportar",
    "comprar",
    "deber",
    "desplazar",
    "detectar",
    "divirtiendo",
    "echar",
    "encontrar",
    "enfrentar",
    "entender",
    "enterar",
    "equivocar",
    "esconder",
    "esforzando",
    "felicitar",
    "fija",
    "ganar",
    "Habituar",
    "hacer",
    "imaginar",
    "iniciar",
    "ir",
    "jugar",
    "Levantar",
    "Manifestar",
    "mantener",
    "marchar",
    "Negar",
    "obsesionar",
    "Olvidar",
    "olvidar",
    "oponer",
    "Para",
    "pasar",
    "plantear",
    "poner",
    "quedar",
    "querer",
    "reciclar",
    "reconoce",
    "reconstruir",
    "recuperar",
    "referir",
    "rendir",
    "reservar",
    "reunir",
    "sentar",
    "sentir",
    "someter",
    "tirando",
    "tomar",
    "unir",
    "Ve",
    "vestir"
  ));

  /**
   * The following verbs always use ambiguous clitic pronouns in an
   * object sense in the corpus.
   */
  private static final Set<String> neverReflexiveVerbs = new HashSet<String>(Arrays.asList(
    "aguar",
    "anunciar",
    "arrebatando",
    "arruinar",
    "clasificar",
    "concretar",
    "contar",
    "crea",
    "Cuente",
    "Decir",
    "devolver",
    "devuelve",
    "dirigiendo",
    "distraer",
    "exigiendo",
    "exigir",
    "haz",
    "ignorar",
    "impedir",
    "llevar",
    "mirar",
    "multar",
    "negar",
    "ocultando",
    "pidiendo",
    "prevenir",
    "quitar",
    "resultar",
    "saludar",
    "servir",
    "situar",
    "tutear",
    "utilizar",
    "vender",
    "ver",
    "visitar"
  ));

  /**
   * Brute-force: based on clauses which we recognize from AnCora,
   * dictate the type of pronoun being used
   *
   * Map from pair (verb, containing clause) to personal pronoun type
   */
  @SuppressWarnings("unchecked")
  private static final Map<Pair<String, String>, PersonalPronounType> bruteForceDecisions =
    new HashMap<Pair<String, String>, PersonalPronounType>();
  static {
    bruteForceDecisions.put(
      new Pair<String, String>("contar", "No contarte mi vida nunca más"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("Creer", "Creerselo todo"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("creer", "creérselo todo ..."), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("dar", "darme la enhorabuena"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("dar", "darnos cuenta"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("dar", "podría darnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("dar", "puede darnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("decir", "suele decirnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("decir", "suelo decírmelo"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("dejar", "debería dejarnos faenar"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("dejar", "dejarme un intermitente encendido"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("dejar", ": dejarnos un país tan limpio en su gobierno como el cielo claro después de las tormentas mediterráneas , que inundan nuestras obras públicas sin encontrar nunca ni un solo responsable político de tanta mala gestión , ya sea la plaza de Cerdà socialista o los incendios forestales de la Generalitat"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("dejar", "podemos dejarnos adormecer"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("engañar", "engañarnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("explicar", "deberá explicarnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("liar", "liarme a tiros"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("llevar", "llevarnos a una trampa en esta elección"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("manifestar", "manifestarme su solidaridad"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("manifestar", "manifestarnos sobre las circunstancias que mantienen en vilo la vida y obra de los colombianos"),
      PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("mirando", "estábamos mirándonos"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
      new Pair<String, String>("poner", "ponerme en ascuas"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("servir", "servirme de guía"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("volver", "debe volvernos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("volver", "deja de volverme"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
      new Pair<String, String>("volver", "volvernos"), PersonalPronounType.REFLEXIVE);
  }

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
