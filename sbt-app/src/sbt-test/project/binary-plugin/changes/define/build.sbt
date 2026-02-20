sbtPlugin := true

name := "demo-plugin"

// TODO fix doc task
Compile / doc / sources := Def.uncached(Seq.empty)
