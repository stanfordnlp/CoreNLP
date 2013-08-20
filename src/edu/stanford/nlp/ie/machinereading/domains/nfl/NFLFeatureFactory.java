package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.util.List;

import edu.stanford.nlp.ie.machinereading.RelationFeatureFactory;
import edu.stanford.nlp.ie.machinereading.structure.EntityMention;
import edu.stanford.nlp.ie.machinereading.structure.ExtractionObject;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.stats.Counter;

/**
 * @author mrsmith
 * 
 */
public class NFLFeatureFactory extends RelationFeatureFactory {

  private static final long serialVersionUID = 6617923038801836320L;
	private static final String[] defaultNFLBaselineFeatures;
	static {
		defaultNFLBaselineFeatures = new String[] { "arg_type", "arg_order", "entity_order_teams_scores",
				"entity_order_teams_scores_negative" };
	}

	public NFLFeatureFactory() {
	  super(defaultNFLBaselineFeatures);
	}
	
	public NFLFeatureFactory(String[] featureList) {
    super(featureList);
  }

	@Override
	public boolean addFeatures(Counter<String> features, RelationMention rel, List<String> types) {
	  EntityMention team = null;
	  EntityMention score = null;
		for (int i = 0; i < rel.getArgs().size(); i++) {
		  ExtractionObject arg = rel.getArgs().get(i);
			if (arg.getType().equals("NFLTeam") && arg instanceof EntityMention) {
				team = (EntityMention) arg;
			}
			if (arg.getType().equals("FinalScore") && arg instanceof EntityMention) {
				score = (EntityMention) arg;
			}
		}
		String teamPosFeature = null;
		String scorePosFeature = null;
		for (EntityMention otherarg : rel.getSentence().get(MachineReadingAnnotations.EntityMentionsAnnotation.class)) {
			if (otherarg.getType().equals("NFLTeam") && team != null && otherarg.getSyntacticHeadTokenPosition() != team.getSyntacticHeadTokenPosition()) {
				teamPosFeature = (otherarg.getSyntacticHeadTokenPosition() < team.getSyntacticHeadTokenPosition()) ? "not_first_team" : "not_last_team";

			}
			if (otherarg.getType().equals("FinalScore") && score != null && otherarg.getSyntacticHeadTokenPosition() != score.getSyntacticHeadTokenPosition()) {
				scorePosFeature = (otherarg.getSyntacticHeadTokenPosition() < score.getSyntacticHeadTokenPosition()) ? "not_first_score" : "not_last_score";
			}
		}
		// Features related to the order of teams and scores within the sentence
		if (types.contains("entity_order_teams_scores")) {
			if (teamPosFeature != null) {
				features.incrementCount(teamPosFeature, 1.0);
			}
			if (scorePosFeature != null) {
				features.incrementCount(scorePosFeature, 1.0);
			}

			if (teamPosFeature != null
					&& scorePosFeature != null
					&& ((teamPosFeature.equals("not_first_team") && scorePosFeature.equals("not_first_score")) || 
					    (teamPosFeature.equals("not_last_team") && scorePosFeature.equals("not_last_score")))) {
				features.incrementCount("team_and_score_positions_match", 1.0);
			}
		}

		if (types.contains("entity_order_teams_scores_negative")) {
			if ((teamPosFeature != null && scorePosFeature != null)
					&& ((teamPosFeature.equals("not_first_team") && scorePosFeature.equals("not_last_score")) || 
					    (teamPosFeature.equals("not_last_team") && scorePosFeature.equals("not_first_score")))) {
				features.incrementCount("team_and_score_positions_match", -1.0);
			}
		}

		super.addFeatures(features, rel, types);
		return true;
	}

}
