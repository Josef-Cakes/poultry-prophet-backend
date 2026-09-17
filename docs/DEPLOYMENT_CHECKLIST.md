# MVP deployment checklist

This checklist is for a separately approved deployment after validation. No production deployment was performed as part of this work.

## Before deployment

- [ ] Confirm stakeholder validation report has no open severity-1/2 defects.
- [ ] Record the exact frontend commit, backend commit, build time, and compatible version.
- [ ] Build backend and frontend from clean, reviewed commits.
- [ ] Set `APP_VERSION`, `APP_COMMIT_SHA`, `APP_BUILD_TIME`, `APP_ENVIRONMENT`, `APP_FRONTEND_VERSION`, and `APP_ALLOWED_ORIGINS` explicitly.
- [ ] Set `NEXT_PUBLIC_APP_VERSION`, `NEXT_PUBLIC_APP_ENVIRONMENT`, `NEXT_PUBLIC_BACKEND_VERSION`, and the production API URL explicitly.
- [ ] Use a production-only JWT secret and a reviewed CORS allowlist; never use `*` with credentials.
- [ ] Back up the production database and verify restore access.
- [ ] Review `V3__validation_ledger_and_explainable_indicators.sql` against the actual schema. Apply it through the approved migration process only; do not run the validation reset/seed scripts.
- [ ] Confirm no synthetic account, validation database URL, or test data is configured in production.

## Smoke tests after deployment

- [ ] `GET /api/health` and `GET /api/version` return expected environment/version.
- [ ] Unauthenticated protected REST request returns 401.
- [ ] Allowed frontend origin receives CORS headers; an unlisted origin does not.
- [ ] STOMP CONNECT without/with invalid JWT is rejected; valid same-farm subscribe works; cross-farm subscribe/send is rejected.
- [ ] Handler and manager login work with least-privilege access.
- [ ] Dashboard-origin and batch-origin logging return links work once each.
- [ ] No save action automatically redirects; confirmation remains visible.

## Sign-off

Record approver, date, deployed commit, database backup identifier, migration identifier, smoke-test evidence, and rollback owner.
