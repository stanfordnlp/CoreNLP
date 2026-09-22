package edu.stanford.nlp.time;

import de.jollyday.HolidayManager;
import de.jollyday.config.Configuration;
import de.jollyday.config.Holiday;
import de.jollyday.config.Holidays;
// import de.jollyday.configuration.ConfigurationProvider;
import de.jollyday.impl.DefaultHolidayManager;
import de.jollyday.parameter.UrlManagerParameter;
import edu.stanford.nlp.ling.tokensregex.Env;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Generics;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Partial;

import edu.stanford.nlp.util.logging.Redwood;

import java.lang.reflect.Method;
// import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Wrapper around jollyday library so we can hook in holiday
 * configurations from jollyday with SUTime.
 *
 * @author Angel Chang
 */
public class JollyDayHolidays implements Env.Binder {

  private static final Redwood.RedwoodChannels logger = Redwood.channels(JollyDayHolidays.class);

  private HolidayManager holidayManager;
  // private CollectionValuedMap<String, JollyHoliday> holidays;
  private Map<String, JollyHoliday> holidays;
  private String varPrefix = "JH_";

  @Override
  public void init(String prefix, Properties props) {
    String xmlPath = props.getProperty(prefix + "xml", "edu/stanford/nlp/models/sutime/jollyday/Holidays_sutime.xml");
    String xmlPathType = props.getProperty(prefix + "pathtype", "classpath");
    varPrefix = props.getProperty(prefix + "prefix", varPrefix);
    logger.info("Initializing JollyDayHoliday for SUTime from " + xmlPathType + ' ' + xmlPath + " as " + prefix);
    Properties managerProps = new Properties();
    managerProps.setProperty("manager.impl", "edu.stanford.nlp.time.JollyDayHolidays$MyXMLManager");
    try {
      URL holidayXmlUrl;
      if (xmlPathType.equalsIgnoreCase("classpath")) {
        holidayXmlUrl = getClass().getClassLoader().getResource(xmlPath);
      } else if (xmlPathType.equalsIgnoreCase("file")) {
        holidayXmlUrl = new URL("file:///" + xmlPath);
      } else if (xmlPathType.equalsIgnoreCase("url")) {
        holidayXmlUrl = new URL(xmlPath);
      } else {
        throw new IllegalArgumentException("Unsupported " + prefix + "pathtype = " + xmlPathType);
      }
      UrlManagerParameter ump = new UrlManagerParameter(holidayXmlUrl, managerProps);
      holidayManager = HolidayManager.getInstance(ump);
    } catch (java.net.MalformedURLException e) {
      throw new RuntimeException(e);
    }
    if (!(holidayManager instanceof MyXMLManager)) {
      throw new AssertionError("Did not get back JollyDayHolidays$MyXMLManager");
    }
    Configuration config = ((MyXMLManager) holidayManager).getConfiguration();
    holidays = getAllHolidaysMap(config);
  }

  @Override
  public void bind(Env env) {
    if (holidays != null) {
      for (Map.Entry<String, JollyHoliday> holidayEntry : holidays.entrySet()) {
        JollyHoliday jh = holidayEntry.getValue();
        env.bind(varPrefix + holidayEntry.getKey(), jh);
      }
    }
  }

  public Map<String, JollyHoliday> getAllHolidaysMap(Set<Holiday> allHolidays) {
    Map<String, JollyHoliday> map = Generics.newHashMap();
    for (Holiday h : allHolidays) {
      String descKey = h.getDescriptionPropertiesKey();
      if (descKey != null) {
        descKey = descKey.replaceAll(".*\\.","");
        JollyHoliday jh = new JollyHoliday(descKey, holidayManager, h);
        map.put(jh.label, jh);
      }
    }
    return map;
  }

