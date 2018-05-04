#!/usr/bin/env bash

#
# This script will upload an sbt distribution (tar/tgz/msi and
# checksump files) to IBM Cloud Object Storage with the correct
# permissions, and prepare the shortened URLs on the "piccolo.link"
# Polr server.
#

#
# Required env vars:
#
# S3KEY -- API Key for the sbt-distribution-archive bucket
# S3SECRET -- Secret corresponding to the API Key
#
# POLR_USERNAME  -- Username for the polr shortener
# POLR_PASSWORD  -- Password for the polr shortener
#

S3KEY='...'
S3SECRET='...'
POLR_USERNAME='...'
POLR_PASSWORD='...'

# Where to find the above information:
# - for S3KEY/S3SECRET, you need the API credentials for the
# 'sbt-downloads' buckets on IBM's COS. Please ask @cunei for
# details.
#
# Once you have S3KEY/S3SECRET, you can (and should) use those
# values also in your favorite S3 GUI browser (like CyberDuck
# or DragonDisk), in order to verify that the files are correctly
# uploaded, or to perform maintenance within the storage bucket.
#
# Note: do *not* use an S3 GUI to upload files, since that will set
# the permissions incorrectly, and the CDN will be unable to access
# the files.
#
# - for POLR_USERNAME/POLR_PASSWORD, you will need the credentials
# on "piccolo.link" for the user "lightbend-tools". Ask @cunei
# for details. Note: do not create a personal account to generate
# the short URLs: they are linked to the user that creates them
# and they would not appear in the "lightbend-tools" dashboard.
#

# Once you have set correctly the four vars above, please call
# this script as follows:
#
# If your files have this directory structure:
#
# here
# here/v0.13.15
# here/v0.13.15/sbt-0.13.15.msi
# here/v0.13.15/sbt-0.13.15.zip.asc
# here/v0.13.15/sbt-0.13.15.tgz.asc
# here/v0.13.15/sbt-0.13.15.tgz
# here/v0.13.15/sbt-0.13.15.zip
# here/v0.13.15/sbt-0.13.15.msi.asc
#
#
# then "cd here", then:
#
# $ .../sbt-upload.sh v0.13.15 [v0.13.16 ...]
#
# Where one or more directories are specified. Each directory name
# must be the letter 'v' plus a release version. The files within
# each directory can be named arbitrarily, and live in any
# subdirectory.
#
# Please note that the script will try to store temporary cookies
# and other info in a directory called "cookies", which will be
# created in the same directory as this script.
#
#--------------------------------------------------------------
#
set -o pipefail

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

usageAndExit() {
  echo 'Usage: "'${0##*/}' <RELEASEDIR> [<RELEASEDIR> ...]"'
  echo "where the final path component of each RELEASEDIR"
  echo "must be 'v' plus"' a release number, for example: "v0.13.15".'
  echo 'This script will leave some cookie files in a directory'
  echo 'called "cookies", in the same location as the script.'
  exit 1
}


if [[ "$#" < 1 ]]
then
  usageAndExit
fi
RELEASE_DIRS=("${@:1}")
for RELEASE_DIR in "${RELEASE_DIRS[@]}"
do
  RELEASE_TAG="$(basename "$RELEASE_DIR")"
  if [[ "${RELEASE_TAG:0:1}" != 'v' || ! -d "${RELEASE_DIR}" ]]
  then
    usageAndExit
  fi
done


POLR_DOMAIN_NAME="${POLR_DOMAIN_NAME:-piccolo.link}"
POLR_URL="https://$POLR_DOMAIN_NAME"
POLR_LOGIN_URL="$POLR_URL/login"
POLR_SHORTEN_URL="$POLR_URL/shorten"

# Let's try to login into Polr

# Prepare a work directory to store the cookies into
COOKIEDIR="${SCRIPTDIR}/cookies"
mkdir -p "${COOKIEDIR}"
HOME_COOKIES_PATH="${COOKIEDIR}/home-cookies"
LOGIN_COOKIES_PATH="${COOKIEDIR}/login-cookies"
CSRF_TOKEN_PATH="${COOKIEDIR}/csrf-token"

# Access home page to get tokens.
RESPONSE="$(
  curl -si "$POLR_URL" \
    --cookie-jar "$HOME_COOKIES_PATH"
)"
CODE="$(echo "$RESPONSE" | head -1 | awk '{print $2}')"
if [ -z "$CODE" -o "$CODE" -ne 200 ]
then
  echo "ERROR: Could not load $POLR_URL; aborting."
  echo -e "Response:\n$RESPONSE"
  exit 1
