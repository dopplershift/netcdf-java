/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.iosp.fysat;


import java.io.*;
import java.util.*;
import java.text.*;
import ucar.nc2.*;
import ucar.nc2.constants.*;
import ucar.nc2.units.DateFormatter;
import ucar.nc2.iosp.fysat.util.EndianByteBuffer;
import ucar.unidata.geoloc.*;
import ucar.unidata.geoloc.projection.LambertConformal;
import ucar.unidata.geoloc.projection.Stereographic;
import ucar.unidata.geoloc.projection.Mercator;
import ucar.unidata.geoloc.projection.LatLonProjection;
import ucar.unidata.io.RandomAccessFile;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.unidata.util.Parameter;


/**
 * Netcdf header reading and writing for version 3 file format.
 * This is used by Fysatiosp.
 */

public final class FysatHeader {

  private static final boolean debug = false;
  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FysatHeader.class);

  int FY_AWX_PIB_LEN = 40; // FY Satellite AWX product indentification block


  double DEG_TO_RAD = 0.017453292;
  private int Z_type = 0;

  private AwxFileFirstHeader firstHeader;
  private AwxFileSecondHeader secondHeader;

  public boolean isValidFile(ucar.unidata.io.RandomAccessFile raf) throws IOException {

    // second check the file name
    if (!((raf.getLocation().endsWith(".AWX") || raf.getLocation().endsWith(".awx")))) {
      return false;
    }
    // more serious checking
    return this.readPIB(raf);// not FY Satellite AWX product file
  }

  /** Read the header and populate the ncfile */
  boolean readPIB(RandomAccessFile raf) throws IOException {

    this.firstHeader = new AwxFileFirstHeader();

    int pos = 0;
    raf.seek(pos);

    // gini header process
    byte[] buf = new byte[FY_AWX_PIB_LEN];
    int count = raf.read(buf);
    EndianByteBuffer byteBuffer;
    if (count == FY_AWX_PIB_LEN) {
      byteBuffer = new EndianByteBuffer(buf, this.firstHeader.byteOrder);
      this.firstHeader.fillHeader(byteBuffer);

    } else {
      return false;
    }

    if (!((this.firstHeader.fileName.endsWith(".AWX") || this.firstHeader.fileName.endsWith(".awx"))
        && this.firstHeader.firstHeaderLength == FY_AWX_PIB_LEN)) {
      return false;
    }

    // skip the fills of the first record
    // raf.seek(FY_AWX_PIB_LEN + this.firstHeader.fillSectionLength);
    buf = new byte[this.firstHeader.secondHeaderLength];
    raf.readFully(buf);
    byteBuffer = new EndianByteBuffer(buf, this.firstHeader.byteOrder);
    switch (this.firstHeader.typeOfProduct) {
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_UNDEFINED:
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_POLARSAT_IMAGE:
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GRAPH_ANALIYSIS:
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_DISCREET:
        throw new UnsupportedDatasetException();

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GEOSAT_IMAGE:
        secondHeader = new AwxFileGeoSatelliteSecondHeader();
        secondHeader.fillHeader(byteBuffer);
        break;

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GRID:
        secondHeader = new AwxFileGridProductSecondHeader();
        secondHeader.fillHeader(byteBuffer);
        break;
    }

    return true;
  }

  void read(RandomAccessFile raf, NetcdfFile ncfile) throws IOException {

    if ((this.firstHeader == null) && (this.secondHeader == null)) {
      readPIB(raf);
    }
    assert this.firstHeader != null;
    assert this.secondHeader != null;

    Attribute att;
    // Attribute att = new Attribute( "Conventions", "AWX");
    // ncfile.addAttribute(null, att);

    att = new Attribute("version", this.firstHeader.version);
    ncfile.addAttribute(null, att);

    String vname;

    switch (this.firstHeader.typeOfProduct) {

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_UNDEFINED:

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GRAPH_ANALIYSIS:
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_DISCREET:
      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_POLARSAT_IMAGE:
        throw new UnsupportedDatasetException();

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GEOSAT_IMAGE: {
        AwxFileGeoSatelliteSecondHeader geoSatelliteSecondHeader = (AwxFileGeoSatelliteSecondHeader) this.secondHeader;

        att = new Attribute("satellite_name", geoSatelliteSecondHeader.satelliteName);
        ncfile.addAttribute(null, att);

        DateFormat dformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dformat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        Calendar cal = Calendar.getInstance();
        cal.set(geoSatelliteSecondHeader.year, geoSatelliteSecondHeader.month - 1, geoSatelliteSecondHeader.day,
            geoSatelliteSecondHeader.hour, geoSatelliteSecondHeader.minute);
        cal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        String dstring = dformat.format(cal.getTime());
        ncfile.addAttribute(null, new Attribute("time_coverage", dstring));
        int nz = 1;
        Dimension dimT = new Dimension("time", nz, true, false, false);
        ncfile.addDimension(null, dimT);

        String timeCoordName = "time";
        Variable taxis = new Variable(ncfile, null, null, timeCoordName);
        taxis.setDataType(DataType.DOUBLE);
        taxis.setDimensions("time");
        taxis.addAttribute(new Attribute(CDM.LONG_NAME, "time since base date"));
        taxis.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Time.toString()));
        double[] tdata = new double[1];
        tdata[0] = cal.getTimeInMillis();
        Array dataA = Array.factory(DataType.DOUBLE, new int[] {1}, tdata);
        taxis.setCachedData(dataA, false);
        DateFormatter formatter = new DateFormatter();
        taxis.addAttribute(new Attribute(CDM.UNITS, "msecs since " + formatter.toDateTimeStringISO(new Date(0))));
        ncfile.addVariable(null, taxis);
        // Get dimensions
        Integer ni = (int) geoSatelliteSecondHeader.widthOfImage;
        att = new Attribute("NX", ni);
        ncfile.addAttribute(null, att);

        ni = (int) geoSatelliteSecondHeader.heightOfImage;
        att = new Attribute("NY", ni);
        ncfile.addAttribute(null, att);

        vname = getGeoSatelliteProductName(geoSatelliteSecondHeader.channel);
        if (vname == null)
          throw new UnsupportedDatasetException("Unsupported GeoSatellite Procuct Dataset");


        // set projection attribute
        // ? which projection
        ProjectionImpl projection = null;
        double dxKm = 0.0, dyKm = 0.0;
        short nv = geoSatelliteSecondHeader.flagOfProjection;
        att = new Attribute("ProjIndex", nv);
        ncfile.addAttribute(null, att);
        int proj = nv;
        if (proj == 2) {
          att = new Attribute("ProjName", "MERCATOR");
          double lat0 = geoSatelliteSecondHeader.centerLatitudeOfProjection;
          double lon0 = geoSatelliteSecondHeader.centerLongitudeOfProjection;
          double par = geoSatelliteSecondHeader.standardLatitude1;
          dxKm = geoSatelliteSecondHeader.horizontalResolution;
          dyKm = geoSatelliteSecondHeader.verticalResolution;
          projection = new Mercator(lon0, par);

        } else if (proj == 1) {
          att = new Attribute("ProjName", "LAMBERT_CONFORNAL");
          double lat0 = geoSatelliteSecondHeader.centerLatitudeOfProjection;
          double lon0 = geoSatelliteSecondHeader.centerLongitudeOfProjection;
          double par1 = geoSatelliteSecondHeader.standardLatitude1;
          double par2 = geoSatelliteSecondHeader.standardLatitude2;
          dxKm = geoSatelliteSecondHeader.horizontalResolution / 100;
          dyKm = geoSatelliteSecondHeader.verticalResolution / 100;
          projection = new LambertConformal(lat0, lon0, par1, par2);

        } else if (proj == 3) {
          att = new Attribute("ProjName", "POLARSTEREOGRAPHIC");
          double latt = geoSatelliteSecondHeader.centerLatitudeOfProjection;
          double lont = geoSatelliteSecondHeader.centerLongitudeOfProjection;
          double scale = (1. + Math.sin(DEG_TO_RAD * latt)) / 2.;
          dxKm = geoSatelliteSecondHeader.horizontalResolution;
          dyKm = geoSatelliteSecondHeader.verticalResolution;
          projection = new Stereographic(90.0, lont, scale);

        } else if (proj == 4) {
          att = new Attribute("ProjName", "LatLonProjection");
          projection = new LatLonProjection();
        }
        ncfile.addAttribute(null, att);

        // coordinate transform variable
        if (proj != 4) {

        }
        // double dxKm = 0.0, dyKm = 0.0, latin, lonProjectionOrigin ;

        // deal with projection

        ncfile.addAttribute(null, new Attribute("channel", geoSatelliteSecondHeader.channel));
        ncfile.addAttribute(null, new Attribute("geospatial_lat_min", geoSatelliteSecondHeader.latitudeOfSouth));
        ncfile.addAttribute(null, new Attribute("geospatial_lat_max", geoSatelliteSecondHeader.latitudeOfNorth));
        ncfile.addAttribute(null, new Attribute("geospatial_lon_min", geoSatelliteSecondHeader.longitudeOfWest));
        ncfile.addAttribute(null, new Attribute("geospatial_lon_max", geoSatelliteSecondHeader.longitudeOfEast));
        ncfile.addAttribute(null, new Attribute("geospatial_vertical_min", (float) 0.0));
        ncfile.addAttribute(null, new Attribute("geospatial_vertical_max", (float) 0.0));
        ncfile.addAttribute(null, new Attribute("sample_ratio", geoSatelliteSecondHeader.sampleRatio));
        ncfile.addAttribute(null,
            new Attribute("horizontal_resolution", geoSatelliteSecondHeader.horizontalResolution));
        ncfile.addAttribute(null, new Attribute("vertical_resolution", geoSatelliteSecondHeader.verticalResolution));

        // only one data variable per awx file

        // set vname and units according to grid feature

        Variable var = new Variable(ncfile, ncfile.getRootGroup(), null, vname);

        var.addAttribute(new Attribute(CDM.LONG_NAME, vname));

        // get dimensions
        int velems;

        int nx = geoSatelliteSecondHeader.widthOfImage;
        int ny = geoSatelliteSecondHeader.heightOfImage;


        Dimension dimX;
        Dimension dimY;

        if (proj != 4) {
          dimX = new Dimension("x", nx, true, false, false);
          dimY = new Dimension("y", ny, true, false, false);
        } else {
          dimX = new Dimension("lon", nx, true, false, false);
          dimY = new Dimension("lat", ny, true, false, false);
        }

        ncfile.addDimension(null, dimY);
        ncfile.addDimension(null, dimX);

        int byteAmountofData = 1;
        var.setDataType(DataType.UBYTE);
        Class dataType = DataType.BYTE.getPrimitiveClassType();
        velems = dimX.getLength() * dimY.getLength() * byteAmountofData;
        List<Dimension> dims = new ArrayList<>();
        dims.add(dimT);
        dims.add(dimY);
        dims.add(dimX);

        var.setDimensions(dims);

        var.addAttribute(new Attribute(CF.COORDINATES, "Lon Lat"));

        // var.addAttribute(new Attribute(CDM.UNSIGNED, "true"));
        var.addAttribute(new Attribute(CDM.UNITS, "percent"));
        // if(var.getDataType() == DataType.BYTE) {
        // var.addAttribute(new Attribute("_missing_value", new Byte((byte)-1)));
        // var.addAttribute( new Attribute("scale_factor", new Byte((byte)(1))));
        // var.addAttribute( new Attribute("add_offset", new Byte((byte)(0))));
        // } else {
        var.addAttribute(new Attribute("_missing_value", (short) -1));
        var.addAttribute(new Attribute(CDM.SCALE_FACTOR, (short) (1)));
        var.addAttribute(new Attribute(CDM.ADD_OFFSET, (short) (0)));
        // }

        // size and beginning data position in file
        int vsize = velems;
        long begin = this.firstHeader.recordsOfHeader * this.firstHeader.recoderLength;
        if (debug)
          log.warn(" name= " + vname + " vsize=" + vsize + " velems=" + velems + " begin= " + begin + "\n");
        var.setSPobject(new Vinfo(vsize, begin, nx, ny, dataType, this.firstHeader.byteOrder));
        String coordinates;
        if (proj != 4) {
          coordinates = "x y time";
        } else {
          coordinates = "Lon Lat time";
        }

        var.addAttribute(new Attribute(_Coordinate.Axes, coordinates));

        ncfile.addVariable(ncfile.getRootGroup(), var);


        LatLonPointImpl startPnt =
            new LatLonPointImpl(geoSatelliteSecondHeader.latitudeOfNorth, geoSatelliteSecondHeader.longitudeOfWest);
        LatLonPointImpl endPnt =
            new LatLonPointImpl(geoSatelliteSecondHeader.latitudeOfSouth, geoSatelliteSecondHeader.longitudeOfEast);
        if (debug)
          System.out.println("start at geo coord :" + startPnt);

        if (projection != null && proj != 4) {
          // we have to project in order to find the origin
          ProjectionPointImpl start = (ProjectionPointImpl) projection.latLonToProj(
              new LatLonPointImpl(geoSatelliteSecondHeader.latitudeOfSouth, geoSatelliteSecondHeader.longitudeOfWest));
          double startx = start.getX();
          double starty = start.getY();

          Variable xaxis = new Variable(ncfile, null, null, "x");
          xaxis.setDataType(DataType.DOUBLE);
          xaxis.setDimensions("x");
          xaxis.addAttribute(new Attribute(CDM.LONG_NAME, "projection x coordinate"));
          xaxis.addAttribute(new Attribute(CDM.UNITS, "km"));
          xaxis.addAttribute(new Attribute(_Coordinate.AxisType, "GeoX"));
          double[] data = new double[nx];
          if (proj == 2) {
            double lon_1 = geoSatelliteSecondHeader.longitudeOfEast;
            double lon_2 = geoSatelliteSecondHeader.longitudeOfWest;
            if (lon_1 < 0)
              lon_1 += 360.0;
            if (lon_2 < 0)
              lon_2 += 360.0;
            double dx = (lon_1 - lon_2) / (nx - 1);

            for (int i = 0; i < data.length; i++) {
              double ln = lon_2 + i * dx;
              ProjectionPointImpl pt = (ProjectionPointImpl) projection
                  .latLonToProj(new LatLonPointImpl(geoSatelliteSecondHeader.latitudeOfSouth, ln));
              data[i] = pt.getX(); // startx + i*dx;
            }
          } else {
            for (int i = 0; i < data.length; i++)
              data[i] = startx + i * dxKm;
          }

          dataA = Array.factory(DataType.DOUBLE, new int[] {nx}, data);
          xaxis.setCachedData(dataA, false);
          ncfile.addVariable(null, xaxis);

          Variable yaxis = new Variable(ncfile, null, null, "y");
          yaxis.setDataType(DataType.DOUBLE);
          yaxis.setDimensions("y");
          yaxis.addAttribute(new Attribute(CDM.LONG_NAME, "projection y coordinate"));
          yaxis.addAttribute(new Attribute(CDM.UNITS, "km"));
          yaxis.addAttribute(new Attribute(_Coordinate.AxisType, "GeoY"));
          data = new double[ny];
          double endy = starty + dyKm * (data.length - 1); // apparently lat1,lon1 is always the lower ledt, but data is
                                                           // upper left
          double lat2 = geoSatelliteSecondHeader.latitudeOfNorth;
          double lat1 = geoSatelliteSecondHeader.latitudeOfSouth;
          if (proj == 2) {
            double dy = (lat2 - lat1) / (ny - 1);
            for (int i = 0; i < data.length; i++) {
              double la = lat2 - i * dy;
              ProjectionPointImpl pt = (ProjectionPointImpl) projection
                  .latLonToProj(new LatLonPointImpl(la, geoSatelliteSecondHeader.longitudeOfWest));
              data[i] = pt.getY(); // endyy - i*dy;
            }
          } else {
            for (int i = 0; i < data.length; i++)
              data[i] = endy - i * dyKm;
          }
          dataA = Array.factory(DataType.DOUBLE, new int[] {ny}, data);
          yaxis.setCachedData(dataA, false);
          ncfile.addVariable(null, yaxis);

          // coordinate transform variable
          Variable ct = new Variable(ncfile, null, null, projection.getClassName());
          ct.setDataType(DataType.CHAR);
          ct.setDimensions("");
          for (Parameter p : projection.getProjectionParameters()) {
            ct.addAttribute(new Attribute(p));
          }
          ct.addAttribute(new Attribute(_Coordinate.TransformType, "Projection"));
          ct.addAttribute(new Attribute(_Coordinate.Axes, "x, y"));
          // fake data
          dataA = Array.factory(DataType.CHAR, new int[] {});
          dataA.setChar(dataA.getIndex(), ' ');
          ct.setCachedData(dataA, false);

          ncfile.addVariable(null, ct);
          ncfile.addAttribute(null, new Attribute(CDM.CONVENTIONS, _Coordinate.Convention));
        } else {
          Variable yaxis = new Variable(ncfile, null, null, "lat");
          yaxis.setDataType(DataType.DOUBLE);
          yaxis.setDimensions("lat");
          yaxis.addAttribute(new Attribute(CDM.LONG_NAME, "latitude"));
          yaxis.addAttribute(new Attribute(CDM.UNITS, "degree"));
          yaxis.addAttribute(new Attribute(_Coordinate.AxisType, "Lat"));
          double[] data = new double[ny];

          double dy = (endPnt.getLatitude() - startPnt.getLatitude()) / (ny - 1);
          for (int i = 0; i < data.length; i++) {
            data[i] = startPnt.getLatitude() + i * dy; // starty + i*dy;
          }

          dataA = Array.factory(DataType.DOUBLE, new int[] {ny}, data);
          yaxis.setCachedData(dataA, false);
          ncfile.addVariable(null, yaxis);


          // create coordinate variables
          Variable xaxis = new Variable(ncfile, null, null, "lon");
          xaxis.setDataType(DataType.DOUBLE);
          xaxis.setDimensions("lon");
          xaxis.addAttribute(new Attribute(CDM.LONG_NAME, "longitude"));
          xaxis.addAttribute(new Attribute(CDM.UNITS, "degree"));
          xaxis.addAttribute(new Attribute(_Coordinate.AxisType, "Lon"));
          data = new double[nx];

          double dx = (endPnt.getLongitude() - startPnt.getLongitude()) / (nx - 1);
          for (int i = 0; i < data.length; i++) {
            data[i] = startPnt.getLongitude() + i * dx; // startx + i*dx;

          }

          dataA = Array.factory(DataType.DOUBLE, new int[] {nx}, data);
          xaxis.setCachedData(dataA, false);
          ncfile.addVariable(null, xaxis);
        }
        break;
      }

      case AwxFileFirstHeader.AWX_PRODUCT_TYPE_GRID: {
        AwxFileGridProductSecondHeader gridprocuctSecondHeader = (AwxFileGridProductSecondHeader) this.secondHeader;


        att = new Attribute("satellite_name", gridprocuctSecondHeader.satelliteName);
        ncfile.addAttribute(null, att);

        att = new Attribute("grid_feature", gridprocuctSecondHeader.gridFeature);
        ncfile.addAttribute(null, att);

        att = new Attribute("byte_amount_of_data", gridprocuctSecondHeader.byteAmountofData);
        ncfile.addAttribute(null, att);

        att = new Attribute("data_scale", gridprocuctSecondHeader.dataScale);
        ncfile.addAttribute(null, att);
        ncfile.addAttribute(null, new Attribute(CF.FEATURE_TYPE, FeatureType.GRID.toString()));

        DateFormat dformat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dformat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        Calendar cal = Calendar.getInstance();
        cal.set(gridprocuctSecondHeader.startYear, gridprocuctSecondHeader.startMonth - 1,
            gridprocuctSecondHeader.startDay, gridprocuctSecondHeader.startHour, gridprocuctSecondHeader.startMinute,
            0);
        cal.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        String dstring = dformat.format(cal.getTime());
        ncfile.addAttribute(null, new Attribute("time_coverage_start", dstring));
        int nz = 1;
        Dimension dimT = new Dimension("time", nz, true, false, false);
        ncfile.addDimension(null, dimT);

        // set time variable with time_coverage_start
        String timeCoordName = "time";
        Variable taxis = new Variable(ncfile, null, null, timeCoordName);
        taxis.setDataType(DataType.DOUBLE);
        taxis.setDimensions("time");
        taxis.addAttribute(new Attribute(CDM.LONG_NAME, "time since base date"));
        taxis.addAttribute(new Attribute(_Coordinate.AxisType, AxisType.Time.toString()));
        double[] tdata = new double[1];
        tdata[0] = cal.getTimeInMillis();
        Array dataA = Array.factory(DataType.DOUBLE, new int[] {1}, tdata);
        taxis.setCachedData(dataA, false);
        DateFormatter formatter = new DateFormatter();
        taxis.addAttribute(new Attribute(CDM.UNITS, "msecs since " + formatter.toDateTimeStringISO(new Date(0))));
        ncfile.addVariable(null, taxis);


        cal.set(gridprocuctSecondHeader.endYear, gridprocuctSecondHeader.endMonth - 1, gridprocuctSecondHeader.endDay,
            gridprocuctSecondHeader.endHour, gridprocuctSecondHeader.endMinute, 0);
        dstring = dformat.format(cal.getTime());
        ncfile.addAttribute(null, new Attribute("time_coverage_end", dstring));


        // Get dimensions
        Integer ni = (int) gridprocuctSecondHeader.amountofHorizontalSpacing;
        att = new Attribute("NX", ni);
        ncfile.addAttribute(null, att);

        ni = (int) gridprocuctSecondHeader.amountofVerticalSpacing;
        att = new Attribute("NY", ni);
        ncfile.addAttribute(null, att);


        // set projection attribute
        // ? which projection
        Byte nv = 0;
        att = new Attribute("ProjIndex", nv);
        ncfile.addAttribute(null, att);
        int proj = nv.intValue();
        if (proj == 1) {
          att = new Attribute("ProjName", "MERCATOR");
        } else if (proj == 3) {
          att = new Attribute("ProjName", "LAMBERT_CONFORNAL");
        } else if (proj == 5) {
          att = new Attribute("ProjName", "POLARSTEREOGRAPGIC");
        }
        ncfile.addAttribute(null, att);


        ProjectionImpl projection = null;
        double dxKm = 0.0, dyKm = 0.0, latin, lonProjectionOrigin;

        // deal with projection
        ncfile.addAttribute(null, new Attribute("geospatial_lat_min", gridprocuctSecondHeader.rightBottomLat));
        ncfile.addAttribute(null, new Attribute("geospatial_lat_max", gridprocuctSecondHeader.leftTopLat));
        ncfile.addAttribute(null, new Attribute("geospatial_lon_min", gridprocuctSecondHeader.leftTopLon));
        ncfile.addAttribute(null, new Attribute("geospatial_lon_max", gridprocuctSecondHeader.rightBottomLon));
        ncfile.addAttribute(null, new Attribute("geospatial_vertical_min", (float) 0.0));
        ncfile.addAttribute(null, new Attribute("geospatial_vertical_max", (float) 0.0));

        ncfile.addAttribute(null, new Attribute("spacing_unit", gridprocuctSecondHeader.getSpacingUnit()));
        ncfile.addAttribute(null, new Attribute("horizontal_spacing", gridprocuctSecondHeader.horizontalSpacing));
        ncfile.addAttribute(null, new Attribute("vertical_spacing", gridprocuctSecondHeader.verticalSpacing));
        ncfile.addAttribute(null,
            new Attribute("amount_of_horizontal_spacing", gridprocuctSecondHeader.amountofHorizontalSpacing));
        ncfile.addAttribute(null,
            new Attribute("amount_of_vertical_spacing", gridprocuctSecondHeader.amountofVerticalSpacing));


        // att = new Attribute( "imageResolution", nv);
        // ncfile.addAttribute(null, att);

        // only one data variable per awx file
        vname = getGridProductName(gridprocuctSecondHeader.gridFeature);
        if (vname == null)
          throw new UnsupportedDatasetException("Unsupported Grid Procuct Dataset");
        // set vname and units according to grid feature
        // String vname= this.firstHeader.fileName.substring(0, this.firstHeader.fileName.length() -4);
        Variable var = new Variable(ncfile, ncfile.getRootGroup(), null, vname);

        var.addAttribute(new Attribute(CDM.LONG_NAME, vname)); // getPhysElemLongName(phys_elem, ent_id)));
        var.addAttribute(new Attribute(CDM.UNITS, getPhysElemUnits(gridprocuctSecondHeader.gridFeature)));
        // var.addAttribute( new Attribute(CDM.MISSING_VALUE, new Byte((byte) 0))); // ??

        // get dimensions
        int velems;

        int nx = gridprocuctSecondHeader.amountofHorizontalSpacing;
        int ny = gridprocuctSecondHeader.amountofVerticalSpacing;
        // int nz = 1;

        Dimension dimX = new Dimension("lon", nx, true, false, false);
        Dimension dimY = new Dimension("lat", ny, true, false, false);

        ncfile.addDimension(null, dimY);
        ncfile.addDimension(null, dimX);

        velems = dimX.getLength() * dimY.getLength() * gridprocuctSecondHeader.byteAmountofData;
        List<Dimension> dims = new ArrayList<>();
        dims.add(dimT);
        dims.add(dimY);
        dims.add(dimX);


        var.setDimensions(dims);

        // data type
        Class dataType;
        switch (gridprocuctSecondHeader.byteAmountofData) {
          case 1:
            var.setDataType(DataType.UBYTE);
            dataType = DataType.BYTE.getPrimitiveClassType();
            break;
          case 2:
            var.setDataType(DataType.USHORT);
            dataType = DataType.SHORT.getPrimitiveClassType();
            break;
          case 4:
            var.setDataType(DataType.UINT);
            dataType = DataType.INT.getPrimitiveClassType();
            break;
          default:
            throw new UnsupportedDatasetException("Unsupported Grid Procuct Dataset");
        }

        var.addAttribute(new Attribute(CF.COORDINATES, "lon lat"));

        // var.addAttribute(new Attribute(CDM.UNSIGNED, "true"));
        // var.addAttribute(new Attribute(CDM.UNITS, "percent"));


        if (var.getDataType() == DataType.UBYTE) {
          var.addAttribute(new Attribute("_missing_value", (byte) -1));
          var.addAttribute(new Attribute(CDM.ADD_OFFSET, gridprocuctSecondHeader.dataBaseValue));
          var.addAttribute(new Attribute(CDM.SCALE_FACTOR, gridprocuctSecondHeader.dataBaseValue));

        } else {
          var.addAttribute(new Attribute("_missing_value", (short) -1));
          var.addAttribute(new Attribute(CDM.ADD_OFFSET, gridprocuctSecondHeader.dataBaseValue));
          var.addAttribute(new Attribute(CDM.SCALE_FACTOR, gridprocuctSecondHeader.dataScale));
        }

        // size and beginning data position in file
        int vsize = velems;
        long begin = this.firstHeader.recordsOfHeader * this.firstHeader.recoderLength;
        if (debug)
          log.warn(" name= " + vname + " vsize=" + vsize + " velems=" + velems + " begin= " + begin + "\n");
        var.setSPobject(new Vinfo(vsize, begin, nx, ny, dataType, this.firstHeader.byteOrder));
        String coordinates = "lon lat time";
        var.addAttribute(new Attribute(_Coordinate.Axes, coordinates));

        ncfile.addVariable(ncfile.getRootGroup(), var);

        // we have to project in order to find the origin
        LatLonPointImpl startPnt =
            new LatLonPointImpl(gridprocuctSecondHeader.leftTopLat, gridprocuctSecondHeader.leftTopLon);
        LatLonPointImpl endPnt =
            new LatLonPointImpl(gridprocuctSecondHeader.rightBottomLat, gridprocuctSecondHeader.rightBottomLon);
        if (debug)
          System.out.println("start at geo coord :" + startPnt);


        Variable yaxis = new Variable(ncfile, null, null, "lat");
        yaxis.setDataType(DataType.DOUBLE);
        yaxis.setDimensions("lat");
        yaxis.addAttribute(new Attribute(CDM.LONG_NAME, "latitude"));
        yaxis.addAttribute(new Attribute(CDM.UNITS, "degree_north"));
        yaxis.addAttribute(new Attribute(_Coordinate.AxisType, "latitude"));
        double[] data = new double[ny];

        double dy = (endPnt.getLatitude() - startPnt.getLatitude()) / (ny - 1);
        for (int i = 0; i < data.length; i++) {
          data[i] = startPnt.getLatitude() + i * dy; // starty + i*dy;
        }

        dataA = Array.factory(DataType.DOUBLE, new int[] {ny}, data);
        yaxis.setCachedData(dataA, false);
        ncfile.addVariable(null, yaxis);


        // create coordinate variables
        Variable xaxis = new Variable(ncfile, null, null, "lon");
        xaxis.setDataType(DataType.DOUBLE);
        xaxis.setDimensions("lon");
        xaxis.addAttribute(new Attribute(CDM.LONG_NAME, "longitude"));
        xaxis.addAttribute(new Attribute(CDM.UNITS, "degree_east"));
        xaxis.addAttribute(new Attribute(_Coordinate.AxisType, "longitude"));
        data = new double[nx];

        double dx = (endPnt.getLongitude() - startPnt.getLongitude()) / (nx - 1);
        for (int i = 0; i < data.length; i++) {
          data[i] = startPnt.getLongitude() + i * dx; // startx + i*dx;

        }

        dataA = Array.factory(DataType.DOUBLE, new int[] {nx}, data);
        xaxis.setCachedData(dataA, false);
        ncfile.addVariable(null, xaxis);


        // // coordinate transform variable
        // Variable ct = new Variable( ncfile, null, null, projection.getClassName());
        // ct.setDataType( DataType.CHAR);
        // ct.setDimensions( "");
        // List params = projection.getProjectionParameters();
        // for (int i = 0; i < params.size(); i++) {
        // Parameter p = (Parameter) params.get(i);
        // ct.addAttribute( new Attribute(p));
        // }
        // ct.addAttribute( new Attribute(_Coordinate.TransformType, "Projection"));
        // ct.addAttribute( new Attribute(_Coordinate.Axes, "x y "));
        // // fake data
        // dataA = Array.factory(DataType.CHAR, new int[] {});
        // dataA.setChar(dataA.getIndex(), ' ');
        // ct.setCachedData(dataA, false);
        //
        // ncfile.addVariable(null, ct);
        // ncfile.addAttribute( null, new Attribute("Conventions", _Coordinate.Convention));


        // add more addAttributes


        // String timeCoordName = "time";
        // Variable taxis = new Variable(ncfile, null, null, timeCoordName);
        // taxis.setDataType(DataType.DOUBLE);
        // taxis.setDimensions("time");
        // taxis.addAttribute( new Attribute(CDM.LONG_NAME, "time since base date"));
        // taxis.addAttribute( new Attribute(_Coordinate.AxisType, AxisType.Time.toString()));
        // double [] tdata = new double[1];
        // tdata[0] = cal.getTimeInMillis();
        // Array dataA = Array.factory(DataType.DOUBLE, new int[] {1}, tdata);
        // taxis.setCachedData( dataA, false);
        // DateFormatter formatter = new DateFormatter();
        // taxis.addAttribute( new Attribute(CDM.UNITS, "msecs since "+formatter.toDateTimeStringISO(new Date(0))));
        // ncfile.addVariable(null, taxis);
        //
        break;
      }
    }


    // finish
    ncfile.finish();
  }

  String getGeoSatelliteProductName(int channel) {
    String vname;
    switch (channel) {
      case 1:
        vname = "IR";
        break;
      case 2:
        vname = "WV";
        break;
      case 3:
        vname = "IR_WV";
        break;
      case 4:
        vname = "VIS";
        break;
      case 34:
        vname = "DST";
        break;
      default: {
        return null;
      }
    }
    return vname;
  }

  String getGridProductName(int feature) {
    String vname;
    switch (feature) {
      case 1:
        vname = "SST";
        break;
      case 2:
        vname = "SeaICE";
        break;
      case 3:
        vname = "SeaICEDensity";
        break;
      case 4:
        vname = "LongWaveRadiation";
        break;
      case 5:
        vname = "plantIdx";
        break;
      case 6:
        vname = "plantIdxRatio";
        break;
      case 7:
        vname = "snow";
        break;
      case 8:
        vname = "soilHumidity";
        break;
      case 9:
        vname = "sunshine";
        break;
      case 10:
        vname = "cloudTopHeight";
        break;
      case 11:
        vname = "cloudTopTemp";
        break;
      case 12:
        vname = "lowCloudVolume";
        break;
      case 13:
        vname = "highCloudVolume";
        break;
      case 14:
        vname = "precipIdx1hour";
        break;
      case 15:
        vname = "precipIdx6hour";
        break;
      case 16:
        vname = "precipIdx12hour";
        break;
      case 17:
        vname = "precipIdx24hour";
        break;
      case 18:
        vname = "waterVapor";
        break;
      case 19:
        vname = "cloudTemp";
        break;
      case 501:
        vname = "TOVS";
        break;
      case 502:
        vname = "TOVS";
        break;
      case 503:
        vname = "TOVS";
        break;
      case 504:
        vname = "TOVS";
        break;
      case 505:
        vname = "TOVS";
        break;
      case 506:
        vname = "TOVS";
        break;
      case 507:
        vname = "TOVS";
        break;
      default: {
        return null;
      }
    }
    return vname;
  }

  String getPhysElemUnits(int feature) {
    String unit;

    switch (feature) {
      case 1:
      case 506:
      case 19:
      case 11:
        unit = "K";
        break;
      case 2:
      case 507:
      case 501:
      case 18:
      case 13:
      case 12:
      case 7:
      case 6:
      case 5:
      case 3:
        unit = "";
        break;
      case 4:
      case 504:
        unit = "W/m2";
        break;
      case 8:
        unit = "kg/m3";
        break;
      case 9:
        unit = "hour";
        break;
      case 10:
      case 505:
        unit = "hPa";
        break;
      case 14:
        unit = "mm/hour";
        break;
      case 15:
        unit = "mm/(6 hour)";
        break;
      case 16:
        unit = "mm/(12 hour)";
        break;
      case 17:
        unit = "mm/(24 hour)";
        break;
      case 502:
        unit = "mm";
        break;
      case 503:
        unit = "Db";
        break;
      default: {
        return null;
      }

    }
    return unit;
  }

  int getCompressType() {
    return Z_type;
  }

  // Return the string of entity ID for the GINI image file


  // ////////////////////////////////////////////////////////////////////////////////////////////////////////
  //
  // variable info for reading/writing

  static class Vinfo {
    int vsize; // size of array in bytes. if isRecord, size per record.
    long begin; // offset of start of data from start of file
    int nx;
    int ny;
    Class classType;
    short byteOrder;

    Vinfo(int vsize, long begin, int x, int y, Class dt, short byteOrder) {
      this.vsize = vsize;
      this.begin = begin;
      this.nx = x;
      this.ny = y;
      this.classType = dt;
      this.byteOrder = byteOrder;
    }
  }

}