  public Map<String, JollyHoliday> getAllHolidaysMap(Configuration config) {
    Map<String, JollyHoliday> map = Generics.newHashMap();
    for (Map.Entry<Holiday, String[]> entry : getAllHolidaysByHierarchy(config).entrySet()) {
      String descKey = entry.getKey().getDescriptionPropertiesKey();
      if (descKey != null) {
        descKey = descKey.replaceAll(".*\\.", "");
        JollyHoliday jh = new JollyHoliday(descKey, holidayManager, entry.getKey(), entry.getValue());
        map.put(jh.label, jh);
      }
    }
    return map;
  }

  /**
   * Every holiday in the configuration, each mapped to the hierarchy path it was found
   * under. A holiday declared at the top level has an empty path; one inside nested
   * SubConfigurations has the hierarchy name of each enclosing configuration, outermost
   * first. The path is what the holiday manager needs in order to compute a date for it.
   */
  public static Map<Holiday, String[]> getAllHolidaysByHierarchy(Configuration config) {
    Map<Holiday, String[]> byHierarchy = new IdentityHashMap<>();
    collectHolidaysByHierarchy(config, EMPTY_HIERARCHY, byHierarchy);
    return byHierarchy;
  }

  private static void collectHolidaysByHierarchy(Configuration config, String[] path,
                                                 Map<Holiday, String[]> byHierarchy) {
    Set<Holiday> here = Generics.newHashSet();
    getAllHolidays(config.getHolidays(), here);
    for (Holiday h : here) {
      byHierarchy.put(h, path);
    }
    for (Configuration sub : config.getSubConfigurations()) {
      String[] subPath = Arrays.copyOf(path, path.length + 1);
      subPath[path.length] = sub.getHierarchy();
      collectHolidaysByHierarchy(sub, subPath, byHierarchy);
    }
  }

  static final String[] EMPTY_HIERARCHY = new String[0];

  public CollectionValuedMap<String, JollyHoliday> getAllHolidaysCVMap(Set<Holiday> allHolidays) {
    CollectionValuedMap<String, JollyHoliday> map = new CollectionValuedMap<>();
    for (Holiday h:allHolidays) {
      String descKey = h.getDescriptionPropertiesKey();
      if (descKey != null) {
        descKey = descKey.replaceAll(".*\\.","");
        JollyHoliday jh = new JollyHoliday(descKey, holidayManager, h);
        map.add(jh.label, jh);
      }
    }
    return map;
  }

  public CollectionValuedMap<String, JollyHoliday> getAllHolidaysCVMap(Configuration config) {
    Set<Holiday> s = getAllHolidays(config);
    return getAllHolidaysCVMap(s);
  }

  public static void getAllHolidays(Holidays holidays, Set<Holiday> allHolidays) {
    for (Method m : holidays.getClass().getMethods()) {
      if (isGetter(m) && m.getReturnType() == List.class) {
        try {
          List<Holiday> l = (List<Holiday>) m.invoke(holidays);
          allHolidays.addAll(l);
        } catch (Exception e) {
          throw new RuntimeException("Cannot create set of holidays.", e);
        }
      }
    }
  }

  public static void getAllHolidays(Configuration config, Set<Holiday> allHolidays) {
    Holidays holidays = config.getHolidays();
    getAllHolidays(holidays, allHolidays);
    List<Configuration> subConfigs = config.getSubConfigurations();
    for (Configuration c:subConfigs) {
      getAllHolidays(c, allHolidays);
    }
  }

  public static Set<Holiday> getAllHolidays(Configuration config) {
    Set<Holiday> allHolidays = Generics.newHashSet();
    getAllHolidays(config, allHolidays);
    return allHolidays;
  }

  private static boolean isGetter(Method method) {
    return method.getName().startsWith("get")
            && method.getParameterTypes().length == 0
            && !void.class.equals(method.getReturnType());
  }

  public static class MyXMLManager extends DefaultHolidayManager {
    public Configuration getConfiguration() {
      return configuration;
    }
  }

  public static class JollyHoliday extends SUTime.Time {

    private static final long serialVersionUID = -1479143694893729803L;

    private final HolidayManager holidayManager;
    private final Holiday base;
    private final String label;
    /** Hierarchy path of the configuration this holiday was declared in, outermost first. */
    private final String[] hierarchy;

