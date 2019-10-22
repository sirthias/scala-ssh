#!/bin/sh

rm -rf volumes/authorized_keys
rm -rf volumes/client

ssh-keygen -t ed25519 -f id_ed25519 -N "" -q

mkdir -p volumes/client
cp id_ed25519.pub volumes/
mv volumes/id_ed25519.pub volumes/authorized_keys
ls -al | grep volu
cp id_ed25519 volumes/client/

docker-compose run --service-ports sbt
