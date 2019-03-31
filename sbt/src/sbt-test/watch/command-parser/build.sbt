import Build._

organization := "sbt"

name := "scripted-watch-parser"

setStringValue := setStringValueImpl.evaluated

checkStringValue := checkStringValueImpl.evaluated

setStringValue / watchTriggers := baseDirectory.value * "string.txt" :: Nil

watchOnEvent := { _ => _ => Watch.CancelWatch }
