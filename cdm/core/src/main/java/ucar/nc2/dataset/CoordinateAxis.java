/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.dataset;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.dataset.conv.CF1Convention;
import ucar.nc2.dataset.conv.COARDSConvention;
import ucar.nc2.time.Calendar;
import java.io.IOException;
import java.util.Formatter;

/**
 * A Coordinate Axis is a Variable that specifies one of the coordinates of a CoordinateSystem.
 * Mathematically it is a scalar function F from index space to S:
 * 
 * <pre>
 *  F:D -> S
 *  where D is a product set of dimensions (aka <i>index space</i>), and S is the set of reals (R) or Strings.
 * </pre>
 * <p/>
 * If its element type is char, it is considered a string-valued Coordinate Axis and rank is reduced by one,
 * since the outermost dimension is considered the string length: v(i, j, .., strlen).
 * If its element type is String, it is a string-valued Coordinate Axis.
 * Otherwise it is numeric-valued, and <i>isNumeric()</i> is true.
 * <p/>
 * The one-dimensional case F(i) -> R is the common case which affords important optimizations.
 * In that case, use the subtype CoordinateAxis1D. The factory methods will return
 * either a CoordinateAxis1D if the variable is one-dimensional, a CoordinateAxis2D if its 2D, or a
 * CoordinateAxis for the general case.
 * <p/>
 * A CoordinateAxis is optionally marked as georeferencing with an AxisType. It should have
 * a units string and optionally a description string.
 * <p/>
 * A Structure cannot be a CoordinateAxis, although members of Structures can.
 *
 * @author john caron
 */

