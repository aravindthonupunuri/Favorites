#!/usr/bin/env bash

#app details
currDir=`pwd`
scriptDir=`dirname $0`
if [[ "$scriptDir" =~ ^\..* ]]; then
   scriptDir="$currDir/$scriptDir"
fi
appname=favorites
gitorg=Lists-Modernization
gitrepo=favorites
resources_location=$scriptDir/../src/main/resources
secret_resources_location=$resources_location/secrets
data_folder=${scriptDir}/../../data

DEV_ENVIRONMENT="dev"
STAGE_ENVIRONMENT="stage"
PROD_ENVIRONMENT="prod"
SUPPORTED_ENVIRONMENTS="$DEV_ENVIRONMENT $STAGE_ENVIRONMENT $PROD_ENVIRONMENT"

# validate appinfo contains correct git repo for this project
if [[ "$scriptDir" != *\/"$gitrepo"\/* ]]; then
    echo "[$scriptname] appinfo.sh gitrepo \"$gitrepo\" doesn't belong to project for script $scriptDir/$scriptname.sh"
    exit 1
fi
