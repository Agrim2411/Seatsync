.PHONY: build up down logs benchmark

build:
	mvn -B -DskipTests package

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f --tail=200

benchmark:
	mkdir -p load-tests/results
	k6 run --summary-export=load-tests/results/contention.json load-tests/contention.js
