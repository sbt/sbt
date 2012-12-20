=====================
 Configure packaging
=====================

.. howto::
   :id: export
   :title: Use the packaged jar on classpaths instead of class directory
   :type: setting
   
   exportJars := true

By default, a project exports a directory containing its resources and compiled class files.  Set ``exportJars`` to true to export the packaged jar instead.  For example,

::

    exportJars := true

The jar will be used by ``run``, ``test``, ``console``, and other tasks that use the full classpath.


.. howto::
   :id: manifest
   :title: Add manifest attributes
   :type: setting
   
   packageOptions in (Compile, packageBin) +=
      Package.ManifestAttributes( Attributes.Name.SEALED -> "true" )

By default, sbt constructs a manifest for the binary package from settings such as ``organization`` and ``mainClass``.  Additional attributes may be added to the ``packageOptions`` setting scoped by the configuration and package task.

Main attributes may be added with ``Package.ManifestAttributes``.  There are two variants of this method, once that accepts repeated arguments that map an attribute of type ``java.util.jar.Attributes.Name`` to a String value and other that maps attribute names (type String) to the String value.  

For example,

::

    packageOptions in (Compile, packageBin) += 
        Package.ManifestAttributes( java.util.jar.Attributes.Name.SEALED -> "true" )
    
Other attributes may be added with ``Package.JarManifest``.

::
    
    packageOptions in (Compile, packageBin) +=  {
        import java.util.jar.{Attributes, Manifest}
        val manifest = new Manifest
        manifest.getAttributes("foo/bar/").put(Attributes.Name.SEALED, "false")
        Package.JarManifest( manifest )
    }
    
Or, to read the manifest from a file:

::

    packageOptions in (Compile, packageBin) +=  {
        val manifest = Using.fileInputStream( in => new java.util.jar.Manifest(in) )
        Package.JarManifest( manifest )
    }

.. howto::
   :id: name
   :title: Change the file name of a package

The ``artifactName`` setting controls the name of generated packages.  See the :doc:`/Detailed-Topics/Artifacts` page for details.

.. howto::
   :id: contents
   :title: Modify the contents of the package
   :type: setting
   
   mappings in (Compile, packageBin) <+=
      baseDirectory map { dir => ( dir / "example.txt") -> "out/example.txt" }

The contents of a package are defined by the ``mappings`` task, of type ``Seq[(File,String)]``.  The ``mappings`` task is a sequence of mappings from a file to include in the package to the path in the package.  See :doc:`/Detailed-Topics/Mapping-Files` for convenience functions for generating these mappings.  For example, to add the file ``in/example.txt`` to the main binary jar with the path "out/example.txt",

::

    mappings in (Compile, packageBin) <+= baseDirectory map { base =>
       (base / "in" / "example.txt") -> "out/example.txt"
    }

Note that ``mappings`` is scoped by the configuration and the specific package task.  For example, the mappings for the test source package are defined by the ``mappings in (Test, packageSrc)`` task.
