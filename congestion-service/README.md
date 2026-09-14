# CongestionServiceApp

## Overview

Tracks the city-wide Congestion Level (0-8).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
congestion-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── CongestionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/congestion-service.jar
```

Listens on port `7022`.

| Endpoint | Method | Behavior |
|---|---|---|
| `/health` | `GET` | Returns the service health check. |
| `/congestion` | `GET` | Returns the current level as `{"level": <0-8>}`. |
| `/congestion` | `PUT` | Accepts `{"level": <0-8>}`. A changed value is published to `congestion-topic`; invalid values return `400`. |

## Test

Run the JUnit 5 congestion-state tests with:

```
mvn test
```
