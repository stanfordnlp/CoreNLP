/**
 * <body>
 * <p>A collection of useful general-purpose utility classes. Below is a selection
 * of some of the most useful utility classes, along with a brief description and
 * sample use. Consult the class comments for more details on any of these classes.</p>
 * <p> edu.stanford.nlp.util.Counter</p>
 * <blockquote>
 * <p>Specialized Map for storing numeric counts for objects. Makes it easy to
 * get/set/increment the count of an object and find the max/argmax. Also makes
 * it easy to prune counts above/below a threshold and get the total count of all
 * or a subset of the objects. Exposes a Comparator that can sort the keySet or
 * entrySet by count.</p>
 * <p>Some useful methods: <code>argmax</code>, <code>averageCount</code>, <code>
 * comparator</code>, <code>incrementCount</code>, <code>keysAbove(threshold)</code>,
 * <code>max</code>, <code>normalize</code>, <code>totalCount</code></p>
 * <p>Example: generate a unigram language model for a Document with low counts
 * (&lt;3 counts) pruned:</p>
 * <p><code>Counter wordProbs = new Counter();<br>
 * for(int i = 0; i &lt; document.size(); i++) {<br>
 * &nbsp;&nbsp;&nbsp; wordProbs.incrementCount(document.get(i));<br>
 * <br>
 * wordProbs.removeAll(wordProbs.keysBelow(3)); // prune low counts<br>
 * wordProbs.normalize(); // convert to probability distribution</code></p>
 * <p>Example: find the Integer param that yields the best value of some <code>
 * computeScore</code> method (that returns an int or double):</p>
 * <p><code>Counter paramScores = new Counter();<br>
 * for(int param=0; param&lt;10; param++) {<br>
 * &nbsp;&nbsp;&nbsp; paramScores.setCount(new Integer(param), computeScore(param));<br>
 * <br>
 * Integer bestParam=(Integer)paramScores.argmax();</code></p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.Filter</p>
 * <blockquote>
 * <p>Interface to accept or reject Objects. A Filter implements <code>boolean
 * accept(Object obj)</code>. This can represent any binary predicate, such as
 * &quot;lowercase Strings&quot;, &quot;numbers above a threshold&quot;, &quot;trees where a VP dominates
 * an NP and PP&quot;, and so on. Particularly useful in conjunction with <code>
 * Filters</code>, which contains some basic filters as well as a method for
 * filtering an array of Objects or a Collection. Another example is Counter's
 * <code>totalCount(Filter)</code>, which returns the sum of all counts in the
 * Counter whose keys pass the filter.</p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.Filters</p>
 * <blockquote>
 * <p>Static class with some useful <code>Filter</code> implementations and
 * utility methods for working with Filters. Contains Filters that always accept
 * or reject, Filters that accept or reject an Object if it's in a given
 * Collection, as well as several composite Filters. Contains methods for
 * creating a new Filter that is the AND/OR of two Filters, or the NOT of a
 * Filter. You can make a Filter that runs a given <code>Appliable</code> on all
 * Objects before comparing them--this is useful when you have a collection of
 * complex objects and you want to accept/reject based on one of their
 * sub-objects or method values.&nbsp; Finally, you can filter an Object[]
 * through a Filter to return a new <code>Object[]</code> with only the accepeted
 * values, or <code>retainAll</code> elements in a Collection that pass a Filter.</p>
 * <p>Some useful methods: <code>andFilter(Filter, Filter)</code>, <code>
 * collectionAcceptFilter(Collection)</code>, <code>filter(Object[], Filter)</code>,&nbsp;
 * <code>retainAll(Collection, Filter)</code>, <code>transformedFilter(Filter,
 * Appliable)</code></p>
 * <p>Example: Filter an array of Strings to retain only those with length less
 * than 10:</p>
 * <p><code>Filter filter = new Filter() { <br>
 * &nbsp;&nbsp;&nbsp; public boolean accept(Object obj) { <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; return (((String)obj).length &lt; 10);
 * <br>
 * &nbsp;&nbsp;&nbsp; <br>
 * <br>
 * String[] shortStrings = (String[])Filters.filter(allStrings, filter);</code></p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.EntryValueComparator</p>
 * <blockquote>
 * <p>Comparator for sorting Map keys and entries. If you use the empty
 * Constructor, this Comparator will compare <code>Map.Entry</code> objects by
 * comparing their values. If you pass a <code>Map</code> into the constructor,
 * the Comparator can sort either the Map's keySet or entrySet. You can also pass
 * an <code>ascending</code> flag to optionally reverse natural sorting order.</p>
 * <p>Sort a Map's keys by their values (descending order): </p>
 * <p><code>List keys = new ArrayList(map.keySet());<br>
 * Collections.sort(keys, new EntryValueComparator(map, false));</code></p>
 * <p>Sort a Map's entries by their values (normal order):</p>
 * <p><code>List entries = new ArrayList(map.entrySet());<br>
 * Collections.sort(entries, new EntryValueComparator());</code></p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.Index</p>
 * <blockquote>
 * <p>List that also maintains a constant-time reverse-lookup of indices for its
 * Objects. Often one uses a List to associate a unique index with each Object
 * (e.g. controlled vocbulary, feature map, etc.). Index offers constant-time
 * performance for both i<code>ndex -&gt; Object</code> (<code>get</code>) and <code>
 * Object -&gt; index</code> (<code>indexOf</code>) as well as for <code>
 * contains(Object)</code>. Otherwise it behaves like a normal list. Index also
 * supports <code>lock()</code> and <code>unlock()</code> to ensure that it's
 * only modified when desired. Another useful method is <code>int[] indices(List
 * elems)</code>, which maps each elem to its index.</p>
 * <p>Some useful methods: <code>add(Object)</code>, <code>contains(Object)</code>,
 * <code>get(index)</code>, <code>indexOf(Object)</code>, <code>lock()</code></p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.StringUtils</p>
 * <blockquote>
 * <p>Static class with lots of useful String manipulation and formatting
 * methods. Many of these methods will be familiar to perl users: <code>join</code>,
 * <code>split</code>, <code>trim</code>, <code>find</code>, <code>lookingAt</code>,
 * and <code>matches</code>. There are also useful methods for padding
 * Strings/Objects with spaces on the right or left for printing even-width table
 * columns: <code>leftPad</code>, <code>pad</code>. Finally, there are
 * convenience methods for reading in all the text in a File or at a URL: <code>
 * slurpFile</code>, <code>slurpURL</code>, as well as a method for making a
 * &quot;clean&quot; filename from a String (where all spaces are turned into hyphens and
 * non-alphanum chars become underscores): <code>fileNameClean</code>.</p>
 * <p>Example: print a comma-separated list of numbers:</p>
 * <p><code>System.out.println(StringUtils.pad(nums, &quot;, &quot;));</code></p>
 * <p>Example: print a 2D array of numbers with 8-char cells:</p>
 * <p><code>for(int i = 0; i &lt; nums.length; i++) {<br>
 * &nbsp;&nbsp;&nbsp; for(int j = 0; j &lt; nums[i].length; j++) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 * System.out.print(StringUtils.leftPad(nums[i][j], 8));<br>
 * &nbsp;&nbsp;&nbsp; <br>
 * &nbsp;&nbsp;&nbsp; System.out.println();<br>
 * </code></p>
 * <p>Example: get a List of lines in a file (ignoring blank lines):</p>
 * <p><code>String fileContents = StringUtils.slurpFile(new File(&quot;filename&quot;));<br>
 * List lines = StringUtils.split(fileContents, &quot;[\r\n]+&quot;);</code></p>
 * </blockquote>
 * <p> edu.stanford.nlp.util.Timing</p>
 * <blockquote>
 * <p>Static class for measuring how long something takes to execute. To use,
 * call <code>startTime</code> before running the code in question. Call <code>
 * tick</code> to print an intermediate update, and <code>endTime</code> to
 * finish the timing and print the result. You can optionally pass a descriptive
 * string and <code>PrintStream</code> to <code>tick</code> and <code>endTime</code>
 * for more control over what gets printed where.</p>
 * <p>Example: time reading in a big file and transforming it:</p>
 * <p><code>Timing.startTime();<br>
 * String bigFileContents = StringUtils.slurpFile(bigFile);<br>
 * Timing.tick(&quot;read in big file&quot;, System.err);<br>
 * String output = costlyTransform(bigFileContents);<br>
 * Timing.endTime(&quot;transformed big file&quot;, System.err);</code></p>
 * </blockquote>
 * <p><b>Other packages with some useful utilies</b></p>
 * <dl>
 * <dt><code>edu.stanford.nlp.io</code></dt>
 * <dd>Contains some useful classes for traversing file systems to get lists of
 * files, writing encoded output, and so on.</dd>
 * <dt><code>edu.stanford.nlp.process</code></dt>
 * <dd>Contains many useful text-filtering classes (they work on Documents from
 * the dbm package).</dd>
 * <dt><code>edu.stanford.nlp.stats</code></dt>
 * <dd>Contains some useful classes for tracking statistics (counts) and
 * performing various calculations (e.g. precision/recall)</dd>
 * <dt><code>edu.stanford.nlp.swing</code></dt>
 * <dd>Contains utilities for working with Swing GUIs, e.g. adding icons to your
 * buttons, representing a GUI for properties, adding undo/redo support, adding
 * smart text selection, etc.</dd>
 * <dt><code>edu.stanford.nlp.web</code></dt>
 * <dd>Contains some classes for doing programmatic web searches and parsing web
 * pages.</dd>
 * </dl>
 * <p><b>Questionable classes in util</b></p>
 * <p>Numberer: this is sort of a duplicate of Index, but adds a level of
 * namespaces on top.  But it's widely used and doesn't quite seem worth
 * removing.</p>
 * </body>
 */
package edu.stanford.nlp.util;