fi

TOKEN="$(echo "$RESPONSE" | grep "_token" | \
  sed -E "s/.+value='([a-zA-Z0-9]+)'.+/\1/")"
if [ -z "$TOKEN" ]
then
  echo "ERROR: Could not find auth token; aborting."
  exit 1
fi

# Save CSRF token.
CSRF_TOKEN="$(echo "$RESPONSE" | grep "csrf-token" | \
  sed -E 's/.+content="([a-zA-Z0-9]+)".+/\1/')"
if [ -z "$CSRF_TOKEN" ]
then
  echo "ERROR: Could not find CSRF token; aborting."
  exit 1
fi

echo "$CSRF_TOKEN" > "$CSRF_TOKEN_PATH"

# Login and save cookie.
RESPONSE="$(
  curl -siX POST "$POLR_LOGIN_URL" \
    --cookie "$HOME_COOKIES_PATH" \
    --cookie-jar "$LOGIN_COOKIES_PATH" \
    -d "username=$POLR_USERNAME" \
    -d "password=$POLR_PASSWORD" \
    -d "_token=$TOKEN" \
    -d "login='Sign In'"
)"
CODE="$(echo "$RESPONSE" | head -1 | awk '{print $2}')"
if [ -z "$CODE" -o "$CODE" -ne 302 ]
then
  echo "ERROR: Login failed; aborting."
  echo -e "Response:\n$RESPONSE"
  exit 1
fi

#
# OK! Now, let's process the files
#

#
# Call this upload routine with:
# 1) relative path to the file from current dir. Do NOT use '..' or '.' in this path
# for example: uploadS3 "v1.1.4/sbt-1.1.4.tgz"
# assuming the file is in <current dir>/v1.1.4/sbt-1.1.4.tgz
#
# The file will be placed in the appropriate S3 bucket, and will be visible
# as: https://sbt-downloads.cdnedge.bluemix.net/releases/v1.1.4/sbt-1.1.4.tgz
#
uploadS3() {
  S3FILEPATH="$1"
  if [[ -z "$S3FILEPATH" ]]
  then
    echo "Internal error! Please report."
    return 1
  fi
  S3BUCKET="sbt-distribution-archives"
  S3HOST="s3-api.us-geo.objectstorage.softlayer.net"
  S3RELATIVEPATH="/${S3BUCKET}/releases/${S3FILEPATH}"
  S3CONTENTTYPE="$(file --brief --mime-type "$S3FILEPATH")"
  S3NOWDATE="$(date -R)"
  S3STRINGTOSIGN="PUT\n\n${S3CONTENTTYPE}\n${S3NOWDATE}\nx-amz-acl:public-read\n${S3RELATIVEPATH}"
  S3SIGNATURE="$(echo -en ${S3STRINGTOSIGN} | openssl sha1 -hmac ${S3SECRET} -binary | base64)"
  echo "Uploading $S3FILEPATH"
  curl -X PUT -T "${S3FILEPATH}" \
    -H "Host: ${S3HOST}" \
    -H "Date: ${S3NOWDATE}" \
    -H "Content-Type: ${S3CONTENTTYPE}" \
    -H "x-amz-acl: public-read" \
    -H "Authorization: AWS ${S3KEY}:${S3SIGNATURE}" \
    "https://${S3HOST}${S3RELATIVEPATH}"
  if [ $? -ne 0 ]
  then
    echo "Sorry, could not upload this file."
    return 1
  fi
}

