# edi-adapter-server

The edi-adapter-server is an [anti corruption layer (ACL)](https://ddd-practitioners.com/home/glossary/bounded-context/bounded-context-relationship/anticorruption-layer/) between the external NHN Meldingstjener API (EDI 2.0) and our internal services.
It exposes the existing `/api/v1/*` and `/api/v2/*` contracts alongside `/api/v3/*` for NHN API V3.

Internal consumers typically interact with this API through `edi-adapter-client`.

**Key Takeaways:**

* Existing consumers can continue using `/api/v1/*` and `/api/v2/*`.
* `/api/v2/*` retains the existing experimental contract; `/api/v3/*` exposes the NHN V3 contract.
* The adapter manages all communication and error handling with NHN.
* Authentication and certificates are configured in `ediClient`.
* Metrics are collected through `PrometheusMeterRegistry`.
* Any change to the NHN API should result in updating this adapter, not the calling services.

## Purpose

* Simplifies sending and receiving EDI 2.0 messages
* Isolates all network calls, parameters, and schema handling
* Shields internal services from changes in the NHN API

## Our API (internal)

Routes are versioned under `/api/v1`, `/api/v2`, and `/api/v3`. Their upstream `api-version` headers are `2`, `2-vNext`, and `3`, respectively. All external routes use the same Azure AD authentication.

### v1

| Method | Path                                                      | Description                          | Calls external NHN endpoint                      |
|--------|-----------------------------------------------------------|--------------------------------------|--------------------------------------------------|
| GET    | `/api/v1/messages`                                        | Fetch messages for given receiver(s) | `GET /Messages`                                  |
| GET    | `/api/v1/messages/{messageId}`                            | Fetch a single message               | `GET /Messages/{id}`                             |
| GET    | `/api/v1/messages/{messageId}/document`                   | Download the message payload         | `GET /Messages/{id}/business-document`           |
| GET    | `/api/v1/messages/{messageId}/status`                     | Get message status                   | `GET /Messages/{id}/status`                      |
| GET    | `/api/v1/messages/{messageId}/apprec`                     | Retrieve application receipt         | `GET /Messages/{id}/apprec`                      |
| POST   | `/api/v1/messages`                                        | Send a new message                   | `POST /Messages`                                 |
| POST   | `/api/v1/messages/{messageId}/apprec/{apprecSenderHerId}` | Send application receipt             | `POST /Messages/{id}/apprec/{appRecSenderHerId}` |
| PUT    | `/api/v1/messages/{messageId}/read/{herId}`               | Mark message as read                 | `PUT /Messages/{id}/read/{herId}`                |

### v2

| Method | Path                       | Description                               | Calls external NHN endpoint  |
|--------|----------------------------|-------------------------------------------|------------------------------|
| GET    | `/api/v2/messages/notices` | Fetch notices for given receiver(s)       | `GET /Messages/notices`      |
| POST   | `/api/v2/mshConfiguration` | Update MSH configuration for given HerIds | `POST /MshConfiguration`     |

### v3

| Method | Path | Calls external NHN endpoint |
|--------|------|----------------------------|
| GET | `/api/v3/notifications` | `GET /notifications` |
| GET | `/api/v3/notifications/stream` | `GET /notifications/stream` (SSE) |
| POST | `/api/v3/messages` | `POST /messages` |
| GET | `/api/v3/messages/{messageId}` | `GET /messages/{id}` |
| GET | `/api/v3/messages/{messageId}/document` | `GET /messages/{id}/business-document` |
| GET | `/api/v3/messages/{messageId}/status` | `GET /messages/{id}/status` |
| POST | `/api/v3/messages/{messageId}/apprec` | `POST /messages/{id}/apprec` |
| PUT | `/api/v3/messages/{messageId}/downloaded` | `PUT /messages/{id}/downloaded` |
| PUT | `/api/v3/mshconfigurations` | `PUT /mshconfigurations` |
| DELETE | `/api/v3/mshconfigurations` | `DELETE /mshconfigurations` |
| GET | `/api/v3/ping` | `GET /ping` |

V3 supports notification polling and SSE streaming. Consumers must reconnect when a stream closes and resume from the last successfully processed offset.

NHN errors are forwarded unchanged; local errors use `MshApiProblemDetails`. The Kotlin client currently supports V1/V2 only.

See the [NHN V3 OpenAPI](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/openapi/meldingstjener-api-test-v3-internett) for request and response details, and the [migration guide](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/docs/api-version-3/migration_to_v3_engbmd) for configuration and migration requirements.

## API documentation (Swagger)

The EDI Adapter exposes OpenAPI/Swagger documentation for its internal API.

When running the server locally, the documentation is available at:

- `/swagger`

The Swagger UI reflects the `/api/v1/*`, `/api/v2/*`, and `/api/v3/*` endpoints exposed by this service and can be used to explore and test the API locally.

Swagger is only intended for local development and internal use.

## Health and metrics

| Path                         | Description                 |
|------------------------------|-----------------------------|
| `/internal/health/liveness`  | Returns “I'm alive! :)”     |
| `/internal/health/readiness` | Returns “I'm ready! :)”     |
| `/prometheus`                | Prometheus metrics endpoint |

## Local development

Spinning up the adapter locally involves a few simple steps:

1. Login to the NAIS Console: https://console.nav.cloud.nais.io
2. Localize the `helsemelding-nhn-edi` secret and copy the `keypair-jwk` value
3. Paste the value into `src/test/resources/keypair-jwk.json`
4. [Disable authentication to AzureAD](#Disable-authentication-to-AzureAD)
5. Run the adapter (typically by running the `App` class in your IDE). 
   See [Running the adapter in IntelliJ](#Running-the-adapter-in-IntelliJ) for troubleshooting.

When the server is running, it is curlable, for example:

`curl http://localhost:8080/api/v1/messages/{messageId}/apprec`

The adapter will POST and GET data to and from the NHN test environment in the background.

**NOTE:**  
If `NHN_KEYPAIR_PATH` is not set locally (this is typically only set in NAIS), Hoplite defaults to the test configuration defined in `application.conf`.

### Disable authentication to AzureAD

Comment out the following in App.kt:
```kotlin
configureAuthentication()
```

In Routes.kt change the following:
```kotlin
authenticate(config().azureAuth.issuer.value) {
    externalRoutes(ediClientV1, ediClientV2, ediClientV3)
}
```

to:
```kotlin
// authenticate(config().azureAuth.issuer.value) {
    externalRoutes(ediClientV1, ediClientV2, ediClientV3)
// }
```

### Running the adapter in IntelliJ

Change working directory from (example for Windows):
> path\to\project\helsemelding-edi-adapter

to:
> path\to\project\helsemelding-edi-adapter\edi-adapter-server
