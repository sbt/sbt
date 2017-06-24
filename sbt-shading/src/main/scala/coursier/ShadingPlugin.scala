package coursier

import java.io.File

import coursier.ivy.IvyXml.{mappings => ivyXmlMappings}
import sbt.Keys._
import sbt.{AutoPlugin, Compile, Configuration, SettingKey, TaskKey, inConfig}

import SbtCompatibility._

object ShadingPlugin extends AutoPlugin {

  override def trigger = noTrigger

  override def requires = sbt.plugins.IvyPlugin

  private val baseSbtConfiguration = Compile
  val Shading = Configuration("shading", "", isPublic = false, Vector(baseSbtConfiguration), transitive = true)

  private val baseDependencyConfiguration = "compile"
  val Shaded = Configuration("shaded", "", isPublic = true, Vector(), transitive = true)

  // make that a setting?
  val shadingNamespace = SettingKey[String]("shading-namespace")

  // make that a setting?
  val shadeNamespaces = SettingKey[Set[String]]("shade-namespaces")

  val toShadeJars = TaskKey[Seq[File]]("to-shade-jars")
  val toShadeClasses = TaskKey[Seq[String]]("to-shade-classes")

  object autoImport {

    /** Scope for shading related tasks */
    val Shading = ShadingPlugin.Shading

    /** Ivy configuration for shaded dependencies */
    val Shaded = ShadingPlugin.Shaded

    /** Namespace under which shaded things will be moved */
    val shadingNamespace = ShadingPlugin.shadingNamespace

    /**
      * Assume everything under these namespaces is to be shaded.
      *
      * Allows to speed the shading phase, if everything under some namespaces is to be shaded.
      */
    val shadeNamespaces = ShadingPlugin.shadeNamespaces

    val toShadeJars = ShadingPlugin.toShadeJars
    val toShadeClasses = ShadingPlugin.toShadeClasses
  }

  // same as similar things under sbt.Classpaths, tweaking a bit the configuration scope
  lazy val shadingDefaultArtifactTasks =
    makePom +: Seq(packageBin, packageSrc, packageDoc).map(_.in(Shading))
  lazy val shadingJvmPublishSettings = Seq(
    artifacts := sbt.Classpaths.artifactDefs(shadingDefaultArtifactTasks).value,
    packagedArtifacts := sbt.Classpaths.packaged(shadingDefaultArtifactTasks).value
  )

  import CoursierPlugin.autoImport._

  override lazy val buildSettings = super.buildSettings ++ Seq(
    shadeNamespaces := Set()
  )

  override lazy val projectSettings =
    Seq(
      coursierConfigurations := Tasks.coursierConfigurationsTask(
        Some(baseDependencyConfiguration -> Shaded.name)
      ).value,
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
            .map(c => c.withExtendsConfigs(c.extendsConfigs.filter(_.name != Shaded.name))),
          libraryDependencies := libraryDependencies.in(baseSbtConfiguration).value.filter { dep =>
            val isShaded = dep.configurations.exists { mappings =>
              ivyXmlMappings(mappings).exists(_._1 == Shaded.name)
            }

            !isShaded
          },
          // required for cross-projects in particular
          unmanagedSourceDirectories := (unmanagedSourceDirectories in Compile).value,
          toShadeJars := {
            coursier.Shading.toShadeJars(
              coursierProject.in(baseSbtConfiguration).value,
              coursierResolutions
                .in(baseSbtConfiguration)
                .value
                .collectFirst {
                  case (configs, res) if configs(baseDependencyConfiguration) =>
                    res
                }
                .getOrElse {
                  sys.error(s"Resolution for configuration $baseDependencyConfiguration not found")
                },
              coursierConfigurations.in(baseSbtConfiguration).value,
              Keys.coursierArtifacts.in(baseSbtConfiguration).value,
              classpathTypes.value,
              baseDependencyConfiguration,
              Shaded.name,
              streams.value.log
            )
          },
          toShadeClasses := {
            coursier.Shading.toShadeClasses(
              shadeNamespaces.value,
              toShadeJars.value,
              streams.value.log
            )
          },
          packageBin := {
            coursier.Shading.createPackage(
              packageBin.in(baseSbtConfiguration).value,
              shadingNamespace.?.value.getOrElse {
                throw new NoSuchElementException("shadingNamespace key not set")
              },
              shadeNamespaces.value,
              toShadeClasses.value,
              toShadeJars.value
            )
          }
        )
    )

}