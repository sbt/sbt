=========================
Estructura de directorios
=========================

Esta página asume que usted ha :doc:`instalado sbt <Setup>` y ha visto el
ejemplo :doc:`Hello, World <Hello>`.

Directorio base
---------------

En la terminología de sbt, el "directorio base" es el directorio que contiene al proyecto.
De modo que si usted creó el proyecto `hello` que contiene `hello/build.sbt` y  `hello/hw.scala`
como se indicó en el ejemplo :doc:`Hello, World <Hello>`, `hello` es su directorio base.

Código fuente
-------------

El código fu8ente puede ponerse en el directorio base del proyecto como en el caso de
`hello/hw.scala`. Sin embargo, la mayoría de las personas no hacen esto para proyectos reales;
se traduce en mucho desorden.

sbt utiliza la misma estructura de directorios que
`Maven <http://maven.apache.org/>`_ para el código fuente por default (todos las rutas son relativas
al directorio base):

.. code-block:: text

      src/
        main/
          resources/
             <archivos que se incluyen en el jar principal van aquí>
          scala/
             <código fuente de Scala de main>
          java/
             <código fuente de Java de main>
        test/
          resources
             <archivos que se incluyen en el jar de test van aquí>
          scala/
             <código fuente de Scala para test>
          java/
             <código fuente de Java para test>


Otros directorios en `src/` serán ignorados. Adicionalmente, todos los directorios ocultos serán
ignorados.

Archivos de definición de la construcción de sbt (sbt build definition files)
-----------------------------------------------------------------------------

Ya ha visto `build.sbt` en el directorio base del proyecto. Otros
archivos sbt aparecen en el subdirectorio `project`.

El subdirectorio `project` puede contener archivos `.scala`, que se combinan
con los archivos `.sbt` para formar la definición completa de la construcción.

Vea :doc:`.scala build definitions <Full-Def>` para más información.

.. code-block:: text

      build.sbt
      project/
        Build.scala

Tal vez pueda ver archivos `.sbt` dentro de `project/` pero no son equivalentes
a archivos `.sbt` en el directorio base del proyecto. La explicación de esto
`viene después <Full-Def>`, dado que necesitará algo de antecedentes primero.

Productos de la construcción
----------------------------

Los archivos generados (clases compiladas, paquetes en jars, archivos gestionados (*managed files*), caches,
y documentación) será escrita al directorio `target` por default.

Configurando el sistema de control de versiones
-----------------------------------------------

Su archivo `.gitignore` (o el equivalente para otro sistema de control de versiones)
debe contener:

.. code-block:: text

      target/

Note que el texto anterior tiene una `/` de forma deliberada (para que únicamente los directorios
sean seleccionados) y de manera deliberada no tiene una `/` al inicio (para que el directorio
`project/target/` también sea seleccionado, además de simplemente el directorio `target/`).

A continuación
==============

Aprenda sobre :doc:`cómo ejecutar sbt <Running>`. 
