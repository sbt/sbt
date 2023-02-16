scalaVersion := "2.12.8"

resolvers += "Private S3 Snapshots" atS3
  "s3://s3-us-west-2.amazonaws.com/bucket-name/snapshots"
resolvers += "Private S3 Releases" atS3
  "s3://s3-us-west-2.amazonaws.com/bucket-name/releases"

// TODO: Would need a public s3 bucket to download an artifact

lazy val check = taskKey[Unit]("")

// Checks FromSbt.repository parses the "s3://" urls correctly.

check := {
  val s: TaskStreams = streams.value

  val sbtResolvers: Seq[sbt.librarymanagement.Resolver] = coursierResolvers.value

  // Sanity check to ensure SBT is loading the resolvers properly
  assert(sbtResolvers.exists(_.name == "Private S3 Snapshots"))
  assert(sbtResolvers.exists(_.name == "Private S3 Releases"))
  
  // Have Coursier SBT Plugin Parse the SBT Resolvers
  val parsedCoursierResolvers: Seq[coursier.core.Repository] =
    sbtResolvers.flatMap{ sbtResolver: sbt.librarymanagement.Resolver =>
      lmcoursier.internal.Resolvers.repository(
        resolver = sbtResolver,
        ivyProperties = lmcoursier.internal.ResolutionParams.defaultIvyProperties(
          ivyPaths.value.ivyHome
        ),
        log = s.log,
        authentication = None,
        classLoaders = Seq()
      )
    }

  // Verify the input resolvers == output resolvers
  assert(
    sbtResolvers.size == parsedCoursierResolvers.size,
    s"SBT resolvers size (${sbtResolvers.size}) did not match " +
      s"Coursier resolvers size (${parsedCoursierResolvers.size})"
  )

  def containsRepo(repo: String): Boolean = {
    val accepted = Set(repo, repo.stripSuffix("/"))
    parsedCoursierResolvers.exists {
      case m: coursier.maven.MavenRepositoryLike => accepted(m.root)
      case _ => false
    }
  }

  assert(containsRepo("s3://s3-us-west-2.amazonaws.com/bucket-name/snapshots/"),
    "Didn't have snapshots s3 repo")
  assert(containsRepo("s3://s3-us-west-2.amazonaws.com/bucket-name/releases/"),
    "Didn't have releases s3 repo")
}