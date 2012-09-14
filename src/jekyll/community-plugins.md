---
layout: default
title: Community Plugins
tagline: ensuring everything is possible.
description: 'The [SBT Organization](http://github.com/sbt) contains a [SBT Community Plugins](http://github.com/sbt/sbt-community-plugins) project.   This project aims to unify all the SBT plugins in the community and ensure their compatibility and timely releases with new versions of SBT.  There is also a [list of plugins](https://github.com/harrah/xsbt/wiki/sbt-0.10-plugins-list) that is up-to-date.'
toplinks:

       - name: 'Available Plugins'
         link: 'plugins.html'
       - name: 'Community Ivy repository'
         id: 'communityrepo'
         content: |
           #### Community Ivy Repository ####           
           [Typesafe, Inc.](http://www.typesafe.com) has provided a freely available [Ivy Repository](http://scalasbt.artifactoryonline.com/scalasbt) for SBT projects to make use of.

           If you would like to publish your project to this Ivy repository, first contact [sbt-repo-admins](http://groups.google.com/group/sbt-repo-admins?hl=en) and request privileges
           (we have to verify code ownership, rights to publish, etc.).  After which, you can deploy your plugins using the following configuration:
           
               publishTo := Some(Resolver.url("sbt-plugin-releases", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns))
               
               publishMavenStyle := false
           
           You'll also need to add your credentials somewhere.  I use a `~/.sbt/sbtpluginpublish.sbt` file:
           
               credentials += Credentials("Artifactory Realm", "scalasbt.artifactoryonline.com", "jsuereth", "@my encrypted password@")
           
           Where `@my encrypted password@` is actually obtained using the following [instructions](http://wiki.jfrog.org/confluence/display/RTF/Centrally+Secure+Passwords).
           
           *Note: Your code must abide by the [repository polices](repository-rules.html).*

           To automatically deploy snapshot/release versions of your plugin use
           the following configuration:

               publishTo <<= (version) { version: String =>
                  val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
                  val (name, url) = if (version.contains("-SNAPSHOT"))
                                      ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
                                    else
                                      ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
                  Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
               }

           *Note: ivy repositories currently don't support Maven-style snapshots.*
       - name: 'SBT Organization'
         id: 'sbtorg'
         content: |
           #### SBT Organization ####
           
           The [SBT Organization](http://github.com/sbt) is available for use by any SBT plugin.  
           Developers who contribute their plugins into the community organization will still retain 
           control over their repository and its access.   The Goal of the SBT organization is to
           organize SBT software into one central location.

           A side benefit to using the SBT organization for projects is that you can use gh-pages to host websites in the http://scala-sbt.org domain.
           
       - name: 'Community Plugin Build'
         id: 'pluginbuild'
         content: |
           #### SBT Community Plugin Build ####
           
           The [SBT Community Plugins](http://github.com/sbt/sbt-community-plugins) project aims to build *all* SBT plugins in a single build.  
           This should enable thorough testing of plugins and ensure that plugins work together.

---


