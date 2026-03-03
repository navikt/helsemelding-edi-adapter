# edi-adapter-client

Kotlin client library for interacting with the EDI Adapter API.

The client wraps the HTTP endpoints exposed by `edi-adapter-server` and provides a typed, idiomatic API for internal services.

## Purpose

* Hide HTTP, serialization, and error handling from consumers
* Provide a stable, strongly typed client API
* Simplify integration with the EDI Adapter

## Usage

Internal services should depend on this module rather than calling the EDI Adapter HTTP API directly.

The client communicates with the adapter's internal API under `/api/v1/*` for stable endpoints and `/api/v2/*` for experimental v2-vNext endpoints.

### Experimental (v2-vNext)

Some client methods are annotated with `@ExperimentalEdiAdapterApi`. These wrap v2-vNext NHN endpoints that are still subject to change.

| Method                                              | Description                               |
|-----------------------------------------------------|-------------------------------------------|
| `getNotices(GetNoticesRequest)`                     | Fetch message notices for receiver(s)     |
| `postMshConfiguration(PostMshConfigurationRequest)` | Update MSH configuration for given HerIds |

Calling these methods without opting in is a **compile-time warning**, See [ExperimentalEdiAdapterApi](src/main/kotlin/no/nav/helsemelding/ediadapter/client/ExperimentalEdiAdapterApi.kt) 
for more information on how to opt in.

## Relationship to other modules

* Uses shared models from `edi-adapter-model`
* Calls the HTTP API exposed by `edi-adapter-server`