// The only reason this file exists is to make sure that a recompilation will
// occur in the macro-client subproject.
// This tests works by verifying that the `Client.scala` in macro-client subproject is
// recompiled after the auxiliary file `util/util.txt` is modified. To check that a file has been
// recompiled, we verify that it was part of the last compilation unit. The problem is that if no
// recompilation occurs at all, then `Client.scala` will be part of the last compilation unit,
// even if it has not be recompiled (because nothing has been recompiled).
object Foo extends Bar