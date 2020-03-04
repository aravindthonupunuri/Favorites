#!/usr/bin/env bash

function log_msg() {
    echo "[$scriptname] $1"
}

# logs errmsg, usage and exits
function log_usage_err() {
    log_msg "Error: $1"
    log_msg "Usage: $usage"
    log_msg "Exiting..."
    exit 1
}

# logs errmsg and exits (no usage)
function log_err() {
    log_msg "Error: $1"
    log_msg "Exiting..."
    exit 1
}
