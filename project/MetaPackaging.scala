import sbt._
import com.typesafe.packager.Keys._
import sbt.Keys._
import com.typesafe.packager.PackagerPlugin._


object MetaPackaging {

  def settings(pkg: Project): Seq[Setting[_]] = packagerSettings ++ Seq(
    name := "sbt",
    version <<= sbtVersion apply {
      case "0.11.3" => "0.11.3-build0300"
      case v => v
    },
    // GENERAL LINUX PACKAGING STUFFS
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Simple Build Tool for Scala-driven builds",
    packageDescription := """This meta-package provides the most up-to-date version of the SBT package.""",
    debianPackageDependencies in Debian <+= (name in Debian in pkg),
    // STUBBED values
    wixConfig := <dummy/>,
    rpmRelease := "1",
    rpmVendor := "typesafe"
  )
}
