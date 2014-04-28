=============
Configuración
=============

Panorama general
================

Para crear un proyecto de sbt, necesitará dar los siguientes pasos:
-  Instalar sbt y crear un script para iniciarlo.
-  Configurar un proyecto simple :doc:`hola mundo <Hello>`.

   -  Crear un directorio llamado project con archivos de código fuente en él.
   -  Crear su *build definition* (definición de construcción del proyecto).

-  Continuar con :doc:`ejecución <Running>` para aprender a ejecutar sbt.
-  Enseguida continuar con :doc:`.sbt build definition <Basic-Def>`
   para aprender más sobre las *build definitions*.

Instalando sbt
==============

sbt proporciiona varios paquetes para diferentes sistemas operativos, pero también puede hacer una `Instalación manual`_, `Manual Installation`_.

Paquetes oficialmente soportados:
  - MSI_ para Windows
  - Paquetes ZIP_ o TGZ_
  - Paquete RPM_ 
  - Paquete DEB_

.. note::

    Por favor reporte cualquier problema que se tenga con los paquetes arriba mencionados al projecto `sbt-launcher-package`_.

**Paquetes de terceros**:
  - :ref:`Homebrew <homebrew_setup>` o :ref:`Macports <macports_setup>` para `Mac`_
  - `Gentoo`_ emerge overlays

.. note::

   Los paquetes de terceros pueden no proporcionar la última versión disponible.
   Por favor asegúrese de reportar cualquier problema con estos paquetes a los mantenedores respectivos.

Mac
---

.. _macports_setup:

`Macports <http://macports.org/>`_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

    $ port install sbt

.. _homebrew_setup:

`Homebrew <http://mxcl.github.com/homebrew/>`_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

    $ brew install sbt

Gentoo
------

En el árbol oficial no hay ebuild para sbt. Pero existen ebuilds para
hacer un *merge* de sbt a partir de los binarios:
https://github.com/whiter4bbit/overlays/tree/master/dev-java/sbt-bin. Para
hacer un merge de sbt a partir de estos ebuilds, puede hacer lo siguiente:

.. code-block:: console

    $ mkdir -p /usr/local/portage && cd /usr/local/portage
    $ git clone git://github.com/whiter4bbit/overlays.git
    $ echo "PORTDIR_OVERLAY=$PORTDIR_OVERLAY /usr/local/portage/overlays" >> /etc/make.conf
    $ emerge sbt-bin

.. note::

   Por favor reporte cualquier problema con el ebuild `aquí <https://github.com/whiter4bbit/overlays/issues>`_.

.. _manual installation:

Instalación manual
------------------

La instalación manual requiere la descarga de `sbt-launch.jar`_ y la creación de un script para ejecutarlo.


Unix
~~~~

Ponga `sbt-launch.jar`_ en `~/bin`.

Cree un script para ejecutar el jar, mediante la creación de `~/bin/sbt` con el siguiente contenido:

.. code-block:: console

    SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"

Haga el script ejecutable con:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

Windows
~~~~~~~

La instalación manual para Windows varía según el tipo de terminal y dependiendo de si Cygwin es usado o no.
En todos los casos, ponga el archivo batch o el script en el *path* de modo que pueda iniciar `sbt`
en cualquier directorio mediante teclear `sbt` en la línea de comandos. También, ajuste los settings de la
JVM de acuerdo con su máquina si es necesario.

Para **usuarios que no utilizan Cygwin, pero que usan la terminal standard de Windows**, cree un archivo batch `sbt.bat`:

.. code-block:: console

    set SCRIPT_DIR=%~dp0
    java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M -jar "%SCRIPT_DIR%sbt-launch.jar" %*

y ponga el `sbt-launch.jar`_ que descargó en el mismo directorio que archivo batch.

Si utiliza **Cygwin con la terminal standard de Windows**, cree un script de bash `~/bin/sbt`: 

.. code-block:: console

    SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    java $SBT_OPTS -jar sbt-launch.jar "$@"

Reemplace `sbt-launch.jar` con la ruta hasta el `sbt-launch.jar`_ que descargó y recuerde utilizar `cygpath` si es necesario.
Haga el scrip ejecutable con:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

Si utiliza **Cygwin con una terminal Ansi** (que soporte secuentas de escape Ansi y que sea configurable mediante `stty`), cree un script `~/bin/sbt`:

.. code-block:: console

    SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
    stty -icanon min 1 -echo > /dev/null 2>&1
    java -Djline.terminal=jline.UnixTerminal -Dsbt.cygwin=true $SBT_OPTS -jar sbt-launch.jar "$@"
    stty icanon echo > /dev/null 2>&1

Reemplace `sbt-launch.jar` con la ruta hasta el `sbt-launch.jar`_ que descargó y recuerde utilizar `cygpath` si es necesario.
Entonces, haga que el script sea ejecutable con:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

Para que la tecla *backspace* funcione correctamente en la consola de scala, necesita asegurarse de que dicha tecla esté enviando el caracter de borrado, de acuerdo a la configuración de stty.
Para la terminal por default de cygwin (mintty) puede encontrar una configuración en Options -> Keys "Backspace sends ^H" que necesitará estar palomeada si su tecla de borrado envía el caracter por default de cygwin ^H.

.. note::

    Otras configuraciones no están actualmente soportadas. 
    Por favor envíe `pull requests <https://github.com/sbt/sbt/blob/0.13/CONTRIBUTING.md>`_ implementando o describiendo dicho soporte.

Tips y notas
==============

Si tiene algún problema ejecutando sbt, vea :doc:`/Detailed-Topics/Setup-Notes` en las codificaciones de la
terminal, HTTP proxies, y opciones de la JVM.

Siguiente
=========

Continúe :doc:`creando un projecto simple <Hello>`.

