[coursier](http://github.com/coursier/coursier) is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its very core (`core` module) aims at being
extremely pure, and only requires to be fed external data (Ivy / Maven metadata) via a monad.

It handles fancy Maven features like

* POM inheritance,
* dependency management,
* import scope,
* properties,
* etc.

and is able to fetch metadata and artifacts from both Maven and Ivy repositories.

Compared to the default dependency resolution of SBT, it adds:

* downloading of artifacts in parallel,
* better offline mode - one can safely work with snapshot dependencies if these are in cache (SBT tends to try and fail if it cannot check for updates),
* non obfuscated cache (cache structure just mimicks the URL it caches),
* no global lock (no "Waiting for ~/.ivy2/.sbt.ivy.lock to be available").

From the command-line, it also has:
* a [launcher](https://github.com/coursier/coursier#launch), able to launch apps distributed via Maven / Ivy repositories,
* a [bootstrap](https://github.com/coursier/coursier#bootstrap) generator, able to generate stripped launchers of these apps.

Lastly, it can be used programmatically via its [API](https://github.com/coursier/coursier#api) and has a Scala JS [demo](https://github.com/coursier/coursier#scala-js-demo).
