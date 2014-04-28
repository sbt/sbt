=========
Ejecución
=========

Esta página describe cómo utilizar `sbt` una vez que usted a configurado su proyecto.
Se asume que usted ha :doc:`instalado sbt <Setup>` y que ha creado un proyecto
`Hello, World <Hello>` u otro proyecto.

Modo interactivo
----------------

Ejecute sbt en el directorio de su proyecto sin argumentos:

.. code-block:: console

    $ sbt

Ejecutar sbt sin ningún argumento en la línea de comandos, inicia sbt en modo interactivo.
El modo interactivo tiene una línea de comandos (¡con *tab completion* e historia!). 
    
Por ejemplo, usted puede teclear :key:`compile` en el prompt de sbt:

.. code-block:: console

    > compile

Para key:`compile` de nuevo, presione la tecla "arriba" y entonces enter.

Para ejecutar su programa nuevamente, teclee :key:`run`.

Para dejar el modo interactivo, teclee `exit` o utilice Ctrl+D (Unix) o Ctrl+Z
(Windows).

Modo Batch (por lotes)
----------------------

También puede ejecutar sbt en modo batch, especificando una lista separada por espacios
de comandos de sbt como argumentos. Para comandos de sbt que toman argumentos, pase el
comando y los argumentos como uno solo a `sbt` mediante encerrarlos entre comillas. Por
ejemplo:

.. code-block:: console

    $ sbt clean compile "testOnly TestA TestB"

En este ejemplo, la *key* :key:`testOnly` tiene argumentos, `TestA` y `TestB`.
Los comandos se ejecutarán en sequencia (:key:`clean`, :key:`compile`, y entonces
:key:`testOnly`).

Construcción y test continuos
-----------------------------

Para acelerar el ciclo de edición-compilación-prueba, puede pedir a sbt que
recompile automáticamente o que ejecute los tests siempre que se guarde un archivo de código fuente.

Puede conseguir que un comando se ejecute siempre que uno o más archivos de código fuente cambien
al agregar como prefijo `~`. Por ejemplo, en modo interactivo, intente:

.. code-block:: console

    > ~ compile

Presione enter para dejar de observar sus cambios.

Usted puede usar el prefijo `~` ya sea en modo interactivo o en modo *batch*.

Vea :doc:`/Detailed-Topics/Triggered-Execution` para más detalles.

Comandos comunes
----------------

Aquí encontrará algunos de los comandos de sbt más comunes. Para una lista más completa,
vea :doc:`/Detailed-Topics/Command-Line-Reference`.

-  :key:`clean` Borra todos los archivos generados (en el directorio :key:`target`).
-  :key:`compile` Compila los archivos de código fuente de main (en los directorios
   `src/main/scala` y `src/main/java`).
-  :key:`test` Compila y ejecuta todos los tests.
-  :key:`console` Inicia el interprete de Scala con un classpath que incluye el código
   fuente compilado y todas las dependencias. Para regresar a sbt, teclee
   `:quit`, Ctrl+D (Unix), o Ctrl+Z (Windows).
-  `run <argument>*` Ejecuta la clase principal para el proyecto en la misma máquina virtual que `sbt`.
-  :key:`package` crea un archivo jar que contiene los archivos en `src/main/resources` y las clases compiladas
   de `src/main/scala` y `src/main/java`.
-  `help <command>` Despliega ayuda detallada para el comando especificado.
   Si no se proporciona ningún comando, despliega una breve descripción de todos los comandos.
-  `reload` Recarga la definición de la construcción (los archivos `build.sbt`,
   `project/*.scala`, `project/*.sbt`). Este comando es necario si cambia la definición de la construcción. 

Tab completion
--------------

El modo interactivo tiene *tab completion*, incluyendo el caso cuando se tiene un prompt vacio. Una convención
especial de sbt es que presionar tab una vez puede mostrar únicamente un subconjunto de *completions* más probables, 
mientras que presionarlo más veces muestra opciones más verbosas.

Comandos de historia
--------------------

El modo interactivo recuerda la historia, incluso si usted sale de sbt y lo reinicia.
La manera más simple de acceder a la historia es con la tecla "arriba". También se soportan
los siguientes comandos:

-  `!` Muestra la ayuda para los comandos de historia.
-  `!!` Ejecuta el comando previo de nuevo.
-  `!:` Muestra todos los comandos previos.
-  `!:n` Muestra los n comandos previos.
-  `!n` Ejecuta el comando con índice `n`, como se indica con el comando `!:`
-  `!-n` Ejecuta el comando n-th previo a este.
-  `!cadena` Ejecuta el comando más reciente que comienza con 'cadena'
-  `!?cadena` Ejecuta el comando más reciente que contenga 'cadena'

En seguida
----------

Continúe :doc:`entendiendo build.sbt <Basic-Def>`.
