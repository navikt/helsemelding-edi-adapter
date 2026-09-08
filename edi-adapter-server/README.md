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

The V3 routes use a separate NHN client with `api-version: 3`. Request and response models are in `no.nav.helsemelding.ediadapter.model.v3`.

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

Notification polling takes repeated `herIds`, a required non-negative 64-bit `offset`, and optional `notificationsToFetch` (1–1000, NHN default 100):

```text
GET /api/v3/notifications?herIds=42&herIds=1337&offset=0&notificationsToFetch=100
```

Polling and SSE accept at most 1500 unique HER IDs. SSE takes `herIds` and an optional `offset`; omitting the offset starts at the end of the stream, as specified in OpenAPI. The response is `text/event-stream`, forwarded incrementally. Consumers must reconnect after disconnection and persist their last successfully processed offset. Offsets are global and may have gaps.

Sending messages and AppRecs returns `202 Accepted` with an object containing `id`. Any `Location` header is preserved. AppRec sender HER ID is supplied in the request body. Marking a message as downloaded takes `{ "receiverHerId": 42 }` and returns `204`.

MSH configuration uses `PUT` with `{ "configurations": [...] }`, and `receiveNotificationChannel: "Api"`. Deletion takes repeated `herIds` query parameters. The adapter preserves NHN's response status; the published DELETE specification does not declare a success code.

NHN errors are forwarded as received; local V3 validation and processing errors use `MshApiProblemDetails`. V1/V2 error handling and contracts remain unchanged. The existing Kotlin client still targets V1/V2.

Before processing V3 messages, configure every HER ID. Consumers should handle duplicates and coordinate the switch from V2 processing, following the [NHN migration guide](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/docs/api-version-3/migration_to_v3_engbmd).

Reference: [NHN V3 OpenAPI](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/openapi/meldingstjener-api-test-v3-internett). AppRec status is serialized using the schema's string enum values (`Ok`, `Rejected`, `OkErrorInMessagePart`); some upstream examples still show numeric codes.

## API documentation (Swagger)

The EDI Adapter exposes OpenAPI/Swagger documentation for its internal API.

When running the server locally, the documentation is available at:

- `/swagger`

The Swagger UI reflects the `/api/v1/*`, `/api/v2/*`, and `/api/v3/*` endpoints exposed by this service and can be used to explore and test the API locally.

Swagger is only intended for local development and internal use.

## Implementation overview

Adapter API routes are registered in `externalRoutes` in `Routes.kt`, which delegates to `RoutesV1.kt`, `RoutesV2.kt` and `RoutesV3.kt`. Swagger definitions are organized by API version in `MessagesApiV1.kt`, `MessagesApiV2.kt` and `MessagesApiV3.kt`. Request and error handling for all API versions, including V3 streaming, is in `RequestHandling.kt`.
Parameter validation and query construction are organized in `ValidationV1.kt`, `ValidationV2.kt` and `ValidationV3.kt`, with shared validators in `ValidationCommon.kt`.
Each route maps directly to the corresponding NHN endpoint.

Metrics and health checks are provided through `internalRoutes`.

Route tests are organized by API version in `RoutesV1Spec.kt`, `RoutesV2Spec.kt` and `RoutesV3Spec.kt`. Shared test setup for V1, V2 and V3, including the streaming test server, is in `RoutesTestSetup.kt`.

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
