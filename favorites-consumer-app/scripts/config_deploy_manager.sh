#!/usr/bin/env bash

# Manages the changes in application's config and secret configurations and deploy them to TAP

set -e

scriptname="config_deploy_manager"
. `dirname $0`/appinfo.sh
. $scriptDir/logging.sh
. $scriptDir/utils.sh

usage="./${scriptname}.sh <git-tag>"

echo " "
log_msg "Running: $0 $@"
echo " "

filename_prefix=$appname
tmp_filename_prefix=${appname}confmgtmp

if [ -z "$1" ]; then
    log_usage_err "Missing git-tag argument"
elif [[ ! "$1" =~ ^"conf.".* ]]; then
    # format expected: conf.<environment>.major.minor
    log_usage_err "Invalid git-tag argument"
fi
git_tag="$1"

if [ -z "$tap_api_token" ]; then
    log_usage_err "Missing tap api token"
fi

if [ -z "$app_private_key" ]; then
    log_usage_err "Missing app private key"
else
    # app_private_key is mounted as a base64 string within .drone.yml as a secret variable
    # we will receive app_private_key as a string rather than as a file
    # put app_private_key contents in a temporary file for passing to other scripts as a file with name ending in _base64.txt

    # first ensure we got a base64 encoded string as app_private_key, and not a private key file reference
    base64_test=`echo $app_private_key|openssl base64 -d -A`
    firstLine=`echo "${base64_test}" | head -1`

    if [ -z "$firstLine" ]; then
        log_usage_err "Invalid app private key, ensure you are not passign a file here [$app_private_key]"
    elif [[ ! "$firstLine" =~ .*"-----BEGIN RSA PRIVATE KEY-----".* ]]; then
        log_usage_err "Malformned app private key [$app_private_key]"
    fi

    app_private_key_file="/tmp/${tmp_filename_prefix}_privkey_base64.txt"
    echo "$app_private_key" > "$app_private_key_file"
fi

# decipher environment from TAG (format: conf.<environment>.major.minor)
envname=`echo "$git_tag"|cut -d'.' -f2`

# ensure env is valid
check_environment "$envname"

#### Secret Deployment section ####

# deploy secret.yml
app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" secrets "$secret_resources_location"/secret-${envname}.yml false

# deploy Base64 encoded client.truststore.jks
app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" secrets "$secret_resources_location"/client-truststore-base64-${envname}.jks true

# deploy Base64 encoded lists-bus.target ssl cert
app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" secrets "$secret_resources_location"/lists-bus-keystore-base64-${envname}.jks true


#### Configuration deployment section ####

# deploy application.yml
app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" configs "$resources_location"/application-${envname}.yml false

# deploy log4j2.properties
app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" configs "$resources_location"/log4j2-${envname}.properties false

# deploy Base64 encoded application-env-<region> files
if [ -f "$resources_location"/application-env-ttc-${envname}.yml ]; then
    app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" configs "$resources_location"/application-env-ttc-${envname}.yml true
fi
if [ -f "$resources_location"/application-env-tte-${envname}.yml ]; then
    app_private_key="$app_private_key_file" tap_api_token="$tap_api_token" $scriptDir/deploy_tap_config.sh "$envname" configs "$resources_location"/application-env-tte-${envname}.yml true
fi
