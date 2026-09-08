# helsemelding-edi-adapter

The **helsemelding-edi-adapter** provides a stable internal interface wrapping the external NHN Meldingstjener API (EDI 2.0).

The adapter handles authentication towards NHN, including [DPoP](https://utviklerportal.nhn.no/informasjonstjenester/helseid/bruksmoenstre-og-eksempelkode/bruk-av-helseid/docs/dpop/dpop_enmd). Consumers authenticate against the adapter using Azure AD access tokens; they do not need to manage NHN authentication.

Official documentation is available [here](https://utviklerportal.nhn.no/informasjonstjenester/meldingsutveksling/edi-20/edi-20-ekstern-docs/openapi/meldingstjener-api-test-v3-internett)

## Modules

| Module | Description                                        |
|---|----------------------------------------------------|
| `edi-adapter-client` | Kotlin client for calling the EDI Adapter API      |
| `edi-adapter-model` | Shared model definitions used by client and server |
| `edi-adapter-server` | EDI Adapter server exposing `/api/v1/*`, `/api/v2/*`, and `/api/v3/*`            |

### edi-adapter-client
Kotlin client library for calling the EDI Adapter API from internal services.  
See: [edi-adapter-client/README.md](edi-adapter-client/README.md)

### edi-adapter-model
Shared model definitions used by both client and server.  
See: [edi-adapter-model/README.md](edi-adapter-model/README.md)

### edi-adapter-server
EDI Adapter server acting as an anti-corruption layer towards NHN and exposing `/api/v1/*`, `/api/v2/*`, and `/api/v3/*`.
See: [edi-adapter-server/README.md](edi-adapter-server/README.md)

---

Refer to each module’s README for detailed usage and implementation notes.