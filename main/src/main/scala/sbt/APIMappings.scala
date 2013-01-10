package sbt

	import java.io.File
	import java.net.{MalformedURLException,URL}

private[sbt] object APIMappings
{
	def extract(cp: Seq[Attributed[File]], log: Logger): Seq[(File,URL)] =
		cp.flatMap(entry => extractFromEntry(entry, log))

	def extractFromEntry(entry: Attributed[File], log: Logger): Option[(File,URL)] =
		entry.get(Keys.entryApiURL) match {
			case Some(u) => Some( (entry.data, u) )
			case None => entry.get(Keys.moduleID.key).flatMap { mid => extractFromID(entry.data, mid, log) }
		}

	private[this] def extractFromID(entry: File, mid: ModuleID, log: Logger): Option[(File,URL)] =
		for {
			urlString <- mid.extraAttributes.get(CustomPomParser.ApiURLKey)
			u <- parseURL(urlString, entry, log)
		} yield (entry, u)

	private[this] def parseURL(s: String, forEntry: File, log: Logger): Option[URL] =
		try Some(new URL(s)) catch { case e: MalformedURLException =>
			log.warn("Invalid API base URL '$s' for classpath entry '$forEntry': ${e.toString}")
			None
		}

	def store[T](attr: Attributed[T], entryAPI: Option[URL]): Attributed[T] = entryAPI match {
		case None => attr
		case Some(u) => attr.put(Keys.entryApiURL, u)
	}
}