public class CoordinateAxis extends VariableDS {
  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CoordinateAxis.class);
  private static int axisSizeToCache = 100 * 1000; // bytes

  /**
   * Create a coordinate axis from an existing Variable.
   *
   * @param ncd the containing dataset
   * @param vds an existing Variable in dataset.
   * @return CoordinateAxis or one of its subclasses (CoordinateAxis1D, CoordinateAxis2D, or CoordinateAxis1DTime).
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  public static CoordinateAxis factory(NetcdfDataset ncd, VariableDS vds) {
    if ((vds.getRank() == 0) || (vds.getRank() == 1) || (vds.getRank() == 2 && vds.getDataType() == DataType.CHAR)) {
      return new CoordinateAxis1D(ncd, vds);
    } else if (vds.getRank() == 2)
      return new CoordinateAxis2D(ncd, vds);
    else
      return new CoordinateAxis(ncd, vds);
  }

  // experimental
  public static CoordinateAxis.Builder fromVariableDS(VariableDS.Builder vdsBuilder) {
    if ((vdsBuilder.getRank() == 0) || (vdsBuilder.getRank() == 1)
        || (vdsBuilder.getRank() == 2 && vdsBuilder.dataType == DataType.CHAR)) {
      return new CoordinateAxis1D(vdsBuilder).toBuilder();
    } else if (vdsBuilder.getRank() == 2)
      return new CoordinateAxis2D(vdsBuilder).toBuilder();
    else
      return new CoordinateAxis(vdsBuilder).toBuilder();
  }

  /**
   * Create a coordinate axis from an existing Variable.
   * General case.
   *
   * @param ncd the containing dataset
   * @param vds an existing Variable
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  protected CoordinateAxis(NetcdfDataset ncd, VariableDS vds) {
    super(vds, false);
    this.ncd = ncd;

    if (vds instanceof CoordinateAxis) {
      CoordinateAxis axis = (CoordinateAxis) vds;
      this.axisType = axis.axisType;
      this.boundaryRef = axis.boundaryRef;
      this.isContiguous = axis.isContiguous;
      this.positive = axis.positive;
    }
    setSizeToCache(axisSizeToCache);
  }

  /**
   * Constructor when theres no underlying variable. You better set the values too!
   *
   * @param ds the containing dataset.
   * @param group the containing group; if null, use rootGroup
   * @param shortName axis name.
   * @param dataType data type
   * @param dims list of dimension names
   * @param units units of coordinates, preferably udunit compatible.
   * @param desc long name.
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  public CoordinateAxis(NetcdfDataset ds, Group group, String shortName, DataType dataType, String dims, String units,
      String desc) {
    super(ds, group, null, shortName, dataType, dims, units, desc);
    this.ncd = ds;
    setSizeToCache(axisSizeToCache);
  }

  /**
   * Make a copy, with an independent cache.
   *
   * @return copy of this CoordinateAxis
   */
  public CoordinateAxis copyNoCache() {
    CoordinateAxis axis = new CoordinateAxis(ncd, getParentGroup(), getShortName(), getDataType(),
        getDimensionsString(), getUnitsString(), getDescription());

    // other state
    axis.axisType = this.axisType;
    axis.boundaryRef = this.boundaryRef;
    axis.isContiguous = this.isContiguous;
    axis.positive = this.positive;

    axis.cache = new Variable.Cache(); // decouple cache
    return axis;
  }

  // for section and slice

  @Override
  protected CoordinateAxis copy() {
    return new CoordinateAxis(this.ncd, this);
  }

  /**
   * Get type of axis
   *
   * @return type of axis, or null if none.
   */
  public AxisType getAxisType() {
    return axisType;
  }

  /**
   * Set type of axis, or null if none. Default is none.
   *
   * @param axisType set to this value
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  public void setAxisType(AxisType axisType) {
    this.axisType = axisType;
  }

  @Override
  public String getUnitsString() {
    String units = super.getUnitsString();
    return units == null ? "" : units;
  }

  /**
   * Does the axis have numeric values.
   *
   * @return true if the CoordAxis is numeric, false if its string valued ("nominal").
   */
  public boolean isNumeric() {
    return (getDataType() != DataType.CHAR) && (getDataType() != DataType.STRING)
        && (getDataType() != DataType.STRUCTURE);
  }

  /**
   * If the edges are contiguous or disjoint
   * Caution: many datasets do not explicitly specify this info, this is often a guess; default is true.
   *
   * @return true if the edges are contiguous or false if disjoint. Assumed true unless set otherwise.
   */
  public boolean isContiguous() {
    return isContiguous;
  }

  /**
   * An interval coordinate consists of two numbers, bound1 and bound2.
   * The coordinate value must lie between them, but otherwise is somewhat arbitrary.
   * If not interval, then it has one number, the coordinate value.
   * 
   * @return true if its an interval coordinate.
   */
  public boolean isInterval() {
    return false; // interval detection is done in subclasses
  }


  public boolean isIndependentCoordinate() {
    if (isCoordinateVariable())
      return true;
    return null != findAttribute(_Coordinate.AliasForDimension);
  }

  /*
   * Set if the edges are contiguous or disjoint.
   *
   * @param isContiguous true if the adjacent edges touch
   *
   * protected void setContiguous(boolean isContiguous) {
   * this.isContiguous = isContiguous;
   * }
   */

  /**
   * Get the direction of increasing values, used only for vertical Axes.
   *
   * @return POSITIVE_UP, POSITIVE_DOWN, or null if unknown.
   */
  public String getPositive() {
    return positive;
  }

  /**
   * Set the direction of increasing values, used only for vertical Axes.
   *
   * @param positive POSITIVE_UP, POSITIVE_DOWN, or null if you dont know..
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  public void setPositive(String positive) {
    this.positive = positive;
  }

  /**
   * The name of this coordinate axis' boundary variable
   *
   * @return the name of this coordinate axis' boundary variable, or null if none.
   */
  public String getBoundaryRef() {
    return boundaryRef;
  }

  /**
   * Set a reference to a boundary variable.
   *
   * @param boundaryRef the name of a boundary coordinate variable in the same dataset.
   * @deprecated Use CoordinateAxis.builder()
   */
  @Deprecated
  public void setBoundaryRef(String boundaryRef) {
    this.boundaryRef = boundaryRef;
  }

  ////////////////////////////////

  private MAMath.MinMax minmax;

  private void init() {
    try {
      Array data = read();
      minmax = MAMath.getMinMax(data);
    } catch (IOException ioe) {
      log.error("Error reading coordinate values ", ioe);
      throw new IllegalStateException(ioe);
    }
  }

  /**
   * The smallest coordinate value. Only call if isNumeric.
   *
   * @return the minimum coordinate value
   */
  public double getMinValue() {
    if (minmax == null)
      init();
    return minmax.min;
  }

  /**
   * The largest coordinate value. Only call if isNumeric.
   *
   * @return the maximum coordinate value
   */
  public double getMaxValue() {
    if (minmax == null)
      init();
    return minmax.max;
  }

  //////////////////////

  /**
   * Get a string representation
   *
   * @param buf place info here
   */
  public void getInfo(Formatter buf) {
    buf.format("%-30s", getNameAndDimensions());
    buf.format("%-20s", getUnitsString());
    if (axisType != null) {
      buf.format("%-10s", axisType.toString());
    }
    buf.format("%s", getDescription());

    /*
     * if (isNumeric) {
     * boolean debugCoords = ucar.util.prefs.ui.Debug.isSet("Dataset/showCoordValues");
     * int ndigits = debugCoords ? 9 : 4;
     * for (int i=0; i< getNumElements(); i++) {
     * buf.append(Format.d(getCoordValue(i), ndigits));
     * buf.append(" ");
     * }
     * if (debugCoords) {
     * buf.append("\n      ");
     * for (int i=0; i<=getNumElements(); i++) {
     * buf.append(Format.d(getCoordEdge(i), ndigits));
     * buf.append(" ");
     * }
     * }
     * } else {
     * for (int i=0; i< getNumElements(); i++) {
     * buf.append(getCoordName(i));
     * buf.append(" ");
     * }
     * }
     */

    // buf.append("\n");
  }

  /**
   * Standard sort on Coordinate Axes
   */
  public static class AxisComparator implements java.util.Comparator<CoordinateAxis> {
    public int compare(CoordinateAxis c1, CoordinateAxis c2) {

      AxisType t1 = c1.getAxisType();
      AxisType t2 = c2.getAxisType();

      if ((t1 == null) && (t2 == null))
        return c1.getShortName().compareTo(c2.getShortName());
      if (t1 == null)
        return -1;
      if (t2 == null)
        return 1;

      return t1.axisOrder() - t2.axisOrder();
    }
  }

  /**
   * Instances which have same content are equal.
   */
  public boolean equals(Object oo) {
    if (this == oo)
      return true;
    if (!(oo instanceof CoordinateAxis))
      return false;
    if (!super.equals(oo))
      return false;
    CoordinateAxis o = (CoordinateAxis) oo;

    if (getAxisType() != null)
      if (getAxisType() != o.getAxisType())
        return false;

    if (getPositive() != null)
      return getPositive().equals(o.getPositive());

    return true;
  }

  /**
   * Override Object.hashCode() to implement equals.
   */
  public int hashCode() {
    int result = super.hashCode();
    if (getAxisType() != null)
      result = 37 * result + getAxisType().hashCode();
    if (getPositive() != null)
      result = 37 * result + getPositive().hashCode();
    return result;
  }

  /////////////////////////////////////

  // needed by time coordinates
  public ucar.nc2.time.Calendar getCalendarFromAttribute() {
    Attribute cal = findAttribute(CF.CALENDAR);
    String s = (cal == null) ? null : cal.getStringValue();
    if (s == null) { // default for CF and COARDS
      Attribute convention = (ncd == null) ? null : ncd.getRootGroup().findAttribute(CDM.CONVENTIONS);
      if (convention != null) {
        String hasName = convention.getStringValue();
        int version = CF1Convention.getVersion(hasName);
        if (version >= 0) {
          return Calendar.gregorian;
          // if (version < 7 ) return Calendar.gregorian;
          // if (version >= 7 ) return Calendar.proleptic_gregorian; //
        }
        if (COARDSConvention.isMine(hasName))
          return Calendar.gregorian;
      }
    }
    return ucar.nc2.time.Calendar.get(s);
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // TODO make these final and immutable in 6.
  protected NetcdfDataset ncd; // needed?
  protected AxisType axisType;
  protected String positive;
  protected String boundaryRef;
  protected boolean isContiguous = true;

  protected CoordinateAxis(Builder<?> builder) {
    super(builder);
    this.ncd = (NetcdfDataset) this.ncfile;
    this.axisType = builder.axisType;
    this.positive = builder.positive;
    this.boundaryRef = builder.boundaryRef;
    this.isContiguous = builder.isContiguous;
  }

  public Builder<?> toBuilder() {
    return addLocalFieldsToBuilder(builder());
  }

  public CoordinateAxis(VariableDS.Builder<?> builder) {
    super(builder);
  }

  // Add local fields to the passed - in builder.
  protected Builder<?> addLocalFieldsToBuilder(Builder<? extends Builder<?>> b) {
    b.setAxisType(this.axisType).setPositive(this.positive).setBoundary(this.boundaryRef)
        .setIsContiguous(this.isContiguous);
    return (Builder<?>) super.addLocalFieldsToBuilder(b);

  }

  /**
   * Get Builder for this class that allows subclassing.
   * 
   * @see "https://community.oracle.com/blogs/emcmanus/2010/10/24/using-builder-pattern-subclasses"
   */
  public static Builder<?> builder() {
    return new Builder2();
  }

  private static class Builder2 extends Builder<Builder2> {
    @Override
    protected Builder2 self() {
      return this;
    }
  }

  public static abstract class Builder<T extends Builder<T>> extends VariableDS.Builder<T> {
    protected AxisType axisType;
    protected String positive;
    protected String boundaryRef;
    protected boolean isContiguous = true;
    private boolean built;

    protected abstract T self();

    public T setAxisType(AxisType axisType) {
      this.axisType = axisType;
      return self();
    }

    public T setPositive(String positive) {
      this.positive = positive;
      return self();
    }

    public T setBoundary(String boundaryRef) {
      this.boundaryRef = boundaryRef;
      return self();
    }

    public T setIsContiguous(boolean isContiguous) {
      this.isContiguous = isContiguous;
      return self();
    }

    public CoordinateAxis build() {
      if (built)
        throw new IllegalStateException("already built");
      built = true;
      return new CoordinateAxis(this);
    }
  }

}
