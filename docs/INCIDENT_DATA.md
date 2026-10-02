# Street evidence and its limits

**No verified live Kolkata incident feed is installed.** Live routes show unknown evidence. The recorded demo uses fictional incidents, prominently labelled. A working importer is not a data partnership or a claim that every incident is reported.

## What the public sources support

Research checked on 2 October 2026:

| Source | Useful for | Does not establish |
| --- | --- | --- |
| [NCRB district crime catalog](https://www.data.gov.in/catalog/district-wise-crimes-committed-against-women) | Regional reported-crime context. This catalog describes district totals and lists an older update date. | Which street on a 1 km walk had an incident; current, complete Kolkata coverage. |
| [Kolkata Police station directory](https://kolkatapolice.gov.in/know-police-station/) and [contacts](https://kolkatapolice.gov.in/contact/) | Police station addresses, contacts and jurisdiction descriptions. | Incident coordinates, crime density, or a promise that a particular station entrance is accessible now. |
| [Safetipin methodology](https://safetipin.com/methodology/) | A possible partner for dated street audits: lighting, visibility, walkability and related observations. | A publicly licensed feed that this project already has access to, or criminal records. |
| Reviewed news reports | A reported event with an attributable source and a location uncertainty. | Complete reporting coverage. A newspaper's silence cannot certify a street. |

Do not geocode a district statistic to its centroid and call it a street incident. Do not map every article about the same case as a separate crime. Do not assign a private/residential incident to passing pedestrians' street exposure. No offender database or victim profiles are needed.

## Implemented admission rules

`api/src/evidence.js` validates a version 3 dataset supplied through `INCIDENT_DATA_PATH`.

- A steward must supply a source registry, geographic scope, license, source record URL and ID, shared event ID, incident/retrieval/review timestamps, reviewer reference, category, setting, and coordinate uncertainty.
- Registry bounds constrain plausible coordinates. They **do not** establish reporting coverage. This rejects a Kolkata record with latitude and longitude accidentally swapped.
- Only reviewed public-space incidents with `verified_coordinate` or `verified_address` and uncertainty of 1–100 m enter street scoring. The steward must inspect the source and location; software cannot certify truth merely because `verification` says `reviewed`.
- Neighbourhood centroids, imprecise locations and non-public settings are kept as area context, outside street scoring. Records without usable provenance, valid dates or plausible coordinates are rejected. The engine retains only a small field allowlist; it does not publish victim names, narratives or addresses.
- Stable event IDs and publisher record IDs prevent double counting. Cross-publisher event linkage remains a review responsibility.
- Only the previous 365 days contribute. Old or invalid imported dates are rejected; already-loaded records age out during route evaluation.
- A source's complete geocoded feed can establish reporting coverage only with a documented collection method, an explicit reporting window covering the analysis year, resolution no worse than 100 m, and updates/window end within 30 days. News cannot declare coverage. Rejected incident rows prevent a dataset being treated as clean complete coverage.
- Reporting coverage is checked against the **whole 150 m route corridor**, not just endpoints or a city bounding box. A rectangle must lie inside the area actually collected; use multiple conservative rectangles instead of enclosing an irregular jurisdiction and claiming its gaps.
- The 30-day freshness threshold, 100 m admission threshold, 150 m corridor and exposure weights are conservative prototype choices, not validated predictions of harm.

Run an audit before configuring the API:

```bash
node scripts/audit_incidents.mjs /path/to/reviewed-incidents.json
```

The report prints counts and rejection reasons, not raw incident payloads. An invalid schema or rejected rows produce a nonzero exit code. `/v1/evidence/status` exposes the audit and source registry to the enrolled app. Restart the API after replacing its dataset.

## Version 3 example

**Schema illustration only. The following is synthetic and must not be loaded as real evidence.** Replace every illustrative record with independently reviewed, licensed source material. The example intentionally has `is_real_data: false` so it cannot accidentally certify a live route.

```json
{
  "schema_version": 3,
  "is_real_data": false,
  "sources": [{
    "id": "example-feed",
    "name": "Synthetic schema example",
    "kind": "official",
    "url": "https://example.org/feed",
    "license": "Illustration only",
    "bounds": [88.20, 22.40, 88.60, 22.80]
  }],
  "incidents": [{
    "event_id": "example-shared-case-id",
    "source_id": "example-feed",
    "source_record_id": "example-publisher-record",
    "source_url": "https://example.org/report/1",
    "lat": 22.567,
    "lng": 88.350,
    "category": "harassment",
    "setting": "public_space",
    "incident_date": "2026-09-30T12:00:00Z",
    "retrieved_at": "2026-10-01T12:00:00Z",
    "reviewed_at": "2026-10-01T14:00:00Z",
    "reviewed_by": "internal-steward-reference",
    "verification": "reviewed",
    "location_method": "verified_coordinate",
    "precision_meters": 30
  }],
  "coverage": { "areas": [] }
}
```

An empty `coverage.areas` is correct for individually curated reports. Positive evidence will still appear; the app keeps overall safety unknown. For an actual complete feed, an area additionally needs:

```json
{
  "source_id": "example-feed",
  "bounds": [88.34, 22.55, 88.36, 22.59],
  "collection": "complete_geocoded_feed",
  "resolution_meters": 50,
  "window_start": "2025-09-01T00:00:00Z",
  "window_end": "2026-10-01T00:00:00Z",
  "updated_at": "2026-10-01T12:00:00Z",
  "method_url": "https://example.org/collection-method"
}
```

Categories: `violent_crime`, `sexual_assault`, `robbery`, `kidnapping`, `assault`, `harassment`, `theft`, `vandalism`, `traffic_incident`, `other`.

## What a short walk now shows

Road geometry is divided into pieces no longer than 50 m; turn indexes are remapped to preserve navigation. This is **analysis resolution**, not a claim of 50 m crime-location accuracy.

Each route exposes:

- Reports and gaps along distance from the start, including a continuous unknown stretch.
- Distance-weighted reported exposure and the highest local exposure; a long quiet section cannot hide the peak in the comparison.
- Coordinate uncertainty: e.g. an incident 200 m away with ±100 m uncertainty may overlap the 150 m corridor. The conservative nearest plausible distance contributes to the heuristic, and the full distance interval is shown.
- Source links, review dates, report counts, and reporting windows.

When any alternative lacks complete comparable reporting coverage, routes sort by walking time. The UI does not select a “safest route.” When alternatives have complete matching source windows, the ranking considers peak local exposure, then route average and time.

**Remaining spatial limits:** straight geographic distance does not determine whether two points share a street, entrance, bridge, wall, or accessible path. Road-side matching and barrier-aware incident attribution require reviewed street references or police GIS. The app explicitly says a nearby report can be on another street. Coverage of reported incidents also cannot measure unreported harm.

## Pinecone

No Pinecone credentials/index or incident dataset are configured in the inspected workspace. Embedding similarity cannot substitute for incident geography.

`scripts/export_pinecone.mjs` now writes an **unreviewed staging file** (`INCIDENT_STAGING_PATH`, default `./data/incidents-staging.json` from `api/`). It cannot write to the configured live data path. It preserves available source/precision metadata and sets `is_real_data: false` and `verification: pending`. A steward must complete the registry, review and event linkage before the version 3 importer can accept records.

## Practical route to real coverage

Start with two or three named station-to-campus/workplace corridors, each 1–2 km. Secure an agreement for de-identified public-space incidents from police or a credible local partner. Ask for source IDs, date ranges, geocoding precision, completeness limits, update cadence, correction/deletion process and permitted reuse.

Have two reviewers independently place each candidate incident against the actual public street/entrance. Disagreement becomes a wider uncertainty or area context, never a confident pin. Review duplicated news coverage against the same event ID. Field-audit walking access and lighting in the relevant time window as a **separate conditions dataset**; do not call darkness a crime report. Obtain permission before importing a commercial/partner audit.

Before expanding, measure held-out geocoding error, missed/duplicated cases, coverage by route metre and time window, stale records, check-in completion and false escalation. Publish the sample size and results. There is no honest substitute for this validation through a larger synthetic heatmap.
