#!/bin/bash

# echo $PGP_SECRET | base64 --decode | gpg --import --batch --yes --pinentry-mode loopback --passphrase $PGP_PASSPHRASE
echo $PGP_SECRET | base64 --decode | gpg --batch --import
