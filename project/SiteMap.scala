import sbt.io.Path._
import sbt._

object SiteMap {
  // represents the configurable aspects of a sitemap entry
  final case class Entry(changeFreq: String, priority: Double) {
    assert(priority >= 0.0 && priority <= 1.0,
           s"Priority must be between 0.0 and 1.0:, was $priority")
  }
  def generate(repoBase: File,
               remoteBase: URI,
               gzip: Boolean,
               entry: (File, String) => Option[Entry],
               log: Logger): (File, Seq[File]) = {
    def relativize(files: PathFinder): Seq[(File, String)] = files pair relativeTo(repoBase)
    def entries(files: PathFinder) =
      relativize(files) flatMap {
        case (f, path) =>
          entry(f, path).toList map { e =>
            entryXML(e, f, path)
          }
      }
    def entriesXML(entries: Seq[xml.Node]): xml.Elem = {
      assert(entries.size <= 50000, "A site map cannot contain more than 50,000 entries.")
      <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
            { entries }
          </urlset>
    }

    def entryXML(e: Entry, f: File, relPath: String) =
      <url>
          <loc>{ remoteBase.resolve(relPath).toString }</loc>
          <lastmod>{ lastModifiedString(f) }</lastmod>
          <changefreq>{ e.changeFreq }</changefreq>
          <priority>{ e.priority.toString }</priority>
        </url>

    def singleSiteMap(dir: File, files: PathFinder): Option[File] = {
      val es = entries(files)
      if (es.isEmpty) None
      else Some(writeXMLgz(dir / "sitemap.xml", dir / "sitemap.xml.gz", gzip, entriesXML(es)))
    }
    def indexEntryXML(sub: File, relPath: String): xml.Elem =
      <sitemap>
          <loc>{ remoteBase.resolve(relPath).toString }</loc>
          <lastmod>{ lastModifiedString(sub) }</lastmod>
        </sitemap>
    def indexEntriesXML(entries: Seq[xml.Node]): xml.Elem =
      <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          { entries }
        </sitemapindex>
    def indexEntries(subs: Seq[File]) =
      relativize(subs) map { case (f, path) => indexEntryXML(f, path) }
    def siteMapIndex(dir: File, subs: Seq[File]): File = {
      val xml = indexEntriesXML(indexEntries(subs))
      writeXMLgz(dir / "sitemap_index.xml", dir / "sitemap_index.xml.gz", gzip, xml)
    }
    def isSymlink(f: File) = f.getCanonicalFile != f.getAbsoluteFile

    val (symlinks, normal) = (repoBase * DirectoryFilter).get.partition(dir => isSymlink(dir))
    log.debug("Detected symlinks: " + symlinks.mkString("\n\t", "\n\t", ""))
    val subMaps =
      singleSiteMap(repoBase, (repoBase * "*.html") +++ (symlinks ** "*.html")).toList ++
        normal.flatMap(dir => singleSiteMap(dir, dir ** "*.html").toList)
    val index = siteMapIndex(repoBase, subMaps)
    (index, subMaps)
  }
  // generates a string suitable for a sitemap file representing the last modified time of the given File
  private[this] def lastModifiedString(f: File): String = {
    val formatter = new java.text.SimpleDateFormat("yyyy-MM-dd")
    formatter.format(new java.util.Date(f.lastModified))
  }
  // writes the provided XML node to `output` and then gzips it to `gzipped` if `gzip` is true
  private[this] def writeXMLgz(output: File, gzipped: File, gzip: Boolean, node: xml.Node): File = {
    writeXML(output, node)
    if (gzip) {
      IO.gzip(output, gzipped)
      gzipped
    } else
      output
  }
  private[this] def writeXML(output: File, node: xml.Node): Unit =
    write(output, new xml.PrettyPrinter(1000, 4).format(node))

  private[this] def write(output: File, xmlString: String): Unit = {
    // use \n as newline because toString uses PrettyPrinter, which hard codes line endings to be \n
    IO.write(output, s"<?xml version='1.0' encoding='${IO.utf8.name}'?>\n")
    IO.append(output, xmlString)
  }
}
