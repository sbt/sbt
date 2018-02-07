package coursier.cli.spark

import java.io.File

import coursier.cli.Helper
import coursier.cli.options.CommonOptions

object Submit {

  def cp(
    scalaVersion: String,
    sparkVersion: String,
    noDefault: Boolean,
    extraDependencies: Seq[String],
    artifactTypes: Set[String],
    common: CommonOptions
  ): Seq[File] = {

    var extraCp = Seq.empty[File]

    for (yarnConf <- sys.env.get("YARN_CONF_DIR") if yarnConf.nonEmpty) {
      val f = new File(yarnConf)

      if (!f.isDirectory) {
        Console.err.println(s"Error: YARN conf path ($yarnConf) is not a directory or doesn't exist.")
        sys.exit(1)
      }

      extraCp = extraCp :+ f
    }

    def defaultDependencies = Seq(
      // FIXME We whould be able to pass these as (parsed) Dependency instances to Helper
      s"org.apache.spark::spark-core:$sparkVersion",
      s"org.apache.spark::spark-yarn:$sparkVersion"
    )

    val helper = new Helper(
      common.copy(
        intransitive = Nil,
        classifier = Nil,
        scalaVersion = scalaVersion
      ),
      // FIXME We whould be able to pass these as (parsed) Dependency instances to Helper
      (if (noDefault) Nil else defaultDependencies) ++ extraDependencies
    )

    helper.fetch(
      sources = false,
      javadoc = false,
      artifactTypes = artifactTypes
    ) ++ extraCp
  }

  def mainClassName = "org.apache.spark.deploy.SparkSubmit"

}
