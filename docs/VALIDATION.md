# Observed validation

Recorded 2 October 2026 for the street-evidence and personal-watch update, following the v2 journey rework. Results below distinguish actual provider requests, emulator behavior, simulated delivery and the separately authorized real Cloud delivery test.

## Build and automated checks

| Check | Observed result |
| --- | --- |
| API production/risk/evidence/rerouting/cloud-SMS/sharing/import/messages regression suite | 80 tests passed, 0 failed |
| Android journey-math, saved-alert compatibility and sharing request unit tests | 12 tests passed, 0 failed |
| Android debug APK build | Passed; `artifacts/SheShield-debug.apk` |
| Android lint | Passed with 0 errors; warnings about hardcoded text, obsolete resources and API deprecations remain |
| Room v1 → v2 recovery instrumentation | Passed on API 37 emulator, 1 test; legacy contact/trip/deadline preservation and reopen checked using a separate database |
| Latest emulator AndroidRuntime fatal-error log | No fatal application error observed |
| Working-tree whitespace check | `git diff --check` passed |
| Authorized n8n Cloud/Twilio call and SMS | One call acknowledged with keypad 1; one SMS delivered; signed callbacks processed |

API tests cover immutable selected geometry, command replay, unique timeout escalation, SAFE/end, owner authorization, standalone SOS, honest unavailable delivery, signed/monotonic Twilio callbacks, late acknowledgement, transaction rollback, SQLite restart recovery, invalid GPS, expiring share links, timely offline SAFE recovery, changed practice endpoints, explicit recorded-demo selection, bounded/matching Kolkata landmark search, route snapping/metrics/deduplication, and the actual geographic scoring engine.

Reproduce API tests with `cd api && npm test`. Build Android with `scripts/build_android.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`. This workspace uses persistent portable tools under ignored `.tools/runtime` and `.tools/android`; Windows Studio can build the `android/` project with its installed SDK.

## Salt Lake source and attribution update

Research on 2 October 2026 produced five actual published Salt Lake references, with priority Sectors II and V. The immutable version `2026-10-02.1` contains **0 admitted street events, 5 context events (2 historical), 0 current observations and 0 complete reporting areas**. Original URLs, event-date intervals, setting and location limits remain attributable. Source checks are automated; no human/provider reviews, field observations or accuracy measurements were invented.

The new API tests check 10 m route analysis without extending a tiny event along the whole road, named-street and alias agreement, nearby parallel roads, perpendicular crossings, unsupported sides, section interiors crossing a curved road, reviewer roles/duplicate identities/rejections, conflicting locations, publisher-ID reuse, date precision and aging, invalid datasets, licensed direct observations and expiry, observation conflicts, coverage counts reconciled to supplied records, private/campus/transport exclusion, and private-field suppression. Immutable snapshot/hash tests and independent-reference evaluation verify that zero reference cases produce `UNMEASURED` and null rates. A legacy saved live HIGH score cannot trigger an inferred segment watch; personal watches continue to work.

Android tests additionally verify that saved older route JSON remains readable and that date intervals, area context, links and route decision fields survive saving/reopening. Build and lint passed, with 0 lint errors and 125 warnings. The source dialog is available on route comparison and the active journey. Live map colors and summaries remain unknown; scores are confined to the fictional recorded rehearsal. The older n8n planner is disabled and refuses execution; Cloud delivery workflow 04 remains the delivery path.

The final APK was installed on the API 37 emulator. Selecting Technopolis and Wipro produced the 1,462 m route and an always-visible source button. Opening it showed the walking-time decision, zero street matches, five wider-area references, the dated first report, its unresolved location explanation and its publisher link; the Done control remained visible. Observed screenshots: [route comparison](../artifacts/saltlake-route.png) and [source explanation](../artifacts/saltlake-sources.png). This check started no journey and requested no alerts.

