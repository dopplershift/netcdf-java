---
title: Writing netCDF
last_updated: 2019-07-23
sidebar: netcdfJavaTutorial_sidebar
permalink: writing_netcdf.html
toc: false
---

## NetCDF File Writing (version 4.3+)

CDM version 4.3 and above allows you to programatically create, edit, and add data to netCDF-3 and netCDF-4 files, using NetcdfFileWriter or the FileWriter2 class. If you just want to copy an existing CDM dataset, you can use the <a href="cdm_utility_programs.html#nccopy">CDM nccopy application</a>. By combining nccopy and NcML, you can copy just parts of an existing dataset, as well as <a href="ncml_overview.html" >make modifications to it with NcML</a>.

Writing netCDF-4 files requires that you install the <a href="netcdf4_c_library.html">netCDF-4 C library</a> on your machine.

### Using NetcdfFileWriter

#### Example creating a new netCDF-3 file

~~~
  public static void main(String[] args) throws IOException {
    String location = "C:/tmp/testWrite.nc";
1)  NetcdfFileWriter writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, location, null);

    // add dimensions
2)  Dimension latDim = writer.addDimension(null, "lat", 64);
    Dimension lonDim = writer.addDimension(null, "lon", 128);

    // add Variable double temperature(lat,lon)
    List<Dimension> dims = new ArrayList<Dimension>();
    dims.add(latDim);
    dims.add(lonDim);
3)  Variable t = writer.addVariable(null, "temperature", DataType.DOUBLE, dims);
4)  t.addAttribute(new Attribute("units", "K"));   // add a 1D attribute of length 3
5)  Array data = Array.factory(int.class, new int[]{3}, new int[]{1, 2, 3});
6)  t.addAttribute(new Attribute("scale", data));

    // add a string-valued variable: char svar(80)
    Dimension svar_len = writer.addDimension(null, "svar_len", 80);
7)  writer.addVariable(null, "svar", DataType.CHAR, "svar_len");

    // add a 2D string-valued variable: char names(names, 80)
    Dimension names = writer.addDimension(null, "names", 3);
8)  writer.addVariable(null, "names", DataType.CHAR, "names svar_len");

    // add a scalar variable
9)  writer.addVariable(null, "scalar", DataType.DOUBLE, new ArrayList<Dimension>());

    // add global attributes
10) writer.addGroupAttribute(null, new Attribute("yo", "face"));
    writer.addGroupAttribute(null, new Attribute("versionD", 1.2));
    writer.addGroupAttribute(null, new Attribute("versionF", (float) 1.2));
    writer.addGroupAttribute(null, new Attribute("versionI", 1));
    writer.addGroupAttribute(null, new Attribute("versionS", (short) 2));
    writer.addGroupAttribute(null, new Attribute("versionB", (byte) 3));

    // create the file
    try {
11)    writer.create();
    } catch (IOException e) {
      System.err.printf("ERROR creating file %s%n%s", location, e.getMessage());
    }
12)  writer.close();
  }
~~~

1. Create new netCDF-3 file with the given filename
2. Create two <b>Dimensions</b>, named lat and lon, of lengths 64 and 128 respectively, and add them to the root group.
3. Create a <b>Variable</b> named temperature, of type <b>double</b>, with shape (lat, lon), and add to the root group.
4. Add a string <b>Attribute</b> to the temperature Variable, with name units and value K.
5. Create a 1D <b>Array</b> of length 3, whose values are {1,2,3}. Attributes can be scalars or 1D arrays of any type and length.
6. Add an integer <b>Attribute</b> to the temperature Variable, with name scale and value (1,2,3).
7. Create a Variable named svar of type character with length 80.
8. Create a 2D Variable named names of type character with shape (3,80).
9. Create a scalar Variable named scalar of type double. Note that the empty ArrayList means that it is a scalar, ie has no Dimensions.
10. Create various global Attributes of different types.
11. Create the file. At this point the (empty) file will be written to disk, and the metadata (Dimensions, Variables and Atributes) is fixed and cannot be changed or added.
12. You must close the file.

The resulting file looks like:

~~~
netcdf C:/tmp/testWrite.nc {
 dimensions:
 lat = 64;
 lon = 128;
 svar_len = 80;
 names = 3;
 variables:
   double temperature(lat=64, lon=128);
    :units = "K";
    :scale = 1, 2, 3; // int
   char svar(svar_len=80);
   char names(names=3, svar_len=80);
   double scalar;
    
    // global attributes:
   :yo = "face";
   :versionD = 1.2; // double
   :versionF = 1.2f; // float
   :versionI = 1; // int
   :versionS = 2S; // short
   :versionB = 3B; // byte
   }
~~~

Notes:

