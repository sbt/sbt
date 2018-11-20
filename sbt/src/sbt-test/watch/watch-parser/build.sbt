import Build._

organization := "sbt"

name := "scripted-watch-parser"

setStringValue := setStringValueImpl.evaluated

checkStringValue := checkStringValueImpl.evaluated

watchSources += file("string.txt")

watchOnEvent := { _ => Watched.CancelWatch }