The production API loaded the audited hashed snapshot while preserving its database. No journeys or deliveries were active during restart. The former quick tunnel returned HTTP 530; a replacement public URL was configured and enrollment, actual place search and routing passed. No real calls or SMS were requested during these checks.

`scripts/verify_saltlake.mjs` checked the same public connection used by Android:

| Selected endpoints | Actual provider options | Shortest walk | Evidence result |
| --- | --- | --- | --- |
| Technopolis → Wipro, Sector V | 3 | 1,462 m | 5 wider-area references, 0 street matches, UNKNOWN safety |
| BG Block → BJ Block, Sector II | 2 | 1,089 m | 5 wider-area references, 0 street matches, UNKNOWN safety |

The exact geocoder pins, route lengths, snapping and snapshot hash are in [the live check](../artifacts/saltlake-live-check.json). An initial generic College More query selected a different location and yielded a 3.8 km walk; the final check uses specific verified landmark/block matches. These provider checks are not a physical GPS walk or proof of incident-location accuracy. A repeat after the final restart verified Sector V again but a Sector II place search returned `503 PROVIDER_UNAVAILABLE`; an earlier cold lookup also timed out. External geocoding availability is intermittent and remains a practical limitation. Failed searches are reported explicitly and do not create invented destinations. The successful proof artifact retains its original check timestamp. A focused retry of BG Block recovered the two expected Sector II matches. The emulator later failed to resolve the temporary tunnel address despite the correct saved URL; its final visual check uses `http://10.0.2.2:8787` against the same running API. This emulator-only address must not be used on the physical phone.

The [manifest](../artifacts/saltlake-evidence-manifest.json), [source review packet](../artifacts/saltlake-review/review.html), [unmeasured accuracy report](../artifacts/saltlake-accuracy.json) and [prepared partner requests](PARTNER_REQUESTS.md) record the current state. No institution has been contacted. The user is not expected to survey streets manually; independent provider/location evidence is still required to fulfill the street-accuracy objective. No physical phone was connected during this update, so its APK installation remains pending.

## Companion sharing request fix

Android now sends an explicit JSON object when creating a companion link. A Retrofit transport test verifies the POST path, JSON content type and nonempty `{}` body. The sharing endpoint also accepts a zero-byte body with an unsupported content type from older clients; this parser is scoped to that endpoint. Tests verify authentication, journey ownership, ended-journey rejection, rejection of nonempty binary input and unchanged parsing on other endpoints.

The running API was updated while preserving its SQLite data and public URL. Public requests with both empty `application/octet-stream` bodies and JSON `{}` returned 200, and their companion HTML and journey data loaded successfully. Temporary test journeys had no contacts and were removed. The updated APK was built, but the physical phone disconnected before installation; its installed app can use the server compatibility fix immediately.

## Rerouting and cloud SMS update

The user's `SheShield.json` was inspected: Telegram trigger, two Twilio call nodes, no Twilio SMS node and no Android delivery webhook. A local Cloud import was generated using its Twilio credential reference and the current account's sender settings. Import tests verify that legacy recipients, callbacks and tokens are not copied, that malformed account/sender configuration is rejected, and that each job retains its intended recipient. Duplicate allowlist entries and a misspelled Account SID variable in ignored `api/.env` were corrected.

Read-only Twilio checks verified an active Trial account, one registered sender supporting both voice and SMS, and the single allowlisted recipient matched a verified caller ID. `TEST_RECIPIENT_ALLOWLIST` contains the personal recipient; `TWILIO_FROM_NUMBER`/`TWILIO_SMS_FROM_NUMBER` contain the Twilio sender. The prepared import prioritizes these settings over the historical export.

`scripts/verify_rerouting.mjs` requested real ORS walking alternatives for Esplanade → Victoria Memorial while excluding a 35 m radius around a point 100 m ahead on the original route. Three returned geometries independently passed avoidance and upcoming-path difference checks: 3,379 m, 3,397 m and 3,351 m (79, 72 and 76 geometry vertices). This checks provider routing, not street safety or actual GPS movement.

