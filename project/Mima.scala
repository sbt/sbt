
import sbt._
import sbt.Keys._

import com.typesafe.tools.mima.plugin.MimaKeys._

object Mima {

  def binaryCompatibilityVersion = "1.0.0-M14"
  def binaryCompatibility212Version = "1.0.0-M15"


  lazy val previousArtifacts = Seq(
    mimaPreviousArtifacts := {
      val version = scalaBinaryVersion.value match {
        case "2.12" => binaryCompatibility212Version
        case _ => binaryCompatibilityVersion
      }
  
      Set(organization.value %% moduleName.value % version)
    }
  )

  lazy val coreFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._
  
      Seq(
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.defaultPublications"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.defaultPackaging"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.maven.MavenSource$DocSourcesArtifactExtensions"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageDirectoryElements"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageSubDirectories"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.compatibility.package.listWebPageFiles"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Project$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.apply"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Project.copy$default$13"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Project.copy$default$12"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Project.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.copy$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.packagingBlacklist"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.apply$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.ignorePackaging"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.<init>$default$5"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.maven.MavenRepository.apply"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Activation$Os"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Version"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.Authentication"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.core.VersionInterval"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Opt"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Const"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Opt"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Var"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.IvyRepository"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.Pattern$Chunk$Const"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Prop"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.ivy.PropertiesPattern$ChunkOrProperty$Var"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.maven.MavenRepository"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.maven.MavenSource"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.package#Resolution.apply$default$9"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.package#Resolution.apply"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.copy$default$9"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("coursier.core.Resolution.copyWithCache$default$8"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.profileActivation"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.copyWithCache"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.copy"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.this"),
        ProblemFilters.exclude[MissingTypesProblem]("coursier.core.Activation$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Activation.apply"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.profiles"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.core.Resolution.apply")
      )
    }
  }

  lazy val cacheFilters = {
    mimaBinaryIssueFilters ++= {
      import com.typesafe.tools.mima.core._

      Seq(
        ProblemFilters.exclude[DirectMissingMethodProblem]("coursier.TermDisplay#UpdateDisplayRunnable.cleanDisplay"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.TermDisplay$DownloadInfo"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.TermDisplay$CheckUpdateInfo"),
        ProblemFilters.exclude[FinalClassProblem]("coursier.util.Base64$B64Scheme"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$Stop$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$Message$Update$"),
        ProblemFilters.exclude[MissingClassProblem]("coursier.TermDisplay$UpdateDisplayThread")
      )
    }
  }

}
