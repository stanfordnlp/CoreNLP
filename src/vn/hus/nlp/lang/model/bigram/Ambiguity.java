package vn.hus.nlp.lang.model.bigram;
	/**
	 * 
	 * @author LE Hong Phuong
	 * <p>
	 * 20 mars 07
	 * <p>
	 * vn.hus.tokenizer
	 * <p>
	 * Ambiguity group. The group contains a triple of tokens (in fact
	 * these are three syllables).
	 * 
	 */
	public class Ambiguity {
		/**
		 * First token
		 */
		String first;
		/**
		 * Second token
		 */
		String second;
		/**
		 * Third token
		 */
		String third;
		/**
		 * If <code>isFirstGroup</code> is <code>true</code>, the
		 * solution (first,second) is chosen, the method <code>getSelection()</code>
		 * will return the first two tokens, otherwise the last two tokens (second,third)
		 * is chosen.
		 */
		boolean isFirstGroup;
		
		public Ambiguity(String f, String s, String t) {
			this.first = f;
			this.second = s;
			this.third = t;
			// default solution is (first,second) group.
			isFirstGroup = true;
		}
	
		/**
		 * Update the <code>isFirstGroup</code> value.
		 * @param b 
		 */
		public void setIsFirstGroup(boolean b) {
			this.isFirstGroup = b;
		}
		/**
		 * Get the selection
		 * @return
		 */
		public boolean getIsFirstGroup() {
			return isFirstGroup;
		}
		
		/**
		 * Get a selection.
		 * @return
		 */
		public String[] getSelection() {
			String[] firstGroup = { first, second };
			String[] secondGroup = { second, third };
			if (isFirstGroup)
				return firstGroup;
			else
				return secondGroup; 
		}
		
		@Override
		public int hashCode() {
			return 2 * first.hashCode() + 3 * second.hashCode() + 5 * third.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Ambiguity))
				return false;
			Ambiguity a = (Ambiguity)obj;
			return first.equalsIgnoreCase(a.first) && 
				second.equalsIgnoreCase(a.second) && third.equalsIgnoreCase(a.third); 
		}
		
		@Override
		public String toString() {
			return "(" + first + "," + second + "," + third + ")";
		}
		
	}
	
