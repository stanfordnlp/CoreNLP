package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.Extractor;
import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.NFLRelationMention;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;

public class BasicNFLInference implements Extractor {
  /*
   * Given a list of relations with teamInGame and teamScoringAll relations, generates the corresponding teamFinalScore, gameWinner/Loser relations
   * Since each pair of entities can only have one relation between them, we will miss many of the mutually exclusive relations.  This fills them in
   * and provides some basic sanity checks while simplifying the extraction process.  The list of relations is modified in place.
   */

  private static final long serialVersionUID = 1L;
  public static final Logger logger = Logger.getLogger(BasicNFLInference.class.getName());
  private final RelationMentionFactory relationMentionFactory = new NFLRelationMentionFactory();

  public void annotate(Annotation dataset) {
    int sentenceIndex = 0;
    for (CoreMap sent : dataset.get(CoreAnnotations.SentencesAnnotation.class)) {
      logger.fine("Postprocessing sentence " + sentenceIndex);
      List<EntityMention> entityMentions = sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      List<RelationMention> relationMentions = sent.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
      Collection<RelationMention> filteredMentions = RelationMention.filterUnrelatedRelations(relationMentions);
      int sizeBefore = filteredMentions.size();
      logger.fine("RELATIONS BEFORE INFERENCE:");
      for(RelationMention rel: filteredMentions) {
        logger.fine("RELATION: " + rel);
      }
      
      postProcessRelations(entityMentions, relationMentions);

      // unique the relation mentions since we may have generated existing relations if we aren't skipping certain relation types
      Set<NFLRelationMention> uniqueRelationMentions = new HashSet<NFLRelationMention>();
      for(RelationMention rel: relationMentions) uniqueRelationMentions.add((NFLRelationMention) rel);
      List<RelationMention> uniqueRelationMentionList = new ArrayList<RelationMention>();
      for(NFLRelationMention rel: uniqueRelationMentions) uniqueRelationMentionList.add(rel);

      sent.set(MachineReadingAnnotations.RelationMentionsAnnotation.class, uniqueRelationMentionList);
      
      Collection<RelationMention> filteredMentionsAfterInference = RelationMention.filterUnrelatedRelations(uniqueRelationMentionList);
      logger.fine("RELATIONS AFTER INFERENCE:");
      for(RelationMention rel: filteredMentionsAfterInference) {
        logger.fine("RELATION: " + rel);
      }
      
      logger.fine("Number of relations: before postprocessing: " + sizeBefore + ", after: " + filteredMentionsAfterInference.size());
      sentenceIndex++;
    }
  }
  
  public void postProcessRelations(List<EntityMention> entityMentions, Collection<RelationMention> relationMentions) {
    // the logic we'd like to implement:
    // teamInGame(team, game), teamScoringAll(team, score) -> teamFinalScore(game, score), gameWinner/Loser(team, game)

    // (1a) create NFLGame objects from NFL*Game entities
    Set<NFLGame> nflGames = new HashSet<NFLGame>();
    for (EntityMention entityMention: entityMentions) {
      if (entityMention.getType().endsWith("Game")) {
        NFLGame nflGame = new NFLGame(entityMention);

        if (!nflGames.contains(nflGame)) {
          logger.fine("Made NFLGame from " + entityMention);
          nflGames.add(nflGame);
        }
      }
    }
    // (1b) associate teams with games for all teamInGame(game, team) and gameWinner/Loser(game, team) relations
    for (RelationMention relation : RelationMention.filterUnrelatedRelations(relationMentions)) {
      logger.fine("Relation: " + relation);      
      String relType = relation.getType();
      if (relType.equals("teamInGame") || relType.equals("gameWinner") || relType.equals("gameLoser")) {
        EntityMention gameArg = (EntityMention) relation.getArg(0);
        NFLGame game = new NFLGame(gameArg);
        NFLGame nflGame = null;
        for (NFLGame candidateGame : nflGames) {
          if (candidateGame.equals(game)) {
            nflGame = candidateGame;
          }
        }
        if (nflGame == null) {
          nflGame = game;
        }
        
        EntityMention teamArg = (EntityMention) relation.getArg(1);
        logger.fine("Adding team: " + teamArg);
        nflGame.addTeam(teamArg);
      }
    }

    // (2) fill in scores from teamScoringAll(team, score) relations
    for (RelationMention relation : relationMentions) {
      if (relation.getType().equals("teamScoringAll")) {
        logger.fine("Found teamScoringAll: " + relation);
        EntityMention teamArg = (EntityMention) relation.getArg(1);
        // to find the game, we will search all games for ones that have our team -- in a perfect world, there would be only one
        // (we'll use the first one we find)
        NFLGame nflGame = findNFLGame(nflGames, teamArg);
        logger.fine("Corresponding NFLGame for teamScoringAll: " + nflGame);
        if (nflGame != null) {
          EntityMention scoreArg = (EntityMention) relation.getArg(0);
          logger.fine("Setting score: " + scoreArg);
          nflGame.setTeamScore(teamArg, scoreArg);
        }
      }
    }
    
    // (3) generate any missing relations
    for (NFLGame game : nflGames) {
      List<RelationMention> missingRelations = game.generateMissingRelations(relationMentionFactory);
      logger.info("Made " + missingRelations.size() + " new relations.");
      for (RelationMention relation: missingRelations) {
        logger.info("New relation: " + relation);  
      }
      relationMentions.addAll(missingRelations);
    }
  }