Rerouting tests cover fresh/accurate GPS, refusal of unchanged or intersecting routes, expired proposals, movement beyond the proposed start, mismatched IDs, an async calculation completing after journey end, retained prior avoidances, immutable destination and watch deadline, idempotent acceptance and outdated check-in state. The emulator loaded and accepted an alternative from simulated Kolkata GPS; monitoring continued on the replacement geometry. Preview controls fit within a fixed scroll viewport, with explicit option numbers and route decision buttons. [Rerouting preview](../artifacts/reroute-preview.png).

Cloud SMS tests use an injected dispatcher and isolated stores. They verify one request per recipient, separate SMS/voice branches in the importable n8n worker, one-time claims, signed monotonic callbacks, invalid signatures/provider IDs, cancellation, uncertain outcomes without duplicate sends, disabled delivery after restart and a stalled cloud job becoming outcome unknown. Delivered SMS never acknowledges safety. No real n8n execution, call or carrier SMS was requested.

After the user published the Cloud workflow, GET on its production URL reported a registered POST webhook. An unauthenticated empty POST returned 403; the same empty POST with the saved `X-Worker-Token` returned 202/accepted. This verifies publication and the webhook's shared credential. These empty probes contain no alert ID or recipient and fail the prepared workflow's input/configuration validation before delivery; an input-validation failure in their execution history is expected. They do not verify the Twilio nodes or real delivery.

Full Cloud metadata inspection remains unavailable on the n8n trial. The app's public tunnel stopped once during acceptance (HTTP 530); it was restored and both device URLs were updated while preserving sessions, contacts and history. A stable hosted API is required for everyday availability.

### Authorized real delivery test

The user explicitly approved one real call and one SMS to their verified personal phone. A separate API session created one LIVE alert with that single contact and no location. Both jobs passed through the published n8n Cloud workflow and Twilio:

- SMS: SENT at 4.0 seconds, DELIVERED at 5.9 seconds after alert creation.
- Voice: RINGING at 5.1 seconds, IN_PROGRESS at 9.2 seconds, ACKNOWLEDGED at 27.2 seconds after the user pressed 1.
- The live API accepted signed Twilio status and acknowledgement callbacks. Exactly one voice attempt and one SMS attempt were stored.

The acknowledgement confirms receipt of the alert; it does not mark the person SAFE. The proof artifact contains statuses and elapsed times without phone numbers or credentials: [Cloud delivery check](../artifacts/cloud-delivery-check.json).

The approved test intentionally included no GPS position. Following the user's feedback about the spoken missing-location line, calls now omit that line when GPS is absent. When GPS exists, calls say latitude/longitude and a relative recording age instead of reading an ISO timestamp. SMS preserves the exact map link and recording timestamp, or clearly states that no location was shared. Missing/invalid timestamps no longer cause message construction to fail or invent a fresh recording time. Message tests and the worker claim regression passed; the revised wording was applied to the live API without placing another call or SMS.

After the interrupted restart, the former quick-tunnel address stopped resolving. A replacement tunnel was started and `PUBLIC_BASE_URL` updated. Public enrollment, place search and a 693 m walking plan with three alternatives passed again. No Android device was connected over ADB, so its saved API URL needs updating in Settings. The prepared Cloud worker reads the callback API address from each authenticated delivery job.

Live calls and cloud SMS are now enabled with the one verified number in the test allowlist. Android contacts must match that allowlist to receive Cloud alerts. This observed test validates the current account and recipient; it does not establish delivery to other recipients or future provider/tunnel uptime.

The rerouting debug APK was installed on the connected physical phone using an update install. Its existing contacts, history, API session and enrollment were retained. No active tracking service was running during installation. This confirms installation and launch; a physical GPS walk still requires a separate test.

## Street evidence and personal watches

