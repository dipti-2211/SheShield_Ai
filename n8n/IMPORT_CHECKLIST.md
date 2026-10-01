# n8n Workflow Import Checklist

## Before import
- [ ] n8n Community Edition running on localhost:5678
- [ ] Docker container started: `docker run -p 5678:5678 n8nio/n8n`

## Import order
1. `n8n/workflows/01_plan_trip_risk.json`
2. `n8n/workflows/02_active_trip.json`
3. `n8n/workflows/03_sos_escalation.json`

## After import – configure each workflow

### Workflow 1 (Plan Trip & Risk)
- [ ] Open "Pinecone: Crime Data Lookup" node → select your Pinecone credential → confirm index = `hackolution`
- [ ] In n8n Variables, set `ORS_API_KEY`
- [ ] Activate workflow

### Workflow 2 (Active Trip)
- [ ] No credentials needed – uses Code nodes with static data
- [ ] Activate workflow

### Workflow 3 (SOS Escalation)
- [ ] Open "Twilio: Call Contact 1" node → select your Twilio credential
- [ ] In n8n Variables, set `TWILIO_FROM_NUMBER` (E.164)
- [ ] In n8n Variables, set `N8N_WEBHOOK_BASE_URL` (your ngrok HTTPS URL)
- [ ] Activate workflow

## Verify
- [ ] `curl http://localhost:5678/webhook/health` → `{"status":"ok",...}`
- [ ] All 3 workflows show green/active status

## Credentials in original SheShield.json (ROTATE THESE)
- Telegram bot token: `8608000445:AAHn7d70QkFZKVnZ3PBNpOV8YIAH3bdyp9o` → revoke via BotFather
- Twilio number used: `+17077541636` → reset auth token in Twilio Console
- Hardcoded emergency contact `+91 7488971265` → REMOVED from all new workflows
