#!/usr/bin/env bash

set -e

SECRET_TYPE="secrets"
CONFIG_TYPE="configs"

# script to deploy config and secrets to TAP
scriptname="deploy_tap_config"
. `dirname $0`/appinfo.sh
. $scriptDir/logging.sh
. $scriptDir/utils.sh

usage="app_private_key=<priv-key-file-base64> tap_api_token=<tap-token> ./${scriptname}.sh <environment | $SUPPORTED_ENVIRONMENTS> <config-type | $SECRET_TYPE or $CONFIG_TYPE> <config-file-path> <binary-mode | true false"
# priv-key-file in pem format is already a base64 encoded file but with new lines in it.
# priv-key-file-base64 represents original pem file further base64 encoded to become a single line base64 encoded string
# priv-key-file-base64 filename needs to end with _base64.txt

echo " "
echo "Running: $0 $@"
echo " "

if [ -z "$app_private_key" ]; then
    log_usage_err "Missing app private key"
elif [[ ! "$app_private_key" =~ .*"_base64.txt"$ ]]; then
    log_usage_err "Invalid private Key File [without _base64.txt extension]: $app_private_key"
fi

if [ -z "$tap_api_token" ]; then
    log_usage_err "Missing tap api token"
fi

check_environment "$1"
envname=$1

if [ -z "$2" ]; then
    log_usage_err "Missing config type argument"
elif [[ "$2" != "$SECRET_TYPE"  && "$2" != "$CONFIG_TYPE" ]]; then
    log_usage_err "Invalid config type argument: $2"
fi
config_type="$2"

if [ -z "$3" ]; then
    log_usage_err "Missing config file argument"
elif [ ! -f "$3" ]; then
    log_usage_err "Nonexisting config file to deploy: $3"
elif [[ ! "$3" =~ .*"-${envname}.".*  && ! "$3" =~ .*"-${envname}"$ ]]; then
    # file environment check
    log_usage_err "config file [$3] doesn't belong to environment [$envname]"
fi
file_to_deploy="$3"
# check if file_to_deploy is under correct config type folder
if [ "$config_type" == "$SECRET_TYPE" ]; then
    if [[ ! "$file_to_deploy" =~ .*"/${SECRET_TYPE}/".* ]]; then
        log_usage_err "config file [$file_to_deploy] is not located under application $SECRET_TYPE folder [$config_type]"
    fi
elif [[ "$file_to_deploy" =~ .*"/${SECRET_TYPE}/".* ]]; then
    log_usage_err "file [$file_to_deploy] located under application $SECRET_TYPE folder should only be used for config-type=$SECRET_TYPE"
fi

binary_mode=false
if [ -z "$4" ]; then
    log_usage_err "Missing binary-mode argument"
elif [[ "$4" != "true"  && "$4" != "false" ]]; then
    log_usage_err "Invalid binary_mode argument value: $4"
elif [ "$4" == "true" ]; then
    log_msg "Enabling binary mode (sending as file - multipart form data)"
    binary_mode=true
fi

filename_prefix=$appname
tmp_filename_prefix=${appname}deploytmp

# extract the TAP deployment key name from deployment filename
input_filename_part=`echo "$file_to_deploy" | awk -F "/" '{print $NF}'`
input_file_name=`echo $input_filename_part | awk -F "." '{print $1}'`
input_file_ext=`echo $input_filename_part | awk -F "." '{print $2}'`
deployment_key_name=$input_file_name
if [ ! -z "$input_file_ext" ]; then
    deployment_key_name=${deployment_key_name}.${input_file_ext}
fi

deployment_key_name=${deployment_key_name/-$envname/} # remove environment extension from key name

tap_base_url="https://tapi-tagg.prod.platform.target.com/api"

if [ "$envname" == "$DEV_ENVIRONMENT" ]; then
    cluster_url="$tap_base_url/applications/${appname}/clusters/${appname}/dev"
elif [ "$envname" == "$STAGE_ENVIRONMENT" ]; then
    cluster_url="$tap_base_url/applications/${appname}/clusters/${appname}-stage/dev"
elif [ "$envname" == "$PROD_ENVIRONMENT" ]; then
    cluster_url="$tap_base_url/applications/${appname}/clusters/${appname}/prod"
else
    log_err "Unmapped TAP Environment for $envname"
fi

echo " "
log_msg "Deploying to cluster: $cluster_url"

