---
layout: howto
title: Configure packaging
sections:
 - id: export
   name: use the packaged jar on classpaths instead of class directory
   setting: exportJars := true
 - id: manifest
   name: add manifest attributes
   setting: 'packageOptions in (Compile, packageBin) += Package.ManifestAttributes( Attributes.Name.SEALED -> "true" )'
 - id: name
   name: change the file name of a package
 - id: contents
   name: modify the contents of the package
   setting: 'mappings in (Compile, packageBin) <+= baseDirectory { dir => ( dir / "example.txt") -> "out/example.txt" }'
---

[mapping files]: https://github.com/harrah/xsbt/wiki/Mapping-Files
[Artifacts]: https://github.com/harrah/xsbt/wiki/Artifacts

<h4 id="export">Use the packaged jar on classpaths instead of class directory</h4>

By default, a project exports a directory containing its resources and compiled class files.  Set `exportJars` to true to export the packaged jar instead.  For example,

{% highlight scala %}
exportJars := true
{% endhighlight %}

The jar will be used by `run`, `test`, `console`, and other tasks that use the full classpath.

<h4 id="manifest">Add attributes to the manifest</h4>

By default, sbt constructs a manifest for the binary package from settings such as `organization` and `mainClass`.  Additional attributes may be added to the `packageOptions` setting scoped by the configuration and package task.

Main attributes may be added with `Package.ManifestAttributes`.  There are two variants of this method, once that accepts repeated arguments that map an attribute of type `java.util.jar.Attributes.Name` to a String value and other that maps attribute names (type String) to the String value.  

For example,

{% highlight scala %}
packageOptions in (Compile, packageBin) += 
    Package.ManifestAttributes( java.util.jar.Attributes.Name.SEALED -> "true" )
{% endhighlight %}

Other attributes may be added with `Package.JarManifest`.

{% highlight scala %}
packageOptions in (Compile, packageBin) +=  {
    import java.util.jar.{Attributes, Manifest}
    val manifest = new Manifest
    manifest.getAttributes("foo/bar/").put(Attributes.Name.SEALED, "false")
    Package.JarManifest( manifest )
}
{% endhighlight %}

Or, to read the manifest from a file:

{% highlight scala %}
packageOptions in (Compile, packageBin) +=  {
    val manifest = Using.fileInputStream( in => new java.util.jar.Manifest(in) )
    Package.JarManifest( manifest )
}
{% endhighlight %}

<h4 id="name">Change the file name of a package</h4>

The `artifactName` setting controls the name of generated packages.  See the [Artifacts] page for details.

<h4 id="contents">Modify the contents of the package</h4>

The contents of a package are defined by the `mappings` task, of type `Seq[(File,String)]`.  The `mappings` task is a sequence of mappings from a file to include in the package to the path in the package.  See the page on [mapping files] for convenience functions for generating these mappings.  For example, to add the file `in/example.txt` to the main binary jar with the path "out/example.txt",

{% highlight scala %}
mappings in (Compile, packageBin) <+= baseDirectory { base =>
   (base / "in" / "example.txt") -> "out/example.txt"
}
{% endhighlight %}

Note that `mappings` is scoped by the configuration and the specific package task.  For example, the mappings for the test source package are defined by the `mappings in (Test, packageSrc)` task.