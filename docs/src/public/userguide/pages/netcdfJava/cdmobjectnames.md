---
title: CDM Object Names
last_updated: 2019-07-22
sidebar: netcdfJavaTutorial_sidebar 
permalink: cdm_objectnames.html
toc: false
---

A CDM Object name refers to the name of a `Group`, `Dimension`, `Variable`, `Attribute`, or `EnumTypedef`.
A CDM object name abstractly is a variable length sequence of [Unicode](https://en.wikipedia.org/wiki/Unicode){:target="_blank"} characters.
Unicode has various encodings which are used in various contexts, for example:

* netCDF files store them as a variable length sequence of UTF-8 characters.
* netCDF-Java library internal format is modified UTF-16 in the java.lang.String object type.
* netCDF C library internal format is a variable length sequence of UTF-8 characters.

This document summarizes the various encodings. To transform between them, translate from form1 to Unicode with form1 unescaping as needed, then from Unicode to form2 with form2 escaping as needed.

* [netCDF file format](#netcdf-3-and-netcdf-4-file-format-identifiers)
* [CDL](#cdl)
* [NcML](#ncml)
* [CDM](#cdm)
* OPeNDAP

## netCDF-3 and netCDF-4 file format Identifiers

*NetCDF C library Object names* refer to the name of a `Group`, `Dimension`, `Variable`, `Attribute`, `user-defined Type`, `compound type Member`, or `enumeration type` Symbol.

A netCDF identifier is stored in a netCDF file as [UTF-8](https://en.wikipedia.org/wiki/UTF-8){:target="_blank"} [Unicode](https://en.wikipedia.org/wiki/Unicode){:target="_blank"} characters, [NFC normalized](https://unicode.org/reports/tr15/){:target="_blank"}.
There are some restrictions on the valid characters used in a netCDF identifier:

~~~
ID = ([a-zA-Z0-9_]|{MUTF8})([^\x00-\x1F\x2F/\x7F]|{MUTF8})* 

where:
  MUTF8 = multibyte UTF8 encoded char
~~~

which says:

* The first character of a name must be alphanumeric, a multi-byte UTF-8 character, or '_' (reserved for special names with meaning to implementations, such as the “_FillValue” attribute).
* Subsequent characters may be anything except control characters, forward slash '/', and del. A UTF-8 multibyte encoding always has the high order bit set. So the excluded byte values are 0-31, 47, and 127.
* Names that have trailing space characters are also not permitted

Also See:

<https://www.unidata.ucar.edu/software/netcdf/docs/file_format_specifications.html>

## CDL

A [CDL](https://www.unidata.ucar.edu/software/netcdf/docs/netcdf_utilities_guide.html#cdl_syntax){:target="_blank"} (netCDF Definition Language) document is encoded in UTF-8.
Certain characters must be escaped. The escape mechanism is to prepend a backslash "\" before the character.

Which characters in an identifier must be escaped in CDL?

~~~
[^\x00-\x1F\x7F/_.@+-a-zA-Z0-9] 
~~~

Alternatively, we can enumerate the escaped characters (using the regular expression syntax accepted by lex or flex):

~~~
idescaped =[ !"#$%&'()*,:;<=>?\[\\\]^`{|}~]
~~~

## NcML

An [NcML (netCDF Markup Language)](ncml_overview.html) document uses [standard XML encoding and escaping](https://www.w3.org/TR/xml/#charsets){:target="_blank"}.

The chars `&`, `<`, `>` must be replaced by these [entity references](https://en.wikipedia.org/wiki/XML#Entity_references){:target="_blank"}: `&amp;`, `&lt;`, `&gt;`.
In some places the single and double quote must be replaced by `&apos;` and `&quot;`, respectively.

Typically an XML parser/library will handle this transparently.

## CDM

A CDM object name abstractly is a variable length sequence of Unicode characters.
It can be anything except:

* Control chars (< 0x20) are not allowed, these are removed.
* Trailing and leading blanks are not allowed and are removed.
* a forward slash "/" and embedded space are converted into an underscore "_".

### Object Full Name as a String

A CDM object has a *short name* (a String) and a *full name*, consisting of the parent groups and structures that it belongs to.
Internally, only short names are used, along with the enclosing `Group` or `Structure` objects.
So there is generally no problem in comparing or searching for short names.
In certain places in the CDM / netCDF-Java library API (eg `NetcdfFile.findVariable()`), a full name can be passed in as a single String, of the form

~~~
groupName/groupName/varName.memberName.memberName
~~~

which uses the "/" and "." as group and structure delimiters, respectively.
In this case, those characters must be escaped in the object names.
Since "/" is not a legal character in an identifier, that leaves just the "." to be escaped.

### cdmremote

The client forms requests of the form endpoint?query. The possible query parameters are:

~~~
  req=( CDL | NcML | capabilities | header | data)
  var=vars

where:
  vars := varspec | varspec[',' varspec]
  varspec := varname[subsetSpec]
  varname := valid variable name
  subsetSpec := '(' fortran-90 arraySpec ')'

  fortran-90 arraySpec := dim | dim ',' dims
  dim := ':' | slice | start ':' end | start ':' end ':' stride
  slice := INTEGER
  start := INTEGER
  stride := INTEGER
  end := INTEGER
~~~

So the characters in variable names that need to be escaped are `,` `:` `(` `)` in order to not interfere with this grammar.
Actually you could get away with just escaping the `(`, since you can use it as a delimiter.

### cdmrFeature

The client forms requests of the form `endpoint?query`.
The possible query parameters are:

~~~
  req=( capabilities | data | form | stations)
  accept= (csv | xml | ncstream | netcdf )
  time_start,time_end=time range
  north,south,east,west=bounding box
  var=vars
  stn=stns

where:
  vars := varName | varName[,varName]
  stns := stnName | stnName[,stnName]
  varName := valid variable name
  stnName := valid station name
~~~

Here we just need the comma `,` in the variable name and in the station names.

### Netcdf Subset Service

It should suffice to URLencode the variable names and station names , and to URL decode all the query parameters.

### Object Escaped Name

Standard practice for escaping names is to use `NetcdfFile.escapeName()`, `unescapeName()`.
This uses [backslash escaping](http://en.wikipedia.org/wiki/Backslash){:target="_blank"}.
The backslash becomes a special char, so it needs to be in the escape set:

~~~
 [\(\),:\.\\]
~~~

Utility routines using this include `Variable.getNameEscaped()`, and `GridDatatype.getNameEscaped()`.

## OPeNDAP

OPeNDAP has an [on-the-wire specification](https://www.opendap.org/pdf/ESE-RFC-004v1.1.pdf){:target="_blank"} that must be followed in order to ensure interoperability.
There are two parts to this:

* URL encoding
* Restriction of identifier names.
  Since these are different from CDM object names, there must be a translation between the two.
  This applies to identifiers in the URL constraint expression, in the DDS or in the DAS.

### URL Encoding

OPeNDAP (we think) uses standard URL encoding, aka [percent encoding](https://en.wikipedia.org/wiki/Percent-encoding){:target="_blank"}.

### OPeNDAP identifiers

An OPeNDAP dataset as represented in the CDM library looks like any other CDM dataset, ie it is not restricted to OPeNDAP encoding.
When making a request over the OPeNDAP protocol, a translation between CDM and OPeNDAP identifiers must be made.

From the spec:

> A DAP variable’s name MUST contain ONLY US-ASCII characters with the following additional limitation:
> The characters MUST be either upper or lower case letters, numbers or from the set _ ! ~ * ’ - " .
> Any other characters MUST be escaped.


To escape a character in a name, the character is replaced by the sequence %<Character Code> where 
Character Code is the two hex digit code corresponding to the US-ASCII character.
From the OPeNDAP lexers:

1. from dds.lex and ce_expr.lex
   ~~~
   [-+a-zA-Z0-9_/%.\\*][-+a-zA-Z0-9_/%.\\#*]*
   ~~~
2. from das.lex
   ~~~
   [-+a-zA-Z0-9_/%.\\*:()][-+a-zA-Z0-9_/%.\\#*:()]*
   ~~~
   (same as dds plus ':','(', and ')' are added)
3. from gse.lex
   ~~~
   [-+a-zA-Z0-9_/%.\\][-+a-zA-Z0-9_/%.\\#]*
   ~~~
   (same as dds except that '*' is removed)

Their note:

> "...Note that the DAS allows Identifiers to have parens and colons while the DDS and expr scanners don't. It's too hard to disambiguate functions when IDs have parens in them and adding colons makes parsing the array projections hard..."

### Making/receiving OPeNDAP requests

Standard practice, then is to translate from CDM identifiers to OPeNDAP identifiers using

~~~
ucar.nc.util.net.EscapeStrings.escapeDAPIdentifier()
~~~

and to translate from OPeNDAP identifiers to CDM identifiers using

~~~
ucar.nc.util.net.EscapeStrings.unescapeDAPIdentifier()
~~~

In addition, `HTTPMethod(String URI)` automatically adds URL encoding.
These may create a double escaped URL.
On the server, one first unescapes the request, and then parses it.
Any identifiers in the request then are unescaped again before comparing with the corresponding CDM object.

### HDF5

A direct translation of their grammar would appear to be this:

~~~
PathName={AbsolutePathName}|{RelativePathName}

Separator=[/]+

AbsolutePathName={Separator}{RelativePathName}?

RelativePathName={Component}({Separator}|{RelativePathName})*

Component=[.]|{Name}

Name=[.]|({Charx}{Character}*)|{Character}+

/* Ascii set - '/'
Character={Charx}|[.]

/* Ascii set - '.' and '/' */
Charx=[ !"#$%&'()*+,-0123456789:;<=>?@\[\\\]^`{|}~\x00-\x1e,\x7f]
~~~

## OGC
 

The Web Map Service Implementation Specification version 1.3.0 states:

> 6.3.2 Reserved characters in HTTP GET URLs
> 
> The URL specification (IETF RFC 2396) reserves particular characters as significant and requires that these be escaped when they might conflict with their defined usage. This International Standard explicitly reserves several of those characters for use in the query portion of WMS requests. When the characters '&', '=', ',' and '+' appear in one of the roles defined in Table 1, they shall appear literally in the URL. When those characters appear elsewhere (for example, in the value of a parameter), they shall be encoded as defined in IETF RFC 2396.
> 
> Table 1 -- Reserved Characters in WMS Query String
> 
> Character  Reserved Usage
> ?  Separator indicating start of query string.
> &  Separator between parameters in query string.
> =  Separator between name and value of parameter.
> ,  Separator between individual values in list-oriented parameters (such as BBOX, LAYERS and STYLES in the GetMap request).
> \+  Shorthand representation for a space character.
> 6.8.2 Parameter lists
> 
> Parameters consisting of lists (for example, BBOX, LAYERS and STYLES in WMS GetMap) shall use the comma (",") as the separator between items in the list. Additional white space shall not be used to delimit list items. If a list item value includes a space or comma, it shall be escaped using the URL encoding rules (6.3.2 and IETF RFC 2396).

## URL encoding

The URL specification [IETF RFC 2396](https://tools.ietf.org/html/rfc2396){:target="_blank"} states the following:
 
> Reserved characters being used for their defined purpose
> Alphanumeric characters
> The characters "-", "_", ".", "!", "~", "*", "'", "(", and ")"
> shall be encoded as "%xx", where xx is the two hexadecimal digits > representing the octet code of the character. Within the query string portion of a URL (i.e., everything after the "?"), the space character (" ") is an exception, and shall be encoded as a plus sign ("+"). A server shall be prepared to decode any character encoded in > this manner.
 
### Browsers

It appears that neither Firefox or Chrome does standard URL encoding.
 
### Clients

`HTTPClient` 3 will not send out a URL with certain chars in it like `[` (possibly the full 2396 set)
 
### Servlets

* `request.getQueryString()` returns raw (undecoded).
* `request.getParameter()` returns decoded

### Best Practice:

The query string is always run through URLDecoder.decode() before further processing:

~~~ 
queryString = URLDecoder.decode(req.getQueryString(), "UTF-8");
~~~

References

<http://www.blooberry.com/indexdot/html/topics/urlencoding.htm>

<https://www.w3schools.com/TAGS/ref_urlencode.asp>