By default, _fill = false_. Setting _fill = true) (</b>writer.setFill(true)</b) causes everything to be written twice: first with the fill value, then with the data values. If you know you will write all the data, you dont need to use fill. If you don't know if all the data will be written, turning fill on ensures that any values not written will have the fill value. Otherwise those values will be undefined: possibly zero, or possibly garbage. 

#### Writing data to a new or existing file

You can now start writing data to the new file. Or you can open an existing file for example:

~~~
  NetcdfFileWriter writer = NetcdfFileWriter.openExisting(location);
~~~
  
In both cases the data writing is the same, for example:

~~~
   // write data to variable
   Variable v = writer.findVariable("temperature");
   int[] shape = v.getShape();
1) ArrayDouble A = new ArrayDouble.D2(shape[0], shape[1]);
   int i, j;
   Index ima = A.getIndex();
   for (i = 0; i < shape[0]; i++) {
     for (j = 0; j < shape[1]; j++) {
       A.setDouble(ima.set(i, j), (double) (i * 1000000 + j * 1000));
     }
   }
   
2) int[] origin = new int[2];
   try {
3)   writer.write(v, origin, A);
   } catch (IOException e) {
     System.err.println("ERROR writing file");
   } catch (InvalidRangeException e) {
     e.printStackTrace();
   }
~~~   
   
~~~   
    // write char variable as String
    v = writer.findVariable("svar");
    shape = v.getShape();
    len = shape[0];
    try {
4)    ArrayChar ac2 = new ArrayChar.D1(len);
      ac2.setString("Two pairs of ladies stockings!");
5)    writer.write(v, ac2);
    } catch (IOException e) {
      System.err.println("ERROR writing Achar2");
      assert (false);
    } catch (InvalidRangeException e) {
      e.printStackTrace();
      assert (false);
    }
~~~

~~~    
    // write String array
    v = writer.findVariable("names");
    shape = v.getShape();
    try {
6)    ArrayChar ac2 = new ArrayChar.D2(shape[0], shape[1]);
      ima = ac2.getIndex();
      ac2.setString(ima.set(0), "No pairs of ladies stockings!");
      ac2.setString(ima.set(1), "One pair of ladies stockings!");
      ac2.setString(ima.set(2), "Two pairs of ladies stockings!");
      writer.write(v, ac2);
    } catch (IOException e) {
      System.err.println("ERROR writing Achar3");
      assert (false);
    } catch (InvalidRangeException e) {
      e.printStackTrace();
      assert (false);
    }
~~~

~~~    
   // write scalar data
   try {
7)   ArrayDouble.D0 datas = new ArrayDouble.D0();
     datas.set(222.333);
     v = writer.findVariable("scalar");

     writer.write(v, datas);
   } catch (IOException e) {
     System.err.println("ERROR writing scalar");
   } catch (InvalidRangeException e) {
     e.printStackTrace();
   }
~~~

~~~   
   try {
8)   ncfile.close();
   } catch (IOException e) {
     e.printStackTrace();
   }
~~~

1. Much of the work of writing is constructing the data Arrays. Here we create a 2D Array of the same shape as temperature(lat, lon) and fill it with some values.
2. A newly created Java integer array is guarenteed to be initialized to zeros.
3. We write the data to the temperature Variable, with <b>origin</b> all zeros. The <b>shape</b> is taken from the data Array.
4. The <b>ArrayChar</b> class has special methods to make it convenient to work with Strings. Note that we use the type and rank specific constructor <b>ArrayChar.D1</b>. The <b>setString(String val)</b> method is for rank one ArrayChar objects.
5. Write the data. Since we dont pass in an origin parameter, it is assumed to be all zeroes.
6. The <b>setString(int index, String val)</b> method is for rank two ArrayChar objects.
7. Working with type and rank specific Array objects provides convenient <b>set()</b> methods. Here, we have a rank-0 (scalar) double Array, whose set() methods sets the scalar value.
8. You must close the file when you are done, else you risk not writing the data to disk. The flush() method will flush to disk without closing. 

#### Writing data one record at a time along the record dimension

