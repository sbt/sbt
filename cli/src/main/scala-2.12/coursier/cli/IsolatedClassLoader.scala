package coursier.cli

import java.net.{URL, URLClassLoader}


class IsolatedClassLoader(
  urls: Array[URL],
  parent: ClassLoader,
  isolationTargets: Array[String]
) extends URLClassLoader(urls, parent) {

  /**
    * Applications wanting to access an isolated `ClassLoader` should inspect the hierarchy of
    * loaders, and look into each of them for this method, by reflection. Then they should
    * call it (still by reflection), and look for an agreed in advance target in it. If it is found,
    * then the corresponding `ClassLoader` is the one with isolated dependencies.
    */
  def getIsolationTargets: Array[String] = isolationTargets

}