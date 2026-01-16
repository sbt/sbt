/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait MavenRepositoryFormats { self: sjsonnew.BasicJsonProtocol & sbt.librarymanagement.MavenRepoFormats & sbt.librarymanagement.MavenCacheFormats =>
given MavenRepositoryFormat: JsonFormat[sbt.librarymanagement.MavenRepository] = flatUnionFormat2[sbt.librarymanagement.MavenRepository, sbt.librarymanagement.MavenRepo, sbt.librarymanagement.MavenCache]("type")
}
