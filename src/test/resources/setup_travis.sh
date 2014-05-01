#!/bin/sh

# Install OpenSSH
sudo apt-get update -qq
sudo apt-get install -qq libssh2-1-dev openssh-client openssh-server
sudo start ssh

# Generate and Register keys
ssh-keygen -t rsa -f ~/.ssh/id_rsa -N "" -q
cat ~/.ssh/id_rsa.pub >>~/.ssh/authorized_keys
ssh-keyscan -t rsa localhost >>~/.ssh/known_hosts

# Create files for unit test
mkdir ~/.scala-ssh
echo localhost > ~/.scala-ssh/.testhost
cat <<EOF >  ~/.scala-ssh/localhost
login-type  = keyfile
username    = $USER
keyfile     = ~/.ssh/id_rsa
EOF
