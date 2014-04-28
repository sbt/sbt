============
Hello, World
============

Esta página asume que usted ha :doc:`instalado sbt <Setup>`.

Cree un directorio project con código fuente
--------------------------------------------

Un proyecto válido de sbt puede ser un directorio que contenga un único archivo de código fuente.
Intente crear un directorio `hello` con un archivo `hw.scala`, que contenga lo siguiente:

::

    object Hola {
      def main(args: Array[String]) = println("¡Hola!")
    }

Después, desde el directorio `hello`, inicie sbt y teclee `run` en la consola interactiva
de sbt. En Linux u OS X los comandos tal vez se vean de la siguiente manera:

.. code-block:: text

      $ mkdir hello
      $ cd hello
      $ echo 'object Hola { def main(args: Array[String]) = println("¡Hola!") }' > hw.scala
      $ sbt
      ...
      > run
      ...
      Hola!

En este caso, sbt funciona simplemente por convención. sbt encontrará lo siguiente de manera
automática:

-  Código fuente en el directorio base.
-  Código fuente en `src/main/scala` o `src/main/java`.
-  Pruebas en `src/test/scala` o `src/test/java`
-  Archivos de datos en `src/main/resources` o `src/test/resources`
-  jars en `lib`

Por default, sbt construirá proyectos con la misma versión de Scala utilizada para ejecutar
sbt en sí mismo.

Usted puede ejecutar el proyecto con `sbt run`o ingresar a la `REPL de Scala <http://www.scala-lang.org/node/2097>`_ con `sbt console`.
`sbt console` configura el classpath de su proyecto para que pueda probar ejemplos de Scala
basados en el código de su proyecto.

Build definition (Definición de la construcción)
------------------------------------------------

La mayoría de los proyectos necesitarán algo de configuración manual. La configuración básica de la construcción 
va en un archivo llamado `build.sbt`, localizado en el directorio base del proyecto.

Por ejemplo, si su proyecto está en el directorio `hello`, en
`hello/build.sbt` usted puede escribir:

.. parsed-literal::

    name := "hello"

    version := "1.0"

    scalaVersion := "|scalaRelease|"

Note la línea en blanco entre cada ítem. Esto no es simplemente porque sí;
se requieren las líneas en blanco para separar cada ítem. En :doc:`.sbt build definition <Basic-Def>` usted
aprenderá más sobre cómo escribir un archivo `build.sbt`.

Si usted planea empaquetar su proyecto en un jar, tal vez desee configurar al menos el nombre y la versión
en un archivo `build.sbt`.

Configurando la versión de sbt
------------------------------

Usted puede forzar una versión partivular de sbt al crear un archivo
`hello/project/build.properties`. En este archivo, escriba:

.. parsed-literal::

    sbt.version=\ |release|

para forzar el uso de sbt |release|. sbt es 99% compatible (con respecto al código fuente) de 
una *release* a otra.

Sin embargo, configurar la versión de sbt en `project/build.properties` evita
cualquier confusión potencial.

A continuación
==============

Aprenda sobre el :doc:`layout de archivos y directorios <Directories>` de un proyecto de sbt.
