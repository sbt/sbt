// check that a plain File can be appended to Classpath
unmanagedJars in Compile += file("doesnotexist")

unmanagedJars in Compile ++= Seq( file("doesnotexist1"), file("doesnotexist2") )

// check that an Attributed File can be appended to Classpath
unmanagedJars in Compile += Attributed.blank(file("doesnotexist"))

unmanagedJars in Compile ++= Attributed.blankSeq( Seq( file("doesnotexist1"), file("doesnotexist2") ) )

maxErrors += 1

name += "-demo"

