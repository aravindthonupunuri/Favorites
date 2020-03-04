#!/usr/bin/env bash

function check_environment() {
    local env_name="$1"
    if [ -z "$env_name" ]; then
        log_usage_err "Missing deployment environment name"
    elif [[ ! "$SUPPORTED_ENVIRONMENTS" =~ .*"${env_name} ".* && ! "$SUPPORTED_ENVIRONMENTS" =~ .*"${env_name}"$ ]]; then
        log_usage_err "Invalid deployment environment name $env_name"
    fi
}
