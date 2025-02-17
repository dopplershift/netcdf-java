/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.iosp.vis5d;

import ucar.ma2.*;
import ucar.nc2.*;
import ucar.nc2.constants.AxisType;
import ucar.nc2.constants.CDM;
import ucar.nc2.constants.CF;
import ucar.nc2.constants._Coordinate;
import ucar.nc2.iosp.AbstractIOServiceProvider;
import ucar.nc2.iosp.grid.*;
import ucar.nc2.util.CancelTask;
import ucar.unidata.io.RandomAccessFile;
import visad.Set;
import visad.VisADException;
import visad.data.BadFormException;
import visad.data.vis5d.Vis5DCoordinateSystem;
import visad.data.vis5d.Vis5DVerticalSystem;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;


/**
 * Vis5D grid file reader. Only support Vis5D grids, not the TOPO files.
 *
 * @author dmurray
 */
public class Vis5DIosp extends AbstractIOServiceProvider {

  /**
   * Vis5D file reader
   */
  private V5DStruct v5dstruct;

  /**
   * file header
   */
  private static final String V5D = "V5D";

  /* from vis5d-4.3/src/v5d.h */

  /**
   * maximum number of varaiables
   */
  private static final int MAXVARS = 200;

  /**
   * maximum number of times
   */
  private static final int MAXTIMES = 400;

  /**
   * maximum number of rows
   */
  private static final int MAXROWS = 400;

  /**
   * maximum number of columns
   */
  private static final int MAXCOLUMNS = 400;

  /**
   * maximum number of levels
   */
  private static final int MAXLEVELS = 400;

  /**
   * row variable name
   */
  private static final String ROW = "row";

  /**
   * column variable name
   */
  private static final String COLUMN = "col";

  /**
   * level variable name
   */
  private static final String LEVEL = "lev";

  /**
   * time variable name
   */
  private static final String TIME = "time";

  /**
   * latitude variable name
   */
  private static final String LAT = "lat";

  /**
   * longitude variable name
   */
  private static final String LON = "lon";

  /**
   * maximum number of projection arguments
   */
  private final int MAXPROJARGS = MAXROWS + MAXCOLUMNS + 1;

  /**
   * maximum number of projection level arguments
   */
  private final int MAXVERTARGS = MAXLEVELS + 1;

  /**
   * table of param name to units for well know params
   */
  private static Hashtable<String, String> unitTable;

  /**
   * table of param name to var index
   */
  private static Hashtable<Variable, Integer> varTable;


  /**
   * Is this a valid file?
   *
   * @param raf RandomAccessFile to check
   * @return true if a valid McIDAS AREA file
   * @throws IOException problem reading file
   */
  public boolean isValidFile(RandomAccessFile raf) throws IOException {
    // quick test
    raf.order(RandomAccessFile.BIG_ENDIAN);
    raf.seek(0);
    String got = raf.readString(V5D.length());
    if (got.equals(V5D)) {
      return true;
    } else { // more rigorous test
      V5DStruct vv;
      try {
        vv = V5DStruct.v5dOpenFile(raf);
      } catch (BadFormException bfe) {
        vv = null;
      }
      return vv != null;
    }
  }

  /**
   * Get the file type id
   *
   * @return the file type id
   */
  public String getFileTypeId() {
    return "Vis5D";
  }

  /**
   * Get the file type description
   *
   * @return the file type description
   */
  public String getFileTypeDescription() {
    return "Vis5D grid file";
  }

  /**
   * Open the service provider for reading.
   *
   * @param raf file to read from
   * @param ncfile netCDF file we are writing to (memory)
   * @param cancelTask task for cancelling
   * @throws IOException problem reading file
   */
  public void open(RandomAccessFile raf, NetcdfFile ncfile, CancelTask cancelTask) throws IOException {
    super.open(raf, ncfile, cancelTask);
    if (unitTable == null) {
      initUnitTable();
    }
    if (v5dstruct == null) {
      makeFile(raf, ncfile, cancelTask);
    }
  }

