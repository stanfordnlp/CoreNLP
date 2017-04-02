package edu.stanford.nlp.time;

import de.jollyday.HolidayManager;
import de.jollyday.config.Configuration;
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

  public Map<String, JollyHoliday> getAllHolidaysMap(Set<de.jollyday.config.Holiday> allHolidays) {
    Map<String, JollyHoliday> map = Generics.newHashMap();
    for (de.jollyday.config.Holiday h : allHolidays) {
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
    Set<de.jollyday.config.Holiday> s = getAllHolidays(config);
    return getAllHolidaysMap(s);
  }

  public CollectionValuedMap<String, JollyHoliday> getAllHolidaysCVMap(Set<de.jollyday.config.Holiday> allHolidays) {
    CollectionValuedMap<String, JollyHoliday> map = new CollectionValuedMap<>();
    for (de.jollyday.config.Holiday h:allHolidays) {
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
    Set<de.jollyday.config.Holiday> s = getAllHolidays(config);
    return getAllHolidaysCVMap(s);
  }

  public static void getAllHolidays(Holidays holidays, Set<de.jollyday.config.Holiday> allHolidays) {
    for (Method m : holidays.getClass().getMethods()) {
      if (isGetter(m) && m.getReturnType() == List.class) {
        try {
          List<de.jollyday.config.Holiday> l = (List<de.jollyday.config.Holiday>) m.invoke(holidays);
          allHolidays.addAll(l);
        } catch (Exception e) {
          throw new RuntimeException("Cannot create set of holidays.", e);
        }
      }
    }
  }

  public static void getAllHolidays(Configuration config, Set<de.jollyday.config.Holiday> allHolidays) {
    Holidays holidays = config.getHolidays();
    getAllHolidays(holidays, allHolidays);
    List<Configuration> subConfigs = config.getSubConfigurations();
    for (Configuration c:subConfigs) {
      getAllHolidays(c, allHolidays);
    }
  }

  public static Set<de.jollyday.config.Holiday> getAllHolidays(Configuration config) {
    Set<de.jollyday.config.Holiday> allHolidays = Generics.newHashSet();
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
    private final de.jollyday.config.Holiday base;
    private final String label;

    public JollyHoliday(String label, HolidayManager holidayManager, de.jollyday.config.Holiday base) {
      this.label = label;
      this.holidayManager = holidayManager;
      this.base = base;
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

    private SUTime.Time resolveWithYear(int year) {
      // TODO: If we knew location of article, can use that information to resolve holidays better
      Set<de.jollyday.Holiday> holidays = holidayManager.getHolidays(year);
      // Try to find this holiday
      for (de.jollyday.Holiday h : holidays) {
        if (h.getPropertiesKey().equals(base.getDescriptionPropertiesKey())) {
          return new SUTime.PartialTime(this, new Partial(h.getDate()));
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