The API suite verifies precise incident admission, source bounds and swapped-coordinate rejection, uncertainty at the route corridor boundary, event deduplication, centroid/private-space exclusion, invalid calendar dates, stale and partial coverage, whole-corridor coverage, local exposure peaks, and walking geometry pieces no longer than 50 m. Legacy city-wide bounds cannot certify street coverage. Coverage metadata exposes an explicit field allowlist.

Personal-watch tests verify registration without GPS or incident evidence, valid windows, immutable pending deadlines, unique timeout escalation without further phone updates, SQLite process restart recovery, companion acknowledgement without SAFE resolution, stale companion event rejection, link revocation, and end-of-journey sharing shutdown. An unavailable first contact now permits the next configured contact to be queued. These tests use isolated stores and never dispatch real calls.

The rebuilt debug APK was installed on the API 37 emulator without clearing contacts/history. A two-minute personal watch showed the countdown and visible SAFE/SOS actions; SAFE returned the journey to ACTIVE. A separate 20-second rehearsal expired into SOS_ACTIVE and exposed the simulated contact timeline. The APK's bundled evidence plan matches the current generated asset.

`scripts/verify_companion.mjs` passed in Chromium at a 412 × 915 viewport: readable text, no horizontal overflow, acknowledgement, absent/stale GPS, server timeout, stopped sharing after journey end, and no browser JavaScript errors. The script creates an in-memory API with simulated delivery and removes it when finished. Browser tooling lives in the ignored `.tools/` directory in this configured workspace:

```bash
FONTCONFIG_FILE="$PWD/.tools/browser/fonts.conf" \
LD_LIBRARY_PATH="$PWD/.tools/browser/sysroot/usr/lib" \
PLAYWRIGHT_BROWSERS_PATH="$PWD/.tools/browser/browsers" \
PLAYWRIGHT_MODULE="$PWD/.tools/browser/node_modules/playwright/index.mjs" \
.tools/runtime/node/bin/node scripts/verify_companion.mjs
```

On a machine with Node 24, Playwright and Chromium installed normally, run `node scripts/verify_companion.mjs`. The `PLAYWRIGHT_MODULE` override selects a separate tooling installation. Current API tests can also run through `.tools/runtime/node/bin/node --test --test-isolation=none api/test/*.test.js` from this workspace.

Observed screenshots: [personal watch](../artifacts/walk-with-me.png), [rehearsal timeout](../artifacts/watch-timeout.png), and [companion page](../artifacts/companion-watch.png). See [the product demonstration](PRACTICALITY.md) for a judge-facing sequence and [the data admission rules](INCIDENT_DATA.md) for the source requirements.

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

- Cloud calling, SMS delivery and press-1 acknowledgement passed for the approved verified recipient. Other recipients need account/trial eligibility and their own delivery checks. Follow [the import checklist](../n8n/IMPORT_CHECKLIST.md).
- Five Salt Lake public references are installed as area context. No precise incident partnership or independent location reference sample has been acquired. Live safety remains UNKNOWN; see [incident-data instructions](INCIDENT_DATA.md).
- No physical-phone GPS walk, Android OEM background-policy behavior, or released/signed APK has been tested. The supplied artifact is a debug APK.
- Basemap tiles need internet or an existing cache. Recorded geometry and local rehearsal continue without the API; editable custom route calculation needs the provider/API. There is no promised offline street map.
- The local API and temporary public tunnel must remain running. A quick-tunnel URL changes on restart, requiring updates to the API public URL and Android connection. The prepared worker reads the API URL from each authenticated job; a manually fixed Cloud Configuration URL also needs updating. Production uptime/deployment is outside these observed checks.
- Force-stopping Android stops its local service until the app reopens. A previously registered live API deadline can still expire server-side. GPS accuracy and walking-map coverage limit navigation precision; inaccessible pins are rejected instead of receiving fabricated paths.
