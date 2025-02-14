/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.ma2.DataType;
import ucar.nc2.util.Indent;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Collections;

/**
 * A Group is a logical collection of Variables.
 * The Groups in a Dataset form a hierarchical tree, like directories on a disk.
 * A Group has a name and optionally a set of Attributes.
 * There is always at least one Group in a dataset, the root Group, whose name is the empty string.
 * <p>
 * Immutable if setImmutable() was called.
 *
 * TODO Groups will be immutable in 6.
 * TODO Groups will not have a reference to their owning NetcdfFile in 6.
 * 
 * @author caron
 */
public class Group extends CDMNode implements AttributeContainer {

  @Deprecated
  static List<Group> collectPath(Group g) {
    List<Group> list = new ArrayList<>();
    while (g != null) {
      list.add(0, g);
      g = g.getParentGroup();
    }
    return list;
  }

  /**
   * Is this the root group?
   *
   * @return true if root group
   */
  public boolean isRoot() {
    return getParentGroup() == null;
  }

  /**
   * Get the "short" name, unique within its parent Group.
   *
   * @return group short name
   */
  public String getShortName() {
    return shortName;
  }

  /**
   * Get the Variables contained directly in this group.
   *
   * @return List of type Variable; may be empty, not null.
   */
  public java.util.List<Variable> getVariables() {
    return variables;
  }

  /**
   * Find the Variable with the specified (short) name in this group.
   *
   * @param varShortName short name of Variable within this group.
   * @return the Variable, or null if not found
   */
  public Variable findVariable(String varShortName) {
    if (varShortName == null)
      return null;

    for (Variable v : variables) {
      if (varShortName.equals(v.getShortName()))
        return v;
    }
    return null;
  }

  /**
   * Find the Variable with the specified (short) name in this group or a parent group.
   *
   * @param varShortName short name of Variable.
   * @return the Variable, or null if not found
   */
  public Variable findVariableOrInParent(String varShortName) {
    if (varShortName == null)
      return null;

    Variable v = findVariable(varShortName);
    Group parent = getParentGroup();
    if ((v == null) && (parent != null))
      v = parent.findVariableOrInParent(varShortName);
    return v;
  }

  /**
   * Get the Groups contained directly in this Group.
   *
   * @return List of type Group; may be empty, not null.
   */
  public java.util.List<Group> getGroups() {
    return groups;
  }

  /**
   * Get the owning NetcdfFile
   *
   * @return owning NetcdfFile.
   */
  public NetcdfFile getNetcdfFile() {
    return ncfile;
  }

  /**
   * Retrieve the Group with the specified (short) name.
   *
   * @param groupShortName short name of the nested group you are looking for.
   * @return the Group, or null if not found
   */
  public Group findGroup(String groupShortName) {
    if (groupShortName == null)
      return null;
    // groupShortName = NetcdfFile.makeNameUnescaped(groupShortName);

    for (Group group : groups) {
      if (groupShortName.equals(group.getShortName()))
        return group;
    }

    return null;
  }

  /**
   * Get the shared Dimensions contained directly in this group.
   *
   * @return List of type Dimension; may be empty, not null.
   */
  public java.util.List<Dimension> getDimensions() {
    return dimensions;
  }

  /**
   * Create a dimension list using dimension names. The dimension is searched for recursively in the parent groups.
   *
   * @param dimString : whitespace separated list of dimension names, or '*' for Dimension.UNKNOWN, or number for anon
   *        dimension. null or empty String is a scalar.
   * @return list of dimensions
   * @throws IllegalArgumentException if cant find dimension or parse error.
   */
  public List<Dimension> makeDimensionsList(String dimString) throws IllegalArgumentException {
    List<Dimension> newDimensions = new ArrayList<>();
    if (dimString == null) // scalar
      return newDimensions; // empty list
    dimString = dimString.trim();
    if (dimString.isEmpty()) // scalar
      return newDimensions; // empty list

    StringTokenizer stoke = new StringTokenizer(dimString);
    while (stoke.hasMoreTokens()) {
      String dimName = stoke.nextToken();
      Dimension d;
      if (dimName.equals("*"))
        d = Dimension.VLEN;
      else
        d = this.findDimension(dimName);
      if (d == null) {
        // if numeric - then its anonymous dimension
        try {
          int len = Integer.parseInt(dimName);
          d = Dimension.builder().setLength(len).setIsShared(false).build();
        } catch (Exception e) {
          throw new IllegalArgumentException("Dimension " + dimName + " does not exist");
        }
      }
      newDimensions.add(d);
    }

    return newDimensions;
  }

