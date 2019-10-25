#!/bin/bash

source scripts/common/common.sh

# Check is source worked
echo "Common definitions loaded: $PUBLIC_KEY_FILENAME"

ssh-keygen -t ed25519 -f "$PRIVATE_KEY_FILENAME" -N "" -q

docker pull "$DOCKER_IMAGE_NAME"
docker run -d -P --name test_sshd "$DOCKER_IMAGE_NAME"

# Ensure that sshd started
RETRIES_LEFT=15
COMMAND_STATUS=1
until { [ $COMMAND_STATUS -eq 0 ] || [ $RETRIES_LEFT -eq 0 ]; }; do
  echo "checking if sshd is up: $RETRIES_LEFT"
  docker ps -a | grep test_sshd
  COMMAND_STATUS=$?
  sleep 2
  let RETRIES_LEFT=RETRIES_LEFT-1
done

docker cp "$PUBLIC_KEY_FILENAME" test_sshd:/root/.ssh/authorized_keys
docker exec test_sshd chown root:root /root/.ssh/authorized_keys

# returns e.g. 0.0.0.0:32875
SSHD_HOST_PORT=`docker port test_sshd 22`

# returns e.g. 32875, uses https://stackoverflow.com/a/3162500/429311
SSHD_PORT=${SSHD_HOST_PORT##*:}
echo "sshd ephemeral port detected: $SSHD_PORT"
write_scala_ssh_config "localhost" "$SSHD_PORT"

ssh-keyscan -t ed25519 -p "$SSHD_PORT" localhost >>~/.ssh/known_hosts

sbt test
