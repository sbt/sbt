package coursier.cli

import java.io.File
import java.net.URLClassLoader

import caseapp._
import coursier.Dependency
import coursier.cli.options.SparkSubmitOptions
import coursier.cli.spark.{SparkAssembly, Submit}


/**
  * Submits spark applications.
  *
  * Can be run with no spark distributions around.
  *
  * @author Alexandre Archambault
  * @author Han Ju
  */
object SparkSubmit extends CaseApp[SparkSubmitOptions] {

  def scalaSparkVersions(dependencies: Iterable[Dependency]): Either[String, (String, String)] = {

    val sparkCoreMods = dependencies.collect {
      case dep if dep.module.organization == "org.apache.spark" &&
        (dep.module.name == "spark-core_2.10" || dep.module.name == "spark-core_2.11") =>
        (dep.module, dep.version)
    }

    if (sparkCoreMods.isEmpty)
      Left("Cannot find spark among dependencies")
    else if (sparkCoreMods.size == 1) {
      val scalaVersion = sparkCoreMods.head._1.name match {
        case "spark-core_2.10" => "2.10"
        case "spark-core_2.11" => "2.11"
        case _ => throw new Exception("Cannot happen")
      }

      val sparkVersion = sparkCoreMods.head._2

      Right((scalaVersion, sparkVersion))
    } else
      Left(s"Found several spark code modules among dependencies (${sparkCoreMods.mkString(", ")})")

  }


  def run(options: SparkSubmitOptions, args: RemainingArgs): Unit = {

    val rawExtraJars = options.extraJars.map(new File(_))

    val extraDirs = rawExtraJars.filter(_.isDirectory)
    if (extraDirs.nonEmpty) {
      Console.err.println(s"Error: directories not allowed in extra job JARs.")
      Console.err.println(extraDirs.map("  " + _).mkString("\n"))
      sys.exit(1)
    }

    val helper: Helper = new Helper(
      options.common,
      args.remaining,
      extraJars = rawExtraJars
    )
    val jars =
      helper.fetch(
        sources = false,
        javadoc = false,
        artifactTypes = options.artifactOptions.artifactTypes(sources = false, javadoc = false)
      ) ++ options.extraJars.map(new File(_))

    val (scalaVersion, sparkVersion) =
      if (options.sparkVersion.isEmpty)
        SparkSubmit.scalaSparkVersions(helper.res.dependencies) match {
          case Left(err) =>
            Console.err.println(
              s"Cannot get spark / scala versions from dependencies: $err\n" +
                "Set them via --scala-version or --spark-version"
            )
            sys.exit(1)
          case Right(versions) => versions
        }
      else
        (options.common.scalaVersion, options.sparkVersion)

    val (sparkYarnExtraConf, sparkBaseJars) =
      if (!options.autoAssembly || sparkVersion.startsWith("2.")) {

        val assemblyJars = SparkAssembly.sparkJars(
          scalaVersion,
          sparkVersion,
          options.yarnVersion,
          options.defaultAssemblyDependencies.getOrElse(options.autoAssembly),
          options.assemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty) ++
            options.sparkAssemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty).map(_ + s":$sparkVersion"),
          options.common,
          options.artifactOptions.artifactTypes(sources = false, javadoc = false)
        )

        val extraConf =
          if (options.autoAssembly && sparkVersion.startsWith("2."))
            Seq(
              "spark.yarn.jars" -> assemblyJars.map(_.getAbsolutePath).mkString(",")
            )
          else
            Nil

        (extraConf, assemblyJars)
      } else {

        val assemblyAndJarsOrError = SparkAssembly.spark(
          scalaVersion,
          sparkVersion,
          options.yarnVersion,
          options.defaultAssemblyDependencies.getOrElse(true),
          options.assemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty) ++
            options.sparkAssemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty).map(_ + s":$sparkVersion"),
          options.common,
          options.artifactOptions.artifactTypes(sources = false, javadoc = false)
        )

        val (assembly, assemblyJars) = assemblyAndJarsOrError match {
          case Left(err) =>
            Console.err.println(s"Cannot get spark assembly: $err")
            sys.exit(1)
          case Right(res) => res
        }

        val extraConf = Seq(
          "spark.yarn.jar" -> assembly.getAbsolutePath
        )

        (extraConf, assemblyJars)
      }


    val idx = {
      val idx0 = args.unparsed.indexOf("--")
      if (idx0 < 0)
        args.unparsed.length
      else
        idx0
    }

    assert(idx >= 0)

    val sparkOpts = args.unparsed.take(idx)
    val jobArgs = args.unparsed.drop(idx + 1)

    val mainClass =
      if (options.mainClass.isEmpty)
        helper.retainedMainClass
      else
        options.mainClass

    val mainJar = helper
      .loader
      .loadClass(mainClass) // FIXME Check for errors, provide a nicer error message in that case
      .getProtectionDomain
      .getCodeSource
      .getLocation
      .getPath              // TODO Safety check: protocol must be file

    val (check, extraJars0) = jars.partition(_.getAbsolutePath == mainJar)

    val extraJars = extraJars0.filterNot(sparkBaseJars.toSet)

    if (check.isEmpty)
      Console.err.println(
        s"Warning: cannot find back $mainJar among the dependencies JARs (likely a coursier bug)"
      )

    val extraSparkOpts = sparkYarnExtraConf.flatMap {
      case (k, v) => Seq(
        "--conf", s"$k=$v"
      )
    }

    val extraJarsOptions =
      if (extraJars.isEmpty)
        Nil
      else
        Seq("--jars", extraJars.mkString(","))

    val mainClassOptions = Seq("--class", mainClass)

    val sparkSubmitOptions = sparkOpts ++ extraSparkOpts ++ extraJarsOptions ++ mainClassOptions ++
      Seq(mainJar) ++ jobArgs

    val submitCp = Submit.cp(
      scalaVersion,
      sparkVersion,
      options.noDefaultSubmitDependencies,
      options.submitDependencies.flatMap(_.split(",")).filter(_.nonEmpty),
      options.artifactOptions.artifactTypes(sources = false, javadoc = false),
      options.common
    )

    val submitLoader = new URLClassLoader(
      submitCp.map(_.toURI.toURL).toArray,
      helper.baseLoader
    )

    Launch(
      submitLoader,
      Submit.mainClassName,
      sparkSubmitOptions,
      options.common.verbosityLevel,
      {
        if (options.common.verbosityLevel >= 1)
          Console.err.println(
            s"Launching spark-submit with arguments:\n" +
              sparkSubmitOptions.map("  " + _).mkString("\n")
          )

        SparkOutputHelper.handleOutput(
          Some(options.yarnIdFile).filter(_.nonEmpty).map(new File(_)),
          Some(options.maxIdleTime).filter(_ > 0)
        )
      }
    )
  }
}
