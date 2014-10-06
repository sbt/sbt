name <<= submitProjectName(pname => "progfun-"+ pname)

version := "1.0.0"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"

libraryDependencies += "junit" % "junit" % "4.10" % "test"

libraryDependencies += "org.scala-lang" % "scala-reflect" % "2.10.2"

// This setting defines the project to which a solution is submitted. When creating a
// handout, the 'createHandout' task will make sure that its value is correct.
submitProjectName := "suggestions"

libraryDependencies <++= (currentProject) { c =>
  if (c.isEmpty || c == "quickcheck") Seq(
    "org.scalacheck" %% "scalacheck" % "1.10.1"
  )
  else Seq.empty
}

libraryDependencies <++= (currentProject) { c =>
  if (c.isEmpty || c == "nodescala" || c == "suggestions") Seq(
    "com.netflix.rxjava" % "rxjava-scala" % "0.15.0",
    "org.json4s" % "json4s-native_2.10" % "3.2.5",
    "org.scala-lang" % "scala-swing" % "2.10.3",
    "net.databinder.dispatch" % "dispatch-core_2.10" % "0.11.0",
    "org.scala-lang" % "scala-reflect" % "2.10.3",
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "org.slf4j" % "slf4j-simple" % "1.7.5",
    "com.squareup.retrofit" % "retrofit" % "1.0.0",
    "org.scala-lang.modules" %% "scala-async" % "0.9.0-M2"
  )
  else Seq.empty
}

libraryDependencies <++= (currentProject) { c =>
  if (c.isEmpty || c == "actorbintree") Seq(
    "com.typesafe.akka" %% "akka-actor" % "2.2.3",
    "com.typesafe.akka" %% "akka-testkit" % "2.2.3"
    )
  else Seq.empty
}

// See documentation in ProgFunBuild.scala
projectDetailsMap := {
val currentCourseId = "reactive-001"
Map(
  "example" ->  ProjectDetails(
                  packageName = "example",
                  assignmentPartId = "fTzFogNl",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "recfun" ->     ProjectDetails(
                  packageName = "recfun",
                  assignmentPartId = "3Rarn9Ki",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "funsets" ->    ProjectDetails(
                  packageName = "funsets",
                  assignmentPartId = "fBXOL6Rd",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "objsets" ->    ProjectDetails(
                  packageName = "objsets",
                  assignmentPartId = "05dMMEz7",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "patmat" ->     ProjectDetails(
                  packageName = "patmat",
                  assignmentPartId = "4gPmpcif",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "forcomp" ->    ProjectDetails(
                  packageName = "forcomp",
                  assignmentPartId = "fG2oZGIO",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "streams" ->    ProjectDetails(
                  packageName = "streams",
                  assignmentPartId = "DWKgCFCi",
                  maxScore = 10d,
                  styleScoreRatio = 0.2,
                  courseId=currentCourseId),
  "quickcheck" -> ProjectDetails(
                  packageName = "quickcheck",
                  assignmentPartId = "02Vi5q7m",
                  maxScore = 10d,
                  styleScoreRatio = 0.0,
                  courseId=currentCourseId),
  "simulations" -> ProjectDetails(
                  packageName = "simulations",
                  assignmentPartId = "pA3TAeu1",
                  maxScore = 10d,
                  styleScoreRatio = 0.0,
                  courseId=currentCourseId),
  "nodescala" -> ProjectDetails(
                  packageName = "nodescala",
                  assignmentPartId = "RvoTAbRy",
                  maxScore = 10d,
                  styleScoreRatio = 0.0,
                  courseId=currentCourseId),
  "suggestions" -> ProjectDetails(
                  packageName = "suggestions",
                  assignmentPartId = "rLLdQLGN",
                  maxScore = 10d,
                  styleScoreRatio = 0.0,
                  courseId=currentCourseId),
  "actorbintree" -> ProjectDetails(
                  packageName = "actorbintree",
                  assignmentPartId = "VxIlIKoW",
                  maxScore = 10d,
                  styleScoreRatio = 0.0,
                  courseId=currentCourseId)
)}

// Files that we hand out to the students
handoutFiles <<= (baseDirectory, projectDetailsMap, commonSourcePackages) map { (basedir, detailsMap, commonSrcs) =>
  (projectName: String) => {
    val details = detailsMap.getOrElse(projectName, sys.error("Unknown project name: "+ projectName))
    val commonFiles = (PathFinder.empty /: commonSrcs)((files, pkg) =>
      files +++ (basedir / "src" / "main" / "scala" / pkg ** "*.scala")
    )
    (basedir / "src" / "main" / "scala" / details.packageName ** "*.scala") +++
    commonFiles +++
    (basedir / "src" / "main" / "resources" / details.packageName ** "*") +++
    (basedir / "src" / "test" / "scala" / details.packageName ** "*.scala") +++
    (basedir / "build.sbt") +++
    (basedir / "project" / "build.properties") +++
    (basedir / "project" ** ("*.scala" || "*.sbt")) +++
    (basedir / "project" / "scalastyle_config.xml") +++
    (basedir / "project" / "scalastyle_config_reactive.xml") +++
    (basedir / "lib_managed" ** "*.jar") +++
    (basedir * (".classpath" || ".project")) +++
    (basedir / ".settings" / "org.scala-ide.sdt.core.prefs")
  }
}

// This setting allows to restrict the source files that are compiled and tested
// to one specific project. It should be either the empty string, in which case all
// projects are included, or one of the project names from the projectDetailsMap.
currentProject := ""

// Packages in src/main/scala that are used in every project. Included in every
// handout, submission.
commonSourcePackages += "common"

// Packages in src/test/scala that are used for grading projects. Always included
// compiling tests, grading a project.
gradingTestPackages += "grading"
