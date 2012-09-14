	import sbt._
	import Keys._
	import Status.{isSnapshot, publishStatus}
	import com.jsuereth.sbtsite.{SitePlugin, SiteKeys}
	import SitePlugin.site
	import SiteKeys.{makeSite,siteMappings}
	import Sxr.sxr

object Docs
{
	def settings: Seq[Setting[_]] =
		site.settings ++
		site.sphinxSupport("manual") ++
		site.jekyllSupport() ++
		site.includeScaladoc("api") ++
		siteIncludeSxr ++
		sitePrefixVersion

	def siteIncludeSxr = Seq(
		mappings in sxr <<= sxr.map(dir => Path.allSubpaths(dir).toSeq),
		site.addMappingsToSiteDir(mappings in sxr, "sxr")
	)

	def sitePrefixVersion = 
		siteMappings <<= (siteMappings, version) map { (ms, v) =>
			ms.map { case (src, path) => (src, v + "/" + path) }
		}

	def siteLinkLatest =
		makeSite <<= (makeSite, version, streams, isSnapshot) map { (dir, v, s, snap) =>
			linkSite(dir, v, if(snap) "snapshot" else "stable", s.log)
			dir
		}

	def linkSite(base: File, to: String, from: String, log: Logger) {
		val current = base / to
		assert(current.isDirectory, "Versioned site not present at " + current.getAbsolutePath)
		val symlinkFile = base / from
		symlinkFile.delete()
		symlink(to = current, from = symlinkFile, log = log)
	}

	// TODO: platform independence/use symlink from Java 7
	def symlink(to: File, from: File, log: Logger): Unit =
		"ln" :: "-s" :: to.getAbsolutePath :: from.getAbsolutePath :: Nil ! log match {
			case 0 => ()
			case code => error("Could not create symbolic link from " + from + " to " + " to.")
		}
}