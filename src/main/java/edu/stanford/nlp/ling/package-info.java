/**
 * <p>This package contains the different data structures used by
 * JavaNLP throughout the years for dealing with linguistic objects in general,
 * of which words are the most generally used.  Most data structures in this
 * package are deprecated.  The current recommendation is to represent an
 * annotated word as a CoreMap (e.g., an ArrayCoreMap) from the util package.
 *
 * <p>CoreMap is a basic type-safe data structure that maps
 * keys to corresponding values, where each value's type must be consistent with
 * the key's definition.  The CoreAnnotations class in this package contains
 * many common annotations used by different portions of JavaNLP, but you can
 * define new keys locally to a package if they aren't of general applicability.
 * See the CoreMap unit tests for an example usage of CoreMap and of defining
 * a key.
 *
 * <p>The oldest code in JavaNLP uses various types of ValueLabel, and
 * might expect data types from the Has* family (like HasWord, HasTag, et
 * al., denoting presence or absence of that particular annotation).  Second
 * generation code made use of the MapLabel family (including AbstractMapLabel,
 * FeatureLabel, and IndexedFeatureLabel), but this code has all been converted
 * across to use CoreLabel.  More modern code will use CoreMap
 * as its basic data structure.  CoreLabel is a CoreMap that unifies all the
 * families of interfaces into a single view of an underlying (Array)CoreMap.
 *
 * <p>It is recommended that new code use the ArrayCoreMap class from the util
 * package as the base representation of a word when possible.  Any CoreMap
 * can be presented as one of the older data structures (MapLabel, HasWord,
 * etc.), by simply wrapping it in a CoreLabel "view" with
 * CoreLabel.forCoreMap(map).
 *
 * <p><i>Legacy description:</i> Classes for linguistic concepts which are common
 * to many NLP classes, such as  Word,  Tag, etc.  Also contains classes for
 * building and operating on documents and data collections. Two of the
 * basic interfaces are  Document for representing a document as a
 * list of words with meta-data, and  DataCollection for
 * representing a collection of documents. The most common document class
 * you will probably use is  BasicDocument, which provides support
 * for constructing documents from a variety of input sources.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author dramage
 * @author rafferty
 */
package edu.stanford.nlp.ling;