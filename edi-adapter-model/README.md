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

Consumers must update imports from the original model package to the corresponding version or `common` package and recompile. For example, `model.PostMessageRequest` is now `model.v1.PostMessageRequest`, and `model.GetNoticesRequest` is now `model.v2.GetNoticesRequest`. JSON field names and formats are unchanged.

## API V3 models

V3 models include offset-based notifications, explicit message addressing and application metadata, nested status/AppRec information, MSH configuration, and problem details. API enum values are serialized as strings. Notification offsets use `Long`; timestamp fields preserve the strings supplied by NHN, including business-document dates without timezone information.
