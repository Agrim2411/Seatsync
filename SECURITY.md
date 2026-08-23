# Security

## Reporting

Please open a private security advisory rather than a public issue for a vulnerability.

## Trust boundaries

- The gateway is the public entry point. The `prod` profile requires OAuth2 JWT authentication.
- Reservation, booking, and payment endpoints are internal services and should be protected with Kubernetes network policies or service-to-service authentication outside local development.
- `paymentMethodToken` is a simulator directive, not cardholder data. The project must never receive or store PAN, CVV, or real payment credentials.
- Idempotency keys are scoped to a command endpoint and are not authentication credentials.

## Deployment requirements

- Replace the example database password and store secrets outside Helm values committed to Git.
- Terminate TLS at an ingress or load balancer and use TLS for PostgreSQL, Redis, Kafka, and gRPC in shared environments.
- Configure explicit WebSocket origins.
- Set resource limits and a no-eviction Redis policy; alert before memory exhaustion.
- Restrict actuator endpoints so only health probes are public.
- Use a managed identity provider and validate issuer, audience, expiry, and signing algorithm.

## Deliberate local-development choices

Docker Compose uses plaintext connections, a known password, permissive service networking, and unauthenticated development routes. These defaults are not suitable for an internet-facing environment.
