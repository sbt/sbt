import sbt._
import com.typesafe.packager.Keys._
import sbt.Keys._
import com.typesafe.packager.PackagerPlugin._

object Packaging {

  val sbtLaunchJarUrl = SettingKey[String]("sbt-launch-jar-url")
  val sbtLaunchJarLocation = SettingKey[File]("sbt-launch-jar-location")  
  val sbtLaunchJar = TaskKey[File]("sbt-launch-jar", "Resolves SBT launch jar")

  val jansiJarUrl = SettingKey[String]("jansi-jar-url")
  val jansiJarLocation = SettingKey[File]("jansi-jar-location")  
  val jansiJar = TaskKey[File]("jansi-jar", "Resolves Jansi jar")

  val winowsReleaseUrl = "http://typesafe.artifactoryonline.com/typesafe/windows-releases"

  def localWindowsPattern = "[organisation]/[module]/[revision]/[module].[ext]"
  
  val settings: Seq[Setting[_]] = packagerSettings ++ Seq(
    sbtLaunchJarUrl <<= sbtVersion apply ("http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-tools.sbt/sbt-launch/"+_+"/sbt-launch.jar"),
    sbtLaunchJarLocation <<= target apply (_ / "sbt-launch.jar"),
    sbtLaunchJar <<= (sbtLaunchJarUrl, sbtLaunchJarLocation) map { (uri, file) =>
      import dispatch._
      if(!file.exists) {
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))
         try Http(url(uri) >>> writer)
         finally writer.close()
      }
      // TODO - GPG Trust validation.
      file
    },
    jansiJarUrl := "http://repo.fusesource.com/nexus/content/groups/public/org/fusesource/jansi/jansi/1.7/jansi-1.7.jar",
    jansiJarLocation <<= target apply (_ / "jansi-1.7.jar"),
    jansiJar <<= (jansiJarUrl, jansiJarLocation) map { (uri, file) =>
      import dispatch._
      if(!file.exists) {
         val writer = new java.io.BufferedOutputStream(new java.io.FileOutputStream(file))
         try Http(url(uri) >>> writer)
         finally writer.close()
      }
      // TODO - GPG Trust validation.
      file
    },
    // GENERAL LINUX PACKAGING STUFFS
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Simple Build Tool for Scala-driven builds",
    packageDescription := """This script provides a native way to run the Simple Build Tool,
  a build tool for Scala software, also called SBT.""",
    linuxPackageMappings <+= (baseDirectory) map { bd =>
      (packageMapping((bd / "sbt") -> "/usr/bin/sbt")
       withUser "root" withGroup "root" withPerms "0755")
    },
    linuxPackageMappings <+= (sourceDirectory) map { bd =>
      (packageMapping(
        (bd / "linux" / "usr/share/man/man1/sbt.1") -> "/usr/share/man/man1/sbt.1.gz"
      ) withPerms "0644" gzipped) asDocs()
    },
    linuxPackageMappings <+= (sourceDirectory in Linux) map { bd =>
      packageMapping(
        (bd / "usr/share/doc/sbt/copyright") -> "/usr/share/doc/sbt/copyright"
      ) withPerms "0644" asDocs()
    },   
    linuxPackageMappings <+= (sourceDirectory in Linux) map { bd =>
      packageMapping(
        (bd / "usr/share/doc/sbt") -> "/usr/share/doc/sbt"
      ) asDocs()
    },
    linuxPackageMappings <+= (sourceDirectory in Linux) map { bd =>
      packageMapping(
        (bd / "etc/sbt") -> "/etc/sbt"
      ) withConfig()
    },
    linuxPackageMappings <+= (sourceDirectory in Linux) map { bd =>
      packageMapping(
        (bd / "etc/sbt/sbtopts") -> "/etc/sbt/sbtopts"
      ) withPerms "0644" withConfig("noreplace")
    },
    linuxPackageMappings <+= (sbtLaunchJar, sourceDirectory in Linux, sbtVersion) map { (jar, dir, v) =>
      packageMapping(dir -> "/usr/lib/sbt",
                     dir -> ("/usr/lib/sbt/" + v),
                     jar -> ("/usr/lib/sbt/"+v+"/sbt-launch.jar")) withPerms "0755"
    },
    // DEBIAN SPECIFIC    
    name in Debian := "sbt",
    version in Debian <<= (version, sbtVersion) apply { (v, sv) =>       
      sv + "-build-" + (v split "\\." map (_.toInt) dropWhile (_ == 0) map ("%02d" format _) mkString "")
    },
    debianPackageDependencies in Debian ++= Seq("curl", "java2-runtime", "bash (>= 2.05a-11)"),
    debianPackageRecommends in Debian += "git",
    linuxPackageMappings in Debian <+= (sourceDirectory) map { bd =>
      (packageMapping(
        (bd / "debian/changelog") -> "/usr/share/doc/sbt/changelog.gz"
      ) withUser "root" withGroup "root" withPerms "0644" gzipped) asDocs()
    },
    
    // RPM SPECIFIC
    name in Rpm := "sbt",
    version in Rpm <<= sbtVersion.identity,
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("http://github.com/paulp/sbt-extras"),
    rpmLicense := Some("BSD"),
    
    
    // WINDOWS SPECIFIC
    name in Windows := "sbt",
    lightOptions ++= Seq("-ext", "WixUIExtension", "-cultures:en-us"),
    wixConfig <<= (sbtVersion, sourceDirectory in Windows) map makeWindowsXml,
    //wixFile <<= sourceDirectory in Windows map (_ / "sbt.xml"),
    mappings in packageMsi in Windows <+= sbtLaunchJar map { f => f -> "sbt-launch.jar" },
    mappings in packageMsi in Windows <+= jansiJar map { f => f -> "jansi.jar" },
    mappings in packageMsi in Windows <+= sourceDirectory in Windows map { d => 
      (d / "sbt.bat") -> "sbt.bat" },
    mappings in packageMsi in Windows <+= (compile in Compile, classDirectory in Compile) map { (c, d) =>
      compile; (d / "SbtJansiLaunch.class") -> "SbtJansiLaunch.class" }
    // WINDOWS MSI Publishing
  ) ++ inConfig(Windows)(Classpaths.publishSettings) ++ Seq(
    packagedArtifacts in Windows <<= (packageMsi in Windows, name in Windows) map { (msi, name) =>
       val artifact = Artifact(name, "msi", "msi", classifier = None, configurations = Iterable.empty, url = None, extraAttributes = Map.empty)
       Map(artifact -> msi)
    },
    publishTo in Windows := Some(Resolver.url("windows-releases", new URL(winowsReleaseUrl))(Patterns(localWindowsPattern))),
    javacOptions := Seq("-source", "1.5", "-target", "1.5"),
    unmanagedJars in Compile <+= sbtLaunchJar map identity,
    unmanagedJars in Compile <+= jansiJar map identity
  )
  
  def makeWindowsXml(sbtVersion: String, sourceDir: File) = {
    val version = (sbtVersion split "\\.") match {
        case Array(major,minor,bugfix, _*) => Seq(major,minor,bugfix, "1") mkString "."
        case Array(major,minor) => Seq(major,minor,"0","1") mkString "."
        case Array(major) => Seq(major,"0","0","1") mkString "."
      }
      (
<Wix xmlns='http://schemas.microsoft.com/wix/2006/wi'>
  <Product Id='ce07be71-510d-414a-92d4-dff47631848a' 
            Name='Simple Build Tool' 
            Language='1033'
            Version={version}
            Manufacturer='Typesafe, Inc.' 
            UpgradeCode='4552fb0e-e257-4dbd-9ecb-dba9dbacf424'>
      <Package Description='Simple Build Tool launcher script.'
                Comments='First attempt to create an SBT windows installer, bear with me.'
                Manufacturer='Typesafe, Inc.' 
                InstallerVersion='200' 
                Compressed='yes' />
 
      <Media Id='1' Cabinet='sbt.cab' EmbedCab='yes' />
 
      <Directory Id='TARGETDIR' Name='SourceDir'>
         <Directory Id='ProgramFilesFolder' Name='PFiles'>
            <Directory Id='INSTALLDIR' Name='sbt'>
               <Directory Id='classes_dir' Name='classes'>
                  <Component Id='JansiLaunch' Guid='*'>
                     <File Id='jansi_launch' Name='SbtJansiLaunch.class' DiskId='1' Source='SbtJansiLaunch.class' />
                  </Component>
               </Directory>
               <Component Id='SbtLauncherScript' Guid='*'>
                  <File Id='sbt_bat' Name='sbt.bat' DiskId='1' Source='sbt.bat' />
               </Component>
               <Component Id='JansiJar' Guid='*'>
                  <File Id='jansi_jar' Name='jansi.jar' DiskId='1' Source='jansi.jar' />
               </Component>
               <Component Id='SbtLauncherJar' Guid='*'>
                  <File Id='sbt_launch_jar' Name='sbt-launch.jar' DiskId='1' Source='sbt-launch.jar' />
               </Component>               
               <Component Id='SbtLauncherPath' Guid='17EA4092-3C70-11E1-8CD8-1BB54724019B'>
                  <CreateFolder/>
                  <Environment Id="PATH" Name="PATH" Value="[INSTALLDIR]" Permanent="no" Part="last" Action="set" System="yes" />
               </Component>
             </Directory>
         </Directory>
      </Directory>
 
      <Feature Id='Complete' Title='Simple Build Tool' Description='The windows installation of Simple Build Tool.'
         Display='expand' Level='1' ConfigurableDirectory='INSTALLDIR'>
        <Feature Id='SbtLauncher' Title='Sbt Launcher Script' Description='The application which downloads and launches SBT.' Level='1'>
          <ComponentRef Id='SbtLauncherScript'/>
          <ComponentRef Id='SbtLauncherJar' />
          <ComponentRef Id='JansiLaunch' />
          <ComponentRef Id='JansiJar' />
        </Feature>
        <Feature Id='SbtLauncherPathF' Title='Add SBT to windows system PATH' Description='This will append SBT to your windows system path.' Level='1'>
          <ComponentRef Id='SbtLauncherPath'/>
        </Feature>
      </Feature>
      
      <UIRef Id="WixUI_Mondo"/>
      <UIRef Id="WixUI_ErrorProgressText"/>
      <Property Id="WIXUI_INSTALLDIR" Value="INSTALLDIR"/>
      <WixVariable Id="WixUILicenseRtf" Value={sourceDir.getAbsolutePath + "\\License.rtf"} />
   </Product>
</Wix>)
  }
}