  /**
   * Get the enumerations contained directly in this group.
   *
   * @return List of type EnumTypedef; may be empty, not null.
   */
  public java.util.List<EnumTypedef> getEnumTypedefs() {
    return enumTypedefs;
  }

  /**
   * Retrieve a Dimension using its (short) name. If it doesnt exist in this group,
   * recursively look in parent groups.
   *
   * @param name Dimension name.
   * @return the Dimension, or null if not found
   */
  public Dimension findDimension(String name) {
    if (name == null)
      return null;
    // name = NetcdfFile.makeNameUnescaped(name);
    Dimension d = findDimensionLocal(name);
    if (d != null)
      return d;
    Group parent = getParentGroup();
    if (parent != null)
      return parent.findDimension(name);

    return null;
  }

  /**
   * Find a Dimension in this or a parent Group, using equals.
   *
   * @param dim Dimension .
   * @return the Dimension, or null if not found
   */
  public Dimension findDimension(Dimension dim) {
    if (dim == null) {
      return null;
    }
    for (Dimension d : dimensions) {
      if (d.equals(dim)) {
        return d;
      }
    }
    Group parent = getParentGroup();
    if (parent != null) {
      return parent.findDimension(dim);
    }
    return null;
  }

  /**
   * Retrieve a Dimension using its (short) name, in this group only
   *
   * @param name Dimension name.
   * @return the Dimension, or null if not found
   */
  public Dimension findDimensionLocal(String name) {
    if (name == null)
      return null;
    for (Dimension d : dimensions) {
      if (name.equals(d.getShortName()))
        return d;
    }

    return null;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // AttributeHelper

  public java.util.List<Attribute> getAttributes() {
    return AttributeContainerHelper.filter(attributes, Attribute.SPECIALS).getAttributes();
  }

  public Attribute findAttribute(String name) {
    return attributes.findAttribute(name);
  }

  public Attribute findAttributeIgnoreCase(String name) {
    return attributes.findAttributeIgnoreCase(name);
  }

  public String findAttValueIgnoreCase(String attName, String defaultValue) {
    return attributes.findAttValueIgnoreCase(attName, defaultValue);
  }

  /** @deprecated Use Group.builder() */
  @Deprecated
  public Attribute addAttribute(Attribute att) {
    return attributes.addAttribute(att);
  }

  /** @deprecated Use Group.builder() */
  @Deprecated
  public void addAll(Iterable<Attribute> atts) {
    attributes.addAll(atts);
  }

  /** @deprecated Use Group.builder() */
  @Deprecated
  public boolean remove(Attribute a) {
    return attributes.remove(a);
  }

  /** @deprecated Use Group.builder() */
  @Deprecated
  public boolean removeAttribute(String attName) {
    return attributes.removeAttribute(attName);
  }

  /** @deprecated Use Group.builder() */
  @Deprecated
  public boolean removeAttributeIgnoreCase(String attName) {
    return attributes.removeAttributeIgnoreCase(attName);
  }

  ////////////////////////////////////////////////////////////////////////

  /**
   * Find an Enumeration Typedef using its (short) name. If it doesnt exist in this group,
   * recursively look in parent groups.
   *
   * @param name Enumeration name.
   * @return the Enumeration, or null if not found
   */
  public EnumTypedef findEnumeration(String name) {
    if (name == null)
      return null;
    // name = NetcdfFile.makeNameUnescaped(name);
    for (EnumTypedef d : enumTypedefs) {
      if (name.equals(d.getShortName()))
        return d;
    }
    Group parent = getParentGroup();
    if (parent != null)
      return parent.findEnumeration(name);

    return null;
  }

  /**
   * Get the common parent of this and the other group.
   * Cant fail, since the root group is always a parent of any 2 groups.
   *
   * @param other the other group
   * @return common parent of this and the other group
   */
  public Group commonParent(Group other) {
    if (isParent(other))
      return this;
    if (other.isParent(this))
      return other;
    while (!other.isParent(this))
      other = other.getParentGroup();
    return other;
  }

  /**
   * Is this a parent of the other Group?
   *
   * @param other another Group
   * @return true is it is equal or a parent
   */
  public boolean isParent(Group other) {
    while ((other != this) && (other.getParentGroup() != null))
      other = other.getParentGroup();
    return (other == this);
  }

  //////////////////////////////////////////////////////////////////////////////////////

  /**
   * Get String with name and attributes. Used in short descriptions like tooltips.
   *
   * @return name and attributes String.
   */
  public String getNameAndAttributes() {
    StringBuilder sbuff = new StringBuilder();
    sbuff.append("Group ");
    sbuff.append(getShortName());
    sbuff.append("\n");
    for (Attribute att : attributes.getAttributes()) {
      sbuff.append("  ").append(getShortName()).append(":");
      sbuff.append(att);
      sbuff.append(";");
      sbuff.append("\n");
    }
    return sbuff.toString();
  }

  /**
   * CDL representation.
   *
   * @param strict if true, write in strict adherence to CDL definition.
   * @return CDL representation.
   */
  public String writeCDL(boolean strict) {
    Formatter buf = new Formatter();
    writeCDL(buf, new Indent(2), strict);
    return buf.toString();
  }

  protected void writeCDL(Formatter out, Indent indent, boolean strict) {
    boolean hasE = (!enumTypedefs.isEmpty());
    boolean hasD = (!dimensions.isEmpty());
    boolean hasV = (!variables.isEmpty());
    // boolean hasG = (groups.size() > 0);
    boolean hasA = (!attributes.getAttributes().isEmpty());

    if (hasE) {
      out.format("%stypes:%n", indent);
      indent.incr();
      for (EnumTypedef e : enumTypedefs) {
        e.writeCDL(out, indent, strict);
        out.format("%n");
      }
      indent.decr();
      out.format("%n");
    }

    if (hasD) {
      out.format("%sdimensions:%n", indent);
      indent.incr();
      for (Dimension myd : dimensions) {
        myd.writeCDL(out, indent, strict);
        out.format("%n");
      }
      indent.decr();
    }

    if (hasV) {
      out.format("%svariables:%n", indent);
      indent.incr();
      for (Variable v : variables) {
        v.writeCDL(out, indent, false, strict);
        out.format("%n");
      }
      indent.decr();
    }

    for (Group g : groups) {
      String gname = strict ? NetcdfFile.makeValidCDLName(g.getShortName()) : g.getShortName();
      out.format("%sgroup: %s {%n", indent, gname);
      indent.incr();
      g.writeCDL(out, indent, strict);
      indent.decr();
      out.format("%s}%n%n", indent);
    }

    // if (hasA && (hasE || hasD || hasV || hasG))
    // out.format("%n");

    if (hasA) {
      if (isRoot())
        out.format("%s// global attributes:%n", indent);
      else
        out.format("%s// group attributes:%n", indent);

      for (Attribute att : attributes.getAttributes()) {
        // String name = strict ? NetcdfFile.escapeNameCDL(getShortName()) : getShortName();
        if (!Attribute.isspecial(att)) {
          out.format("%s", indent);
          att.writeCDL(out, strict, null);
          out.format(";");
          if (!strict && (att.getDataType() != DataType.STRING))
            out.format(" // %s", att.getDataType());
          out.format("%n");
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////

  /**
   * Constructor
   *
   * @param ncfile NetcdfFile owns this Group
   * @param parent parent of Group. If null, this is the root Group.
   * @param shortName short name of Group.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public Group(NetcdfFile ncfile, Group parent, String shortName) {
    super(shortName);
    this.ncfile = ncfile;
    this.attributes = new AttributeContainerHelper(shortName);
    setParentGroup(parent == null ? ncfile.getRootGroup() : parent);
  }

  /**
   * Set the Group's parent Group
   *
   * @param parent parent group.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public void setParentGroup(Group parent) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    super.setParentGroup(parent == null ? ncfile.getRootGroup() : parent);
  }


  /**
   * Set the short name, converting to valid CDM object name if needed.
   *
   * @param shortName set to this value
   * @return valid CDM object name
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public String setName(String shortName) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    setShortName(shortName);
    return getShortName();
  }

  /**
   * Adds the specified shared dimension to this group.
   *
   * @param dim the dimension to add.
   * @throws IllegalStateException if this dimension is {@link #setImmutable() immutable}.
   * @throws IllegalArgumentException if {@code dim} isn't shared or a dimension with {@code dim}'s name already
   *         exists within the group.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public void addDimension(Dimension dim) {
    if (immutable)
      throw new IllegalStateException("Cant modify");

    if (!dim.isShared()) {
      throw new IllegalArgumentException("Dimensions added to a group must be shared.");
    }

    if (findDimensionLocal(dim.getShortName()) != null)
      throw new IllegalArgumentException(
          "Dimension name (" + dim.getShortName() + ") must be unique within Group " + getShortName());

    dimensions.add(dim);
    dim.setGroup(this);
  }

  /**
   * Adds the specified shared dimension to this group, but only if another dimension with the same name doesn't
   * already exist.
   *
   * @param dim the dimension to add.
   * @return {@code true} if {@code dim} was successfully added to the group. Otherwise, {@code false} will be returned,
   *         meaning that a dimension with {@code dim}'s name already exists within the group.
   * @throws IllegalStateException if this dimension is {@link #setImmutable() immutable}.
   * @throws IllegalArgumentException if {@code dim} isn't shared.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean addDimensionIfNotExists(Dimension dim) {
    if (immutable)
      throw new IllegalStateException("Cant modify");

    if (!dim.isShared()) {
      throw new IllegalArgumentException("Dimensions added to a group must be shared.");
    }

    if (findDimensionLocal(dim.getShortName()) != null)
      return false;

    dimensions.add(dim);
    dim.setGroup(this);
    return true;
  }

  /**
   * Add a nested Group
   *
   * @param g add this Group.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public void addGroup(Group g) {
    if (immutable)
      throw new IllegalStateException("Cant modify");

    if (findGroup(g.getShortName()) != null)
      throw new IllegalArgumentException(
          "Group name (" + g.getShortName() + ") must be unique within Group " + getShortName());

    groups.add(g);
    g.setParentGroup(this); // groups are a tree - only one parent
  }

  /**
   * Add an Enumeration
   *
   * @param e add this Enumeration.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public void addEnumeration(EnumTypedef e) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    if (e == null)
      return;
    e.setParentGroup(this);
    enumTypedefs.add(e);
  }

  /**
   * Add a Variable
   *
   * @param v add this Variable.
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public void addVariable(Variable v) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    if (v == null)
      return;

    if (findVariable(v.getShortName()) != null) {
      // Variable other = findVariable(v.getShortName()); // debug
      throw new IllegalArgumentException(
          "Variable name (" + v.getShortName() + ") must be unique within Group " + getShortName());
    }

    variables.add(v);
    v.setParentGroup(this); // variable can only be in one group
  }

  /**
   * Remove an Dimension : uses the dimension hashCode to find it.
   *
   * @param d remove this Dimension.
   * @return true if was found and removed
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean remove(Dimension d) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    return d != null && dimensions.remove(d);
  }

  /**
   * Remove an Attribute : uses the Group hashCode to find it.
   *
   * @param g remove this Group.
   * @return true if was found and removed
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean remove(Group g) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    return g != null && groups.remove(g);
  }

  /**
   * Remove a Variable : uses the variable hashCode to find it.
   *
   * @param v remove this Variable.
   * @return true if was found and removed
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean remove(Variable v) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    return v != null && variables.remove(v);
  }

  /**
   * remove a Dimension using its name, in this group only
   *
   * @param dimName Dimension name.
   * @return true if dimension found and removed
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean removeDimension(String dimName) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    for (int i = 0; i < dimensions.size(); i++) {
      Dimension d = dimensions.get(i);
      if (dimName.equals(d.getShortName())) {
        dimensions.remove(d);
        return true;
      }
    }
    return false;
  }

  /**
   * remove a Variable using its (short) name, in this group only
   *
   * @param shortName Variable name.
   * @return true if Variable found and removed
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public boolean removeVariable(String shortName) {
    if (immutable)
      throw new IllegalStateException("Cant modify");
    for (int i = 0; i < variables.size(); i++) {
      Variable v = variables.get(i);
      if (shortName.equals(v.getShortName())) {
        variables.remove(v);
        return true;
      }
    }
    return false;
  }

  /**
   * Make this immutable.
   *
   * @return this
   * @deprecated Use Group.builder()
   */
  @Deprecated
  public Group setImmutable() {
    super.setImmutable();
    variables = Collections.unmodifiableList(variables);
    dimensions = Collections.unmodifiableList(dimensions);
    groups = Collections.unmodifiableList(groups);
    attributes.setImmutable();
    return this;
  }

  @Override
  public String toString() {
    return writeCDL(false);
  }

  /**
   * Instances which have same name and parent are equal.
   */
  @Override
  public boolean equals(Object oo) {
    if (this == oo)
      return true;
    if (!(oo instanceof Group))
      return false;
    Group og = (Group) oo;
    if (!getShortName().equals(og.getShortName()))
      return false;
    return !((getParentGroup() != null) && !getParentGroup().equals(og.getParentGroup()));
  }

  /**
   * Override Object.hashCode() to implement equals.
   */
  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 37 * result + getShortName().hashCode();
      if (getParentGroup() != null)
        result = 37 * result + getParentGroup().hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  /**
   * Create groups to ensure path is defined
   *
   * @param ncf the containing netcdf file object
   * @param path the path to the desired group
   * @param ignorelast true => ignore last element in the path
   * @return the Group, or null if not found
   * @deprecated do not use
   */
  @Deprecated
  public Group makeRelativeGroup(NetcdfFile ncf, String path, boolean ignorelast) {
    path = path.trim();
    path = path.replace("//", "/");
    boolean isabsolute = (path.charAt(0) == '/');
    if (isabsolute)
      path = path.substring(1);

    // iteratively create path
    String[] pieces = path.split("/");
    if (ignorelast)
      pieces[pieces.length - 1] = null;

    Group current = (isabsolute ? ncfile.getRootGroup() : this);
    for (String name : pieces) {
      if (name == null)
        continue;
      String clearname = NetcdfFile.makeNameUnescaped(name); // ??
      Group next = current.findGroup(clearname);
      if (next == null) {
        next = new Group(ncf, current, clearname);
        current.addGroup(next);
      }
      current = next;
    }
    return current;
  }

  ////////////////////////////////////////////////////////////////
  // TODO make private final and immutable in 6
  protected NetcdfFile ncfile;
  protected List<Variable> variables = new ArrayList<>();
  protected List<Dimension> dimensions = new ArrayList<>();
  protected List<Group> groups = new ArrayList<>();
  protected AttributeContainerHelper attributes;
  protected List<EnumTypedef> enumTypedefs = new ArrayList<>();
  private int hashCode;

  public Group(Builder builder, Group parent) {
    super(builder.shortName);
    this.group = parent;
    this.ncfile = builder.ncfile;

    builder.dimensions.forEach(d -> d.setGroup(this)); // LOOK remove in 6
    this.dimensions = new ArrayList<>(builder.dimensions);
    this.enumTypedefs = new ArrayList<>(builder.enumTypedefs);

    // only the root group build() should be called, the rest get called recursively
    this.groups = builder.gbuilders.stream().map(g -> g.build(this)).collect(Collectors.toList());

    builder.vbuilders.forEach(v -> {
      v.setGroup(this);
      // dont override ncfile if its been set.
      if (v.ncfile == null) {
        v.setNcfile(this.ncfile);
      }
    });
    for (Variable.Builder<?> vb : builder.vbuilders) {
      Variable var = vb.build();
      this.variables.add(var);
    }

    this.attributes = builder.attributes;

    // This needs to go away in 6.
    this.dimensions.forEach(d -> d.setParentGroup(this));
    this.enumTypedefs.forEach(e -> e.setParentGroup(this));
  }

  public Builder toBuilder() {
    Builder builder =
        builder().setName(this.shortName).setNcfile(this.ncfile).addAttributes(this.attributes.getAttributes())
            .addDimensions(this.dimensions).addEnumTypedefs(this.enumTypedefs);

    this.groups.forEach(g -> builder.addGroup(g.toBuilder()));
    this.variables.forEach(v -> builder.addVariable(v.toBuilder()));

    return builder;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    static private final Logger logger = LoggerFactory.getLogger(Builder.class);

    public List<Group.Builder> gbuilders = new ArrayList<>();
    public List<Variable.Builder<?>> vbuilders = new ArrayList<>();
    public String shortName;
    private NetcdfFile ncfile; // set by NetcdfFile.build()
    private AttributeContainerHelper attributes = new AttributeContainerHelper("");
    private List<Dimension> dimensions = new ArrayList<>();
    private List<EnumTypedef> enumTypedefs = new ArrayList<>();
    private boolean built;

    public Builder addAttribute(Attribute att) {
      Preconditions.checkNotNull(att);
      attributes.addAttribute(att);
      return this;
    }

    public Builder addAttributes(Iterable<Attribute> atts) {
      Preconditions.checkNotNull(atts);
      attributes.addAll(atts);
      return this;
    }

    public AttributeContainerHelper getAttributeContainer() {
      return attributes;
    }

    public Builder addDimension(Dimension dim) {
      Preconditions.checkNotNull(dim);
      findDimension(dim.shortName).ifPresent(d -> {
        throw new IllegalArgumentException("Dimension '" + d.shortName + "' already exists");
      });
      dimensions.add(dim);
      return this;
    }

    public Builder addDimensions(Collection<Dimension> dims) {
      Preconditions.checkNotNull(dims);
      dims.forEach(this::addDimension);
      return this;
    }

    /**
     * Replace dimension of same name, if it exists, else just add it.
     * 
     * @return true if there was an existing dimension of that name
     */
    public boolean replaceDimension(Dimension dim) {
      Optional<Dimension> want = findDimension(dim.shortName);
      want.ifPresent(d -> dimensions.remove(d));
      addDimension(dim);
      return want.isPresent();
    }

    /**
     * Remove dimension, if it exists.
     * 
     * @return true if there was an existing dimension of that name
     */
    public boolean removeDimension(String name) {
      Optional<Dimension> want = findDimension(name);
      want.ifPresent(d -> dimensions.remove(d));
      return want.isPresent();
    }

    public Optional<Dimension> findDimension(String name) {
      return dimensions.stream().filter(d -> d.shortName.equals(name)).findFirst();
    }

    /** Add a nested Group. */
    public Builder addGroup(Group.Builder nested) {
      Preconditions.checkNotNull(nested);
      gbuilders.add(nested);
      return this;
    }

    public Builder addGroups(Collection<Group.Builder> groups) {
      Preconditions.checkNotNull(groups);
      groups.addAll(groups);
      return this;
    }

    /**
     * Remove group, if it exists.
     * 
     * @return true if there was an existing group of that name
     */
    public boolean removeGroup(String name) {
      Optional<Group.Builder> want = findGroup(name);
      want.ifPresent(v -> gbuilders.remove(v));
      return want.isPresent();
    }

    public Optional<Builder> findGroup(String name) {
      return this.gbuilders.stream().filter(g -> g.shortName.equals(name)).findFirst();
    }

    /** Add an EnumTypedef. */
    public Builder addEnumTypedef(EnumTypedef typedef) {
      Preconditions.checkNotNull(typedef);
      enumTypedefs.add(typedef);
      return this;
    }

    public Builder addEnumTypedefs(Collection<EnumTypedef> typedefs) {
      Preconditions.checkNotNull(typedefs);
      enumTypedefs.addAll(typedefs);
      return this;
    }

    /** Add a Variable, replacing one of same name if its exists. */
    public Builder addVariable(Variable.Builder<?> variable) {
      Preconditions.checkNotNull(variable);
      findVariable(variable.shortName).ifPresent(v -> {
        throw new IllegalArgumentException("Variable '" + v.shortName + "' already exists");
      });
      vbuilders.add(variable);
      return this;
    }

    public Builder addVariables(Collection<Variable.Builder<?>> vars) {
      vbuilders.addAll(vars);
      return this;
    }

    /**
     * Replace variable of same name, if it exists, else just add it.
     * 
     * @return true if there was an existing variable of that name
     */
    public boolean replaceVariable(Variable.Builder<?> vb) {
      Optional<Variable.Builder<?>> want = findVariable(vb.shortName);
      want.ifPresent(v -> vbuilders.remove(v));
      addVariable(vb);
      return want.isPresent();
    }

    /**
     * Remove variable, if it exists.
     * 
     * @return true if there was an existing variable of that name
     */
    public boolean removeVariable(String name) {
      Optional<Variable.Builder<?>> want = findVariable(name);
      want.ifPresent(v -> vbuilders.remove(v));
      return want.isPresent();
    }

    public Optional<Variable.Builder<?>> findVariable(String name) {
      return vbuilders.stream().filter(v -> v.shortName.equals(name)).findFirst();
    }

    // Generally ncfile in NetcdfFile.build()
    public Builder setNcfile(NetcdfFile ncfile) {
      this.ncfile = ncfile;
      return this;
    }

    public Builder setName(String shortName) {
      this.shortName = shortName;
      return this;
    }

    public NetcdfFile getNcfile() {
      return this.ncfile;
    }

    public List<Dimension> makeDimensionsList(String dimString) throws IllegalArgumentException {
      return Dimensions.makeDimensionsList(this.dimensions, dimString);
    }

    /** Normally this is called by NetcdfFile.build() */
    public Group build(Group parent) {
      if (built)
        throw new IllegalStateException("already built");
      built = true;
      return new Group(this, parent);
    }

  }
}
