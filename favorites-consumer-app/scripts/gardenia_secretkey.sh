#!/usr/bin/env bash

set -e

# script to read/write app private key as gardenia secret
scriptname="gardenia_secretkey"
. `dirname $0`/appinfo.sh
. $scriptDir/logging.sh
. $scriptDir/utils.sh

usage="
./${scriptname}.sh <command | list>
./${scriptname}.sh <command | read> <environment | $SUPPORTED_ENVIRONMENTS>
./${scriptname}.sh <command | write> <environment | $SUPPORTED_ENVIRONMENTS> <priv-key-file-base64>
"
# priv-key-file in pem format is already a base64 encoded file but with new lines in it.
# priv-key-file-base64 represents original pem file further base64 encoded to become a single line base64 encoded string
# priv-key-file-base64 filename needs to end with _base64.txt

# gardenia docs: https://pages.git.target.com/drone/doc-site/02-advanced/secrets/#drone-secrets-from-vault

echo " "
echo "Running: $0 $@"
echo " "

if [ -z "$1" ]; then
    log_usage_err "Missing command argument"
elif [[ "$1" != "list" && "$1" != "read" && "$1" != "write" ]]; then
    log_usage_err "Invalid command $1"
fi

check_environment "$2"
envname=$2

if [ "$1" == "write" ]; then
    if [ -z "$3" ]; then
        log_usage_err "Missing private Key File"
    elif [ ! -f "$3" ]; then
        log_usage_err "Nonexisting private Key File: $3"
    elif [[ ! "$3" =~ .*"_base64.txt"$ ]]; then
        log_usage_err "Invalid private Key File [without _base64.txt extension]: $3"
    fi
    privkey_value=`cat $3`
fi

cd $scriptDir

echo " "
privkey_name=${appname}-privkey-${envname}
if [ "$1" == "list" ]; then
    log_msg "Listing private keys from gardenia"
    echo " "
    gardenia list repo $gitorg/$gitrepo
elif [ "$1" == "read" ]; then
    log_msg "Reading private key from gardenia"
    echo " "
    gardenia read repo $gitorg/$gitrepo $privkey_name
elif [ "$1" == "write" ]; then
    # warn if we are overwriting an existing private key
    set +e
    existing_key=`gardenia read repo $gitorg/$gitrepo ${privkey_name} 2>&1`
    set -e
    if [[ "$existing_key" != *"Failed to read secret. 404 Not Found"* ]]; then
        proceed=""
        while [[ "$proceed" != "Y" && "$proceed" != "y" && "$proceed" != "N" && "$proceed" != "n" ]]; do
            echo " "
            read -p "WARNING: $gitorg/$gitrepo/$privkey_name already exists, do you want to overwrite? [Y/N]: " proceed
            if [[ "$proceed" == "N" || "$proceed" == "n" ]]; then
                echo "Exiting without overwriting..."
                exit 0
            fi
        done
    fi
    log_msg "Writing private key to gardenia"
    echo " "
    echo "$value"
    gardenia write repo $gitorg/$gitrepo $privkey_name value=$privkey_value
fi
echo " "

cd $currDir
