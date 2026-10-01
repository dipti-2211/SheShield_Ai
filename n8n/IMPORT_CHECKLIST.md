# n8n Cloud delivery setup

Use your existing Cloud instance: https://alluvia.app.n8n.cloud. No local n8n installation is needed.

Import **only `n8n/workflows/04_cloud_delivery_v2.json`** for version 2. Workflows 01–03 and `SheShield.json` are historical references and are incompatible with the new API. Disable their old trip/SOS webhooks to avoid duplicate alerts.

1. In n8n, choose **Import from File** and select `04_cloud_delivery_v2.json`.
2. In **Configuration**, set:
   - `api_base_url`: your API's external HTTPS tunnel URL, without a trailing slash. This must match `PUBLIC_BASE_URL` in `api/.env`.
   - `account_sid`: your Twilio Account SID, beginning with `AC`.
   - `from_number`: your Twilio voice-capable phone number in international format.
3. Create an **HTTP Header Auth** credential named `SheShield worker`:
   - Name: `X-Worker-Token`
   - Value: the `WORKER_TOKEN` from your local `api/.env`.
   - Select this credential in **Delivery Job**, **Claim Attempt**, and **Record Request Result**. Delivery Job's authentication must be Header Auth.
4. Create/select an **HTTP Basic Auth** credential for **Place Call**:
   - User: Twilio Account SID.
   - Password: Twilio Auth Token.
   - Do not place the token in a Code or Configuration node.
5. Save and publish/activate the workflow. Use the production webhook URL:
   `https://alluvia.app.n8n.cloud/webhook/sheshield-delivery`.
6. Set `TWILIO_AUTH_TOKEN` in the ignored `api/.env` as well. The API uses it to verify callbacks. Your Twilio account's outgoing calls must be allowed to the chosen test recipient; trial accounts restrict recipients.
7. Set `TEST_RECIPIENT_ALLOWLIST` to the international numbers whose owners agreed to your test. Set `LIVE_ALERTS_ENABLED=true` only when ready for actual calls. Restart the API after changing its environment.
8. In the Android app, set its API URL and enrollment code under Settings → Demo connection. Start with `/ready` and a live route calculation. For a real SOS test, use a consenting contact and keep the API and tunnel running.

The API dispatches one job; the worker must claim it before calling Twilio. Duplicate claims fail. Ringing, no answer, completed without acknowledgement, and acknowledgement are separate states. Pressing **1** acknowledges the alert and stops future queued calls. A lost request response produces **REQUEST_UNKNOWN**, rather than placing a duplicate call. Cancel stops future escalation; an already ringing call or sent message cannot be recalled.

Workflow execution payload saving is disabled to avoid retaining contact/location data in routine execution history. View provider call logs and API delivery states when troubleshooting. Never test by POSTing arbitrary attempt IDs or bypassing the API claim step.

The historical source workflow contained exposed credentials. Rotate any credentials that were shared with that file. New documentation and the v2 workflow contain no secret values.
