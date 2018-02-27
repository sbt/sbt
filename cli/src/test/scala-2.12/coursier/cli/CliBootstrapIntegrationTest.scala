package coursier.cli

import java.io._
import java.util.zip.ZipInputStream

import caseapp.core.RemainingArgs
import coursier.cli.options._
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

/**
  * Bootstrap test is not covered by Pants because it does not prebuild a bootstrap.jar
  */
@RunWith(classOf[JUnitRunner])
class CliBootstrapIntegrationTest extends FlatSpec with CliTestLib {

  "bootstrap" should "not add POMs to the classpath" in withFile() {

    def zipEntryContent(zis: ZipInputStream, path: String): Array[Byte] = {
      val e = zis.getNextEntry
      if (e == null)
        throw new NoSuchElementException(s"Entry $path in zip file")
      else if (e.getName == path)
        coursier.internal.FileUtil.readFully(zis)
      else
        zipEntryContent(zis, path)
    }

    (bootstrapFile, _) =>
      val artifactOptions = ArtifactOptions()
      val common = CommonOptions(
        repository = List("bintray:scalameta/maven")
      )
      val isolatedLoaderOptions = IsolatedLoaderOptions(
        isolateTarget = List("foo"),
        isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = bootstrapFile.getPath,
        isolated = isolatedLoaderOptions,
        force = true,
        common = common
      )
      val bootstrapOptions = BootstrapOptions(artifactOptions, bootstrapSpecificOptions)

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      var fis: InputStream = null

      val content = try {
        fis = new FileInputStream(bootstrapFile)
        coursier.internal.FileUtil.readFully(fis)
      } finally {
        if (fis != null) fis.close()
      }

      val actualContent = {
        val header = Seq[Byte](0x50, 0x4b, 0x03, 0x04)
        val idx = content.indexOfSlice(header)
        if (idx < 0)
          throw new Exception(s"ZIP header not found in ${bootstrapFile.getPath}")
        else
          content.drop(idx)
      }

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent))

      val lines = new String(zipEntryContent(zis, "bootstrap-isolation-foo-jar-urls"), "UTF-8").lines.toVector

      val extensions = lines
        .map { l =>
          val idx = l.lastIndexOf('.')
          if (idx < 0)
            l
          else
            l.drop(idx + 1)
        }
        .toSet

      assert(extensions == Set("jar"))
  }
}
