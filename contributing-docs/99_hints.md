Random tidbits
==============

### Instruction to build all modules from source

When working on a change that requires changing one or more sub modules, the source code for these modules can be pulled in by running the following script
(instead of building them from Maven for example and not being able to change the source code for these sub-modules).

1. Install the current stable binary release of sbt (see [Setup]), which will be used to build sbt from source.
2. Get the source code.

   ```bash
   $ mkdir sbt-modules
   $ cd sbt-modules
   $ for i in sbt io zinc; do \
     git clone https://github.com/sbt/$i.git && (cd $i; git checkout -b develop origin/develop)
   done
   $ cd sbt
   $ ./sbt-allsources.sh
   ```

3. To build and publish all components locally,

   ```bash
   $ ./sbt-allsources.sh
   sbt:sbtRoot> publishLocalAllModule
   ```

### Updating Scala version

See https://github.com/sbt/sbt/pull/6522 for the list of files to change for Scala version upgrade.



### Import statements

You'd need alternative DSL import since you can't rely on sbt package object.

```scala
// for slash syntax
import sbt.SlashSyntax0.given

// for IO
import sbt.io.syntax.*
```
