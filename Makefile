#!/usr/bin/make -f

# see bootstrapping_build/README.md

export JAVA_OPTS := -Xmx512M -Xms32M

# configuration variables
IVYJAR := /usr/share/java/ivy.jar
# Ivy can be downloaded from http://repo2.maven.org/maven2/org/apache/ivy/ivy/2.2.0/ivy-2.2.0.jar
SCALAC := fsc
JAVA := java
JAVAC := javac

# static variables
PWD := ${shell pwd}
BOOTSTRAPPINGDIR := $(PWD)/bootstrapping_build
IVY := ${JAVA} -jar $(IVYJAR) -settings $(BOOTSTRAPPINGDIR)/ivysettings.xml
TARGETBASE := $(BOOTSTRAPPINGDIR)/target
SRCMANAGED := $(BOOTSTRAPPINGDIR)/src_managed
BASEPREFIX := .
CLASSPATH-EXTERN := ${shell mkdir -p $(BOOTSTRAPPINGDIR); \
	                    $(IVY) -cachepath $(BOOTSTRAPPINGDIR)/ivy.classpath \
	                           -ivy $(BOOTSTRAPPINGDIR)/ivy.xml \
	                              2>&1  >$(BOOTSTRAPPINGDIR)/ivy.out; \
	                    cat $(BOOTSTRAPPINGDIR)/ivy.classpath}

# recursive (dynamic) variables
TARGETDIR = ${TARGETBASE}/$(subst /,_,$@)
BASE = ${BASEPREFIX}/$@

SCALAFILES = $(shell find ${BASE} -path $(BASE)/src/test -prune -o -readable -type f -name "*.scala" -print)
JAVAFILES = $(shell test ! -d $(BASE)/src/main/java || find $(BASE)/src/main/java -readable -type f -name "*.java")

CLASSPATH-INTERN = ${shell find $(TARGETBASE) -maxdepth 1 -mindepth 1 -type d -printf %p:}
CLASSPATH=$(CLASSPATH-INTERN):$(CLASSPATH-EXTERN):${TARGETDIR}
CLASSPATH-OPTION = -classpath $(CLASSPATH)

# Task definitions
MKTARGETDIR = mkdir -p ${TARGETDIR}
COMPILE-SCALA = $(if $(SCALAFILES),${SCALAC} -d ${TARGETDIR} ${CLASSPATH-OPTION} -sourcepath ${BASE} ${SCALAFILES})
COMPILE-JAVA = $(if $(JAVAFILES),$(JAVAC) -d $(TARGETDIR) $(CLASSPATH-OPTION) -sourcepath ${BASE} $(JAVAFILES))

define COMPILE
$(MKTARGETDIR)
$(COMPILE-JAVA)
$(COMPILE-SCALA)
endef

# Target lists
UTIL-TARGETS-SIMPLE := control io datatype collection complete process
UTIL-TARGETS-DEPON-INTERFACE := log classfile
UTIL-TARGETS := $(UTIL-TARGETS-SIMPLE) $(UTIL-TARGETS-DEPON-INTERFACE) classpath
TARGETS1 := launch/interface launch
TARGETS2 := run ivy compile cache
TARGETS := $(TARGETS1) $(TARGETS2) main

# Target recipes
_default: _compile-all _target2jar _invoke

_clean:
	rm -rf $(BOOTSTRAPPINGDIR)/full.jar
	rm -rf $(BOOTSTRAPPINGDIR)/target
	rm -rf $(BOOTSTRAPPINGDIR)/ivycache
	rm -rf $(BOOTSTRAPPINGDIR)/allclasses
	rm -rf $(BOOTSTRAPPINGDIR)/src_managed
	rm -rf $(BOOTSTRAPPINGDIR)/ivy.classpath
	rm -rf $(BOOTSTRAPPINGDIR)/ivy.out

_compile-all: $(UTIL-TARGETS-SIMPLE) interface $(UTIL-TARGETS-DEPON-INTERFACE) $(TARGETS1) classpath $(TARGETS2) testing tasks main

$(UTIL-TARGETS): BASEPREFIX:=util
$(UTIL-TARGETS) $(TARGETS):
	${COMPILE}

testing:
	$(MKTARGETDIR)
	$(SCALAC) -d $(TARGETDIR) $(CLASSPATH-OPTION) $(shell find testing -maxdepth 1 -name "*.scala")

tasks:
	$(MKTARGETDIR)
	$(SCALAC) -d $(TARGETDIR) $(CLASSPATH-OPTION) $(shell find tasks -maxdepth 2 -name "*.scala") tasks/src/test/scala/TaskGen.scala tasks/src/test/scala/checkResult.scala

interface:
	mkdir -p $(SRCMANAGED)
	scala ${CLASSPATH-OPTION} xsbt.datatype.GenerateDatatypes immutable xsbti.api $(SRCMANAGED) interface/definition interface/other interface/type
	$(MKTARGETDIR)
	$(JAVAC) -d $(TARGETDIR) $(CLASSPATH-OPTION) -sourcepath $(SRCMANAGED):interface/src/main/java \
		$$(find $(SRCMANAGED) -name "*.java") \
		$(shell find interface/src/main/java -name "*.java")

_target2jar:
	mkdir -p $(BOOTSTRAPPINGDIR)/allclasses
	find $(TARGETBASE) -mindepth 1 -maxdepth 1 -type d \
	  -exec sh -c "cp -al --target-directory=$(BOOTSTRAPPINGDIR)/allclasses {}/* || true" \;
#	cp -al $(BOOTSTRAPPINGDIR)/sbt.boot.properties $(BOOTSTRAPPINGDIR)/allclasses
	jar -cf $(BOOTSTRAPPINGDIR)/full.jar -C $(BOOTSTRAPPINGDIR)/allclasses .

_invoke:
	$(IVY) -cache $(BOOTSTRAPPINGDIR)/ivycache -ivy $(BOOTSTRAPPINGDIR)/ivy-runtime.xml
	scala ${CLASSPATH-OPTION}:$(BOOTSTRAPPINGDIR)/runtime-classpath \
	      -Divy.cache.dir=$(BOOTSTRAPPINGDIR)/ivycache \
	      xsbt.boot.Boot @$(BOOTSTRAPPINGDIR)/runtime-classpath/sbt.boot.properties

.PHONY: interface main testing tasks $(TARGETS) $(UTIL-TARGETS)