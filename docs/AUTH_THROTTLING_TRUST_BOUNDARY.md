# Authentication throttling and proxy identity

Login and refresh application-level throttling is **not implemented**. The safe
default is to trust no forwarded client-address header and not apply a
per-address limit to the Next proxy or Render ingress address. Configure an
independent edge-level protection for the public frontend and the public API
until a verified client-identity path is available; the frontend proxy alone
does not protect direct requests to the API.

## Current trust boundary

Browser requests go through the Next `/api/backend` route before reaching the
backend. That route copies incoming headers, including forwarding headers,
without establishing which address was set by a trusted ingress. The backend
is also deployed behind Render ingress. The repository does not specify a
verified list of trusted hops, a trusted client-address header contract, or a
way to authenticate the Next-to-backend identity assertion. Consequently:

- A client-supplied `X-Forwarded-For` or similar header is not proof of the
  browser address, regardless of which position in the list is selected.
- The backend's apparent peer may be an intermediary shared by many users.
  Using that peer as the browser identity could throttle an entire farm.
- An email address is attacker-supplied and a refresh token changes on
  rotation. Either alone is not a reliable substitute for a client address.

Do not enable generic framework forwarded-header processing to establish the
limiter's identity. It can replace the observed peer before the application
has verified the proxy boundary. No trusted-proxy configuration is supplied
or implied by this repository.

## Requirements before enabling a limiter

1. Document the actual ingress chain for both browser and direct API traffic,
   including which hops can reach each service, the authenticated or network
   restricted peer for every hop, and the provider's verified behavior for
   stripping and replacing client-address headers. Do not infer trust from
   private address ranges or a fixed number of hops alone.
2. Ensure the first trusted ingress discards client-supplied identity headers
   and sets a verified address. Ensure Next transmits only that verified
   identity to the backend over an authenticated or suitably restricted path,
   and that a direct caller cannot impersonate Next or bypass the same policy.
   If a hop cannot provide these guarantees, enforce the limit at an ingress
   that can, or keep backend address-based throttling disabled.
3. Configure explicit, deployment-specific trusted peers and header semantics;
   reject malformed or missing identity at that trust boundary rather than
   silently using the shared proxy address. Verify the deployment with forged,
   duplicate, and multi-hop headers, plus direct API requests. Preserve access
   for different users behind the same legitimate network when selecting limits.
4. Implement separate bounded login and refresh limits, concurrency-safe
   accounting, expiry and a defined saturation policy. A process-local store
   resets on restart and does not coordinate replicas; use a shared bounded
   store or enforce equivalent limits at the edge for multiple instances.
   Avoid storing raw credentials or tokens in limiter keys or logs.
5. Return HTTP 429 with an accurate `Retry-After` for denied requests. Test
   expiry, retry timing, storage saturation, concurrent requests, independent
   identities and endpoints, bypass attempts, and frontend propagation of
   429 responses without clearing valid refresh cookies.

These are prerequisites for a future implementation, not settings that are
currently available. No proxy trust, throttling threshold, or storage backend is
configured by this document.
