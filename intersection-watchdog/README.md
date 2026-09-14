# IntersectionWatchdogApp

## Overview

Cries for help if the Intersection Service crashes, since routes can no longer be validated.

Part of the [TrafficFlow](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

MQ: this service subscribes to the ActiveMQ queue `intersection-heartbeat-queue` — see [`../common/`](../common). Broker URL and queue name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module. Mechanism: watch for missed heartbeats and/or dead-lettered messages from `intersection-service` and raise an alert.

## Project structure

```
intersection-watchdog/
├── pom.xml
└── src/main/java/co/wethinkcode/trafficflow/
    ├── IntersectionWatchdogApp.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/intersection-watchdog.jar
```

Listens on port `7024`. It observes the heartbeat queue and broker dead-letter queue, raises an alert after 15 seconds without a heartbeat by default, and clears the alert on recovery. Set `HEARTBEAT_TIMEOUT_SECONDS` to override the timeout.

| Endpoint | Method | Behavior |
|---|---|---|
| `/health` | `GET` | Returns `200` while heartbeats are current, or `503` with alert details when the intersection service is unavailable. |
| `/alert` | `GET` | Returns the current liveness state, last heartbeat timestamp, and alert details if present. |

## Test

Run the JUnit 5 watchdog-state tests with:

```
mvn test
```
