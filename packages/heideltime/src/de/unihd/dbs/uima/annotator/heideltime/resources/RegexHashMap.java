package de.unihd.dbs.uima.annotator.heideltime.resources;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Implements a HashMap extended with regular expression keys and caching functionality.
 *  
 * @author Julian Zell
 *
 */
public class RegexHashMap<T> implements Map<String, T> {
	
	private HashMap<String, T> container = new HashMap<String, T>();
	private HashMap<String, T> cache = new HashMap<String, T>();
	
	/**
	 * clears both the container and the cache hashmaps
	 */
	public void clear() {
		container.clear();
		cache.clear();
	}
	
	/**
	 * checks whether the cache or container contain a specific key, then evaluates the
	 * container's keys as regexes and checks whether they match the specific key.
	 */
	public boolean containsKey(Object key) {
		// the key is a direct hit from our cache
		if(cache.containsKey(key))
			return true;
		// the key is a direct hit from our hashmap
		if(container.containsKey(key))
			return true;

		// check if the requested key is a matching string of a regex key from our container
		Iterator<String> regexKeys = container.keySet().iterator();
		while(regexKeys.hasNext()) {
			if(Pattern.matches(regexKeys.next(), (String) key))
				return true;
		}
		
		// if the three previous tests yield no result, the key does not exist
		return false;
	}
	
	/**
	 * checks whether a specific value is container within either container or cache
	 */
	public boolean containsValue(Object value) {
		// the value is a direct hit from our cache
		if(cache.containsValue(value))
			return true;
		// the value is a direct hit from our hashmap
		if(container.containsValue(value))
			return true;
		
		// otherwise, the value isn't within this object
		return false;
	}
	
	/**
	 * returns a merged entryset containing within both the container and cache entrysets
	 */
	public Set<Entry<String, T>> entrySet() {
		// prepare the container
		HashSet<Entry<String, T>> set = new HashSet<Entry<String, T>>();
		// add the set from our container
		set.addAll(container.entrySet());
		// add the set from our cache
		set.addAll(cache.entrySet());
		
		return set;
	}
	
	/**
	 * checks whether the requested key has a direct match in either cache or container, and if it
	 * doesn't, also evaluates the container's keyset as regexes to match against the input key and
	 * if any of those methods yield a value, returns that value
	 * if a value is found doing regex evaluation, use that regex-key's match as a non-regex 
	 * key with the regex's value to form a new entry in the cache.
	 */
	public T get(Object key) {
		// output for requested key null is the value null; normal Map behavior
		if(key == null) return null;
		
		T result = null;
		if((result = cache.get(key)) != null) {
			// if the requested key maps to a value in the cache
			return result;
		} else if((result = container.get(key)) != null) {
			// if the requested key maps to a value in the container
			return result;
		} else {
			// check if the requested key is a matching string of a regex key from our container
			Iterator<Entry<String, T>> regexKeys = container.entrySet().iterator();
			while(regexKeys.hasNext()) {
				// prepare current entry
				Entry<String, T> entry = regexKeys.next();
				// check if the key is a regex matching the input key
				if(Pattern.matches(entry.getKey(), (String) key)) {
					putCache((String) key, entry.getValue());
					return entry.getValue();
				}
			}
		}
		
		// no value for the given key was found in any of container/cache/regexkey-container
		return null;
	}

	/**
	 * checks whether both container and cache are empty
	 */
	public boolean isEmpty() {
		return container.isEmpty() && cache.isEmpty();
	}
	
	/**
	 * returns the keysets of both the container and cache hashmaps 
	 */
	public Set<String> keySet() {
		// prepare container
		HashSet<String> set = new HashSet<String>();
		// add container keys
		set.addAll(container.keySet());
		// add cache keys
		set.addAll(cache.keySet());
		
		return set;
	}
	
	/**
	 * associates a key with a value in the container hashmap
	 */
	public T put(String key, T value) {
		return container.put(key, value);
	}
	
	/**
	 * associates a key with a value in the cache hashmap.
	 * @param key Key to map from
	 * @param value Value to map to
	 * @return previous value associated with the key, or null if unassociated before
	 */
	public T putCache(String key, T value) {
		return cache.put(key, value);
	}

	/**
	 * adds a map to the container
	 */
	public void putAll(Map<? extends String, ? extends T> m) {
		container.putAll(m);
	}

	/**
	 * removes a specific key's association from the container
	 */
	public T remove(Object key) {
		return container.remove(key);
	}
	
	/**
	 * returns the combined size of container and cache
	 */
	public int size() {
		return container.size() + cache.size();
	}

	/**
	 * returns the combined collection of both the values of the container as well as
	 * the cache.
	 */
	public Collection<T> values() {
		// prepare set
		HashSet<T> set = new HashSet<T>();
		// add all container values
		set.addAll(container.values());
		// add all cache values
		set.addAll(cache.values());
		
		return set;
	}	
}
