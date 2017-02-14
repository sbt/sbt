package coursier

import coursier.ivy.IvyXml.{ mappings => ivyXmlMappings }
import sbt.Keys._
import sbt.{AutoPlugin, Compile, Configuration, TaskKey, inConfig}

object ShadingPlugin extends AutoPlugin {

  override def trigger = noTrigger

  override def requires = sbt.plugins.IvyPlugin

  private val baseSbtConfiguration = Compile
  val Shading = Configuration("shading", "", isPublic = false, List(baseSbtConfiguration), transitive = true)

  private val baseDependencyConfiguration = "compile"
  val Shaded = Configuration("shaded", "", isPublic = true, List(), transitive = true)

  val shadingNamespace = TaskKey[String]("shading-namespace")

  object autoImport {

    /** Scope for shading related tasks */
    val Shading = ShadingPlugin.Shading

    /** Ivy configuration for shaded dependencies */
    val Shaded = ShadingPlugin.Shaded

    val shadingNamespace = ShadingPlugin.shadingNamespace
  }

  // same as similar things under sbt.Classpaths, tweaking a bit the configuration scope
  lazy val shadingDefaultArtifactTasks =
    makePom +: Seq(packageBin, packageSrc, packageDoc).map(_.in(Shading))
  lazy val shadingJvmPublishSettings = Seq(
    artifacts <<= sbt.Classpaths.artifactDefs(shadingDefaultArtifactTasks),
    packagedArtifacts <<= sbt.Classpaths.packaged(shadingDefaultArtifactTasks)
  )

  import CoursierPlugin.autoImport._

  override lazy val projectSettings =
    Seq(
      coursierConfigurations <<= Tasks.coursierConfigurationsTask(
        Some(baseDependencyConfiguration -> Shaded.name)
      ),
      ivyConfigurations := Shaded +: ivyConfigurations.value.map {
        conf =>
          if (conf.name == "compile")
            conf.extend(Shaded)
          else
            conf
      }
    ) ++
    inConfig(Shading)(
      sbt.Defaults.configSettings ++
        sbt.Classpaths.ivyBaseSettings ++
        sbt.Classpaths.ivyPublishSettings ++
        shadingJvmPublishSettings ++
        CoursierPlugin.coursierSettings(
          Some(baseDependencyConfiguration -> Shaded.name),
          Seq(Shading -> Compile.name)
        ) ++
        CoursierPlugin.treeSettings ++
        Seq(
          configuration := baseSbtConfiguration, // wuw
          ivyConfigurations := ivyConfigurations.in(baseSbtConfiguration).value
            .filter(_.name != Shaded.name)
            .map(c => c.copy(extendsConfigs = c.extendsConfigs.filter(_.name != Shaded.name))),
          libraryDependencies := libraryDependencies.in(baseSbtConfiguration).value.filter { dep =>
            val isShaded = dep.configurations.exists { mappings =>
              ivyXmlMappings(mappings).exists(_._1 == Shaded.name)
            }

            !isShaded
          },
          // required for cross-projects in particular
          unmanagedSourceDirectories := (unmanagedSourceDirectories in Compile).value,
          packageBin := {
            coursier.Shading.createPackage(
              packageBin.in(baseSbtConfiguration).value,
              coursierProject.in(baseSbtConfiguration).value,
              coursierResolution.in(baseSbtConfiguration).value,
              coursierConfigurations.in(baseSbtConfiguration).value,
              Keys.coursierArtifacts.in(baseSbtConfiguration).value,
              classpathTypes.value,
              shadingNamespace.?.value.getOrElse {
                throw new NoSuchElementException("shadingNamespace key not set")
              },
              baseDependencyConfiguration,
              Shaded.name,
              streams.value.log
            )
          }
        )
    )

}