# Read file contents as json for sending to TAP.
# Escaping double quotes, and escape newline with backslash newline chars
# input: configuration file name
# return: escaped data from file
function file_contents_json() {
    local __data_file=$1
    tempFile=/tmp/${appname}_tapdata.txt
    sed 's/"/\\"/g' $__data_file > $tempFile
    result=`cat $tempFile|perl -e 'while(<>) { $_ =~ s/[\r\n]/\\\n/g; print "$_" }'`
    rm -f $tempFile
    echo "$result" # return result to caller
}

# Call TAP endpoint to create/update configuration
function callTAP() {
    local __endpoint="$1"
    local __name="$2"
    local __value="$3"

    local __data='{"name":"'$__name'","value":"'$__value'"}'

    # first try to update, and if that fails with 404 then create it
    if [ "$binary_mode" == false ]; then
        log_msg "Sending update $__endpoint request to TAP: $cluster_url"
        echo " "
        tap_response=`curl -k -w 'httpcode:%{http_Code}\n' -XPUT --header "Token: $tap_api_token" --header "Content-Type: application/json" \
            ${cluster_url}/$__endpoint/$__name --data "$__data" 2>&1`
    else
        # sending file as multipart form data
        log_msg "Sending update $__endpoint request to TAP [binary mode]: $cluster_url"
        echo " "
        tap_response=`curl -k -w 'httpcode:%{http_Code}\n' -XPUT --header "Token: $tap_api_token" -F "${__name}=@${__value}" \
            ${cluster_url}/$__endpoint/$__name 2>&1`
    fi

    if [[ "$tap_response" =~ .*"httpcode:404".* ]]; then
        # resource doesn't exists, let's create it
        if [ "$binary_mode" == false ]; then
            log_msg "$__name not in TAP yet, Sending create $__endpoint request to TAP: $cluster_url"
            echo " "
            tap_response=`curl -k -w 'httpcode:%{http_Code}\n' -XPOST --header "Token: $tap_api_token" --header "Content-Type: application/json" \
                ${cluster_url}/$__endpoint --data "$__data" 2>&1`
        else
            # sending file as multipart form data
            log_msg "$__name not in TAP yet, Sending create $__endpoint request to TAP [binary mode]: $cluster_url"
            echo " "
            tap_response=`curl -k -w 'httpcode:%{http_Code}\n' -XPOST --header "Token: $tap_api_token" -F "${__name}=@${__value}" \
                ${cluster_url}/$__endpoint 2>&1`
        fi
        if [[ "$tap_response" =~ .*"httpcode:200".* ]]; then
            log_msg "Successfully created $__name [$__endpoint]"
        else
            log_err "Failed to create $__name [$__endpoint] [error: $tap_response]"
        fi
    elif [[ "$tap_response" =~ .*"httpcode:200".* ]]; then
        log_msg "Successfully updated $__name [$__endpoint]"
    else
        log_err "Failed to update $__name [$__endpoint] [error: $tap_response]"
    fi
}

if [ "$config_type" == "$SECRET_TYPE" ]; then
    echo " "
    log_msg "Deploying secret $file_to_deploy to env: ${envname}"

    # decrypt the secrets file
    # set decrypted data output file name (produced as a result of decrypt_secret_config.sh execution)
    decrypted_data_file=/tmp/${tmp_filename_prefix}_decrypted_data.txt

    DECRYPTED_DATA_FILE=$decrypted_data_file $scriptDir/decrypt_secret_config.sh "$file_to_deploy" "$envname" "$app_private_key"
    if [ $? -ne 0 ]; then
        log_usage_err "decrypted_data_file failed..."
    fi

    # prepare data for sending to TAP
    if [ "$binary_mode" == false ]; then
        # in regular non-binary mode, secret_value = decrypted_data_file contents as json
        secret_value=`file_contents_json "$decrypted_data_file"`
    else
        # binary mode, secret_value = decrypted filename
        secret_value="$decrypted_data_file"
    fi

    callTAP "secrets" "$deployment_key_name" "$secret_value"
else
    echo " "
    log_msg "Deploying config $file_to_deploy to env: ${envname}"

    # prepare data for sending to TAP
    if [ "$binary_mode" == false ]; then
        # in regular non-binary mode, config_value = file_to_deploy contents as json
        config_value=`file_contents_json "$file_to_deploy"`
    else
        # binary mode, config_value = file_to_deploy filename
        config_value="$file_to_deploy"
    fi

    callTAP "configs" "$deployment_key_name" "$config_value"
fi

echo " "
echo "=============================="
log_msg "Cleaning up temp files:"
echo "=============================="
rm -f /tmp/${tmp_filename_prefix}*

cd $currDir
