#!/usr/bin/env bash

set -e

# decrypts an encrypted data file located under app's secret resources location folder
scriptname="decrypt_secret_config"
. `dirname $0`/appinfo.sh
. $scriptDir/logging.sh
. $scriptDir/utils.sh

usage="./${scriptname}.sh <input-file-name-with-path> <environment | $SUPPORTED_ENVIRONMENTS> <priv-key-file-base64 or gardenia> <optional debug>"
# priv-key-file in pem format is already a base64 encoded file but with new lines in it.
# priv-key-file-base64 represents original pem file further base64 encoded to become a single line base64 encoded string
# priv-key-file-base64 filename needs to end with _base64.txt


# Encryption Process
# ==================
# We have a public-private key pair per application => app-public-key, app-private-key
#
# Since public/private keys can NOT be used to encrypt/decrypt large files, we do not use app key pair to encrypt
# file data.
#
# Instead we encrypt input file data using a spontaneously generated random symmetric key (random-sym-key) => encrypted-file-data
# We use app-public-key to encrypt random-sym-key => encrypted-random-sym-key.
# We concatenate encrypted-file-data and encrypted-random-sym-key, using a space between them as delimiter => encrypted-output-file.
# This encrypted-output-file can be stored safely in git.
#
# Decryption Process
# ==================
# Read encrypted-output-file
# Separate its contents using space delimiter between them => encrypted-file-data and encrypted-random-sym-key
# Using app-private-key, decrypt encrypted-random-sym-key => decrypted-random-sym-key
# Using decrypted-random-sym-key, decrypt the encrypted-file-data => decrypted-file-data

echo " "
log_msg "Running: $0 $@"
echo " "

filename_prefix=$appname
tmp_filename_prefix=${appname}decrypttmp

if [ -z "$1" ]; then
    log_usage_err "Missing input file name argument"
fi
file_to_decrypt="$1"
if [ ! -f "$file_to_decrypt" ]; then
    log_usage_err "Nonexisting input file name $file_to_decrypt"
fi

check_environment "$2"
envname=$2

privkey_file=/tmp/${tmp_filename_prefix}_privkey.pem
use_gardenia=false
if [ -z "$3" ]; then
    log_usage_err "Missing private key file argument"
elif [ "$3" == "gardenia" ]; then
    use_gardenia=true
elif [[ ! "$3" =~ .*"_base64.txt"$ ]]; then
    log_usage_err "Invalid private Key File [without _base64.txt extension]: $3"
elif [ ! -f $3  ]; then
    log_usage_err "Nonexisting private key file: $privkey_file"
else
    # original privkey pem file was base64 encoded
    # decode privkey base64 encoded file to extract original privkey_file pem file
    cat "$3"|openssl base64 -d -A > $privkey_file
fi

debug=false
if [ ! -z "$4" ]; then
    if [ "$4" == "debug" ]; then
        echo " "
        log_msg "*** WARN: Enabling debug output of decrypted data"
        echo " "
        debug=true
    else
        log_usage_err "Invalid debug argument"
    fi
fi

if [ "$use_gardenia" == true ]; then
    # read private key from gardenia
    privkey_name=${appname}-privkey-${envname}
    log_msg "Reading private key ${gitorg}/${gitrepo}/${privkey_name} from gardenia"
    privkey_file_base64_data=`gardenia read repo "$gitorg"/"$gitrepo" $privkey_name 2>&1`

    value_line=`grep 'value:' <<< "$privkey_file_base64_data"`

    privkey_base64=`echo ${value_line//value: /@}`
    privkey_base64=`echo $privkey_base64|cut -d'@' -f2`
    privkey_base64=`echo $privkey_base64|cut -d'"' -f1`

    echo $privkey_base64|openssl base64 -d -A > $privkey_file
fi

cd $scriptDir

# decrypted data output file
decrypted_data_file=/tmp/${filename_prefix}_decrypted_data.txt
if [ ! -z "$DECRYPTED_DATA_FILE" ]; then
    log_msg "Externally provided Decrypted Data Output file: $DECRYPTED_DATA_FILE"
    decrypted_data_file=$DECRYPTED_DATA_FILE
fi

# read input data file, and separate its contents into into encrypted_data and encrypted_random_key (using space delimiter)
log_msg "Reading input data file: $file_to_decrypt"
file_data=`cat $file_to_decrypt`
encrypted_data=`echo $file_data|cut -d' ' -f1`
encrypted_random_key=`echo $file_data|cut -d' ' -f2`

# decrypt the encrypted_random_key
echo " "
log_msg "Decrypting encrypted_random_key"
decrypted_sym_key_file=/tmp/${tmp_filename_prefix}_decrypted_random_key.bin
echo $encrypted_random_key|openssl base64 -d -A > /tmp/${tmp_filename_prefix}_encrypted_random_key_enc.bin
openssl rsautl -decrypt -inkey $privkey_file -in /tmp/${tmp_filename_prefix}_encrypted_random_key_enc.bin -out $decrypted_sym_key_file

# decrypt the encrypted_data using decrypted_sym_key
echo " "
log_msg "Decrypting encrypted_data to: $decrypted_data_file"
encrypted_data_file=/tmp/${tmp_filename_prefix}_enc_data.txt
echo $encrypted_data > $encrypted_data_file

# Openssl can base64 decode and decrypt in the same step with the -a or -base64 switch.
# But there is a bug in openssl's base64 processing, it expects a newline at the end of the base64 encoded data.
# This causes issues when the secret data input file is long (longer than 735 chars)
# Therefore decode the base64 first separately i.e. don't use -base64 flag with openssl decode (enc -d) command
echo -n $encrypted_data| openssl base64 -d -A | openssl enc -d -aes-256-cbc -out $decrypted_data_file -pass file:$decrypted_sym_key_file

if [ "$debug" == true ]; then
    echo " "
    echo "=============================="
    log_msg "Decrypted data:"
    echo "=============================="
    cat $decrypted_data_file
fi

echo " "
echo "=============================="
log_msg "Cleaning up temp files:"
echo "=============================="
rm -f /tmp/${tmp_filename_prefix}*

cd $currDir
