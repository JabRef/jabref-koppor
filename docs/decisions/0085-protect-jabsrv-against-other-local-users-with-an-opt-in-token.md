---
nav_order: 85
parent: Decision Records
status: proposed
---

# Protect jabsrv against other local users with an opt-in token

`adr~jabsrv.local-user-token~1`

## Context and Problem Statement

The request filter of [ADR-0084](0084-accept-jabsrv-requests-from-browsers-only-from-trusted-callers.md) rejects websites.
It accepts every request without `Origin` and `Sec-Fetch-Site`, because editors and curl send such requests.

On a computer with several logged-in users (e.g., a terminal server), `localhost` is shared.
A program of another user sends the same kind of request and is accepted.
That user cannot read the library files, so jabsrv gives access that the file system does not.

The single-instance listener on port 6050 is open to other local users as well.
It is on by default and reads serialized Java objects from every local connection ([`Protocol`](https://github.com/JabRef/jabref/blob/9b8a93e48a5a410ea6c3a0726663293ee241fd0f/jablib/src/main/java/org/jabref/logic/remote/Protocol.java#L73-L74)).
It is not part of this decision.

How can jabsrv reject other local users without breaking single-user setups?

## Considered Options

* Do not protect against other local users
* Opt-in token; the Firefox extension reads it from managed storage
* Opt-in token; the extension gets it from the native messaging host
* Check which user owns the TCP connection

## Decision Outcome

Chosen option: "Opt-in token; the Firefox extension reads it from managed storage", because JabRef can set it up alone: no installer, no extra process, no administrator rights.

A preference turns the token on.
JabRef then writes a token to a file that only the user can read, like the `tokenFile` of the fulltext provider ([#16124](https://github.com/JabRef/jabref/pull/16124)), and jabsrv requires `Authorization: Bearer <token>`.
Editors and scripts read the file.
For the Firefox extension, JabRef also writes the token to the extension's managed storage manifest (`~/.mozilla/managed-storage/@jabfox.json` on Linux), and the extension reads it with `browser.storage.managed`.

```mermaid
flowchart LR
    subgraph Clients
        E[Browser extension]
        C[Editors, curl, scripts]
    end
    MS([Managed storage manifest])
    TF([Token file, user-only])
    H((HTTP 23119))
    subgraph JabRef Desktop
        S[jabsrv]
        J[JabRef]
    end
    E -- reads --- MS
    C -- reads --- TF
    MS -- "written by" --- J
    TF -- "written by" --- J
    E -- "Authorization: Bearer" --- H
    C -- "Authorization: Bearer" --- H
    H --- S
    S --- J
```

### Consequences

* Good, because a request from another user has no token and is rejected.
* Good, because nothing changes for users who leave the preference off.
* Bad, because with the preference on, every CAYW client and script has to send the token.
* Bad, because JabMap web cannot read the token and stops working with the preference on.
* Bad, because it covers only Firefox. Chrome has managed storage only through an enterprise policy.
* Bad, because two users still cannot both use port 23119.

### Confirmation

Managed storage is not tested yet.
It has to be tried on Linux, Windows, and macOS, and with the snap and Flatpak packages of Firefox, which may not see `~/.mozilla`.

## Pros and Cons of the Options

### Do not protect against other local users

* Good, because nothing changes.
* Bad, because another user can read and change the library through jabsrv.

### Opt-in token; the Firefox extension reads it from managed storage

See [Native manifests](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_manifests) for the manifest locations.

* Good, because JabRef writes the manifest itself.
* Good, because the extension already has the `storage` permission.
* Bad, because it is Firefox only.

### Opt-in token; the extension gets it from the native messaging host

Proposed in the review of [#15295](https://github.com/JabRef/jabref/pull/15295).

* Good, because it works in Firefox and Chrome.
* Bad, because the native messaging host has to be installed. It is missing for a portable build and when JabRef runs from source.

### Check which user owns the TCP connection

* Good, because no client changes.
* Bad, because each operating system needs its own code.
