package coursier.cli.util

import java.io.File
import java.util.Objects

import coursier.Artifact
import coursier.core.{Attributes, Dependency, Resolution}
import coursier.util.Print

import scala.collection.mutable
import scala.collection.parallel.ParSeq
import argonaut._
import Argonaut._

/**
 * Lookup table for files and artifacts to print in the JsonReport.
 */
final case class JsonPrintRequirement(fileByArtifact: Map[String, File], depToArtifacts: Map[Dependency, Vector[Artifact]])

/**
 * Represents a resolved dependency's artifact in the JsonReport.
 * @param coord String representation of the artifact's maven coordinate.
 * @param file The path to the file for the artifact.
 * @param dependencies The dependencies of the artifact.
 */
final case class DepNode(coord: String, file: Option[String], dependencies: Set[String])

final case class ReportNode(conflict_resolution: Map[String, String], dependencies: Vector[DepNode], version: String)

/**
  * FORMAT_VERSION_NUMBER: Version number for identifying the export file format output. This
  * version number should change when there is a change to the output format.
  *
  * Major Version 1.x.x : Increment this field when there is a major format change
  * Minor Version x.1.x : Increment this field when there is a minor change that breaks backward
  *   compatibility for an existing field or a field is removed.
  * Patch version x.x.1 : Increment this field when a minor format change that just adds information
  *   that an application can safely ignore.
  *
  * Note format changes in cli/README.md and update the Changelog section.
  */
object ReportNode {
  import argonaut.ArgonautShapeless._
  implicit val encodeJson = EncodeJson.of[ReportNode]
  implicit val decodeJson = DecodeJson.of[ReportNode]
  val version = "0.1.0"
}


object JsonReport {

  private val printer = PrettyParams.nospace.copy(preserveOrder = true)

  def apply[T](roots: IndexedSeq[T], conflictResolutionForRoots: Map[String, String], overrideClassifiers: Set[String])
              (children: T => Seq[T], reconciledVersionStr: T => String, requestedVersionStr: T => String, getFile: T => Option[String]): String = {

    val rootDeps: ParSeq[DepNode] = roots.par.map(r => {

      /**
        * Same printing mechanism as [[coursier.util.Tree#recursivePrint]]
        */
      def flattenDeps(elems: Seq[T], ancestors: Set[T], acc: mutable.Set[String]): Unit = {
        val unseenElems: Seq[T] = elems.filterNot(ancestors.contains)
        for (elem <- unseenElems) {
          val depElems = children(elem)
          acc ++= depElems.map(reconciledVersionStr(_))

          if (depElems.nonEmpty) {
            flattenDeps(children(elem), ancestors + elem, acc)
          }
        }
      }

      val acc = scala.collection.mutable.Set[String]()
      flattenDeps(Seq(r), Set(), acc)
      DepNode(reconciledVersionStr(r), getFile(r), acc.toSet)

    })
    val report = ReportNode(conflictResolutionForRoots, rootDeps.toVector.sortBy(_.coord), ReportNode.version)
    printer.pretty(report.asJson)
  }

}


final case class JsonElem(dep: Dependency,
                          artifacts: Seq[(Dependency, Artifact)] = Seq(),
                          jsonPrintRequirement: Option[JsonPrintRequirement],
                          resolution: Resolution,
                          colors: Boolean,
                          printExclusions: Boolean,
                          excluded: Boolean,
                          overrideClassifiers: Set[String]
  ) {

  val (red, yellow, reset) =
    if (colors)
      (Console.RED, Console.YELLOW, Console.RESET)
    else
      ("", "", "")

  // This is used to printing json output
  // Option of the file path
  lazy val downloadedFile: Option[String] = {
    jsonPrintRequirement.flatMap(req =>
        req.depToArtifacts.getOrElse(dep, Seq())
          .filter(_.classifier == dep.attributes.classifier)
          .map(x => req.fileByArtifact.get(x.url))
          .filter(_.isDefined)
          .filter(_.nonEmpty)
          .map(_.get.getPath)
          .headOption
    )
  }

  lazy val reconciledVersion: String = resolution.reconciledVersions
    .getOrElse(dep.module, dep.version)

  // These are used to printing json output
  val reconciledVersionStr = s"${dep.mavenPrefix}:$reconciledVersion"
  val requestedVersionStr = s"${dep.module}:${dep.version}"

  lazy val repr =
    if (excluded)
      resolution.reconciledVersions.get(dep.module) match {
        case None =>
          s"$yellow(excluded)$reset ${dep.module}:${dep.version}"
        case Some(version) =>
          val versionMsg =
            if (version == dep.version)
              "this version"
            else
              s"version $version"

          s"${dep.module}:${dep.version} " +
            s"$red(excluded, $versionMsg present anyway)$reset"
      }
    else {
      val versionStr =
        if (reconciledVersion == dep.version)
          dep.version
        else {
          val assumeCompatibleVersions = Print.compatibleVersions(dep.version, reconciledVersion)

          (if (assumeCompatibleVersions) yellow else red) +
            s"${dep.version} -> $reconciledVersion" +
            (if (assumeCompatibleVersions || colors) "" else " (possible incompatibility)") +
            reset
        }

      s"${dep.module}:$versionStr"
    }

  lazy val children: Seq[JsonElem] =
    if (excluded)
      Nil
    else {
      val dep0 = dep.copy(version = reconciledVersion)

      val dependencies = resolution.dependenciesOf(
        dep0,
        withReconciledVersions = false
      ).sortBy { trDep =>
        (trDep.module.organization, trDep.module.name, trDep.version)
      }.map { d =>
        if (overrideClassifiers.contains(dep0.attributes.classifier)) {
          d.copy(attributes = d.attributes.copy(classifier = dep0.attributes.classifier))
        } else {
          d
        }
      }

      def excluded = resolution
        .dependenciesOf(
          dep0.copy(exclusions = Set.empty),
          withReconciledVersions = false
        )
        .sortBy { trDep =>
          (trDep.module.organization, trDep.module.name, trDep.version)
        }
        .map(_.moduleVersion)
        .filterNot(dependencies.map(_.moduleVersion).toSet).map {
        case (mod, ver) =>
          JsonElem(
            Dependency(mod, ver, "", Set.empty, Attributes("", ""), optional = false, transitive = false),
            artifacts,
            jsonPrintRequirement,
            resolution,
            colors,
            printExclusions,
            excluded = true,
            overrideClassifiers = overrideClassifiers
          )
      }

      dependencies.map(JsonElem(_, artifacts, jsonPrintRequirement, resolution, colors, printExclusions, excluded = false, overrideClassifiers = overrideClassifiers)) ++
        (if (printExclusions) excluded else Nil)
    }

    /**
      * Override the hashcode to explicitly exclude `children`, because children will result in recursive hash on
      * children's children, causing performance issue. Hash collision should be rare, but when that happens, the
      * default equality check should take of the recursive aspect of `children`.
      */
    override def hashCode(): Int = Objects.hash(dep, requestedVersionStr, reconciledVersion, downloadedFile)
}
