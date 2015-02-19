#!/bin/bash

HOST_IP=`boot2docker ip`
echo "Starting cluster at boot2docker host $HOST_IP"

echo "Starting Cassandra"
docker run --name cassandra --net="host" --expose 7199 --expose 7000 --expose 7001 --expose 9160 --expose 9042 --expose 22 --expose 8012 --expose 61621 spotify/cassandra &

echo "Waiting for Cassandra to start"
sleep 10

echo "Starting main monolith 3 nodes"
docker run --name main --net="host" -e "HOST=$HOST_IP" -e "APP_PORT=2551" -e "REST_PORT=8082" -e "SEED_NODES=akka.tcp://Lift@$HOST_IP:2551" -e "APP_ADDR" -e "JOURNAL=$HOST_IP" -e "SNAPSHOT=$HOST_IP" -p "2551:2551" -p "8082:80" janm399/lift:main-production &
docker run --name main2 --net="host" -e "HOST=$HOST_IP" -e "APP_PORT=2552" -e "REST_PORT=8083" -e "SEED_NODES=akka.tcp://Lift@$HOST_IP:2551" -e "APP_ADDR" -e "JOURNAL=$HOST_IP" -e "SNAPSHOT=$HOST_IP" -p "2552:2552" -p "8083" janm399/lift:main-production &
docker run --name main3 --net="host" -e "HOST=$HOST_IP" -e "APP_PORT=2553" -e "REST_PORT=8084" -e "SEED_NODES=akka.tcp://Lift@$HOST_IP:2551" -e "APP_ADDR" -e "JOURNAL=$HOST_IP" -e "SNAPSHOT=$HOST_IP" -p "2553:2553" -p "8084" janm399/lift:main-production &

echo "Starting spark monolith 1 node"
docker run --name spark --net="host" -e "CASSANDRA_HOST=$HOST_IP" -e "APP_REST_API=$HOST_IP" -e "APP_REST_PORT=8083" -e "SPARK_MASTER_HOST=$HOST_IP" -e "SPARK_MASTER_PORT=7077" -p "8080" -p "7077:7077" -p "8081:8081" -p "8888:8888" -p "4040:4040" -p "9001:9001" janm399/lift:spark &