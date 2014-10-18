package sbt

import java.io.File
import java.net.URL
import org.json4s._
import org.apache.ivy.core
import core.module.descriptor.ModuleDescriptor

private[sbt] object JsonUtil {
  def parseUpdateReport(md: ModuleDescriptor, path: File, cachedDescriptor: File, log: Logger): UpdateReport =
    {
      import org.json4s._
      implicit val formats = native.Serialization.formats(NoTypeHints) +
        new ConfigurationSerializer +
        new ArtifactSerializer +
        new FileSerializer +
        new URLSerializer
      try {
        val json = jawn.support.json4s.Parser.parseFromFile(path)
        fromLite(json.get.extract[UpdateReportLite], cachedDescriptor)
      } catch {
        case e: Throwable =>
          log.error("Unable to parse mini graph: " + path.toString)
          throw e
      }
    }
  def writeUpdateReport(ur: UpdateReport, graphPath: File): Unit =
    {
      implicit val formats = native.Serialization.formats(NoTypeHints) +
        new ConfigurationSerializer +
        new ArtifactSerializer +
        new FileSerializer
      import native.Serialization.write
      val str = write(toLite(ur))
      IO.write(graphPath, str, IO.utf8)
    }
  def toLite(ur: UpdateReport): UpdateReportLite =
    UpdateReportLite(ur.configurations map { cr =>
      ConfigurationReportLite(cr.configuration, cr.details)
    })
  def fromLite(lite: UpdateReportLite, cachedDescriptor: File): UpdateReport =
    {
      val stats = new UpdateStats(0L, 0L, 0L, false)
      val configReports = lite.configurations map { cr =>
        val details = cr.details
        val modules = details flatMap {
          _.modules filter { mr =>
            !mr.evicted && mr.problem.isEmpty
          }
        }
        val evicted = details flatMap {
          _.modules filter { mr =>
            mr.evicted
          }
        } map { _.module }
        new ConfigurationReport(cr.configuration, modules, details, evicted)
      }
      new UpdateReport(cachedDescriptor, configReports, stats)
    }
}

private[sbt] case class UpdateReportLite(configurations: Seq[ConfigurationReportLite])
private[sbt] case class ConfigurationReportLite(configuration: String, details: Seq[OrganizationArtifactReport])

private[sbt] class URLSerializer extends CustomSerializer[URL](format => (
  {
    case JString(s) => new URL(s)
  },
  {
    case x: URL => JString(x.toString)
  }
))

private[sbt] class FileSerializer extends CustomSerializer[File](format => (
  {
    case JString(s) => new File(s)
  },
  {
    case x: File => JString(x.toString)
  }
))

private[sbt] class ConfigurationSerializer extends CustomSerializer[Configuration](format => (
  {
    case JString(s) => new Configuration(s)
  },
  {
    case x: Configuration => JString(x.name)
  }
))

private[sbt] class ArtifactSerializer extends CustomSerializer[Artifact](format => (
  {
    case json: JValue =>
      implicit val fmt = format
      Artifact(
        (json \ "name").extract[String],
        (json \ "type").extract[String],
        (json \ "extension").extract[String],
        (json \ "classifier").extract[Option[String]],
        (json \ "configurations").extract[List[Configuration]],
        (json \ "url").extract[Option[URL]],
        (json \ "extraAttributes").extract[Map[String, String]]
      )
  },
  {
    case x: Artifact =>
      import DefaultJsonFormats.{ OptionWriter, StringWriter, mapWriter }
      val optStr = implicitly[Writer[Option[String]]]
      val mw = implicitly[Writer[Map[String, String]]]
      JObject(JField("name", JString(x.name)) ::
        JField("type", JString(x.`type`)) ::
        JField("extension", JString(x.extension)) ::
        JField("classifier", optStr.write(x.classifier)) ::
        JField("configurations", JArray(x.configurations.toList map { x => JString(x.name) })) ::
        JField("url", optStr.write(x.url map { _.toString })) ::
        JField("extraAttributes", mw.write(x.extraAttributes)) ::
        Nil)
  }
))
