AGENTS instructions
===================

The main developer documentation is [Contributor's guide](contributing-docs/README.md).

- [Development environment](contributing-docs/02_development_environment.md)

Compiling with sbt
------------------

```bash
sbt compile
```

Pull reuqest guideline
----------------------

- Follow the PR guidance in [CONTRIBUTING.md](./CONTRIBUTING.md).
- [ ] Before working on a pull request, please confirm that **you can reproduce the reported problem** using GitHub Actions or your computer.
- [ ] After making the code change, please confirm that **your change compiles, and has fixed the problem**.
- [ ] In the commit message, include "Generated-by" tag for Gen-AI tools.

Coding style
------------

```bash
sbt scalafmtAll
```

- Follow [Coding style and best practices](contributing-docs/03_coding_style.md)
- Avoid inline comments!

Tests
-----

Always add tests. For changes with small scopes prefer HedgeHog for Scala.
For changes that require coordination with file changes and tasks, use scripted test.

- [contributing-docs/04_unit_tests.md](contributing-docs/04_unit_tests.md)
- [contributing-docs/05_scripted_tests.md](contributing-docs/05_scripted_tests.md)
- [contributing-docs/06_manual_tests.md](contributing-docs/06_manual_tests.md)

Tech stack
----------

- [contributing-docs/07_tech_stack.md](contribution-docs/07_tech_stach.md)

Binary compatibility
--------------------

sbt MUST maintain backward binary compatibility across minor releases.
This means removing public method signature MUST be avoided.

Use mima to check:

```bash
sbt mimaReportBinaryIssues
```

Copyright
---------

- NEVER reproduce copyrighted material.
