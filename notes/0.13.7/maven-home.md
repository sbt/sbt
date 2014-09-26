  [1600]: https://github.com/sbt/sbt/pull/1600
  [@topping]: https://github.com/topping
  
### Improvements

* Maven local repository is now resolved from the first of the <localRepository/> element in ~/.m2/settings.xml, $M2_HOME/conf/settings.xml or the default of 
  ~/.m2/repository if neither of those configuration elements exist. If more Maven settings are required to be recovered, the proper thing to do is merge 
  the two possible settings.xml files, then query against the element path of the merge.  This code avoids the merge by checking sequentially.