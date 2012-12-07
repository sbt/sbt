package sbt

final case class ConfigKey(name: String)
object ConfigKey
{
	implicit def configurationToKey(c: Configuration): ConfigKey = ConfigKey(c.name)
}
