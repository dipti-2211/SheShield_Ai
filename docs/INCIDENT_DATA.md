# Live incident evidence

Do not assume the existing Pinecone index contains usable incident data. Inspect its metadata first: each record needs a real latitude/longitude, a category, a date, and a source. Text embeddings or neighborhood-wide statistics without incident coordinates cannot support precise route-segment alerts.

The API accepts this shape through `INCIDENT_DATA_PATH`:

```json
{
  "is_real_data": true,
  "coverage": {
    "bounds": [88.20, 22.40, 88.50, 22.70],
    "source": "Name of the actual dataset",
    "license": "Dataset usage terms",
    "exported_at": "2026-10-02T00:00:00Z"
  },
  "incidents": [
    {
      "id": "source-record-id",
      "lat": 22.56,
      "lng": 88.35,
      "category": "harassment",
      "incident_date": "2026-09-01T00:00:00Z",
      "source": "Source record or publisher"
    }
  ]
}
```

The numbers above illustrate the schema; they do not establish Kolkata coverage or represent real incidents. Use only the area covered by your actual source. Invalid/future dates and coordinates are rejected; duplicate IDs are scored once. A route outside the stated coverage is shown as unknown. A real empty dataset within documented coverage can mean low *reported* exposure; missing data means unknown.

Categories: violent_crime, sexual_assault, robbery, kidnapping, assault, harassment, theft, vandalism, traffic_incident, other. The model uses a 500 m corridor, a 60-day recency half-life, geographic distance decay, and distance-weighted route aggregation. It is an explainable heuristic for a hackathon, not a calibrated prediction of harm.

## Optional Pinecone import

Configure these fields in ignored `api/.env`:

- `PINECONE_API_KEY`, `PINECONE_INDEX_HOST` (the index host from Console), `PINECONE_NAMESPACE`.
- `INCIDENT_SOURCE`, `INCIDENT_LICENSE`, `INCIDENT_COVERAGE_BOUNDS` as `west,south,east,north`.
- `INCIDENT_RECORDS_VERIFIED=true` only after confirming the records are actual dated incidents.
- `INCIDENT_DATA_PATH=./data/incidents.json` when running the exporter from `api/`.

Run from `api/`:

```bash
node --env-file=.env ../scripts/export_pinecone.mjs
```

The exporter lists all IDs with pagination, fetches batches, and exports metadata only. It rejects fictional/undated/malformed records and never prints credentials or full incident payloads. A serverless index is needed for vector ID listing. [Pinecone list API](https://docs.pinecone.io/reference/api/2025-04/data-plane/list), [fetch API](https://docs.pinecone.io/reference/api/2025-04/data-plane/fetch).

Restart the API after export. Check `/ready`, then calculate a route and inspect its evidence panel. No Pinecone dataset has been verified in this workspace yet; live coverage remains unknown.
