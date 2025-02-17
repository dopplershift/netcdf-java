/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.stream;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import java.util.Optional;
import ucar.httpservices.*;
import ucar.ma2.*;
import ucar.nc2.Structure;
import ucar.nc2.Variable;
import ucar.nc2.util.IO;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Formatter;

/**
 * A remote CDM dataset (extending NetcdfFile), using cdmremote protocol to communicate.
 * Similar to Opendap in that it is a remote access protocol using indexed data access.
 * Supports full CDM / netcdf-4 data model.
 *
 * @author caron
 * @since Feb 7, 2009
 */
public class CdmRemote extends ucar.nc2.NetcdfFile {
  public static final String PROTOCOL = "cdmremote";
  public static final String SCHEME = PROTOCOL + ":";

  // static private org.slf4SCHEMEj.Logger logger = org.slf4j.LoggerFactory.getLogger(CdmRemote.class);
  private static boolean showRequest;
  private static boolean compress;

  public static void setDebugFlags(ucar.nc2.util.DebugFlags debugFlag) {
    showRequest = debugFlag.isSet("CdmRemote/showRequest");
  }

  public static void setAllowCompression(boolean b) {
    compress = b;
  }


  /**
   * Create the canonical form of the URL.
   * If the urlName starts with "http:", change it to start with "cdmremote:", otherwise
   * leave it alone.
   *
   * @param urlName the url string
   * @return canonical form
   */
  public static String canonicalURL(String urlName) {
    if (urlName.startsWith("http:"))
      return SCHEME + urlName.substring(5);
    return urlName;
  }

  //////////////////////////////////////////////////////

  private HTTPSession httpClient; // stays open until close is called

  private final String remoteURI;

  public CdmRemote(String _remoteURI) throws IOException {
    long start = System.currentTimeMillis();

    // get http URL
    String temp = _remoteURI;
    try {
      if (temp.startsWith(SCHEME))
        temp = temp.substring(SCHEME.length());
      if (!temp.startsWith("http:"))
        temp = "http:" + temp;
    } catch (Exception e) {
      throw new IOException(e);
    }
    remoteURI = temp;
    httpClient = HTTPFactory.newSession(remoteURI);
    // get the header
    String url = remoteURI + "?req=header";
    if (showRequest)
      System.out.printf(" CdmRemote request %s%n", url);
    try (HTTPMethod method = HTTPFactory.Get(httpClient, url)) {
      method.setFollowRedirects(true);
      int statusCode = method.execute();

      if (statusCode == 404)
        throw new FileNotFoundException(getErrorMessage(method));

      if (statusCode >= 300)
        throw new IOException(getErrorMessage(method));

      InputStream is = method.getResponseAsStream();
      NcStreamReader reader = new NcStreamReader();
      reader.readStream(is, this);
      this.location = SCHEME + remoteURI;
    }

    long took = System.currentTimeMillis() - start;
    if (showRequest)
      System.out.printf(" CdmRemote request %s took %d msecs %n", url, took);
  }

  // Closes the input stream.
  public CdmRemote(InputStream is, String location) throws IOException {
    remoteURI = location;

    try {
      NcStreamReader reader = new NcStreamReader();
      reader.readStream(is, this);
      this.location = SCHEME + remoteURI;

    } finally {
      is.close();
    }
  }

