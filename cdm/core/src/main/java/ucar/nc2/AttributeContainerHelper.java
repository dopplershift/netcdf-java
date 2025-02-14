/* Copyright */
package ucar.nc2;

import com.google.common.collect.ImmutableList;
import javax.annotation.concurrent.Immutable;
import ucar.nc2.util.Indent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

/**
 * Manages Collections of Attributes.
 * This is the mutable version, call toImmutable() for immutable version.
 * 
 * @author caron
 * @since 5/5/2015
 */
public class AttributeContainerHelper implements AttributeContainer {

  private final String name;
  private List<Attribute> atts;

  public AttributeContainerHelper(String name) {
    this.name = name;
    this.atts = new ArrayList<>();
  }

  public AttributeContainerHelper(String name, List<Attribute> from) {
    this(name);
    addAll(from);
  }

  @Override
  public String getName() {
    return name;
  }

  public void setImmutable() {
    this.atts = Collections.unmodifiableList(atts);
  }

  @Override
  public List<Attribute> getAttributes() {
    return atts;
  }

  @Override
  public Attribute addAttribute(Attribute att) {
    if (att == null)
      return null;
    for (int i = 0; i < atts.size(); i++) {
      Attribute a = atts.get(i);
      if (att.getShortName().equals(a.getShortName())) {
        atts.set(i, att); // replace
        return att;
      }
    }
    atts.add(att);
    return att;
  }

  /**
   * Add all; replace old if has same name
   */
  @Override
  public void addAll(Iterable<Attribute> atts) {
    for (Attribute att : atts)
      addAttribute(att);
  }

  @Override
  public String findAttValueIgnoreCase(String attName, String defaultValue) {
    String attValue = null;
    Attribute att = findAttributeIgnoreCase(attName);

    if ((att != null) && att.isString())
      attValue = att.getStringValue();

    if (null == attValue) // not found, use default
      attValue = defaultValue;

    return attValue;
  }

  @Override
  public Attribute findAttribute(String name) {
    for (Attribute a : atts) {
      if (name.equals(a.getShortName()))
        return a;
    }
    return null;
  }

  @Override
  public Attribute findAttributeIgnoreCase(String name) {
    for (Attribute a : atts) {
      if (name.equalsIgnoreCase(a.getShortName()))
        return a;
    }
    return null;
  }

  /**
   * Remove an Attribute : uses the attribute hashCode to find it.
   *
   * @param a remove this attribute
   * @return true if was found and removed
   */
  @Override
  public boolean remove(Attribute a) {
    return a != null && atts.remove(a);
  }

  /**
   * Remove an Attribute by name.
   *
   * @param attName if exists, remove this attribute
   * @return true if was found and removed
   */
  @Override
  public boolean removeAttribute(String attName) {
    Attribute att = findAttribute(attName);
    return att != null && atts.remove(att);
  }

  /**
   * Remove an Attribute by name, ignoring case
   *
   * @param attName if exists, remove this attribute
   * @return true if was found and removed
   */
  @Override
  public boolean removeAttributeIgnoreCase(String attName) {
    Attribute att = findAttributeIgnoreCase(attName);
    return att != null && atts.remove(att);
  }

  public static AttributeContainer filter(AttributeContainer atts, String... remove) {
    List<Attribute> result = new ArrayList<>();
    for (Attribute att : atts.getAttributes()) {
      boolean ok = true;
      for (String s : remove)
        if (att.getShortName().startsWith(s))
          ok = false;
      if (ok)
        result.add(att);
    }
    return new AttributeContainerHelper(atts.getName(), result);
  }

  public static void show(AttributeContainer atts, Indent indent, Formatter f) {
    for (Attribute att : atts.getAttributes()) {
      f.format("%s%s%n", indent, att);
    }
  }

  public AttributeContainer toImmutable() {
    return new AttributeContainerImmutable(name, atts);
  }

  @Immutable
  private static class AttributeContainerImmutable implements AttributeContainer {
    private final String name;
    private final ImmutableList<Attribute> atts;

    private AttributeContainerImmutable(String name, List<Attribute> atts) {
      this.name = name;
      this.atts = ImmutableList.copyOf(atts);
    }

    @Override
    public List<Attribute> getAttributes() {
      return atts;
    }

    @Override
    public void addAll(Iterable<Attribute> atts) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Attribute addAttribute(Attribute att) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String findAttValueIgnoreCase(String attName, String defaultValue) {
      return atts.stream().filter(a -> a.getShortName().equals(attName)).findFirst().map(Attribute::getStringValue)
          .orElse(defaultValue);
    }

    @Override
    public Attribute findAttribute(String attName) {
      return atts.stream().filter(a -> a.getShortName().equals(attName)).findFirst().orElse(null);
    }

    @Override
    public Attribute findAttributeIgnoreCase(String attName) {
      return atts.stream().filter(a -> a.getShortName().equalsIgnoreCase(attName)).findFirst().orElse(null);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean remove(Attribute a) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAttribute(String attName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAttributeIgnoreCase(String attName) {
      throw new UnsupportedOperationException();
    }
  }
}