    public JollyHoliday(String label, HolidayManager holidayManager, Holiday base) {
      this(label, holidayManager, base, EMPTY_HIERARCHY);
    }

    public JollyHoliday(String label, HolidayManager holidayManager, Holiday base,
                        String[] hierarchy) {
      this.label = label;
      this.holidayManager = holidayManager;
      this.base = base;
      this.hierarchy = (hierarchy != null) ? hierarchy : EMPTY_HIERARCHY;
    }

    @Override
    public String toFormattedString(int flags) {
      if (getTimeLabel() != null) {
        return getTimeLabel();
      }
      if ((flags & SUTime.FORMAT_ISO) != 0) {
        return null;
      }
      return label;
    }

    @Override
    public boolean isGrounded()  { return false; }

    @Override
    public SUTime.Time getTime() { return this; }

    // TODO: compute duration/range => uncertainty of this time
    @Override
    public SUTime.Duration getDuration() { return SUTime.DURATION_NONE; }

    @Override
    public SUTime.Range getRange(int flags, SUTime.Duration granularity) { return new SUTime.Range(this,this); }

    @Override
    public String toISOString() { return base.toString(); }

    @Override
    public SUTime.Time intersect(SUTime.Time t) {
      SUTime.Time resolved = resolve(t, 0);
      if (resolved != this) {
        return resolved.intersect(t);
      } else {
        return super.intersect(t);
      }
    }

    /** A holiday's date is fully specified, so it maps onto a year/month/day partial. */
    private static Partial toPartial(java.time.LocalDate date) {
      return new Partial(
          new DateTimeFieldType[] { DateTimeFieldType.year(),
                                    DateTimeFieldType.monthOfYear(),
                                    DateTimeFieldType.dayOfMonth() },
          new int[] { date.getYear(), date.getMonthValue(), date.getDayOfMonth() });
    }

    private SUTime.Time resolveWithYear(int year) {
      // TODO: If we knew location of article, can use that information to resolve holidays better
      // de.jollyday.Holiday is the computed occurrence with a date on it, as distinct from
      // the config Holiday imported above, which is the declaration read from the XML.
      Set<de.jollyday.Holiday> holidays = holidayManager.getHolidays(year, hierarchy);
      // Try to find this holiday
      for (de.jollyday.Holiday h : holidays) {
        if (h.getPropertiesKey().equals(base.getDescriptionPropertiesKey())) {
          return new SUTime.PartialTime(this, toPartial(h.getDate()));
        }
      }
      return null;
    }

    @Override
    public SUTime.Time resolve(SUTime.Time t, int flags) {
      Partial p = (t != null)? t.getJodaTimePartial():null;
      if (p != null) {
        if (JodaTimeUtils.hasField(p, DateTimeFieldType.year())) {
          int year = p.get(DateTimeFieldType.year());
          SUTime.Time resolved = resolveWithYear(year);
          if (resolved != null) {
            return resolved;
          }
        }
      }
      return this;
    }

    @Override
    public SUTime.Temporal next() {
      // TODO: Handle holidays that are not yearly
      return new SUTime.RelativeTime(
        new SUTime.RelativeTime(SUTime.TemporalOp.NEXT, SUTime.YEAR, SUTime.RESOLVE_TO_FUTURE),
        SUTime.TemporalOp.INTERSECT, this);
    }

    @Override
    public SUTime.Temporal prev() {
      // TODO: Handle holidays that are not yearly
      return new SUTime.RelativeTime(
        new SUTime.RelativeTime(SUTime.TemporalOp.PREV, SUTime.YEAR, SUTime.RESOLVE_TO_PAST),
          SUTime.TemporalOp.INTERSECT, this);
    }

    @Override
    public SUTime.Time add(SUTime.Duration offset) {
      return new SUTime.RelativeTime(this, SUTime.TemporalOp.OFFSET_EXACT, offset);
    }
  }

}
