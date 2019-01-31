import sbt.legacy.sources.Build._

Global / watchSources += new sbt.internal.io.Source(baseDirectory.value, "global.txt", NothingFilter, false)

watchSources in setStringValue += new sbt.internal.io.Source(baseDirectory.value, "foo.txt", NothingFilter, false)

setStringValue := setStringValueImpl.evaluated

checkStringValue := checkStringValueImpl.evaluated

watchOnTriggerEvent := { (_, _) => Watched.CancelWatch }
watchOnInputEvent := { (_, _) => Watched.CancelWatch }
watchOnMetaBuildEvent := { (_, _) => Watched.CancelWatch }
