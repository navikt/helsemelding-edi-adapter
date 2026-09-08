# edi-adapter-model

Shared model definitions for the EDI Adapter.

This module contains request and response objects used by both the client and the server.

## Purpose

* Define the shared contract between client and server
* Avoid duplication of model classes
* Ensure consistency across modules

## Characteristics

* No HTTP, framework, or runtime dependencies
* Pure data structures
* Used by:
    * `edi-adapter-client`
    * `edi-adapter-server`

## Model packages

Models are organized by API version under `no.nav.helsemelding.ediadapter.model`:

| Package | Models |
|---------|--------|
| `v1` | Messages, AppRec, delivery status, message queries and ebXML overrides |
| `v2` | Notices, notice queries and MSH configuration |
| `v3` | NHN V3 requests, responses, notifications, configuration and problem details |
| `common` | `ErrorMessage` for V1/V2 and `GetBusinessDocumentResponse` for V1/V3 |
