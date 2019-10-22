#!/bin/bash

source scripts/common/common.sh

# Check is source worked
echo "Common definitions loaded: $PUBLIC_KEY_FILENAME"

# hostname "sshd" as in docker-compose
write_scala_ssh_config sshd 22

mkdir ~/.ssh
ssh-keyscan -t ed25519 -p 22 sshd >>~/.ssh/known_hosts

sbt shell
