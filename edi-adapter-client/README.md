# edi-adapter-client

Kotlin client for the EDI Adapter's `/api/v3/*` endpoints, using models from `edi-adapter-model`.

The client wraps the HTTP endpoints exposed by `edi-adapter-server` and provides a typed API for internal services.

## Purpose

* Handle HTTP and serialization, and expose typed errors to consumers
* Provide a strongly typed client API
* Simplify integration with the EDI Adapter

## Usage

Internal services should depend on this module rather than calling the EDI Adapter HTTP API directly.

Import request and response models from `no.nav.helsemelding.ediadapter.model.v3`.
`GetBusinessDocumentResponse` is in `.model.common`.
See the [model package overview](../edi-adapter-model/README.md#model-packages) for details.

Create an `EdiAdapterClient` with `HttpEdiAdapterClient(scopedAuthHttpClient(scope))`.
The client authenticates with Azure AD; the adapter handles DPoP towards NHN.
Cancel active streams before closing the client.

| Method | Description |
|--------|-------------|
| `getNotifications` | Deprecated; use `getUnreadNotifications` |
| `streamNotifications` | Deprecated; use `streamUnreadNotifications` |
| `getUnreadNotifications` | Fetch unread notifications without an offset |
| `streamUnreadNotifications` | Stream unread notifications as a Kotlin Flow |
| `deleteNotifications` | Delete notifications after successful processing |
| `postMessage` | Send a business document |
| `getMessage` | Fetch message metadata |
| `getBusinessDocument` | Fetch the encoded document |
| `getMessageStatus` | Fetch delivery state and AppRec information |
| `postApprec` | Send an application receipt |
| `markMessageAsDownloaded` | Mark a message as downloaded |
| `setMshConfigurations` | Create or update MSH configurations |
| `deleteMshConfigurations` | Delete MSH configurations |
| `ping` | Check connectivity to NHN |

Calls return `Either<EdiAdapterError, T>` with `Api`, `Transport` or `Decoding` errors.
Cancellation propagates. Ordinary HTTP calls are not retried automatically.

## Notification streaming

`streamNotifications` accepts one her id or a list, plus an optional `Long` offset.
Omitting the offset starts at the end of the stream. Each collection opens its own connection and
reconnects from the last emitted notification, with exponential backoff and jitter capped at 30 seconds.
EOF, transport failures and HTTP 408, 429 or 5xx trigger reconnect. Other HTTP errors and invalid data
emit a terminal `Left`; HTTP 204 ends the stream normally. Configuration and collector exceptions propagate.

`findLatestOffset()` returns the latest stored offset as a `Long`, or `0L` when no offsets are found:

```kotlin
val initialOffset = checkpointStore.findLatestOffset()
client.streamNotifications(herIds, offset = initialOffset).collect { result ->
    when (result) {
        is Either.Left -> handleError(result.value)
        is Either.Right -> {
            process(result.value)
            checkpointStore.save(result.value.offset)
        }
    }
}
```

The checkpoint store and handlers above are application code. Persist offsets after processing for recovery
after restart, and tolerate redelivery. With buffering or asynchronous processing, the last emitted offset
may be ahead of the last processed offset. Without an initial offset, reconnects before the first notification
can miss events. Cancelling collection closes the connection.

## Unread notifications

`getUnreadNotifications` and `streamUnreadNotifications` accept one her id or a list without an offset.
Polling accepts an optional `notificationsToFetch` and returns `GetUnreadNotificationsResponse`.
Streaming emits `Either<EdiAdapterError, UnreadNotification>` and uses the same reconnect, error handling
and cancellation behavior as `streamNotifications`. Reconnects use the same her ids without an offset;
processing should tolerate redelivery.

After successful processing, call `deleteNotifications(DeleteNotificationsRequest(notificationIds))`
with the processed notification IDs. It returns `Either<EdiAdapterError, Unit>`.

```kotlin
val response = client.getUnreadNotifications(herIds, notificationsToFetch = 100)

client.streamUnreadNotifications(herIds).collect { result ->
    when (result) {
        is Either.Left -> handleError(result.value)
        is Either.Right -> process(result.value)
    }
}
```

## Relationship to other modules

* Uses shared models from `edi-adapter-model`
* Calls the HTTP API exposed by `edi-adapter-server`
