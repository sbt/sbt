package sbt

final case class ConfigKey(name: String)
object ConfigKey {
  implicit def configurationToKey(c: sbt.librarymanagement.Configuration): ConfigKey = ConfigKey(c.name)
}
