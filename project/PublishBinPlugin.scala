package sbt

import java.nio.file.{ FileAlreadyExistsException, Files }

import sbt.Keys.*
import sbt.util.CacheImplicits.given
import sbt.librarymanagement.LibraryManagementCodec.given

/** This local plugin provides ways of publishing just the binary jar. */
object PublishBinPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val publishLocalBin = taskKey[Unit]("")
    val publishLocalBinConfig = taskKey[PublishConfiguration]("")
  }
  import autoImport.*

  private val dummyDoc = taskKey[HashedVirtualFileRef]("").withRank(Int.MaxValue)
  override val globalSettings = Seq(publishLocalBin := (()))

  override val projectSettings: Seq[Def.Setting[?]] = Def.settings(
    publishLocalBin := Classpaths
      .publishOrSkip(publishLocalBinConfig, publishLocalBin / skip)
      .value,
    publishLocalBinConfig := Def.uncached {
      val _ = makeIvyXmlLocalConfiguration.value
      Classpaths.publishConfig(
        false, // publishMavenStyle.value,
        Classpaths.deliverPattern(crossTarget.value),
        if (isSnapshot.value) "integration" else "release",
        ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
        (publishLocalBin / packagedArtifacts).value.map { (k, v) =>
          k -> fileConverter.value.toPath(v).toFile
        }.toVector,
        (publishLocalBin / checksums).value.toVector,
        logging = ivyLoggingLevel.value,
        overwrite = isSnapshot.value
      )
    },
    dummyDoc := {
      val _ = projectID.value
      val dummyFile = target.value / "dummy-doc" / "doc.jar"
      try {
        Files.createDirectories(dummyFile.toPath.getParent)
        Files.createFile(dummyFile.toPath)
      } catch { case _: FileAlreadyExistsException => }
      val out = fileConverter.value.toVirtualFile(dummyFile.toPath)
      Def.declareOutput(out)
      (out: HashedVirtualFileRef)
    },
    dummyDoc / packagedArtifact := Def.uncached(
      (Compile / packageDoc / artifact).value -> dummyDoc.value
    ),
    publishLocalBin / packagedArtifacts := Def.uncached(
      Classpaths
        .packaged(Seq(Compile / packageBin, Compile / packageSrc, makePom, dummyDoc))
        .value
    )
  )
}
