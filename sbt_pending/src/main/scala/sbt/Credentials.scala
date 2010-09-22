/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.File
import org.apache.ivy.util.url.CredentialsStore

object Credentials
{
	/** Add the provided credentials to Ivy's credentials cache.*/
	def add(realm: String, host: String, userName: String, passwd: String): Unit =
		CredentialsStore.INSTANCE.addCredentials(realm, host, userName, passwd)
	/** Load credentials from the given file into Ivy's credentials cache.*/
	def apply(path: File, log: Logger)
	{
		val msg =
			if(path.exists)
			{
				val properties = new scala.collection.mutable.HashMap[String, String]
				def get(keys: List[String]) = keys.flatMap(properties.get).firstOption.toRight(keys.head + " not specified in credentials file: " + path)

					MapIO.read(properties, path, log) orElse
					{
						List.separate( List(RealmKeys, HostKeys, UserKeys, PasswordKeys).map(get) ) match
						{
							case (Nil, List(realm, host, user, pass)) => add(realm, host, user, pass); None
							case (errors, _) => Some(errors.mkString("\n"))
						}
					}
			}
			else
				Some("Credentials file " + path + " does not exist")
		msg.foreach(x => log.warn(x))
	}
	private[this] val RealmKeys = List("realm")
	private[this] val HostKeys = List("host", "hostname")
	private[this] val UserKeys = List("user", "user.name", "username")
	private[this] val PasswordKeys = List("password", "pwd", "pass", "passwd")
}