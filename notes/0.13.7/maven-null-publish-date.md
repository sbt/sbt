  [1618]: https://github.com/sbt/sbt/pull/1618
  [1611]: Https://github.com/sbt/sbt/issues/1611
  [@jsuereth]: https://github.com/jsuereth
  
  
### Improvements

* You can now publish to maven repositories that are `file` URLs.

### Fixes 

* When resolving from maven, and unable to read maven-metadata.xml file (common given the divergence in
  Maven 3 and Ivy 2), we attempt to use LastModified timestamp in lieu of "published" timestamp.  
  [#1618][1618] by [@jsuereth][@jsuereth]
* NPE exception when using ChainResolver and maven repositories [#1611]/[1611] by [@jsuereth][@jsuereth]