~~~
  public void testWriteRecordOneAtaTime() throws IOException, InvalidRangeException {
    String filename = TestLocal.temporaryDataDir + "testWriteRecord2.nc";
    NetcdfFileWriter writer = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf3, filename);

    // define dimensions, including unlimited
    Dimension latDim = writer.addDimension(null, "lat", 3);
    Dimension lonDim = writer.addDimension(null, "lon", 4);
    Dimension timeDim = writer.addUnlimitedDimension("time");

    // define Variables
    Variable lat = writer.addVariable(null, "lat", DataType.FLOAT, "lat");
    lat.addAttribute( new Attribute("units", "degrees_north"));
    Variable lon = writer.addVariable(null, "lon", DataType.FLOAT, "lon");
    lon.addAttribute( new Attribute("units", "degrees_east"));
    Variable rh = writer.addVariable(null, "rh", DataType.INT, "time lat lon");
    rh.addAttribute( new Attribute("long_name", "relative humidity"));
    rh.addAttribute( new Attribute("units", "percent"));
    Variable t = writer.addVariable(null, "T", DataType.DOUBLE, "time lat lon");
    t.addAttribute( new Attribute("long_name", "surface temperature"));
    t.addAttribute( new Attribute("units", "degC"));
    Variable time = writer.addVariable(null, "time", DataType.INT, "time");
    time.addAttribute( new Attribute("units", "hours since 1990-01-01"));

    // create the file
1)  writer.create();

    // write out the non-record variables
2)  writer.write(lat, Array.factory(new float[]{41, 40, 39}));
    writer.write(lon, Array.factory(new float[]{-109, -107, -105, -103}));

    //// heres where we write the record variables
    // different ways to create the data arrays.
    // Note the outer dimension has shape 1, since we will write one record at a time
3)  ArrayInt rhData = new ArrayInt.D3(1, latDim.getLength(), lonDim.getLength());
    ArrayDouble.D3 tempData = new ArrayDouble.D3(1, latDim.getLength(), lonDim.getLength());
    Array timeData = Array.factory(DataType.INT, new int[]{1});
    Index ima = rhData.getIndex();

    int[] origin = new int[]{0, 0, 0};
    int[] time_origin = new int[]{0};

    // loop over each record
4)  for (int timeIdx = 0; timeIdx < 10; timeIdx++) {
      // make up some data for this record, using different ways to fill the data arrays.
5.1)  timeData.setInt(timeData.getIndex(), timeIdx * 12);

      for (int latIdx = 0; latIdx < latDim.getLength(); latIdx++) {
        for (int lonIdx = 0; lonIdx < lonDim.getLength(); lonIdx++) {
5.2)      rhData.setInt(ima.set(0, latIdx, lonIdx), timeIdx * latIdx * lonIdx);
5.3)      tempData.set(0, latIdx, lonIdx, timeIdx * latIdx * lonIdx / 3.14159);
        }
      }
      // write the data out for one record
      // set the origin here
6)    time_origin[0] = timeIdx;
      origin[0] = timeIdx;
7)    writer.write(rh, origin, rhData);
      writer.write(t, origin, tempData);
      writer.write(time, time_origin, timeData);
    } // loop over record

    // all done
    writer.close();
  }
~~~

1. Define the dimensions, variables, and attributes. Note the use of <b>NetcdfFileWriter.addUnlimitedDimension()</b> to add a record dimension.
2. Write the non-record variables
3. Create the arrays to hold the data. Note that the outer dimension has shape of 1, since we will write only one record at a time.
4. Loop over the unlimited (record) dimension. Each loop will write one record.
5. Set the data for this record, using three different ways to fill the data arrays. In all cases the first dimension has index = 0.
    * <b>Array.setInt(Index ima, int value)</b> : timeData.getIndex() returns an Index initialized to zero.
    * <b>Array.setInt(Index ima, int value)</b> : ima.set(0, lat, lon) explicitly sets the dimension indices
    * <b>ArrayDouble.D3.set(int i, int j, int k, double value)</b>: by using a type and rank specific Array class (ArrayDouble.D3), we don't need to use an Index object.
6. Set the origin to the current record number. The other dimensions have origin 0.
7. Write the data at the specified origin.

### Writing to a netCDF-4 file with compression (version 4.5)

To write to netCDF-4, you must install the <a href="netcdf4_c_library.html">netCDF-4 C library</a> on your machine.

The main use of netCDF-4 is to get the performance benefits from compression, and possibly from chunking (<a href="http://www.unidata.ucar.edu/blogs/developer/en/entry/chunking_data_why_it_matters">why it matters</a>). By default, the Java library will write chunked and compressed netcdf-4 files, using the default chunking algorithm. To have your own control of chunking and compression, you must create a Nc4Chunking object and pass it into NetcdfFileWriter.createNew():

~~~
Nc4Chunking chunker = Nc4Chunking factory(Strategy type, int deflateLevel, boolean shuffle);
NetcdfFileWriter.Version version = NetcdfFileWriter.Version.netcdf4;

FileWriter2 writer = new ucar.nc2.FileWriter2(ncfileIn, filenameOut, version, chunker);
...
NetcdfFile ncfileOut = writer.write();
ncfileIn.close();
ncfileOut.close();
~~~
  
See <a href="netcdf4_c_library.html#writing-netcdf-4-files">here</a> for more details on Nc4Chunking.