  @Override
  protected Array readData(ucar.nc2.Variable v, Section section) throws IOException {
    // if (unlocked)
    // throw new IllegalStateException("File is unlocked - cannot use");

    if (v.getDataType() == DataType.SEQUENCE) {
      Structure s = (Structure) v;
      StructureDataIterator siter = getStructureIterator(s, -1);
      return new ArraySequence(s.makeStructureMembers(), siter, -1);
    }

    Formatter f = new Formatter();
    f.format("%s?req=data", remoteURI);
    if (compress)
      f.format("&deflate=5");
    // f.format("&var=%s", v.getShortName());
    f.format("&var=%s", v.getFullNameEscaped());
    if ((section != null) && (section.computeSize() != v.getSize()) && (v.getDataType() != DataType.SEQUENCE)) {
      f.format("(%s)", section.toString());
    }
    String url = f.toString();
    // String escapedURI = f.toString();
    URI escapedURI;
    try {
      escapedURI = HTTPUtil.parseToURI(url);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    if (showRequest)
      System.out.printf("CdmRemote data request for variable: '%s' section=(%s)%n url='%s'%n esc='%s'%n",
          v.getFullName(), section, url, escapedURI);

    try (HTTPMethod method = HTTPFactory.Get(httpClient, escapedURI.toString())) {
      int statusCode = method.execute();
      if (statusCode == 404)
        throw new FileNotFoundException(getErrorMessage(method));

      if (statusCode >= 300)
        throw new IOException(getErrorMessage(method));

      Optional<String> contentLength = method.getResponseHeaderValue("Content-Length");
      if (contentLength.isPresent()) {
        int readLen = Integer.parseInt(contentLength.get());
        if (showRequest)
          System.out.printf(" content-length = %d%n", readLen);
        if (v.getDataType() != DataType.SEQUENCE) {
          int wantSize = (int) (v.getElementSize() * (section == null ? v.getSize() : section.computeSize()));
          if (readLen != wantSize)
            throw new IOException("content-length= " + readLen + " not equal expected Size= " + wantSize); // LOOK
        }
      }

      InputStream is = method.getResponseAsStream(); // Closed by HTTPMethod.close().
      NcStreamReader reader = new NcStreamReader();
      NcStreamReader.DataResult result = reader.readData(is, this, remoteURI);

      assert v.getFullNameEscaped().equals(result.varNameFullEsc);
      return result.data;
    }
  }

  private static String getErrorMessage(HTTPMethod method) {
    String path = method.getURI().toString();
    String status = method.getStatusLine();
    String content = method.getResponseAsString();
    return (content == null) ? status + " " + path : status + " " + path + "\n " + content;
  }

  protected StructureDataIterator getStructureIterator(Structure s, int bufferSize) {
    try {
      InputStream is = sendQuery(httpClient, remoteURI, s.getFullNameEscaped());
      NcStreamReader reader = new NcStreamReader();
      return reader.getStructureIterator(is, this);

    } catch (Throwable e) {
      e.printStackTrace();
      throw new IllegalStateException(e);
    }
  }

  // session may be null, if so, will be closed on method.close()
  public static InputStream sendQuery(HTTPSession session, String remoteURI, String query) throws IOException {
    long start = System.currentTimeMillis();

    StringBuilder sbuff = new StringBuilder(remoteURI);
    sbuff.append("?");
    sbuff.append(query);
    if (showRequest)
      System.out.printf(" CdmRemote sendQuery= %s", sbuff);

    HTTPMethod method = HTTPFactory.Get(session, sbuff.toString());
    try {
      int statusCode = method.execute();
      if (statusCode == 404) {
        throw new FileNotFoundException(method.getPath() + " " + method.getStatusLine());
      } else if (statusCode >= 400) {
        throw new IOException(method.getPath() + " " + method.getStatusLine());
      }

      InputStream stream = method.getResponseBodyAsStream();
      if (showRequest)
        System.out.printf(" took %d msecs %n", System.currentTimeMillis() - start);

      // Leave the stream open. We must also leave the HTTPMethod open because the two are linked:
      // calling close() on one object causes the other object to be closed as well.
      return stream;
    } catch (IOException e) {
      // Close the HTTPMethod if there was an exception; otherwise leave it open.
      method.close();
      throw e;
    }
  }

  @Override
  public String getFileTypeId() {
    return "ncstreamRemote";
  }

  @Override
  public String getFileTypeDescription() {
    return "ncstreamRemote";
  }

  public long writeToFile(String filename) throws IOException {
    File file = new File(filename);
    String url = remoteURI + "?req=header";
    Escaper urlParamEscaper = UrlEscapers.urlFormParameterEscaper();

    long size = 0;
    try (FileOutputStream fos = new FileOutputStream(file)) {

      fos.write(NcStream.MAGIC_START);
      size += 4;

      // header
      try (HTTPMethod method = HTTPFactory.Get(httpClient, url)) {
        if (showRequest)
          System.out.printf("CdmRemote request %s %n", url);
        int statusCode = method.execute();

        if (statusCode == 404)
          throw new FileNotFoundException(getErrorMessage(method));

        if (statusCode >= 300)
          throw new IOException(getErrorMessage(method));

        InputStream is = method.getResponseBodyAsStream();
        size += IO.copyB(is, fos, IO.default_socket_buffersize);
      }

      for (Variable v : getVariables()) {
        StringBuilder sbuff = new StringBuilder(remoteURI);
        sbuff.append("?var=");
        sbuff.append(urlParamEscaper.escape(v.getShortName()));

        if (showRequest)
          System.out.println(" CdmRemote data request for variable: " + v.getFullName() + " url=" + sbuff);

        try (HTTPMethod method = HTTPFactory.Get(httpClient, sbuff.toString())) {
          int statusCode = method.execute();

          if (statusCode == 404)
            throw new FileNotFoundException(getErrorMessage(method));

          if (statusCode >= 300)
            throw new IOException(getErrorMessage(method));

          int wantSize = (int) (v.getSize());

          Optional<String> contentLength = method.getResponseHeaderValue("Content-Length");
          if (contentLength != null) {
            int readLen = Integer.parseInt(contentLength.get());
            if (readLen != wantSize)
              throw new IOException("content-length= " + readLen + " not equal expected Size= " + wantSize);
          }

          InputStream is = method.getResponseBodyAsStream();
          size += IO.copyB(is, fos, IO.default_socket_buffersize);
        }
      }

      fos.flush();
    }
    return size;
  }

  @Override
  public synchronized void close() {
    if (httpClient != null)
      httpClient.close();
  }

}