  private NFLGame findNFLGame(Set<NFLGame> nflGames, EntityMention teamArg) {
    NFLGame nflGame = null;
    for (NFLGame candidateNflGame : nflGames) {
      if (candidateNflGame.hasTeam(teamArg)) {
        nflGame = candidateNflGame;
        break;
      }
    }
    return nflGame;
  }
  
  /**
   * This class encapsulates some of the (very) basic logic about NFLGames -- games have a single winner and a single loser, e.g.
   *  Once given a subset of the relations, it can generate the rest.
   */
  public static class NFLGame {
    private EntityMention game;
    
    public EntityMention team1 = null, team2 = null;
    public EntityMention score1 = null, score2 = null;
    
    public NFLGame(EntityMention game) {
      this.game = game;
    }

    /**
     * Keyed from the entents of the NFLGame.
     */
    public int hashCode() {
      return game.getExtent().hashCode();
    }

    /**
     * Equality is only based on the extents of the NFLGame.
     */
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      NFLGame other = (NFLGame) obj;
      if (game == null) {
        if (other.game != null) {
          return false;
        }
      } else if (hashCode() != other.hashCode()) {
        return false;
      }
      return true;
    }

    public void addTeam(EntityMention arg) {
      if (team1 == null) {
        team1 = arg;
      } else {
        team2 = arg;
      }
    }

    public boolean hasTeam(EntityMention team) {
      return (team1 != null && team1.getExtent().equals(team.getExtent()) || 
          (team2 != null && team2.getExtent() == team.getExtent()));
    }

    public void setTeamScore(EntityMention team, EntityMention score) {
      if (team1 == team) {
        score1 = score;
      } else if (team2 == team) {
        score2 = score;
      } else {
        throw new RuntimeException("Failed sanity check.");
      }
    }
    
    /*
     * This makes the teamFinalScore and gameWinner/gameLoser relations.
     */
    public List<RelationMention> generateMissingRelations(RelationMentionFactory factory) {
      List<RelationMention> rels = new ArrayList<RelationMention>();
      
      if (score1 != null) {
        rels.add(generateTeamFinalScore(factory, score1));
      }
      if (score2 != null) {
        rels.add(generateTeamFinalScore(factory, score2));
      }
      if (team1 != null) {
        rels.add(generateTeamGameRelation(factory, team1, "teamInGame"));
      }
      if (team2 != null) {
        rels.add(generateTeamGameRelation(factory, team2, "teamInGame"));
      }
      
      // can't generate anything else if we don't have two scores
      if (score1 == null || score2 == null) {
        return rels;
      }
      
      int scoreValue1 = Integer.parseInt(score1.getExtentString());
      int scoreValue2 = Integer.parseInt(score2.getExtentString());
      EntityMention winner, loser;
      if (scoreValue1 > scoreValue2) {
        winner = team1;
        loser = team2;
      } else {
        winner = team2;
        loser = team1;
      }
      
      rels.add(generateTeamGameRelation(factory, winner, "gameWinner"));
      rels.add(generateTeamGameRelation(factory, loser, "gameLoser"));
      
      return rels;
    }

    private RelationMention generateTeamFinalScore(RelationMentionFactory factory, EntityMention score) {
      List<ExtractionObject> args = new ArrayList<ExtractionObject>();
      args.add(score);
      args.add(game);
      
      Span relExtent = new Span(game.getExtent(), score.getExtent());
      RelationMention finalScore = factory.constructRelationMention( 
        RelationMention.makeUniqueId(), game.getSentence(), relExtent, "teamFinalScore", null, args, null);
      setDefaultTypeProbs(finalScore);
      return finalScore;
    }

    /**
     * Makes relations between teams and games.  Set relationType to gameWinner, gameLoser, or teamInGame.
     */
    private RelationMention generateTeamGameRelation(RelationMentionFactory factory, EntityMention team, String relationType) {
      List<ExtractionObject> args = new ArrayList<ExtractionObject>();
      args.add(game);
      args.add(team);
      
      Span relExtent = new Span(game.getExtent(), team.getExtent());     
      RelationMention teamGameRelation = factory.constructRelationMention(RelationMention.makeUniqueId(), game.getSentence(), relExtent, relationType, null, args, null);
      setDefaultTypeProbs(teamGameRelation);
      return teamGameRelation;
    }
    
    private void setDefaultTypeProbs(RelationMention relation) {
      // using the variable name "probs" very loosely here...
      Counter<String> probs = new ClassicCounter<String>();
      probs.incrementCount(relation.getType(), 0.5);
      relation.setTypeProbabilities(probs);
    }
  }

  // stubs required by the Extractor interface
  public void save(String path) throws IOException {
  }

  public void setLoggerLevel(Level level) {
  }

  public void train(Annotation dataset) {
  }
}
