#!/usr/bin/env bash

scp ./target/scala-2.11/fat-bots-2.24.jar morgaroth@138.128.210.155:/mnt/bots/ -p 28223

cd /mnt/bots && rm bots-current.jar && ln -s fat-bots-2.24.jar bots-current.jar && supervisorctl restart bots