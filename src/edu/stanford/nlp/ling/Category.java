package edu.stanford.nlp.ling;


/**
 * A <code>Category</code> object acts as a Label by containing a
 * <code>String</code> that is a category (nonterminal).
 *
 * @author Christopher Manning
 * @version 2000/12/20
 */
public class Category extends StringLabel implements HasCategory {

  /**
   * 
   */
  private static final long serialVersionUID = 7162506625143996046L;
  /**
   * The empty category.
   */
  public static final String EMPTYSTRING = "-NONE-";


  /**
   * Constructs a Category object.
   */
  public Category() {
    super();
  }

  /**
   * Constructs a Category object.
   *
   * @param category The name of the category
   */
  public Category(String category) {
    super(category);
  }


  /**
   * Creates a new category whose category value is the value of any
   * class that supports the <code>Label</code> interface.
   *
   * @param lab The label to be used as the basis of the new Category
   */
  public Category(Label lab) {
    super(lab);
  }


  public String category() {
    return value();
  }


  public void setCategory(String category) {
    setValue(category);
  }


  /**
   * A <code>CategoryFactory</code> acts as a factory for creating objects
   * of class <code>Category</code>
   */
  private static class CategoryFactory implements LabelFactory {

    public CategoryFactory() {
    }


    /**
     * Create a new <code>Category</code>, where the label is formed
     * from the <code>String</code> passed in.
     *
     * @param cat The cat that will go into the <code>Category</code>
     */
    public Label newLabel(String cat) {
      return new Category(cat);
    }


    /**
     * Create a new <code>Category</code>, where the label is formed
     * from the <code>String</code> passed in.
     *
     * @param cat     The cat that will go into the <code>Category</code>
     * @param options is ignored by a CategoryFactory
     */
    public Label newLabel(String cat, int options) {
      return new Category(cat);
    }


    /**
     * Create a new <code>Category</code>, where the label is formed
     * from the <code>String</code> passed in.
     *
     * @param cat The cat that will go into the <code>Category</code>
     */
    public Label newLabelFromString(String cat) {
      return new Category(cat);
    }


    /**
     * Create a new <code>Category Label</code>, where the label is
     * formed from
     * the <code>Label</code> object passed in.  Depending on what fields
     * each label has, other things will be <code>null</code>.
     *
     * @param oldLabel The Label that the new label is being created from
     * @return a new label of a particular type
     */
    public Label newLabel(Label oldLabel) {
      return new Category(oldLabel);
    }

  }


  // extra class guarantees correct lazy loading (Bloch p.194)
  private static class LabelFactoryHolder {

    private static final LabelFactory lf = new CategoryFactory();

  }


  /**
   * Return a factory for this kind of label
   * (i.e., <code>Category</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  @Override
  public LabelFactory labelFactory() {
    return LabelFactoryHolder.lf;
  }


  /**
   * Return a factory for this kind of label
   * (i.e., <code>Category</code>).
   * The factory returned is always the same one (a singleton).
   *
   * @return The label factory
   */
  public static LabelFactory factory() {
    return LabelFactoryHolder.lf;
  }

}
