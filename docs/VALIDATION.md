# Observed validation

Recorded 2 October 2026 for the v2 rework and endpoint-routing correction. Results below distinguish actual provider requests, emulator behavior, and simulated delivery. No real calls or SMS were sent by these checks.

## Build and automated checks

| Check | Observed result |
| --- | --- |
| API production/risk regression suite | 17 tests passed, 0 failed |
| Android production journey-math unit tests | 8 tests passed, 0 failed |
| Android debug APK build | Passed; `artifacts/SheShield-debug.apk` |
| Android lint | Passed with 0 errors and 114 warnings; hardcoded text, obsolete resources and API deprecations remain |
| Room v1 → v2 recovery instrumentation | Passed on API 37 emulator, 1 test; legacy contact/trip/deadline preservation and reopen checked using a separate database |
| Latest emulator AndroidRuntime fatal-error log | No fatal application error observed |
| Working-tree whitespace check | `git diff --check` passed |

API tests cover immutable selected geometry, command replay, unique timeout escalation, SAFE/end, owner authorization, standalone SOS, honest unavailable delivery, signed/monotonic Twilio callbacks, late acknowledgement, transaction rollback, SQLite restart recovery, invalid GPS, expiring share links, timely offline SAFE recovery, changed practice endpoints, explicit recorded-demo selection, bounded/matching Kolkata landmark search, route snapping/metrics/deduplication, and the actual geographic scoring engine.

Reproduce API tests with `cd api && npm test`. Build Android with `scripts/build_android.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. This workspace uses portable tools under `/tmp/sheshield-tools`; Windows Studio can build the `android/` project with its installed SDK.

## Actual routing and search

The routing regression requests ordinary `REHEARSAL` plans with different selected coordinates; these plans use the real ORS walking provider and `is_demo_data: false`. Each returned geometry is hashed and required to differ from all previous pairs. Geometry must have more than two vertices and begin/end within the enforced 200 m snapping limit.

| Selected pair | Fastest distance | Geometry vertices | Start / destination snapping |
| --- | --- | --- | --- |
| Esplanade → Victoria Memorial | 3,132 m | 60 | 10 m / 24 m |
| Esplanade → Indian Museum | 716 m | 14 | 10 m / 72 m |
| Park Street → Victoria Memorial | 2,097 m | 57 | 4 m / 24 m |
| Park Street → Indian Museum | 925 m | 21 | 4 m / 72 m |

All four produced three alternatives and four distinct selected paths. These measurements apply to the exact coordinates in `scripts/verify_route_changes.mjs`; geocoder-selected buildings/entrances can produce different distances. Reproduce from the repository root with `node --env-file=api/.env scripts/verify_route_changes.mjs` while the API runs.

`scripts/verify_live.mjs` verified two matching Victoria Memorial results and one actual Indian Museum result in Greater Kolkata. ORS did not contain a matching Indian Museum entry; bounded Nominatim returned it. The API caches place results for one hour and limits search frequency. Search boundaries and fallback parameters follow [Pelias search documentation](https://github.com/pelias/documentation/blob/master/search.md) and [Nominatim bounded search documentation](https://nominatim.org/release-docs/latest/api/Search/).

`scripts/verify_api.mjs` verified enrollment, a real live walking plan, idempotent trip start, a location upload, trip completion, and cleanup. It used no contacts and requested no alerts. Missing live incident data correctly yielded UNKNOWN exposure.

## Emulator interactions

Tested on `Medium_Phone_API_37.0` using software graphics, preserving app contacts/history:

- Searched and explicitly selected Esplanade and Victoria Memorial Hall; the map showed a 2.8 km provider walking path, rather than the bundled scenario.
- Selected the actual Indian Museum search result and calculated a 0.6 km path with a visible 59 m walking-access note. Changed the destination back to Victoria Memorial Hall in the same planning flow and obtained the different 2.8 km path, with a 43 m access note.
- Changed the origin to an explicitly selected Park Street result and recalculated. Duplicate place labels now include coordinates so different points with the same name can be distinguished.
- Confirmed the separate recorded-demo action still loads its three routes and fictional-evidence badge after editable custom planning.
- Returned to planning, long-pressed a nearby map point, assigned it as destination, and calculated a new 0.1 km path. The selected coordinates and pins remained visible.
- Recorded demo: compared three walking paths and evidence, started a journey, triggered an elevated-segment check-in and acknowledged SAFE before expiry.
- Background timeout: created a fresh pending check-in, pressed Android Home, waited beyond its 20-second practice deadline, reopened the trip and observed SOS_ACTIVE. Simulated first-contact no-answer and second-contact acknowledgement appeared in the timeline.
- Canceled future escalation, ended a journey, and opened Activity/history.
- Standalone SOS with no active trip worked; missing location was disclosed. Emergency dial/SMS-composer/cancel actions remained accessible in the fixed footer.
- Room recovery instrumentation passed without clearing the app's actual database.
- Inspected the home screen in light and dark appearances, then restored System appearance.

Screenshots are under `artifacts/`. Simulated replay and delivery results validate the app's state transitions; they do not validate actual GPS walking or carrier delivery.

## External checks still required

- The user is importing/configuring `n8n/workflows/04_cloud_delivery_v2.json` in n8n Cloud. Workflow activation, its Header Auth/Twilio credentials, and an actual call/press-1 callback have not been verified. The API still needs the matching local `TWILIO_AUTH_TOKEN`, consenting recipient allowlist and live-alert enablement for that test. Follow [the import checklist](../n8n/IMPORT_CHECKLIST.md).
- No real Pinecone incident dataset has been inspected. Live exposure remains UNKNOWN until a valid dated dataset with source and geographic coverage is supplied; see [incident-data instructions](INCIDENT_DATA.md).
- No physical-phone GPS walk, real carrier SMS receipt, Android OEM background-policy behavior, or released/signed APK has been tested. The supplied artifact is a debug APK.
- Basemap tiles need internet or an existing cache. Recorded geometry and local rehearsal continue without the API; editable custom route calculation needs the provider/API. There is no promised offline street map.
- The local API and temporary public tunnel must remain running. A quick-tunnel URL changes on restart, requiring updates to API public URL, Android connection and workflow Configuration. Production uptime/deployment is outside these observed checks.
- Force-stopping Android stops its local service until the app reopens. A previously registered live API deadline can still expire server-side. GPS accuracy and walking-map coverage limit navigation precision; inaccessible pins are rejected instead of receiving fabricated paths.
