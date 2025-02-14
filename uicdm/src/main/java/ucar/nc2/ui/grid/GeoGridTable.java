/*
 * Copyright (c) 1998-2018 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.ui.grid;

// import thredds.wcs.Request;
// import thredds.wcs.v1_0_0_1.*;
import ucar.nc2.*;
import ucar.nc2.constants.AxisType;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.dt.GridCoordSystem;
import ucar.nc2.dt.GridDataset;
import ucar.nc2.dt.grid.CFGridWriter2;
import ucar.nc2.dt.grid.GridCoordSys;
import ucar.nc2.dataset.*;
import ucar.nc2.ft.fmrc.GridDatasetInv;
import ucar.nc2.ui.dialog.NetcdfOutputChooser;
import ucar.ui.widget.*;
import ucar.ui.widget.PopupMenu;
import ucar.unidata.geoloc.Projection;
import ucar.util.prefs.PreferencesExt;
import ucar.ui.prefs.*;
import ucar.unidata.geoloc.ProjectionImpl;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.*;
import java.util.List;
import java.io.IOException;
import javax.swing.*;

/**
 * A Swing widget to examine a GridDataset.
 *
 * @author caron
 */

public class GeoGridTable extends JPanel {
  private PreferencesExt prefs;
  private GridDataset gridDataset;

  private BeanTable varTable, csTable, axisTable;
  private JSplitPane split, split2;
  private TextHistoryPane infoTA;
  private IndependentWindow infoWindow;
  private NetcdfOutputChooser outChooser;

