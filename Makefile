.PHONY: build test up down logs smoke benchmark

build:
	mvn -B -DskipTests package

test:
	mvn -B test

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f --tail=200

smoke:
	./scripts/smoke-test.sh

benchmark:
	mkdir -p load-tests/results
	k6 run --summary-export=load-tests/results/contention.json load-tests/contention.js
