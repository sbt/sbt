package sbt

import java.io.File


/** Represents that one module ousted others. */
final case class DependencyEviction(val chosen: ModuleID, val evicted: Seq[ModuleID]) {
	override def toString = s"${chosen} -> ${evicted.map(_.revision).mkString("(", ", ", ")")}"
}
final case class CrossVersionConflict(organization: String, rawName: String, crossVersions: Set[String]) {
	override def toString = s"${organization}:${rawName} -> ${crossVersions.mkString("(", ", ", ")")}"
}
/** represents the evictions for a given configuration (duplicated data from UpdateReport). */
final case class ConfigurationEvictions(config: String, evictions: Seq[DependencyEviction]) {
	override def toString = 
       if(evictions.isEmpty) s"[evictions - $config]"
	   else s"[evictions - $config]\n${evictions.mkString("* ", "\n* ", "")}"
}
/** A report of all eviction/conflicts within the previous update. */
final case class ConflictReport private (configurations: Seq[ConfigurationEvictions], crossVersions: Seq[CrossVersionConflict]) {
	private[this] def crossVersionToString: String = 
	  s"[scala binary conflicts]${if(crossVersions.isEmpty) "" else crossVersions.mkString("\n* ", "\n* ", "")}"
	override def toString = s"-- ConflictReport --\n${crossVersionToString}\n${configurations mkString "\n"}"
}

object ConflictReport {
	def makeConflictReport(report: UpdateReport): ConflictReport = {
		def key(m: ModuleID): String = s"${m.organization}:${m.name}"
		def evictionsForConfig(c: ConfigurationReport): Iterable[DependencyEviction] = {
			def chosen(mkey: String): Option[ModuleID] =
			  c.modules map (_.module) find { m => key(m) == mkey }
			c.evicted groupBy key flatMap {
				case (mkey, evicted) =>
				  chosen(mkey) match {
				  	case Some(m) => DependencyEviction(m, evicted) :: Nil
				  	case None => Nil
				  }
			}
		}
		val crossConflicts = 
			for( ((org,rawName), fullNames) <- ConflictWarning.crossVersionMismatches(report) ) 
			yield {
				val suffixes = fullNames map ConflictWarning.getCrossSuffix
				CrossVersionConflict(org, rawName, suffixes)
			}

		ConflictReport(report.configurations map { configReport =>
			ConfigurationEvictions(
				configReport.configuration,
				evictionsForConfig(configReport).toSeq
			)
		}, crossConflicts.toSeq)
	}
}