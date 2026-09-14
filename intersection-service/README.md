# IntersectionServiceApp

## Overview

Validates intersection/district names (source of truth).

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Project structure

```
intersection-service/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionServiceApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-service.jar
```

Listens on port `7021`. It loads canonical data from ingestion-service at startup and can reload it with `POST /intersections/refresh`. It publishes a heartbeat every five seconds by default; set `HEARTBEAT_INTERVAL_SECONDS` to override the interval.

| Endpoint | Method | Behavior |
|---|---|---|
| `/health` | `GET` | Returns health and the current catalogue size. |
| `/intersections` | `GET` | Returns the canonical intersection list. |
| `/intersections/{id}` | `GET` | Looks up an intersection; returns `404` when unknown. |
| `/districts/{district}` | `GET` | Returns matching intersections; returns `404` when unknown. |
| `/intersections/refresh` | `POST` | Reloads canonical data from ingestion-service. |

## Test

Run the JUnit 5 catalogue tests with:

```
mvn test
```
