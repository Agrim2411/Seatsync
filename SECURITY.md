# Security

SeatSync is a portfolio project and is not configured for public deployment.

- The default development profile is unauthenticated. The gateway's `prod` profile enables JWT validation through `JWT_ISSUER_URI`.
- The Compose password and plaintext connections are local-development defaults, not production credentials.
- The payment token is only a simulator instruction such as `pm_success`; never send real card details.
- The payment and application service ports are exposed locally for debugging. A real deployment should expose only the gateway and keep internal services private.

Before any internet-facing deployment, use TLS, external secret storage, and restricted actuator access. Report vulnerabilities through a private GitHub security advisory.
