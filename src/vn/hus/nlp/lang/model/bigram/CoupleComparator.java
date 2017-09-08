package vn.hus.nlp.lang.model.bigram;

import java.util.Comparator;

/**
 * @author phuonglh
 *
 */
public class CoupleComparator implements Comparator<Couple> {

		public int compare(Couple c1, Couple c2) {
			return (c1.getFirst()+c1.getSecond()).compareTo(c2.getFirst()+c2.getSecond());
		}
		
	}
