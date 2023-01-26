// disablePlugins(Q) will prevent R from being auto-added
lazy val projA = project.enablePlugins(A, B).disablePlugins(Q)

// without B, Q is not added
lazy val projB = project.enablePlugins(A)

// with both A and B, Q is selected, which in turn selects R, but not S
lazy val projC = project.enablePlugins(A, B)

// with no natures defined, nothing is auto-added
lazy val projD = project

// with S selected, Q is loaded automatically, which in turn selects R
lazy val projE = project.enablePlugins(S)

lazy val projF = project

// with X enabled, TopA is loaded automatically
lazy val projG = project.enablePlugins(X)

// only TopB should be enabled
lazy val projH = project.enablePlugins(TopB)

// enables TopC, which declares topLevelKeyTest
lazy val projI = project.enablePlugins(TopC)


// Tests that we can disable an auto-enabled root plugin
lazy val disableAutoNoRequirePlugin = project.disablePlugins(OrgPlugin)

check := {
  // Ensure organization on root is overridable.
  val rorg = (organization).value // Should be None
  same(rorg, "override", "organization")
  // this will pass when the raw disablePlugin works.
  val dversion = (projD / projectID).?.value // Should be None
  same(dversion, None, "projectID in projD")

  //  Ensure with multiple .sbt files that disabling/enabling works across them
  val fDel = (projF / Quux / del).?.value
  same(fDel, Some(" Q"), "del in Quux in projF")
  //
  val adel = (projA / del).?.value // should be None
  same(adel, None, "del in projA")
  val bdel = (projB / del).?.value // should be None
  same(bdel, None, "del in projB")
  val ddel = (projD / del).?.value // should be None
  same(ddel, None, "del in projD")
  //
  val buildValue = (ThisBuild / demo).value
  same(buildValue, "build 0", "demo in ThisBuild")
  val globalValue = (Global / demo).value
  same(globalValue, "global 0", "demo in Global")
  val projValue = (projC / demo).?.value
  same(projValue, Some("project projC Q R"), "demo in projC")
  val qValue = (projC / Quux / del).?.value
  same(qValue, Some(" Q R"), "del in projC in Quux")
  val optInValue = (projE / Quux / del).value
  same(optInValue, " Q S R", "del in projE in Quux")
  val overrideOrgValue = (projE / organization).value
  same(overrideOrgValue, "S", "organization in projE")
  // tests for top level plugins
  val topLevelAValueG = (projG / topLevelDemo).value
  same(topLevelAValueG, "TopA: topLevelDemo project projG", "topLevelDemo in projG")
  val demoValueG = (projG / demo).value
  same(demoValueG, "TopA: demo project projG", "demo in projG")
  val topLevelBValueH = (projH / topLevelDemo).value
  same(topLevelBValueH, "TopB: topLevelDemo project projH", "topLevelDemo in projH")
  val hdel = (projH / del).?.value
  same(hdel, None, "del in projH")
}

keyTest := "foo"

topLevelKeyTest := "bar"

def same[T](actual: T, expected: T, label: String): Unit = {
  assert(actual == expected, s"Expected '$expected' for `$label`, got '$actual'")
}
