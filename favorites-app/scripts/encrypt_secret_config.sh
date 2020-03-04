#!/usr/bin/env bash

set -e

# encrypts input text file data (input file name shouldn't have any environment extension in it)
# output filename is derived from input data filename extended with environment
# e.g. input file name: secret.yml => output file name: secret-dev.yml for dev environment
scriptname="encrypt_secret_config"
. `dirname $0`/appinfo.sh
. $scriptDir/logging.sh
. $scriptDir/utils.sh

usage="./${scriptname}.sh <input-data-file-with-path> <environment | $SUPPORTED_ENVIRONMENTS>"

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

# Decryption Process
# ==================
# Read encrypted-output-file
# Separate its contents using space delimiter between them => encrypted-file-data and encrypted-random-sym-key
# Using app-private-key, decrypt encrypted-random-sym-key => decrypted-random-sym-key
# Using decrypted-random-sym-key, decrypt the encrypted-file-data => decrypted-file-data
#

echo " "
echo "Running: $0 $@"
echo " "

if [ -z "$1" ]; then
    log_usage_err "Missing input data file argument"
fi
file_to_encrypt=$1
if [ ! -f $file_to_encrypt ]; then
    log_usage_err "Nonexisting input data file: $file_to_encrypt"
fi

check_environment "$2"
envname=$2

pubkey_file="$data_folder"/${appname}_publickey_${envname}.pem
if [ ! -f $pubkey_file ]; then
    log_err "Missing public key file: $pubkey_file"
fi

cd $scriptDir

filename_prefix=${appname}file
tmp_filename_prefix=${appname}encrypttmp

# encrypted data output file

# encrypt the secret-config data file
# extract just the file name out of file_to_encrypt
input_filename_part=`echo $file_to_encrypt | awk -F "/" '{print $NF}'`
input_file_name=`echo $input_filename_part | awk -F "." '{print $1}'`
input_file_ext=`echo $input_filename_part | awk -F "." '{print $2}'`

# define the name of output file using input_file_name with path under secret resources_location
encrypted_data_outfile=${secret_resources_location}/$input_file_name-${envname}
if [ ! -z "$input_file_ext" ]; then
    encrypted_data_outfile=${encrypted_data_outfile}.${input_file_ext}
fi

# remove existing encrypted_data_outfile file
echo " "
log_msg "Removing existing file: $encrypted_data_outfile"
rm -f $encrypted_data_outfile

# generate a random private key
random_sym_key_file=/tmp/${filename_prefix}_random_sym_key.bin

echo " "
log_msg "Generating random symmetric key [file: $random_sym_key_file] for encrypting input file: $file_to_encrypt"
openssl rand -base64 32 -out $random_sym_key_file

# Encrypt the input data file using random symmetric key
echo " "
log_msg "Encrypting data file to output file: $encrypted_data_outfile"
openssl enc -base64 -A -aes-256-cbc -salt -in $file_to_encrypt -out $encrypted_data_outfile -pass file:$random_sym_key_file

# Encrypt the symmetric key
echo " "
log_msg "Encrypting random symmetric key (output: base64)"
encrypted_random_sym_key=`openssl rsautl -encrypt -inkey $pubkey_file -pubin -in $random_sym_key_file | openssl base64 | tr -d '\n'`

# append encrypted_random_sym_key to output file
echo " "
log_msg "Appending random symmetric key to output file: $encrypted_data_outfile"
echo " $encrypted_random_sym_key" >> $encrypted_data_outfile

# delete unencrypted  random private key file
echo " "
log_msg "Deleting unencrypted random symmetric key file: $random_sym_key_file"
rm -f $random_sym_key_file

echo " "
echo "=========================================================================================="
log_msg "Output file (Base64): $encrypted_data_outfile"
echo "=========================================================================================="
echo " "
cat $encrypted_data_outfile

echo " "

cd $currDir