  public GeoGridTable(PreferencesExt prefs, boolean showCS) {
    this.prefs = prefs;

    varTable = new BeanTable(GeoGridBean.class, (PreferencesExt) prefs.node("GeogridBeans"), false);
    JTable jtable = varTable.getJTable();

    PopupMenu csPopup = new ucar.ui.widget.PopupMenu(jtable, "Options");
    csPopup.addAction("Show Declaration", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        GeoGridBean vb = (GeoGridBean) varTable.getSelectedBean();
        Variable v = vb.geogrid.getVariable();
        infoTA.clear();
        if (v == null)
          infoTA.appendLine(
              "Cant find variable " + vb.getName() + " escaped= (" + NetcdfFile.makeValidPathName(vb.getName()) + ")");
        else {
          infoTA.appendLine("Variable " + v.getFullName() + " :");
          infoTA.appendLine(v.toString());
        }
        infoTA.gotoTop();
        infoWindow.show();
      }
    });

    csPopup.addAction("Show Coordinates", new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        GeoGridBean vb = (GeoGridBean) varTable.getSelectedBean();
        Formatter f = new Formatter();
        showCoordinates(vb, f);
        infoTA.setText(f.toString());
        infoTA.gotoTop();
        infoWindow.show();
      }
    });

    /*
     * csPopup.addAction("WCS DescribeCoverage", new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * GeoGridBean vb = (GeoGridBean) varTable.getSelectedBean();
     * if (gridDataset.findGridDatatype(vb.getName()) != null) {
     * List<String> coverageIdList = Collections.singletonList(vb.getName());
     * try {
     * DescribeCoverage descCov =
     * ((DescribeCoverageBuilder)
     * WcsRequestBuilder
     * .newWcsRequestBuilder("1.0.0",
     * Request.Operation.DescribeCoverage,
     * gridDataset, ""))
     * .setCoverageIdList(coverageIdList)
     * .buildDescribeCoverage();
     * String dc = descCov.writeDescribeCoverageDocAsString();
     * infoTA.clear();
     * infoTA.appendLine(dc);
     * infoTA.gotoTop();
     * infoWindow.show();
     * } catch (WcsException e1) {
     * e1.printStackTrace();
     * } catch (IOException e1) {
     * e1.printStackTrace();
     * }
     * }
     * }
     * });
     */

    // the info window
    infoTA = new TextHistoryPane();
    infoWindow = new IndependentWindow("Variable Information", BAMutil.getImage("nj22/NetcdfUI"), infoTA);
    infoWindow.setBounds((Rectangle) prefs.getBean("InfoWindowBounds", new Rectangle(300, 300, 500, 300)));

    // optionally show coordinate systems and axis
    Component comp = varTable;
    if (showCS) {
      csTable =
          new BeanTable(GeoCoordinateSystemBean.class, (PreferencesExt) prefs.node("GeoCoordinateSystemBean"), false);
      axisTable = new BeanTable(GeoAxisBean.class, (PreferencesExt) prefs.node("GeoCoordinateAxisBean"), false);

      split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, varTable, csTable);
      split.setDividerLocation(prefs.getInt("splitPos", 500));

      split2 = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false, split, axisTable);
      split2.setDividerLocation(prefs.getInt("splitPos2", 500));
      comp = split2;
    }

    setLayout(new BorderLayout());
    add(comp, BorderLayout.CENTER);
  }

  public void addExtra(JPanel buttPanel, FileManager fileChooser) {

    AbstractButton infoButton = BAMutil.makeButtcon("Information", "Detail Info", false);
    infoButton.addActionListener(e -> {
      if ((gridDataset != null) && (gridDataset instanceof ucar.nc2.dt.grid.GridDataset)) {
        ucar.nc2.dt.grid.GridDataset gdsImpl = (ucar.nc2.dt.grid.GridDataset) gridDataset;
        infoTA.clear();
        infoTA.appendLine(gdsImpl.getDetailInfo());
        infoTA.gotoTop();
        infoWindow.show();
      }
    });
    buttPanel.add(infoButton);

    /*
     * JButton wcsButton = new JButton("WCS");
     * wcsButton.addActionListener(new ActionListener() {
     * public void actionPerformed(ActionEvent e) {
     * if (gridDataset != null) {
     * URI gdUri = null;
     * try {
     * gdUri = new URI("http://none.such.server/thredds/wcs/dataset");
     * } catch (URISyntaxException e1) {
     * e1.printStackTrace();
     * return;
     * }
     * GetCapabilities getCap =
     * ((GetCapabilitiesBuilder)
     * WcsRequestBuilder
     * .newWcsRequestBuilder("1.0.0",
     * Request.Operation.GetCapabilities,
     * gridDataset, ""))
     * .setServerUri(gdUri)
     * .setSection(GetCapabilities.Section.All)
     * .buildGetCapabilities();
     * try {
     * String gc = getCap.writeCapabilitiesReportAsString();
     * infoTA.setText(gc);
     * infoTA.gotoTop();
     * infoWindow.show();
     * } catch (WcsException e1) {
     * e1.printStackTrace();
     * }
     * }
     * }
     * });
     * buttPanel.add(wcsButton);
     */

    JButton invButton = new JButton("GridInv");
    invButton.addActionListener(e -> {
      if (gridDataset == null)
        return;
      GridDatasetInv inv = new GridDatasetInv((ucar.nc2.dt.grid.GridDataset) gridDataset, null);
      try {
        infoTA.setText(inv.writeXML(new Date()));
        infoTA.gotoTop();
        infoWindow.show();
      } catch (Exception e1) {
        e1.printStackTrace();
      }
    });
    buttPanel.add(invButton);

    AbstractAction netcdfAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (gridDataset == null)
          return;
        List<String> gridList = getSelectedGrids();
        if (gridList.isEmpty()) {
          JOptionPane.showMessageDialog(GeoGridTable.this, "No Grids are selected");
          return;
        }

        if (outChooser == null) {
          outChooser = new NetcdfOutputChooser((Frame) null);
          outChooser.addPropertyChangeListener("OK", new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
              writeNetcdf((NetcdfOutputChooser.Data) evt.getNewValue());
            }
          });
        }
        outChooser.setOutputFilename(gridDataset.getLocation());
        outChooser.setVisible(true);
      }
    };
    BAMutil.setActionProperties(netcdfAction, "nj22/Netcdf", "Write netCDF-CF file", false, 'S', -1);
    BAMutil.addActionToContainer(buttPanel, netcdfAction);

    /*
     * AbstractAction writeAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * if (gridDataset == null) return;
     * List<String> gridList = getSelectedGrids();
     * if (gridList.size() == 0) {
     * JOptionPane.showMessageDialog(GeoGridTable.this, "No Grids are selected");
     * return;
     * }
     * String location = gridDataset.getLocationURI();
     * if (location == null) location = "test";
     * String suffix = (location.endsWith(".nc") ? ".sub.nc" : ".nc");
     * int pos = location.lastIndexOf(".");
     * if (pos > 0)
     * location = location.substring(0, pos);
     * 
     * String filename = fileChooser.chooseFilenameToSave(location + suffix);
     * if (filename == null) return;
     * 
     * try {
     * NetcdfCFWriter.makeFileVersioned(filename, gridDataset, gridList, null, null);
     * JOptionPane.showMessageDialog(GeoGridTable.this, "File successfully written");
     * } catch (Exception ioe) {
     * JOptionPane.showMessageDialog(GeoGridTable.this, "ERROR: " + ioe.getMessage());
     * ioe.printStackTrace();
     * }
     * }
     * };
     * BAMutil.setActionProperties(writeAction, "netcdf", "Write netCDF-CF file", false, 'W', -1);
     * BAMutil.addActionToContainer(buttPanel, writeAction);
     */
  }

  private void showCoordinates(GeoGridBean vb, Formatter f) {
    GridCoordSystem gcs = vb.geogrid.getCoordinateSystem();
    gcs.show(f, true);
  }

  private void writeNetcdf(NetcdfOutputChooser.Data data) {
    if (data.version == NetcdfFileWriter.Version.ncstream)
      return;

    try {
      NetcdfFileWriter writer = NetcdfFileWriter.createNew(data.version, data.outputFilename, null); // default chunking
                                                                                                     // - let user
                                                                                                     // control at some
                                                                                                     // point
      CFGridWriter2.writeFile(gridDataset, getSelectedGrids(), null, null, 1, null, null, 1, false, writer);
      JOptionPane.showMessageDialog(this, "File successfully written");
    } catch (Exception ioe) {
      JOptionPane.showMessageDialog(this, "ERROR: " + ioe.getMessage());
      ioe.printStackTrace();
    }
  }

  public PreferencesExt getPrefs() {
    return prefs;
  }

  public void save() {
    varTable.saveState(false);
    prefs.putBeanObject("InfoWindowBounds", infoWindow.getBounds());
    if (split != null)
      prefs.putInt("splitPos", split.getDividerLocation());
    if (split2 != null)
      prefs.putInt("splitPos2", split2.getDividerLocation());
    if (csTable != null)
      csTable.saveState(false);
    if (axisTable != null)
      axisTable.saveState(false);
  }

  public void clear() {
    this.gridDataset = null;
    varTable.clearBeans();
    csTable.clearBeans();
    axisTable.clearBeans();
  }

  public void setDataset(NetcdfDataset ds, Formatter parseInfo) throws IOException {
    this.gridDataset = new ucar.nc2.dt.grid.GridDataset(ds, parseInfo);

    List<GeoGridBean> beanList = new ArrayList<>();
    java.util.List<GridDatatype> list = gridDataset.getGrids();
    for (GridDatatype g : list)
      beanList.add(new GeoGridBean(g));
    varTable.setBeans(beanList);

    if (csTable != null) {
      List<GeoCoordinateSystemBean> csList = new ArrayList<>();
      List<GeoAxisBean> axisList;
      axisList = new ArrayList<>();
      for (GridDataset.Gridset gset : gridDataset.getGridsets()) {
        csList.add(new GeoCoordinateSystemBean(gset));
        GridCoordSystem gsys = gset.getGeoCoordSystem();
        List<CoordinateAxis> axes = gsys.getCoordinateAxes();
        for (CoordinateAxis axis : axes) {
          GeoAxisBean axisBean = new GeoAxisBean(axis);
          if (!contains(axisList, axisBean.getName())) {
            axisList.add(axisBean);
          }
        }
      }
      csTable.setBeans(csList);
      axisTable.setBeans(axisList);
    }
  }

  public void setDataset(GridDataset gds) throws IOException {
    this.gridDataset = gds;

    List<GeoGridBean> beanList = new ArrayList<>();
    java.util.List<GridDatatype> list = gridDataset.getGrids();
    for (GridDatatype g : list)
      beanList.add(new GeoGridBean(g));
    varTable.setBeans(beanList);

    if (csTable != null) {
      List<GeoCoordinateSystemBean> csList = new ArrayList<>();
      List<GeoAxisBean> axisList;
      axisList = new ArrayList<>();
      for (GridDataset.Gridset gset : gridDataset.getGridsets()) {
        csList.add(new GeoCoordinateSystemBean(gset));
        GridCoordSystem gsys = gset.getGeoCoordSystem();
        List<CoordinateAxis> axes = gsys.getCoordinateAxes();
        for (CoordinateAxis axis : axes) {
          GeoAxisBean axisBean = new GeoAxisBean(axis);
          if (!contains(axisList, axisBean.getName())) {
            axisList.add(axisBean);
          }
        }
      }
      csTable.setBeans(csList);
      axisTable.setBeans(axisList);
    }
  }

  private boolean contains(List<GeoAxisBean> axisList, String name) {
    for (GeoAxisBean axis : axisList)
      if (axis.getName().equals(name))
        return true;
    return false;
  }

  public GridDataset getGridDataset() {
    return gridDataset;
  }

  public List<String> getSelectedGrids() {
    List grids = varTable.getSelectedBeans();
    List<String> result = new ArrayList<>();
    for (Object bean : grids) {
      GeoGridBean gbean = (GeoGridBean) bean;
      result.add(gbean.getName());
    }
    return result;
  }


  public GridDatatype getGrid() {
    GeoGridBean vb = (GeoGridBean) varTable.getSelectedBean();
    if (vb == null) {
      List grids = gridDataset.getGrids();
      if (!grids.isEmpty())
        return (GridDatatype) grids.get(0);
      else
        return null;
    }
    return gridDataset.findGridDatatype(vb.getName());
  }

  public static class GeoGridBean {
    // static public String editableProperties() { return "title include logging freq"; }

    GridDatatype geogrid;
    String name, desc, units, csys;
    String dims, x, y, z, t, ens, rt;

    // no-arg constructor
    public GeoGridBean() {}

    // create from a dataset
    public GeoGridBean(GridDatatype geogrid) {
      this.geogrid = geogrid;
      setName(geogrid.getFullName());
      setDescription(geogrid.getDescription());
      setUnits(geogrid.getUnitsString());

      GridCoordSystem gcs = geogrid.getCoordinateSystem();
      setCoordSystem(gcs.getName());

      // collect dimensions
      StringBuilder buff = new StringBuilder();
      java.util.List dims = geogrid.getDimensions();
      for (int j = 0; j < dims.size(); j++) {
        ucar.nc2.Dimension dim = (ucar.nc2.Dimension) dims.get(j);
        if (j > 0)
          buff.append(",");
        buff.append(dim.getLength());
      }
      setShape(buff.toString());

      x = getAxisName(gcs.getXHorizAxis());
      y = getAxisName(gcs.getYHorizAxis());
      z = getAxisName(gcs.getVerticalAxis());
      t = getAxisName(gcs.getTimeAxis());
      rt = getAxisName(gcs.getRunTimeAxis());
      ens = getAxisName(gcs.getEnsembleAxis());

      /*
       * Dimension d= geogrid.getXDimension();
       * if (d != null) setX( d.getName());
       * d= geogrid.getYDimension();
       * if (d != null) setY( d.getName());
       * d= geogrid.getZDimension();
       * if (d != null) setZ( d.getName());
       * 
       * GridCoordSystem gcs = geogrid.getCoordinateSystem();
       * 
       * d= geogrid.getTimeDimension();
       * if (d != null)
       * setT( d.getName());
       * else {
       * CoordinateAxis taxis = gcs.getTimeAxis();
       * if (taxis != null) setT( taxis.getName());
       * }
       * 
       * CoordinateAxis1D axis = gcs.getEnsembleAxis();
       * if (axis != null)
       * setEns( axis.getDimension(0).getName());
       * 
       * axis = gcs.getRunTimeAxis();
       * if (axis != null)
       * setRt( axis.getDimension(0).getName());
       */
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return desc;
    }

    public void setDescription(String desc) {
      this.desc = desc;
    }

    public String getUnits() {
      return units;
    }

    public void setUnits(String units) {
      this.units = units;
    }

    public String getCoordSystem() {
      return csys;
    }

    public void setCoordSystem(String csys) {
      this.csys = csys;
    }

    public String getX() {
      return x;
    }

    public String getY() {
      return y;
    }

    public String getZ() {
      return z;
    }

    public String getT() {
      return t;
    }

    public String getEns() {
      return ens;
    }

    public String getRt() {
      return rt;
    }

    public String getShape() {
      return dims;
    }

    public void setShape(String dims) {
      this.dims = dims;
    }

    private String getAxisName(CoordinateAxis axis) {
      if (axis != null)
        return (axis.isCoordinateVariable()) ? axis.getShortName() : axis.getNameAndDimensions(false);
      return "";
    }
  }

  public static class GeoCoordinateSystemBean {
    private GridCoordSystem gcs;
    private String proj, coordTrans;
    private int ngrids = -1;

    // no-arg constructor
    public GeoCoordinateSystemBean() {}

    public GeoCoordinateSystemBean(GridDataset.Gridset gset) {
      gcs = gset.getGeoCoordSystem();

      setNGrids(gset.getGrids().size());

      Projection proj = gcs.getProjection();
      if (proj != null)
        setProjection(proj.getClassName());

      int count = 0;
      StringBuilder buff = new StringBuilder();
      List ctList = gcs.getCoordinateTransforms();
      for (Object o : ctList) {
        CoordinateTransform ct = (CoordinateTransform) o;
        if (count > 0) {
          buff.append("; ");
        }
        // buff.append( ct.getTransformType());
        if (ct instanceof VerticalCT) {
          buff.append(((VerticalCT) ct).getVerticalTransformType());
          count++;
        }
        if (ct instanceof ProjectionCT) {
          ProjectionCT pct = (ProjectionCT) ct;
          if (pct.getProjection() == null) { // only show CT if theres no projection
            buff.append("-").append(pct.getName());
            count++;
          }
        }
      }
      setCoordTransforms(buff.toString());
    }

    public String getName() {
      return gcs.getName();
    }

    public boolean isRegularSpatial() {
      return gcs.isRegularSpatial();
    }

    public boolean isLatLon() {
      return gcs.isLatLon();
    }

    public boolean isGeoXY() {
      return ((GridCoordSys) gcs).isGeoXY();
    }

    /*
     * public boolean isProductSet() { return isProductSet; }
     * public void setProductSet(boolean isProductSet) { this.isProductSet = isProductSet; }
     */

    public int getDomainRank() {
      return gcs.getDomain().size();
    }

    public int getRangeRank() {
      return gcs.getCoordinateAxes().size();
    }

    public int getNGrids() {
      return ngrids;
    }

    public void setNGrids(int ngrids) {
      this.ngrids = ngrids;
    }

    public String getProjection() {
      return proj;
    }

    public void setProjection(String proj) {
      this.proj = proj;
    }

    public String getCoordTransforms() {
      return coordTrans;
    }

    public void setCoordTransforms(String coordTrans) {
      this.coordTrans = coordTrans;
    }
  }

  public static class GeoAxisBean {
    // static public String editableProperties() { return "title include logging freq"; }

    CoordinateAxis axis;
    CoordinateSystem firstCoordSys;
    String name, desc, units, axisType = "", positive = "", incr = "";
    String dims, shape, csNames;
    boolean isCoordVar;

    // no-arg constructor
    public GeoAxisBean() {}

    // create from a dataset
    public GeoAxisBean(CoordinateAxis v) {
      this.axis = v;

      setName(v.getFullName());
      setCoordVar(v.isCoordinateVariable());
      setDescription(v.getDescription());
      setUnits(v.getUnitsString());

      // collect dimensions
      StringBuilder lens = new StringBuilder();
      StringBuilder names = new StringBuilder();
      java.util.List dims = v.getDimensions();
      for (int j = 0; j < dims.size(); j++) {
        ucar.nc2.Dimension dim = (ucar.nc2.Dimension) dims.get(j);
        if (j > 0) {
          lens.append(",");
          names.append(",");
        }
        String name = dim.isShared() ? dim.getShortName() : "anon";
        names.append(name);
        lens.append(dim.getLength());
      }
      setDims(names.toString());
      setShape(lens.toString());

      AxisType at = v.getAxisType();
      if (at != null)
        setAxisType(at.toString());
      String p = v.getPositive();
      if (p != null)
        setPositive(p);

      if (v instanceof CoordinateAxis1D) {
        CoordinateAxis1D v1 = (CoordinateAxis1D) v;
        if (v1.isRegular())
          setRegular(Double.toString(v1.getIncrement()));
      }
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public boolean isCoordVar() {
      return isCoordVar;
    }

    public void setCoordVar(boolean isCoordVar) {
      this.isCoordVar = isCoordVar;
    }

    public String getShape() {
      return shape;
    }

    public void setShape(String shape) {
      this.shape = shape;
    }

    public String getAxisType() {
      return axisType;
    }

    public void setAxisType(String axisType) {
      this.axisType = axisType;
    }

    public String getDims() {
      return dims;
    }

    public void setDims(String dims) {
      this.dims = dims;
    }

    public String getDescription() {
      return desc;
    }

    public void setDescription(String desc) {
      this.desc = desc;
    }

    public String getUnits() {
      return units;
    }

    public void setUnits(String units) {
      this.units = (units == null) ? "null" : units;
    }

    public String getPositive() {
      return positive;
    }

    public void setPositive(String positive) {
      this.positive = positive;
    }

    public String getRegular() {
      return incr;
    }

    public void setRegular(String incr) {
      this.incr = incr;
    }
  }


  /**
   * Wrap this in a JDialog component.
   *
   * @param parent JFrame (application) or JApplet (applet) or null
   * @param title dialog window title
   * @param modal modal dialog or not
   * @return JDialog
   */
  public JDialog makeDialog(RootPaneContainer parent, String title, boolean modal) {
    return new Dialog(parent, title, modal);
  }

  private class Dialog extends JDialog {

    private Dialog(RootPaneContainer parent, String title, boolean modal) {
      super(parent instanceof Frame ? (Frame) parent : null, title, modal);

      // L&F may change
      UIManager.addPropertyChangeListener(new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent e) {
          if (e.getPropertyName().equals("lookAndFeel"))
            SwingUtilities.updateComponentTreeUI(GeoGridTable.Dialog.this);
        }
      });

      /*
       * add a dismiss button
       * JButton dismissButton = new JButton("Dismiss");
       * buttPanel.add(dismissButton, null);
       * 
       * dismissButton.addActionListener(new ActionListener() {
       * public void actionPerformed(ActionEvent evt) {
       * setVisible(false);
       * }
       * });
       */

      // add it to contentPane
      Container cp = getContentPane();
      cp.setLayout(new BorderLayout());
      cp.add(GeoGridTable.this, BorderLayout.CENTER);
      pack();
    }
  }
}