shorten() {
  # Arguments
  URL="$1"
  ENDING="$2"
  TAGS=("${@:3}")
  
#  echo "Short requested: ${ENDING} -- tags: ${TAGS[@]}"
# Retrieve the CSRF token
# CSRF_TOKEN="$(cat "$CSRF_TOKEN_PATH")"
  
  TAG_LIST=""
  if [ "${#TAGS[@]}" -gt 0 ]; then
    for TAG in "${TAGS[@]}"; do # tag_list%5B%5D=$TAG
      TAG_LIST="$TAG_LIST -d tag_list[]=$TAG"
    done
  fi

  RESPONSE="$(
  curl -siX POST "$POLR_SHORTEN_URL" \
    --cookie "$LOGIN_COOKIES_PATH" \
    -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
    --data-urlencode "link-url=$URL" \
    -d "custom-ending=$ENDING" \
    $TAG_LIST
  )"
  
  CODE="$(echo "$RESPONSE" | head -n 1 | awk '{print $2}')"
  if [ "$CODE" -ne 200 ]
  then
    echo "Could not shorten to: '$ENDING', creating a different link."
    return 1
  fi
  
  SHORT_URL="$(
    echo "$RESPONSE" | \
      egrep "value='http[s]?://$POLR_DOMAIN_NAME/" | \
      sed -E "s@.+value='(http[s]?://$POLR_DOMAIN_NAME/[-_\.0-9a-zA-Z]+)'.+@\1@"
  )"
  
  echo "--> $SHORT_URL" | sed 's@http://@https://@'
}

for RELEASE_DIR in "${RELEASE_DIRS[@]}"
do
  RELEASE_TAG="$(basename "$RELEASE_DIR")"
  cd "$(dirname "$RELEASE_DIR")"
  find "$RELEASE_TAG" -type f -print | while read
  do
    echo
  	uploadS3 "$REPLY"
    if [ $? -eq 0 ]
    then
      URL="https://sbt-downloads.cdnedge.bluemix.net/releases/${REPLY}"
      echo "--> $URL"
      #
      # let's calculate the Polr tags.
      #
      # In Polr the tags can only be combined with 'or', therefore
      # we need to subdivide them according to the combinations that
      # might be needed.
      #
      # We add these tags:
      # - if it is an 'asc' checksum file, "${TAG}_asc", "asc"
      # - if it is a tar/tgz/tar.gz/txz/tar.xz/zip/msi/deb/rpm/dmg, "${TAG}_bin", "bin"
      # - if it is another file, "${TAG}_other", "other"
      # In any case, we add "${TAG}"
      #
      # so that:
      # 1) "${TAG}_asc" + "${TAG}_bin" + "${TAG}_other" are the statistics
      # for the entire "${TAG}" release
      # 2) "bin" are all of the archives downloads
      # 3) "bin" + "asc" + "other" are all of the downloads
      #
      if [[ "${REPLY}" == *.asc ]]
      then
        KIND="asc"
      elif [[ "${REPLY}" == *.tar || \
        "${REPLY}" == *.tgz || \
        "${REPLY}" == *.tar.gz || \
        "${REPLY}" == *.txz || \
        "${REPLY}" == *.tar.xz || \
        "${REPLY}" == *.zip || \
        "${REPLY}" == *.msi || \
        "${REPLY}" == *.deb || \
        "${REPLY}" == *.rpm || \
        "${REPLY}" == *.dmg ]]
      then
        KIND="bin"
      else
        KIND="other"
      fi
      # First we try to shorten using just the file name.
      # If that fails, we use an encoding of the entire path.
      SHORT1="$(basename "$REPLY")"
      shorten "$URL" "$SHORT1" "${RELEASE_TAG}_${KIND}" "${RELEASE_TAG}" "${KIND}"
      if [[ $? != 0 ]]
      then
        SHORT2="${REPLY//\//_}"
        shorten "$URL" "$SHORT2" "${RELEASE_TAG}_${KIND}" "${RELEASE_TAG}" "${KIND}"
        if [[ $? != 0 ]]
        then
          echo "Sorry, could not create a short link."
        fi
      fi
    fi
  done
done
