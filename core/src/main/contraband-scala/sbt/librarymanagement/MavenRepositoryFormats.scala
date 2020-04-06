/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement

import _root_.sjsonnew.JsonFormat
trait MavenRepositoryFormats { self: sjsonnew.BasicJsonProtocol with sbt.librarymanagement.MavenRepoFormats with sbt.librarymanagement.MavenCacheFormats =>
implicit lazy val MavenRepositoryFormat: JsonFormat[sbt.librarymanagement.MavenRepository] = flatUnionFormat2[sbt.librarymanagement.MavenRepository, sbt.librarymanagement.MavenRepo, sbt.librarymanagement.MavenCache]("type")
}
