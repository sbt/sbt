---
layout: default
title: download
tagline: up and running in moments.
description: |
  The [SBT Launcher](http://github.com/sbt/sbt-launcher-package) project contains a set of native packages for use in your operating system.

  [msi](#windows) | [yum](#rpm) | [apt-get](#deb) | [homebrew](#mac) | [by hand](#manual)

toplinks:
       - name: 'Windows MSI downloads'
         id: 'windows'
         content: |
           #### Windows Releases ####
           
           [Click here](http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.12.0/sbt.msi) for the latest windows MSI.
           
           *Note: please make sure to report any issues you may find [here](https://github.com/sbt/sbt-launcher-package/issues).*
       - name: 'Yum Repository'
         id: 'rpm'
         content: |
            #### Yum Repositories ####

            The sbt package is available from the [Typesafe Yum Repository](http://rpm.typesafe.com).
            Please install [this rpm](http://rpm.typesafe.com/typesafe-repo-2.0.0-1.noarch.rpm) to add the typesafe yum repository to your list of approved sources. 
            Then run:
            
                yum install sbt
             
             to grab the latest release of sbt.
            
            *Note: please make sure to report any issues you may find [here](https://github.com/sbt/sbt-launcher-package/issues).*"
       - name: 'Apt Repository'
         id: 'deb'
         content: |
           #### APT Repositories ####
           
           The sbt package is available from the [Typesafe Debian Repository](http://apt.typesafe.com).
           Please install [this deb](http://apt.typesafe.com/repo-deb-build-0002.deb) to enable the typesafe repository.
           Then run:
           
               apt-get install sbt
           
           to grab the latest release of sbt.
            
            *Note: please make sure to report any issues you may find [here](https://github.com/sbt/sbt-launcher-package/issues).*"
       - name: 'Homebrew'
         id: 'mac'
         content: |
           #### Hombrew ####
           
           Use HomeBrew:
           
               $ brew install sbt
       - name: 'Manual Installation'
         id: 'manual'
         content: |
           #### Pre-Built Zip files ###
           Download one of the pre-built [zip](http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.12.0/sbt.zip)
           or [tgz](http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages/org/scala-sbt/sbt/0.12.0/sbt.tgz) and
           add the bin/ to your path.

           #### By Hand installation ####
           First, download the [launcher jar](http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.12.0/sbt-launch.jar) 
           and place it somewhere useful.
           THEN, create a script in that same directory.
           ##### Windows #####
           Create a `sbt.bat` file next to the launch jar.
           
               set SCRIPT_DIR=%~dp0
               java -Xmx512M -jar "%SCRIPT_DIR%sbt-launch.jar" %*
           
           Add the directory containing `sbt.bat` to the windows path.
           ##### Unix-Like #####
           Create a `sbt` script (a good place is `~/bin/sbt`
           
               java -Xmx512M -jar `dirname $0`/sbt-launch.jar "$@"
           
           then make the script executable:
           
               $ chmod u+x ~/bin/sbt
---

<!-- This page has no content. -->

