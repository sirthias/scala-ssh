#!/bin/bash

# enable job control
set -m

PRIVATE_KEY_FILENAME="id_ed25519"
PUBLIC_KEY_FILENAME="id_ed25519.pub"
DOCKER_IMAGE_NAME="rastasheep/ubuntu-sshd:16.04"

function write_scala_ssh_config() {
  local SSHD_HOST="$1"
  local SSHD_PORT="$2"

  mkdir -p ~/.scala-ssh
  echo $SSHD_HOST > ~/.scala-ssh/.testhost
  FULLPATH=`realpath $PRIVATE_KEY_FILENAME`

  cat <<EOF >  ~/.scala-ssh/$SSHD_HOST
login-type  = keyfile
username    = root
keyfile     = $FULLPATH
port        = $SSHD_PORT
EOF
}