  /**
   * Make the netcdf file
   *
   * @param raf the RandomAccessFile
   * @param ncfile the netCDF file handle
   * @param cancelTask the cancel task
   * @throws IOException problem reading the file
   */
  private void makeFile(RandomAccessFile raf, NetcdfFile ncfile, CancelTask cancelTask) throws IOException {


    ncfile.empty();
    int[] sizes = new int[5];
    int[] map_proj = new int[1];
    String[] varnames = new String[MAXVARS];
    String[] varunits = new String[MAXVARS];
    int[] n_levels = new int[MAXVARS];
    int[] vert_sys = new int[1];
    float[] vertargs = new float[MAXVERTARGS];
    double[] times = new double[MAXTIMES];
    float[] projargs = new float[MAXPROJARGS];

    try {
      v5dstruct =
          V5DStruct.v5d_open(raf, sizes, n_levels, varnames, varunits, map_proj, projargs, vert_sys, vertargs, times);
    } catch (BadFormException bfe) {
      throw new IOException("Vis5DIosp.makeFile: bad file " + bfe.getMessage());
    }

    if (sizes[0] < 1) {
      throw new IOException("Vis5DIosp.makeFile: bad file");
    }

    int nr = sizes[0];
    int nc = sizes[1];
    int nl = sizes[2];
    int ntimes = sizes[3];
    int nvars = sizes[4];

    Dimension time = new Dimension(TIME, ntimes, true);
    Dimension row = new Dimension(ROW, nr, true);
    Dimension col = new Dimension(COLUMN, nc, true);
    ncfile.addDimension(null, time);
    ncfile.addDimension(null, row);
    ncfile.addDimension(null, col);

    // time
    Variable timeVar = new Variable(ncfile, null, null, TIME);
    timeVar.setDataType(DataType.DOUBLE);
    timeVar.setDimensions(TIME);
    timeVar.addAttribute(new Attribute(CDM.UNITS, "seconds since 1900-01-01 00:00:00"));
    timeVar.addAttribute(new Attribute("long_name", TIME));
    timeVar.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Time.toString()));
    Array varArray = new ArrayDouble.D1(ntimes);
    for (int i = 0; i < ntimes; i++) {
      ((ArrayDouble.D1) varArray).set(i, times[i]);
    }
    timeVar.setCachedData(varArray, false);
    ncfile.addVariable(null, timeVar);

    // rows and columns
    Variable rowVar = new Variable(ncfile, null, null, ROW);
    rowVar.setDataType(DataType.INT);
    rowVar.setDimensions(ROW);
    varArray = new ArrayInt.D1(nr, false);
    for (int i = 0; i < nr; i++) {
      ((ArrayInt.D1) varArray).set(i, i);
    }
    rowVar.setCachedData(varArray, false);
    ncfile.addVariable(null, rowVar);

    Variable colVar = new Variable(ncfile, null, null, COLUMN);
    colVar.setDataType(DataType.INT);
    colVar.setDimensions(COLUMN);
    varArray = new ArrayInt.D1(nc, false);
    for (int i = 0; i < nc; i++) {
      ((ArrayInt.D1) varArray).set(i, i);
    }
    colVar.setCachedData(varArray, false);
    ncfile.addVariable(null, colVar);

    // sanity check on levels
    Hashtable<Integer, Object> var_table = new Hashtable<>();
    boolean have3D = false;
    for (int i = 0; i < nvars; i++) {
      int nlevs = n_levels[i];
      if (!have3D && (nlevs > 1)) {
        have3D = true;
      }
      var_table.put(nlevs, new Object());
    }
    int n_var_groups = var_table.size();
    if (n_var_groups > 2) {
      throw new IOException("Vis5DIosp.makeFile: more than two variable groups by n_levels");
    } else if (n_var_groups == 0) {
      throw new IOException("Vis5DIosp.makeFile: number of variable groups == 0");
    }
    Variable vert = null;
    if (have3D) {
      Dimension lev = new Dimension(LEVEL, nl, true);
      ncfile.addDimension(null, lev);
      vert = makeVerticalVariable(vert_sys[0], nl, vertargs);
      if (vert != null) {
        ncfile.addVariable(null, vert);
      }
    }
    varTable = new Hashtable<>();
    String dim3D = TIME + " " + LEVEL + " " + COLUMN + " " + ROW;
    String dim2D = TIME + " " + COLUMN + " " + ROW;
    // String coords3D = TIME + " " + vert.getName() + " " + LAT + " " + LON;
    String coords3D = "unknown";
    if (vert != null) {
      coords3D = TIME + " Height " + LAT + " " + LON;
    }
    String coords2D = TIME + " " + LAT + " " + LON;

    for (int i = 0; i < nvars; i++) {
      Variable v = new Variable(ncfile, null, null, varnames[i]);
      if (n_levels[i] > 1) {
        v.setDimensions(dim3D);
        v.addAttribute(new Attribute(CF.COORDINATES, coords3D));
      } else {
        v.setDimensions(dim2D);
        v.addAttribute(new Attribute(CF.COORDINATES, coords2D));
      }
      v.setDataType(DataType.FLOAT);
      String units = varunits[i].trim();
      if (units.equals("")) { // see if its in the unitTable
        String key = varnames[i].trim().toLowerCase();
        units = unitTable.get(key);
      }
      if (units != null) {
        v.addAttribute(new Attribute(CDM.UNITS, units));
      }
      // TODO: do two vars with the same name have different values?
      // check agaist duplicat variable names
      if (varTable.get(v) == null) {
        varTable.put(v, i);
        ncfile.addVariable(null, v);
      }
    }
    double[][] proj_args = Set.floatToDouble(new float[][] {projargs});
    addLatLonVariables(map_proj[0], proj_args[0], nr, nc);
    // Vis5DGridDefRecord gridDef = new Vis5DGridDefRecord(map_proj[0], proj_args[0], nr, nc);
    ncfile.addAttribute(null, new Attribute("Conventions", "CF-1.0"));
    ncfile.finish();
  }

  /**
   * Read the data for the variable
   *
   * @param v2 Variable to read
   * @param section section infomation
   * @return Array of data
   * @throws IOException problem reading from file
   * @throws InvalidRangeException invalid Range
   */
  public Array readData(Variable v2, Section section) throws IOException, InvalidRangeException {
    // long startTime = System.currentTimeMillis();
    Integer varIdx = varTable.get(v2);
    if (varIdx == null) {
      throw new IOException("unable to find variable index");
    }

    int count = 0;
    int[] shape = v2.getShape();
    boolean haveZ = shape.length == 4;
    int nt = shape[count++];
    int nz = haveZ ? shape[count++] : 1;
    int ny = shape[count++];
    int nx = shape[count];
    count = 0;

    Range timeRange = section.getRange(count++);
    Range zRange = haveZ ? section.getRange(count++) : null;
    Range yRange = section.getRange(count++);
    Range xRange = section.getRange(count);
    int grid_size = nx * ny * nz;

    Array dataArray = Array.factory(DataType.FLOAT, section.getShape());
    IndexIterator ii = dataArray.getIndexIterator();

    // loop over time
    for (int timeIdx : timeRange) {

      float[] data = new float[grid_size];
      float[] ranges = new float[2];
      try {
        v5dstruct.v5d_read(timeIdx, varIdx, ranges, data);
      } catch (BadFormException bfe) {
        throw new IOException("Vis5DIosp.readData: " + bfe.getMessage());
      }

      if ((ranges[0] >= 0.99E30) && (ranges[1] <= -0.99E30)) {
        // range_sets[j] = new Linear1DSet(0.0, 1.0, 255);
      } else if (ranges[0] > ranges[1]) {
        throw new IOException("Vis5DIosp.readData: bad read " + v2.getFullName());
      }

      // invert the rows
      float[] tmp_data = new float[grid_size];

      if (zRange == null) {
        int cnt = 0;
        for (int mm = 0; mm < ny; mm++) {
          int start = (mm + 1) * nx - 1;
          for (int nn = 0; nn < nx; nn++) {
            tmp_data[cnt++] = data[start--];
          }
        }
      } else {
        int cnt = 0;
        for (int ll = 0; ll < nz; ll++) {
          for (int mm = 0; mm < ny; mm++) {
            int start = ((mm + 1) * nx - 1) + nx * ny * ll;
            for (int nn = 0; nn < nx; nn++) {
              tmp_data[cnt++] = data[start--];
            }
          }
        }
      }
      data = tmp_data;

      // copy the data into the array
      for (float aData : data) {
        ii.setFloatNext(aData);
      }
    }

    // long end = System.currentTimeMillis() - startTime;
    return dataArray;
  }

  /**
   * Close this IOSP
   *
   * @throws IOException problem closing file
   */
  public void close() throws IOException {
    if (v5dstruct != null) {
      v5dstruct = null;
    }
    super.close();
  }

  /**
   * Initialize the unit table. This is used if there are no
   * units in the file.
   */
  private static void initUnitTable() {
    unitTable = new Hashtable<>();
    // temperatures
    unitTable.put("t", "K");
    unitTable.put("td", "K");
    unitTable.put("thte", "K");
    // winds
    unitTable.put("u", "m/s");
    unitTable.put("v", "m/s");
    unitTable.put("w", "m/s");
    // pressure
    unitTable.put("p", "hPa");
    unitTable.put("mmsl", "hPa");
    // moisture
    unitTable.put("rh", "%");
    // misc
    unitTable.put("rhfz", "%");
    unitTable.put("zagl", "m");
  }

  /**
   * Create a vertical dimension variable based on the info. Based on
   * visad.data.vis5d.Vis5DVerticalSystem.
   *
   * @param vert_sys the vertical system id
   * @param n_levels the number of levels
   * @param vert_args the vertical system arguments
   * @return the vertical dimesion variable
   * @throws IOException problem reading the file or creating the data
   */
  private Variable makeVerticalVariable(int vert_sys, int n_levels, float[] vert_args) throws IOException {

    String vert_unit;
    String vert_type;
    ArrayFloat.D1 data = new ArrayFloat.D1(n_levels);
    AxisType axisType = null;

    switch (vert_sys) {

      case (0):
        vert_unit = null;
        vert_type = "height";
        break;

      case (1):
      case (2):
        vert_unit = "km";
        vert_type = "altitude";
        axisType = AxisType.Height;
        break;

      case (3):
        vert_unit = "mbar";
        vert_type = "pressure";
        axisType = AxisType.Pressure;
        break;

      default:
        throw new IOException("vert_sys unknown");
    }

    Variable vertVar = new Variable(ncfile, null, null, vert_type);
    vertVar.setDimensions(LEVEL);
    vertVar.setDataType(DataType.FLOAT);
    if (vert_unit != null) {
      vertVar.addAttribute(new Attribute(CDM.UNITS, vert_unit));
    }
    if (axisType != null) {
      vertVar.addAttribute(new Attribute(_Coordinate.AxisType, axisType.toString()));
    }

    switch (vert_sys) {

      case (0):
      case (1):
        for (int i = 0; i < n_levels; i++) {
          data.set(i, vert_args[0] + vert_args[1] * i);
        }
        break;

      case (2): // Altitude in km - non-linear
        for (int i = 0; i < n_levels; i++) {
          data.set(i, vert_args[i]);
        }
        break;

      case (3): // heights of pressure surfaces in km - non-linear
        try {
          Vis5DVerticalSystem.Vis5DVerticalCoordinateSystem vert_cs =
              new Vis5DVerticalSystem.Vis5DVerticalCoordinateSystem();
          float[][] pressures = new float[1][n_levels];
          System.arraycopy(vert_args, 0, pressures[0], 0, n_levels);
          for (int i = 0; i < n_levels; i++) {
            pressures[0][i] *= 1000; // km->m
          }
          pressures = vert_cs.fromReference(pressures); // convert to pressures
          for (int i = 0; i < n_levels; i++) {
            data.set(i, pressures[0][i]);
          }
        } catch (VisADException ve) {
          throw new IOException("unable to make vertical system");
        }
        break;
    }
    vertVar.setCachedData(data, false);
    return vertVar;
  }

  /**
   * Add lat/lon variables to the file
   *
   * @param map_proj the map projection identifier
   * @param proj_args the projection args
   * @param nr number of rows
   * @param nc number of columns
   * @throws IOException Problem making the projection
   */
  private void addLatLonVariables(int map_proj, double[] proj_args, int nr, int nc) throws IOException {
    // Vis5DGridDefRecord.printProjArgs(map_proj, proj_args);
    Vis5DGridDefRecord vgd = new Vis5DGridDefRecord(map_proj, proj_args, nr, nc);
    GridHorizCoordSys ghc = new GridHorizCoordSys(vgd, new Vis5DLookup(), null);

    Vis5DCoordinateSystem coord_sys;
    try {
      coord_sys = new Vis5DCoordinateSystem(map_proj, proj_args, nr, nc);

      Variable lat = new Variable(ncfile, null, null, LAT);
      lat.setDimensions(COLUMN + " " + ROW);
      lat.setDataType(DataType.DOUBLE);
      lat.addAttribute(new Attribute("long_name", "latitude"));
      lat.addAttribute(new Attribute(CDM.UNITS, CDM.LAT_UNITS));
      lat.addAttribute(new Attribute(CF.STANDARD_NAME, "latitude"));
      lat.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Lat.toString()));
      ncfile.addVariable(null, lat);

      Variable lon = new Variable(ncfile, null, null, LON);
      lon.setDimensions(COLUMN + " " + ROW);
      lon.setDataType(DataType.DOUBLE);
      lon.addAttribute(new Attribute(CDM.UNITS, CDM.LON_UNITS));
      lon.addAttribute(new Attribute("long_name", "longitude"));
      lon.addAttribute(new Attribute(CF.STANDARD_NAME, "longitude"));
      lon.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Lon.toString()));
      ncfile.addVariable(null, lon);

      int[] shape = {nc, nr};
      Array latArray = Array.factory(DataType.DOUBLE, shape);
      Array lonArray = Array.factory(DataType.DOUBLE, shape);
      double[][] rowcol = new double[2][nr * nc];
      for (int x = 0; x < nc; x++) {
        for (int y = 0; y < nr; y++) {
          int index = x * nr + y;
          rowcol[0][index] = y;
          rowcol[1][index] = x;
        }
      }
      double[][] latlon = coord_sys.toReference(rowcol);
      Index latIndex = latArray.getIndex();
      Index lonIndex = lonArray.getIndex();
      /*
       * for (int y = 0; y < nr; y++) {
       * for (int x = 0; x < nc; x++) {
       * int index = y * nc + x;
       */
      for (int x = 0; x < nc; x++) {
        for (int y = 0; y < nr; y++) {
          int index = x * nr + y;
          /*
           * latArray.setDouble(latIndex.set(x, y), latlon[0][index]);
           * lonArray.setDouble(lonIndex.set(x, y), latlon[1][index]);
           */
          latArray.setDouble(index, latlon[0][index]);
          lonArray.setDouble(index, latlon[1][index]);
        }
      }
      lat.setCachedData(latArray, false);
      lon.setCachedData(lonArray, false);
    } catch (VisADException ve) {
      throw new IOException("Vis5DIosp.addLatLon: " + ve.getMessage());
    }
  }


  /**
   * Get all the information about a Vis5D file
   */
  public static class Vis5DLookup implements GridTableLookup {

    public String getShapeName(GridDefRecord gds) {
      return "Spherical";
    }

    /**
     * gets the grid type.
     */
    public final String getGridName(GridDefRecord gds) {
      return gds.toString();
    }

    /**
     * gets parameter table, then grib1 parameter based on number.
     *
     * @param gr GridRecord
     * @return Parameter
     */
    public final GridParameter getParameter(GridRecord gr) {
      // not needed for this implementation
      return null;
    }

    /**
     * gets the DisciplineName.
     */
    public final String getDisciplineName(GridRecord gr) {
      // all disciplines are the same in Vis5D
      return "Meteorological Products";
    }

    /**
     * gets the CategoryName.
     */
    public final String getCategoryName(GridRecord gr) {
      // no categories in Vis5D
      return "Meteorological Parameters";
    }

    /**
     * gets the LevelName.
     */
    public final String getLevelName(GridRecord gr) {
      // not needed for this implementation
      return null;
    }

    /**
     * gets the LevelDescription.
     */
    public final String getLevelDescription(GridRecord gr) {
      // not needed for this implementation
      return null;
    }

    /**
     * gets the LevelUnit.
     */
    public final String getLevelUnit(GridRecord gr) {
      // not needed for this implementation
      return null;
    }

    public final String getTimeRangeUnitName(int tunit) {
      return "second";
    }

    /**
     * gets the BaseTime Forecastime.
     *
     * @return BaseTime
     */
    public final java.util.Date getFirstBaseTime() {
      return new Date();
    }

    /**
     * is this a LatLon grid.
     */
    public final boolean isLatLon(GridDefRecord gds) {
      return getProjectionName(gds).equals("GENERIC") || getProjectionName(gds).equals("LINEAR")
          || getProjectionName(gds).equals("CYLINDRICAL") || getProjectionName(gds).equals("SPHERICAL");
    }

    /**
     * gets the ProjectionType.
     */
    public final int getProjectionType(GridDefRecord gds) {
      String name = getProjectionName(gds).trim();
      switch (name) {
        case "LAMBERT":
          return LambertConformal;
        case "STEREO":
          return PolarStereographic;
        default:
          return -1;
      }
    }

    /**
     * is this a VerticalCoordinate.
     */
    public final boolean isVerticalCoordinate(GridRecord gr) {
      // not needed for this implementation
      return false;
    }

    /**
     * is this a PositiveUp VerticalCoordinate.
     */
    public final boolean isPositiveUp(GridRecord gr) {
      // not needed for this implementation
      return false;
    }

    /**
     * gets the MissingValue.
     *
     * @return MissingValue
     */
    public final float getFirstMissingValue() {
      return -9999;
    }

    /**
     * Is this a layer?
     *
     * @param gr record to check
     * @return true if a layer
     */
    public boolean isLayer(GridRecord gr) {
      return false;
    }

    /**
     * Get the projection name
     *
     * @param gds the projection name
     * @return the name or null if not set
     */
    private String getProjectionName(GridDefRecord gds) {
      return gds.getParam(GridDefRecord.PROJ);
    }

    // CF Conventions Global Attributes

    /**
     * gets the CF title.
     *
     * @return title
     */
    public final String getTitle() {
      return "GRID data";
    }

    /**
     * Institution for CF conventions
     *
     * @return Institution
     */
    public String getInstitution() {
      return null;

    }

    /**
     * gets the Source, Generating Process or Model.
     *
     * @return source
     */
    public final String getSource() {
      return null;

    }

    /**
     * comment for CF conventions.
     *
     * @return comment
     */
    public final String getComment() {
      return null;
    }

    /**
     * Get the grid type for labelling
     *
     * @return the grid type
     */
    public String getGridType() {
      return "Vis5D";
    }

  }
}

