#!/bin/bash

source scripts/common/common.sh

# Check is source worked
echo "source works? $PRIVATE_KEY_FILENAME"

# there may exist files from previous run - remove them
rm -f id_ed25519 id_ed25519.pub

ssh-keygen -t ed25519 -f "$PRIVATE_KEY_FILENAME" -N "" -q
# docker-compose -f scripts/local/docker-compose.yml pull --include-deps sbt
docker-compose -f scripts/local/docker-compose.yml up -d sshd

docker cp "$PUBLIC_KEY_FILENAME" test_sshd:/root/.ssh/authorized_keys
docker exec test_sshd chown root:root /root/.ssh/authorized_keys

docker-compose -f scripts/local/docker-compose.yml run --service-ports sbt
