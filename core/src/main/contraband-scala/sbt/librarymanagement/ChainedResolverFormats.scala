/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ChainedResolverFormats { self: sbt.librarymanagement.ResolverFormats with
  sjsonnew.BasicJsonProtocol with
  sbt.librarymanagement.MavenRepoFormats with
  sbt.librarymanagement.MavenCacheFormats with
  sbt.librarymanagement.PatternsFormats with
  sbt.librarymanagement.FileConfigurationFormats with
  sbt.librarymanagement.FileRepositoryFormats with
  sbt.librarymanagement.URLRepositoryFormats with
  sbt.librarymanagement.SshConnectionFormats with
  sbt.librarymanagement.SshAuthenticationFormats with
  sbt.librarymanagement.SshRepositoryFormats with
  sbt.librarymanagement.SftpRepositoryFormats with
  sbt.librarymanagement.PasswordAuthenticationFormats with
  sbt.librarymanagement.KeyFileAuthenticationFormats =>
implicit lazy val ChainedResolverFormat: JsonFormat[sbt.librarymanagement.ChainedResolver] = new JsonFormat[sbt.librarymanagement.ChainedResolver] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ChainedResolver = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val resolvers = unbuilder.readField[Vector[sbt.librarymanagement.Resolver]]("resolvers")
      unbuilder.endObject()
      sbt.librarymanagement.ChainedResolver(name, resolvers)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ChainedResolver, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("resolvers", obj.resolvers)
    builder.endObject()
  }
}
}
