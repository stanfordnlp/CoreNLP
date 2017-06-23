package edu.stanford.nlp.international.spanish.process;

import edu.stanford.nlp.international.spanish.SpanishVerbStripper;
import edu.stanford.nlp.util.logging.Redwood;
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
public class AnCoraPronounDisambiguator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AnCoraPronounDisambiguator.class);

  public static enum PersonalPronounType {OBJECT, REFLEXIVE, UNKNOWN}

  private static final Set<String> ambiguousPersonalPronouns = new HashSet<>(Arrays.asList(
          "me", "te", "se", "nos", "os"
  ));

  /**
   * The following verbs always use ambiguous pronouns in a reflexive
   * sense in the corpus.
   */
  private static final Set<String> alwaysReflexiveVerbs = new HashSet<>(Arrays.asList(
          "acercar",
          "acostumbrar",
          "adaptar",
          "afeitar",
          "agarrar",
          "ahincar",
          "alegrar",
          "Anticipar",
          "aplicar",
          "aprobar",
          "aprovechar",
          "asegurar",
          "Atreve",
          "bajar",
          "beneficiar",
          "callar",
          "casar",
          "cobrar",
          "colocar",
          "comer",
          "comportar",
          "comprar",
          "concentrar",
          "cuidar",
          "deber",
          "decidir",
          "defender",
          "desplazar",
          "detectar",
          "divirtiendo",
          "echar",
          "encontrar",
          "enfrentar",
          "entender",
          "enterar",
          "entrometer",
          "equivocar",
          "escapar",
          "esconder",
          "esforzando",
          "establecer",
          "felicitar",
          "fija",
          "Fija",
          "ganar",
          "guarda",
          "guardar",
          "Habituar",
          "hacer",
          "imagina",
          "imaginar",
          "iniciar",
          "inscribir",
          "ir",
          "jode",
          "jugar",
          "Levantar",
          "Manifestar",
          "mantener",
          "marchar",
          "meter",
          "Negar",
          "obsesionar",
          "Olvida",
          "Olvidar",
          "olvidar",
          "oponer",
          "Para",
          "pasar",
          "plantear",
          "poner",
          "pudra",
          "queda",
          "quedar",
          "querer",
          "quita",
          "reciclar",
          "reconoce",
          "reconstruir",
          "recordar",
          "recuperar",
          "reencontrar",
          "referir",
          "registrar",
          "reincorporar",
          "rendir",
          "reservar",
          "retirar",
          "reunir",
          "sentar",
          "sentir",
          "someter",
          "subir",
          "tirando",
          "toma",
          "tomar",
          "tomen",
          "Une",
          "unir",
          "Ve",
          "vestir"
  ));

  /**
   * The following verbs always use ambiguous clitic pronouns in an
   * object sense **in the corpora supported.**
   *
   * This does not imply that the below verbs are only ever non-reflexive!
   * This list may need to be revised in order to produce correct gold trees
   * on new datasets.
   */
  private static final Set<String> neverReflexiveVerbs = new HashSet<>(Arrays.asList(
          "abrir",
          "aguar",
          "anunciar",
          "arrebatando",
          "arruinar",
          "clasificar",
          "compensar",
          "compra",
          "comprar",
          "concretar",
          "contar",
          "crea",
          "crear",
          "Cuente",
          "Decir",
          "decir",
          "deja",
          "digan",
          "devolver",
          "devuelve",
          "dirigiendo",
          "distraer",
          "enfrascar",
          "exigiendo",
          "exigir",
          "haz",
          "ignorar",
          "impedir",
          "insultar",
          "juzgar",
          "llamar",
          "llevando",
          "llevar",
          "manda",
          "mirar",
          "Miren",
          "multar",
          "negar",
          "ocultando",
          "pagar",
          "patear",
          "pedir",
          "permitir",
          "pidiendo",
          "preguntar",
          "prevenir",
          "quitar",
          "razona",
          "resultar",
          "saca",
          "sacar",
          "saludar",
          "seguir",
          "servir",
          "situar",
          "suceder",
          "tener",
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
          new HashMap<>();
  static {
    bruteForceDecisions.put(
            new Pair<>("contar", "No contarte mi vida nunca más"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("Creer", "Creerselo todo"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("creer", "creérselo todo ..."), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("creer", "creerte"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("Dar", "Darte de alta ahi"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("da", "A mi dame billetes uno al lado del otro que es la forma mas líquida que uno pueda estar"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("da", "danos UNA razon UNA"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("da", "y ... dame una razon por la que hubiera matado o se hubiera comido a el compañero ?"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dar", "darme cuenta"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dar", "darme la enhorabuena"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dar", "darnos cuenta"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dar", "darselo a la doña"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dar", "darte cuenta"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dar", "darte de alta"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dar", "darte vuelta en cuestiones que no tienen nada que ver con lo que comenzaste diciendo"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dar", "podría darnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dar", "puede darnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("decir", "suele decirnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("decir", "suelo decírmelo"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dejar", "debería dejarnos faenar"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dejar", "dejarme un intermitente encendido"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("dejar", ": dejarnos un país tan limpio en su gobierno como el cielo claro después de las tormentas mediterráneas , que inundan nuestras obras públicas sin encontrar nunca ni un solo responsable político de tanta mala gestión , ya sea la plaza de Cerdà socialista o los incendios forestales de la Generalitat"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("dejar", "podemos dejarnos adormecer"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("engañar", "engañarnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("estira", "=LRB= al menos estirate a los japoneses HDP !!! =RRB="),
      PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("explica", "explicame como hago"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("explicar", "deberá explicarnos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("liar", "liarme a tiros"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("librar", "librarme de el mismo para siempre"),
      PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("llevar", "llevarnos a una trampa en esta elección"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("manifestar", "manifestarme su solidaridad"),
      PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("manifestar", "manifestarnos sobre las circunstancias que mantienen en vilo la vida y obra de los colombianos"),
      PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("mirando", "estábamos mirándonos"), PersonalPronounType.REFLEXIVE);
    bruteForceDecisions.put(
            new Pair<>("poner", "ponerme en ascuas"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("servir", "servirme de guía"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("volver", "debe volvernos"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("volver", "deja de volverme"), PersonalPronounType.OBJECT);
    bruteForceDecisions.put(
            new Pair<>("volver", "volvernos"), PersonalPronounType.REFLEXIVE);
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
   * @param strippedVerb Stripped verb as returned by
   *                     {@link edu.stanford.nlp.international.spanish.SpanishVerbStripper#separatePronouns(String)}.
   * @param pronounIdx The index of the pronoun within
   *                   {@code strippedVerb.getPronouns()} which should be
   *                   disambiguated.
   * @param clauseYield A string representing the yield of the
   *                    clause which contains the given verb
   * @throws java.lang.IllegalArgumentException If the given pronoun is
   *         not ambiguous, or its disambiguation is not supported.
   */
  public static PersonalPronounType disambiguatePersonalPronoun(SpanishVerbStripper.StrippedVerb strippedVerb,
                                                                int pronounIdx, String clauseYield) {
    List<String> pronouns = strippedVerb.getPronouns();
    String pronoun = pronouns.get(pronounIdx).toLowerCase();
    if (!ambiguousPersonalPronouns.contains(pronoun))
      throw new IllegalArgumentException("We don't support disambiguating pronoun '" + pronoun + "'");

    if (pronouns.size() == 1 && pronoun.equalsIgnoreCase("se"))
      return PersonalPronounType.REFLEXIVE;

    String verb = strippedVerb.getStem();
    if (alwaysReflexiveVerbs.contains(verb))
      return PersonalPronounType.REFLEXIVE;
    else if (neverReflexiveVerbs.contains(verb))
      return PersonalPronounType.OBJECT;

    Pair<String, String> bruteForceKey = new Pair<>(verb, clauseYield);
    if (bruteForceDecisions.containsKey(bruteForceKey))
      return bruteForceDecisions.get(bruteForceKey);

    // Log this instance where a clitic pronoun could not be disambiguated.
    log.info("Failed to disambiguate: " + verb
             + "\nContaining clause:\t" + clauseYield + "\n");

    return PersonalPronounType.UNKNOWN;
  }

}
