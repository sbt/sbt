Global / credentials := Seq(Credentials("", "pgp", "", "test password"))

scalaVersion := "3.8.4"
name := "app"
version := "1.0"
publishLocal := {}
Global / pgpSecretRing := baseDirectory.value / "secring.pgp"
Global / pgpPublicRing := baseDirectory.value / "pubring.pgp"
Global / useGpg := false

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if isSnapshot.value then Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
usePgpKeyHex("AA2DBC9295B91B7A")
