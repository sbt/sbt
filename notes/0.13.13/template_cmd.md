
### new command and templateResolvers

sbt 0.13.13 adds `new` command, which helps create a new build definition.
The `new` command is extensible via a mechanism called the template resolver,
which evaluates the arguments passed to the command to find and run a template.
As a reference implementation [Giter8][g8] is provided as follows:

    sbt new eed3si9n/hello.g8

This will run eed3si9n/hello.g8 using Giter8.

  [@eed3si9n]: https://github.com/eed3si9n
  [g8]: http://www.foundweekends.org/giter8/
