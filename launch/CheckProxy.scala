/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package xsbt.boot

import Pre._
import java.net.{MalformedURLException, URL}

object CheckProxy
{
	def apply()
	{
		import ProxyProperties._
		val httpProxy = System.getenv(HttpProxyEnv)
		if(isDefined(httpProxy) && !isPropertyDefined(ProxyHost) && !isPropertyDefined(ProxyPort))
		{
			try
			{
				val proxy = new URL(httpProxy)
				setProperty(ProxyHost, proxy.getHost)
				val port = proxy.getPort
				if(port >= 0)
					System.setProperty(ProxyPort, port.toString)
				copyEnv(HttpProxyUser, ProxyUser)
				copyEnv(HttpProxyPassword, ProxyPassword)
			}
			catch
			{
				case e: MalformedURLException =>
					System.out.println("Warning: could not parse http_proxy setting: " + e.toString)
			}
		}
	}
	private def copyEnv(envKey: String, sysKey: String) { setProperty(sysKey, System.getenv(envKey)) }
	private def setProperty(key: String, value: String) { if(value != null) System.setProperty(key, value) }
	private def isPropertyDefined(k: String) = isDefined(System.getProperty(k))
	private def isDefined(s: String) = s != null && isNonEmpty(s)
}