#!/bin/sh

rm -rf volumes/authorized_keys
rm -rf volumes/client

ssh-keygen -t rsa -f id_rsa -N "" -q

mkdir -p volumes/client
cp id_rsa.pub volumes/
mv volumes/id_rsa.pub volumes/authorized_keys
cp id_rsa volumes/client/

docker-compose run sbt
