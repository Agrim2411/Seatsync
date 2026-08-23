# Reproducible benchmark

This benchmark validates the single-winner invariant and records latency under a hot-seat burst. It is intentionally separate from unit testing.

## Run

1. Start a fresh stack: `docker compose down -v && docker compose up -d --build`.
2. Install k6.
3. Run `k6 run --summary-export=load-tests/results/contention.json load-tests/contention.js`.
4. Increase concurrency with `VUS=1000 k6 run --summary-export=load-tests/results/contention-1000.json load-tests/contention.js`.
5. Confirm `seat_hold_won` equals one and retain the JSON result with the machine specification and commit SHA.

Run against `http://localhost:8082` to measure reservation correctness without gateway admission control. A separate gateway benchmark should be used to tune per-customer quotas.

## Resume evidence

Only replace the placeholders below using a retained result file:

> Validated zero overselling under **[VUS]** concurrent attempts for a single seat, with **[p95] ms** reservation latency on **[machine]**.

Do not compare results across machines without documenting CPU, memory, container limits, Java version, and database configuration.

## GitHub-hosted run

Use `.github/workflows/benchmark.yml` when Docker is unavailable locally:

1. Push the repository to GitHub.
2. Open the repository's **Actions** tab.
3. Select **cloud contention benchmark**.
4. Choose **Run workflow** and select 100, 500, 1000, or 2000 contenders.
5. Download `seatsync-contention-<count>` from the run's **Artifacts** section.

The artifact is retained for 30 days. Record the workflow URL and commit SHA with the JSON result.
