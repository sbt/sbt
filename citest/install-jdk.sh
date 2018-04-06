#!/usr/bin/env bash

#
# Install JDK for Linux
#
# This script determines the most recent early-access build number,
# downloads the JDK archive to the user home directory and extracts
# it there.
#
# Example usage
#
#   install-jdk.sh -D              | only gather and print variable values
#   install-jdk.sh                 | install most recent (early-access) OpenJDK
#   install-jdk.sh -W /usr/opt     | install most recent (early-access) OpenJDK to /usr/opt
#   install-jdk.sh -C              | install most recent (early-access) OpenJDK with linked system CA certificates
#   install-jdk.sh -F 9            | install most recent OpenJDK 9
#   install-jdk.sh -F 10           | install most recent OpenJDK 10
#   install-jdk.sh -F 11           | install most recent OpenJDK 11
#   install-jdk.sh -F 11 -L BCL    | install most recent Oracle JDK 11
#
# Options
#
#   -D   | Dry-run, only gather and print variable values
#   -F f | Feature number of the JDK release  [9|10|...|?]
#   -L l | License of the JDK                 [GPL|BCL]
#   -W w | Working directory and install path [${HOME}]
#   -C   | Use system CA certificates (currently only Debian/Ubuntu is supported)
#
# Exported environment variables
#
#   JAVA_HOME is set to the extracted JDK directory
#   PATH is prepended with ${JAVA_HOME}/bin
#
# (C) 2018 Christian Stein
#
# https://github.com/sormuras/bach/blob/master/install-jdk.sh
#
set -e

VERSION='2018-04-05'
DRY_RUN='0'
LINK_SYSTEM_CACERTS='0'

JDK_FEATURE='?'
JDK_BUILD='?'
JDK_LICENSE='GPL'
JDK_WORKSPACE=${HOME}
JDK_DOWNLOAD='https://download.java.net/java'
JDK_ORACLE='http://download.oracle.com/otn-pub/java/jdk'

echo "install-jdk.sh (${VERSION})"

#
# Parse command line options
#
while getopts ':F:L:W:CD' option; do
  case "${option}" in
    D) DRY_RUN='1';;
    C) LINK_SYSTEM_CACERTS='1';;
    F) JDK_FEATURE=${OPTARG};;
    L) JDK_LICENSE=${OPTARG};;
    W) JDK_WORKSPACE=${OPTARG};;
    :) echo "Option -${OPTARG} requires an argument."; exit 1;;
   \?) echo "Invalid option: -${OPTARG}"; exit 1;;
 esac
done

#
# Determine latest (early access or release candidate) number.
#
LATEST='11'
TMP=${LATEST}
while [ "${TMP}" != '99' ]
do
  CODE=$(curl -o /dev/null --silent --head --write-out %{http_code} http://jdk.java.net/${TMP})
  if [ "${CODE}" -ge '400' ]; then
    break
  fi
  LATEST=${TMP}
  TMP=$[${TMP} +1]
done

#
# Sanity checks.
#
if [ "${JDK_FEATURE}" == '?' ]; then
  JDK_FEATURE=${LATEST}
fi
if [ "${JDK_FEATURE}" -lt '9' ] || [ "${JDK_FEATURE}" -gt "${LATEST}" ]; then
  echo "Expected JDK_FEATURE number in range of [9..${LATEST}], but got: ${JDK_FEATURE}"
  exit 1
fi

#
# Determine URL...
#
JDK_URL=$(wget -qO- http://jdk.java.net/${JDK_FEATURE} | grep -Eo 'href[[:space:]]*=[[:space:]]*"[^\"]+"' | grep -Eo '(http|https)://[^"]+')
if [ "${JDK_FEATURE}" == "${LATEST}" ]; then
  JDK_URL=$(echo "${JDK_URL}" | grep -Eo "${JDK_DOWNLOAD}/.+/jdk${JDK_FEATURE}/.+/${JDK_LICENSE}/.*jdk-${JDK_FEATURE}.+linux-x64_bin.tar.gz$")
else
  JDK_URL=$(echo "${JDK_URL}" | grep -Eo "${JDK_DOWNLOAD}/.+/jdk${JDK_FEATURE}/.+/.*jdk-${JDK_FEATURE}.+linux-x64_bin.tar.gz$")
  if [ "${JDK_LICENSE}" == 'BCL' ]; then
    case "${JDK_FEATURE}" in
      9)  JDK_URL="${JDK_ORACLE}/9.0.4+11/c2514751926b4512b076cc82f959763f/jdk-9.0.4_linux-x64_bin.tar.gz";;
      10) JDK_URL="${JDK_ORACLE}/10+46/76eac37278c24557a3c4199677f19b62/jdk-10_linux-x64_bin.tar.gz";;
    esac
  fi
fi

#
# Inspect URL properties.
#
JDK_ARCHIVE=$(basename ${JDK_URL})
JDK_STATUS=$(curl -o /dev/null --silent --head --write-out %{http_code} ${JDK_URL})

#
# Print variables and exit if dry-run is active.
#
echo "  FEATURE = ${JDK_FEATURE}"
echo "  LICENSE = ${JDK_LICENSE}"
echo "  ARCHIVE = ${JDK_ARCHIVE}"
echo "      URL = ${JDK_URL} [${JDK_STATUS}]"
echo
if [ "${DRY_RUN}" == '1' ]; then
  exit 0
fi

#
# Create any missing intermediate paths, switch to workspace, download, unpack, switch back.
#
mkdir -p ${JDK_WORKSPACE}
cd ${JDK_WORKSPACE}
wget --continue --header "Cookie: oraclelicense=accept-securebackup-cookie" ${JDK_URL}
file ${JDK_ARCHIVE}
JDK_HOME=$(tar --list --auto-compress --file ${JDK_ARCHIVE} | head -1 | cut -f1 -d"/")
tar --extract --auto-compress --file ${JDK_ARCHIVE}
cd ${OLDPWD}

#
# Update environment variables.
#
export JAVA_HOME=${JDK_WORKSPACE}/${JDK_HOME}
export PATH=${JAVA_HOME}/bin:$PATH

#
# Link to system certificates.
#  - http://openjdk.java.net/jeps/319
#  - https://bugs.openjdk.java.net/browse/JDK-8196141
#
if [ "${LINK_SYSTEM_CACERTS}" == '1' ]; then
  mv "${JAVA_HOME}/lib/security/cacerts" "${JAVA_HOME}/lib/security/cacerts.jdk"
  # TODO: Support for other distros than Debian/Ubuntu could be provided
  ln -s /etc/ssl/certs/java/cacerts "${JAVA_HOME}/lib/security/cacerts"
fi

#
# Test-drive.
#
echo
java --version
echo

#
# Always print value of JAVA_HOME as last line.
# Usage from other scripts or shell: JAVA_HOME=$(./install-jdk.sh -L GPL | tail -n 1)
#
echo ${JAVA_HOME}
