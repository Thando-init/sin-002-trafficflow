.DEFAULT_GOAL := help

MVN ?= mvn
DOCKER_COMPOSE ?= docker compose
CURL ?= curl

SERVICES := ingestion-service intersection-service congestion-service routing-service intersection-watchdog

.PHONY: help build package test clean verify \
        broker-start broker-stop \
        run-ingestion run-intersection run-congestion run-routing run-watchdog \
        health smoke

help:
	@echo "TrafficFlow build and run targets"
	@echo ""
	@echo "Build and test:"
	@echo "  make build              Compile all five Maven modules"
	@echo "  make package            Build executable shaded JARs"
	@echo "  make test               Run every module's unit tests"
	@echo "  make verify             Run tests and build executable JARs"
	@echo "  make clean              Remove Maven target directories"
	@echo ""
	@echo "Broker:"
	@echo "  make broker-start       Start ActiveMQ through common/docker-compose.yml"
	@echo "  make broker-stop        Stop the Docker Compose broker"
	@echo ""
	@echo "Services: run each target in a separate terminal after building"
	@echo "  make run-ingestion      Start ingestion-service on port 7020"
	@echo "  make run-intersection   Start intersection-service on port 7021"
	@echo "  make run-congestion     Start congestion-service on port 7022"
	@echo "  make run-routing        Start routing-service on port 7023"
	@echo "  make run-watchdog       Start intersection-watchdog on port 7024"
	@echo ""
	@echo "Checks:"
	@echo "  make health             Check all five HTTP health endpoints"
	@echo "  make smoke              Run basic HTTP endpoint checks"

build:
	@set -e; for service in $(SERVICES); do \
		echo "=== Building $$service ==="; \
		$(MAKE) --no-print-directory -C $$service compile; \
	done

compile:
	$(MVN) -q compile

package:
	@set -e; for service in $(SERVICES); do \
		echo "=== Packaging $$service ==="; \
		$(MVN) -q -f $$service/pom.xml package; \
	done

test:
	@set -e; for service in $(SERVICES); do \
		echo "=== Testing $$service ==="; \
		$(MVN) -q -f $$service/pom.xml test; \
	done

verify: test package

clean:
	@set -e; for service in $(SERVICES); do \
		echo "=== Cleaning $$service ==="; \
		$(MVN) -q -f $$service/pom.xml clean; \
	done

broker-start:
	$(DOCKER_COMPOSE) -f common/docker-compose.yml up -d

broker-stop:
	$(DOCKER_COMPOSE) -f common/docker-compose.yml down

run-ingestion:
	java -jar ingestion-service/target/ingestion-service.jar

run-intersection:
	java -jar intersection-service/target/intersection-service.jar

run-congestion:
	java -jar congestion-service/target/congestion-service.jar

run-routing:
	java -jar routing-service/target/routing-service.jar

run-watchdog:
	java -jar intersection-watchdog/target/intersection-watchdog.jar

health:
	$(CURL) --fail --silent --show-error http://localhost:7020/health
	$(CURL) --fail --silent --show-error http://localhost:7021/health
	$(CURL) --fail --silent --show-error http://localhost:7022/health
	$(CURL) --fail --silent --show-error http://localhost:7023/health
	$(CURL) --fail --silent --show-error http://localhost:7024/health

smoke:
	$(CURL) --fail --silent --show-error http://localhost:7020/intersections
	$(CURL) --fail --silent --show-error http://localhost:7021/intersections
	$(CURL) --fail --silent --show-error http://localhost:7022/congestion
	$(CURL) --fail --silent --show-error http://localhost:7024/alert
	@echo "Smoke checks passed."

# The Windows ActiveMQ installation can be started manually instead of Docker:
#   C:\Tools\apache-activemq-5.19.11\bin\activemq.bat start
# The Java service targets above work from Git Bash, WSL, or another shell with make.
# In native PowerShell, run the equivalent java -jar commands from README.md.
