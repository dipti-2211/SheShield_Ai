# SheShield — Master AI Coding-Agent Prompt

This file is the master execution prompt for the coding agent. Give the agent access to the repository and the existing `SheShield.json` workflow export, then provide the prompt below as its primary instruction.

---

## Prompt


You are the primary senior engineer responsible for turning the existing SheShield prototype into a complete, demonstrable Android application and a reliable backend/orchestration system.

You have access to the repository/project files and the attached `SheShield.json` n8n workflow export. Treat that workflow as an existing system that must be carefully audited before you change anything.

Your job is not to produce a superficial UI, a scaffold, or a collection of disconnected demos. Build the complete end-to-end hackathon prototype, make the core safety journey work, update the n8n side where required, and leave behind excellent documentation so another engineer can reproduce, test, demo, and understand the project.

Do not ask me to make architecture choices that you can reasonably make yourself. You are a frontier coding model: use your own engineering judgment, current documentation, and the project constraints below. When several implementations are valid, choose the simplest robust approach and explain the decision afterward. Do not over-engineer the system.

---

# 0. Hackathon Cost and Deployment Constraint

The hackathon target is a **near-zero-cost / ₹0 prototype**.

Assume the following deployment model unless there is a compelling technical reason not to:

- n8n Community Edition runs locally on the developer's Windows laptop, preferably through Docker.
- The laptop is expected to remain powered on and connected to the internet during the demo.
- The Android app is run from Android Studio on a physical Android device or emulator.
- A free/local tunnel may be used only when an externally reachable webhook is required.
- Prefer free API tiers, open-source software, public/free datasets, and pay-as-you-go services only where unavoidable.
- Do not introduce recurring paid hosting merely for convenience.
- Do not design the architecture around n8n Cloud, a paid VPS, Firebase paid services, or other monthly infrastructure unless you can demonstrate that the free/local architecture cannot satisfy the core demo.
- Real telephony may use a free/trial allowance during development/demo when available. Also provide a deterministic/mock/demo path for testing the escalation UX if real calls cannot be exercised reliably.
- Use quotas/rate limits/cost controls on externally billed APIs wherever the provider supports them.

The project does **not** need 24/7 uptime, production cloud deployment, Play Store distribution, or always-on backend infrastructure for the hackathon.

The final documentation must include a clear "Zero-Cost Local Demo" setup that explains how the laptop, n8n, Android app, tunnel (if needed), external APIs, and telephony fit together.

If you identify a paid dependency that is genuinely necessary, explicitly justify it, state its expected cost/usage, and provide the cheapest viable fallback for non-demo development.


---

## 1. Product Context

SheShield is a personal safety journey assistant.

The intended experience is:

1. A user enters where they want to go.
2. SheShield obtains a route or multiple viable route alternatives.
3. Available crime/risk intelligence is used to estimate the reported-risk exposure of each route/route segment.
4. The app presents the route and its risk context clearly.
5. The user starts a trip.
6. During the trip, the app tracks location while the trip is active.
7. When the user enters an elevated-risk segment/area, the app gives a safety check-in prompt.
8. The user can confirm that they are safe.
9. If a safety check-in expires or the user explicitly triggers SOS, the system escalates.
10. The system attempts to contact the user and, when appropriate, contacts trusted people and shares the latest available location/trip context with them.
11. The user can explicitly initiate an emergency call using the appropriate emergency-service mechanism for the target country/region.
12. A trip ends when the user reaches the destination or explicitly stops it.

This is a hackathon prototype. The goal is a convincing, technically credible, reliable demonstration — not a claim that SheShield can perfectly predict crime or guarantee personal safety.

Use careful wording such as "reported-risk", "crime-data exposure", or equivalent language. Do not represent incomplete or aggregate crime statistics as exact real-time street-level danger.

---

## 2. What Matters Most

Optimize for these priorities, in this order:

1. The end-to-end demo must actually work.
2. The safety flow must be understandable and deterministic enough to demonstrate.
3. The risk calculation must be explainable and must not hallucinate data.
4. The Android experience should feel native and polished.
5. The system should remain lightweight enough for a hackathon and a developer with little Android experience to maintain.
6. External dependencies should be minimized where they do not materially improve the demo.
7. Failure modes should be handled gracefully.
8. The project must be documented so setup and testing are reproducible.

Do not add flashy features that weaken reliability of the core journey.

---

# 3. First: Audit the Existing System Before Coding

Before implementing anything, inspect:

- the repository structure
- all relevant source files/configuration
- `SheShield.json`
- any existing README/docs
- any existing API/backend code
- any existing data ingestion/risk code
- build configuration
- deployment configuration
- environment-variable conventions
- tests, if any

Create an internal implementation map before editing files.

Specifically inventory:

- what the current system really does
- what it only claims to do
- what is missing
- what is unsafe or unreliable
- what can be reused
- what should be replaced
- what can be postponed

Do not blindly rewrite a working component.

The attached workflow is the source of truth for the current n8n implementation. Important observations you should verify yourself while auditing include:

- It currently starts from a Telegram message/location trigger.
- The AI Agent uses Gemini and is connected to Pinecone retrieval.
- The Pinecone vector store is configured with `topK: 2`.
- The AI prompt asks the model to reverse-geocode and search nearby crime/RAG information even though those capabilities are not all implemented as direct agent tools.
- The workflow parses an LLM JSON response into `risk_level`.
- One IF branch checks specifically for `MEDIUM`, while the warning text describes a "high-risk area".
- The workflow waits and then calls Telegram `getUpdates` to inspect a reply.
- The reply check compares against a text value rather than robustly binding the response to a specific trip/user/session.
- Twilio is used for calls, but the current escalation flow is timer-driven rather than being fully driven by call status callbacks.
- Reverse geocoding currently occurs in the later escalation path.
- The current export contains hardcoded sensitive values, including a bot token in an HTTP URL and hardcoded phone numbers. Treat exposed credentials as compromised and do not reuse them blindly.
- The exported workflow contains no actual route-generation/routing node.

Do not assume any of these observations are correct without verifying them in the file and/or current project.

---

# 4. Security Rule — Protect Secrets Immediately

Treat all secrets found in source files, JSON exports, screenshots, logs, or configuration as sensitive.

Do not:

- copy secrets into new source files
- commit secrets
- put API keys in Android source
- put Twilio credentials in the APK
- expose bot tokens in URLs
- hardcode user emergency contacts in the workflow
- include real personal phone numbers in demo fixtures

Where the existing export contains credentials or hardcoded sensitive values:

1. Remove them from new production-facing artifacts.
2. Replace them with environment variables, n8n credentials, secure configuration, or equivalent.
3. Preserve the original workflow only as an untouched reference/backup.
4. Document any credential rotation that should be performed.
5. Never print an actual secret into the final documentation.

If a secret appears to have been exposed publicly, explicitly flag that it should be rotated.

---

# 5. Do Not Lock Yourself Into My Suggested Implementation

You have freedom to choose the exact technologies and internal structure.

I care about the outcome, not about blindly following one architecture I may have suggested previously.

Choose the implementation that best satisfies:

- native-feeling Android UX
- low operational complexity
- reliable location handling
- reliable API communication
- predictable demo behavior
- easy local development
- easy debugging
- straightforward testing
- minimal unnecessary dependencies

For the Android framework and libraries, use your own judgment. Native Android is a natural default, but if you identify a materially better approach for this specific project, you may choose it. Explain the decision in the final documentation.

Do not introduce heavyweight architectural ceremony just because it is a known best practice.

Avoid unnecessary:

- multi-module architectures
- excessive dependency-injection layers
- state-management frameworks when simple state will work
- complex databases
- authentication systems that are not required for the hackathon
- microservices for their own sake
- cloud infrastructure unrelated to the product demo

Prefer one understandable system over many theoretically scalable systems.

---

# 6. Target Architecture

Design the system so that the Android app and n8n have clear responsibilities.

A good separation will generally look like:

### Android/client responsibilities

The client should own the interactive experience and capabilities that need to feel immediate/native, such as:

- location permission and active-trip location handling
- map rendering
- route preview
- trip state presentation
- safety countdown/check-in interaction
- native notifications
- manual SOS UI
- emergency-call UX
- trusted-contact management UI
- trip start/stop
- displaying risk explanations
- graceful handling of temporary network loss
- local state needed to keep the active-trip UI responsive

### n8n/backend responsibilities

n8n should be used as orchestration/integration rather than as the entire application.

Typical responsibilities include:

- route/risk orchestration
- calling external services
- risk-data retrieval
- optional LLM reasoning/explanation where useful
- trip-related server-side workflows
- SOS escalation
- Twilio integration
- Twilio status webhooks
- notification/escalation branching
- server-side state transitions if needed
- audit/logging where appropriate

You may introduce a small backend component alongside n8n if it materially improves reliability or state handling. Do so only when justified, and keep the architecture simple.

Do not force every client action through an LLM or n8n round-trip when it can be handled locally and reliably.

---

# 7. Risk Intelligence Must Be Data-Driven

This is one of the most important parts of the project.

First inspect the actual data available to the project/Pinecone index or any other source.

Determine whether the data actually contains enough information for geographic risk analysis.

Look for fields such as:

- latitude
- longitude
- incident date/time
- category
- severity
- source
- unique incident identifier
- administrative area
- other useful geospatial metadata

Do not assume that semantic retrieval is equivalent to geographic retrieval.

If the available dataset is aggregate rather than incident-level, acknowledge that explicitly and adapt the product wording and algorithm accordingly.

If the current dataset is insufficient for the desired street-level demo:

- do not fabricate real-world claims
- do not invent crime incidents
- do not silently pretend aggregate statistics are precise GPS-level information
- create a clearly labelled deterministic demo-data mode/fixture if needed so the hackathon demo can still exercise the complete product
- keep real-data mode and demo-data mode distinguishable in code and documentation

The system should be able to explain the evidence behind a risk result.

---

# 8. Risk Engine Design

Do not make the LLM the authoritative source of the final risk score.

Prefer an explainable risk-calculation layer that uses actual available data and explicit logic.

The exact formula is your decision.

Potential factors to consider include:

- incident proximity
- incident density
- severity
- recency
- category
- distance decay
- route-segment exposure
- time-of-day context, when actually supported by data
- data confidence/quality
- source reliability, if meaningful evidence exists

The resulting risk model should be:

- deterministic/reproducible
- explainable
- testable
- configurable
- resistant to obvious hallucinations

The LLM may be used for:

- explaining a computed result in human-friendly language
- summarizing supporting evidence
- converting structured facts into concise UI copy

It should not invent crime records, coordinates, counts, timestamps, or sources.

If you retain an LLM in the risk path, put strict boundaries around what factual information it is allowed to use.

---

# 9. Geographic Logic

Use actual geospatial computation where appropriate.

For route risk, think in terms of route exposure rather than merely evaluating the destination or current GPS point.

The desired conceptual flow is:

origin + destination
→ obtain one or more valid routes
→ represent the route geometry
→ identify relevant risk data near route segments/corridor
→ aggregate risk exposure
→ return structured route risk information
→ render it in the app

The exact routing provider, spatial data structure, algorithm, storage, and segment-sampling approach are your decision.

Make the algorithm efficient enough for a hackathon demo and easy to explain to judges.

Avoid performing an expensive LLM/vector search on every GPS update unless you can prove it is necessary.

Where appropriate, precompute route risk and use local route geometry for frequent client-side checks.

---

# 10. Routing Experience

The user should not be shown a single black-box "safe route".

Prefer a route comparison experience whenever the chosen routing provider supports alternatives.

The app should be able to communicate concepts such as:

- travel time
- distance
- reported-risk exposure
- risk level
- relevant evidence
- trade-off between safety exposure and travel time

Example UI concept:

"Lower reported-risk exposure — +3 min"

The exact labels and visual design are your decision.

Do not call a route objectively "safe" when the underlying data cannot justify that claim.

---

# 11. Android UX Requirements

Design a polished, native-feeling safety app, not a generic CRUD app.

At minimum, the user journey should cover:

### Onboarding

- brief explanation of what SheShield does
- location permission
- notification permission if required by platform
- trusted-contact setup
- clear distinction between normal trip monitoring and emergency behavior

### Home

- current/starting location context
- clear primary action to plan/start a safe trip
- access to trusted contacts
- immediate SOS access
- compact safety status

### Trip planning

- destination selection
- route calculation
- clear route comparison
- risk visualization
- evidence/context for why risk differs

### Active trip

- map
- current position
- route
- trip status
- ETA where available
- active monitoring state
- clear SOS control

### Risk event/check-in

When the user enters an elevated-risk area/segment:

- obvious but non-panicky warning
- safety check-in action
- visible countdown
- explicit SOS action
- recovery once the user confirms safety

### SOS

The SOS experience should be immediate and stateful.

Show:

- SOS active state
- current/last-known location
- what the system is doing
- who has been contacted
- call status where available
- cancellation/recovery path where appropriate

### Trip completion

- clear successful-arrival state
- end monitoring
- clean up any active trip state

You may combine or split screens as you judge appropriate.

---

# 12. Background Location / Active Trip

Background/continuous location is a platform-sensitive area.

Implement it according to the current Android platform requirements rather than assuming a timer-based background task will always work.

The active-trip state should be explicit.

Conceptually:

START TRIP
→ establish active tracking
→ receive location updates
→ update local trip state
→ periodically communicate with backend where useful
→ stop tracking when trip ends

Design around:

- user permission state
- foreground/background behavior
- service lifecycle
- battery efficiency
- network loss
- process restart considerations
- emulator behavior

Do not overbuild this, but do not fake active-trip tracking with a UI timer.

Use current official Android documentation when implementation details depend on the platform version.

---

# 13. Safety Check-In Architecture

Do not use Telegram polling as the app's safety acknowledgement mechanism.

The app should be able to immediately handle:

- "I'm Safe"
- "SOS"
- timeout

The backend should receive an explicit event for the trip/session.

Use a clear trip/session identifier so check-ins cannot accidentally satisfy another user's or another trip's warning.

Design the state transitions explicitly.

For example, conceptually:

NORMAL
→ RISK_DETECTED
→ CHECK_IN_PENDING
→ SAFE
or
→ CHECK_IN_EXPIRED
→ ESCALATION

And:

ANY_ACTIVE_STATE
→ MANUAL_SOS
→ ESCALATION

You may implement this differently, but the state machine must be explicit, race-condition-aware, and testable.

---

# 14. Emergency Escalation

The current prototype attempts Twilio calls.

Redesign this so escalation is based on actual call events/status when the provider supports them, not merely "wait N seconds and assume X happened".

The exact provider workflow is your choice, but it should distinguish relevant states such as:

- answered/completed
- no answer
- busy/rejected
- failed
- otherwise unavailable

The escalation logic should be idempotent so duplicate callbacks do not cause accidental repeated escalation.

Trusted contacts must come from user configuration/trip state rather than hardcoded workflow values.

For emergency services, verify the current rules and provider limitations for the target geography. Do not assume a third-party calling provider can directly call emergency services in every country.

Support a clear manual emergency action in the app appropriate to the target location.

---

# 15. Failure Handling

This is a safety-oriented product, so silently failing is unacceptable.

Handle at least:

- location permission denied
- location temporarily unavailable
- low GPS accuracy
- network unavailable
- routing provider failure
- risk service failure
- backend timeout
- notification permission denied
- app restart during active trip, as reasonably feasible
- Twilio call failure
- Twilio callback duplicated or delayed
- stale location
- trip expired/ended unexpectedly

Do not pretend safety monitoring is active when the app knows it is not.

The UI should communicate degraded state clearly.

---

# 16. Authentication / User Identity

Do not build a complicated account system unless required.

However, trip/session requests must still be associated with the correct client/session.

Choose an appropriately simple mechanism for the hackathon and explain why.

Do not use anonymous hardcoded identifiers in a way that could cause users/trips to collide.

---

# 17. API Contract

Define a clean API contract between the Android app and backend/n8n.

The exact endpoint names, payloads, and versioning are your choice.

The contract should cover the core lifecycle at minimum:

- health/readiness
- trip planning
- trip creation/start
- active-trip location/heartbeat
- safety check-in
- manual SOS
- trip end/cancel
- Twilio/provider status callbacks
- any required demo/reset endpoints

Use structured JSON.

Make request/response models explicit.

Use timestamps and identifiers consistently.

Document the API.

Do not bury API contracts inside random string literals in the UI code.

---

# 18. n8n Changes

Audit the existing workflow and then produce the corrected n8n implementation.

Preserve the original `SheShield.json` as a reference; do not destroy the only copy of the existing workflow.

You may split the workflow into multiple smaller workflows if that improves reliability.

That is encouraged when the current workflow mixes unrelated concerns.

The resulting n8n design should have clear responsibilities for things such as:

- trip planning/routing
- risk evaluation
- trip location updates
- safety-check processing
- SOS escalation
- Twilio status handling

Exact workflow boundaries are your decision.

For every changed/created n8n workflow, provide:

- workflow purpose
- entry trigger/webhook
- inputs
- outputs
- external services
- required credentials
- state assumptions
- failure paths
- security notes
- how to import/configure it
- how to test it

If you have direct n8n access, modify the actual workflows where possible.

If you do not have direct n8n access, create importable workflow JSON files and a precise manual-change checklist instead.

Do not claim a workflow is deployed/working when you could only generate its JSON.

---

# 19. Do Not Overuse n8n

Do not force the following through n8n if local app handling is more appropriate:

- UI state
- countdown rendering
- basic map interactions
- local route-segment proximity checks when the data is already on-device
- every animation
- every GPS update
- simple notification presentation

n8n should orchestrate integrations and server-side decisions rather than becoming the Android runtime.

---

# 20. Demo Reliability Is a First-Class Requirement

Build a deliberate demo mode or deterministic test path if needed.

The judge should not have to hope that live external data happens to produce an interesting scenario.

The demo should allow a controlled sequence such as:

safe segment
→ elevated-risk segment
→ safety prompt
→ missed check-in
→ escalation
→ contact notification/call
→ location sharing
→ recovery

The exact mechanism is your decision.

Use emulator/device location simulation where useful.

Make it obvious when the system is using:

- live external data
- seeded demo fixtures
- mocked provider responses

Never label simulated data as real-world evidence.

---

# 21. Testing Requirements

Do not stop after the app compiles.

Implement and run appropriate automated tests for the highest-risk logic, especially:

### Risk engine

Test:

- no relevant incidents
- one nearby incident
- multiple incidents
- varying distance
- varying recency
- severity/category weighting
- route with mixed-risk segments
- deterministic repeated results

### API/backend

Test:

- valid requests
- invalid requests
- duplicate requests
- unknown trip/session
- expired trip
- backend dependency failure
- timeout behavior

### Trip state

Test:

- start
- location update
- risk transition
- check-in success
- check-in timeout
- manual SOS
- escalation
- trip end

### Android

At minimum manually verify on an emulator/device:

- first-run permissions
- trip start
- location movement
- route display
- warning
- countdown
- "I'm Safe"
- manual SOS
- degraded network behavior
- trip completion

### Telephony

Where real calls cannot be safely/cheaply exercised, provide a deterministic test path and document the limitation.

---

# 22. Build and Tooling

Set up the project so a developer can understand exactly how to:

- install prerequisites
- configure secrets
- run the backend/n8n dependencies
- run the Android app
- launch an emulator
- install the APK
- run tests
- run a complete demo

Avoid requiring a developer to discover undocumented commands.

Use current stable tooling/documentation appropriate to the environment.

If build problems occur, solve them rather than leaving the repository half-configured.

---

# 23. Code Quality

Write production-quality code appropriate for a hackathon.

Requirements:

- clear naming
- sensible package/module organization
- no giant monolithic files when separation is genuinely useful
- no unexplained magic constants
- structured error handling
- no swallowed exceptions
- no hardcoded secrets
- useful logs
- comments only where they clarify non-obvious decisions
- no placeholder TODOs on the core user journey
- no fake success states

Don't optimize prematurely.

---

# 24. Privacy and Safety

Treat location and emergency-contact information as sensitive.

Minimize data collection.

Do not log raw location unnecessarily.

Avoid putting precise location into analytics or debug logs unless needed.

Make clear:

- what is transmitted
- when it is transmitted
- who receives it
- what is retained, if anything

The project should not claim that route scoring guarantees safety.

Prefer "reported-risk exposure" or similarly defensible terminology.

---

# 25. Current-Technologies Verification

When implementing anything that depends on current platform/provider behavior, verify the current official documentation before finalizing it.

This applies especially to:

- Android background/foreground location behavior
- notification permissions
- routing provider APIs/terms
- Twilio calling/status behavior
- n8n webhook/security behavior
- Telegram bot behavior, if Telegram remains anywhere
- external crime/open-data APIs

Prefer primary/official documentation over blog posts.

Record meaningful provider assumptions in the final documentation.

---

# 26. UI Quality Bar

The app should look like a serious hackathon product.

Aim for:

- coherent visual hierarchy
- clear typography
- consistent spacing
- accessible contrast
- clear status states
- minimal clutter
- map as a central visual
- risk represented visually but responsibly
- obvious primary actions
- strong SOS affordance without making the entire UI alarming

Do not copy another product's branding.

Do not create unnecessary screens.

The app should feel native to its chosen platform.

---

# 27. Required Project Deliverables

When you finish, the project should contain all artifacts needed to reproduce the system.

At minimum:

1. Complete Android application source.
2. Any backend/helper source that you actually introduce.
3. Revised/importable n8n workflow JSON(s).
4. Any schemas/contracts/config examples needed to run it.
5. Test fixtures/demo data if required.
6. Automated tests for important logic.
7. Scripts or commands that simplify setup/demo.
8. A master project document named something clear such as:
   `SHE_SHIELD_MASTER.md`

Do not merely describe these artifacts. Create them.

---

# 28. Mandatory Final Documentation: SHE_SHIELD_MASTER.md

Create a comprehensive master Markdown document after implementation.

This document is not marketing copy. It is the technical source of truth for the project.

It must include:

## A. Project overview

- what SheShield does
- the end-to-end user journey
- the intended hackathon story
- what the system does not claim

## B. Architecture

Include a readable architecture diagram.

Explain:

- Android
- backend/API, if any
- n8n
- risk engine
- crime data
- vector database, if retained
- routing provider
- geocoding
- Twilio
- storage
- external services

Explain the boundaries and data flow.

## C. Repository map

Document important directories/files and their purpose.

## D. Android implementation

Explain:

- framework choice
- major dependencies
- screen/navigation structure
- location architecture
- active-trip lifecycle
- state model
- notifications
- SOS flow
- local persistence
- network layer

## E. Risk-engine implementation

Explain:

- actual data sources used
- data schema
- geographic logic
- scoring formula/weights
- thresholds
- confidence/limitations
- why the chosen method was used
- exactly where LLMs are and are not used

## F. Routing implementation

Explain:

- routing provider
- how alternatives are obtained
- route geometry handling
- route-risk calculation
- what the UI displays

## G. n8n changes

This section must be extremely concrete.

For every old workflow/node removed, changed, or retained:

- name
- purpose
- what changed
- why
- relevant inputs/outputs
- dependencies

Also provide the new workflow layout.

## H. API contract

Document endpoints/actions and example payloads.

## I. Environment variables and secrets

List:

- variable name
- what it is for
- where it is configured
- whether it belongs in n8n, backend, or local development

Never put the actual values in the document.

## J. Setup instructions

A new developer should be able to go from a clean machine/project checkout to a running app.

Include:

- prerequisites
- Android setup
- SDK requirements
- emulator/device setup
- API/backend setup
- n8n setup/import
- credential setup
- routing provider setup
- crime-data setup
- Pinecone setup if retained
- Twilio setup
- optional demo-mode setup

## K. Running the app

Give exact commands and IDE steps.

## L. Testing

Give:

- automated test commands
- manual test procedure
- expected results
- emulator GPS procedure
- demo mode procedure
- Twilio test procedure

## M. Complete hackathon demo script

Write a deterministic step-by-step script that can be followed in front of judges.

Include what to click and what should happen.

Keep the core demo short enough to perform reliably.

## N. Troubleshooting

List likely failures and exact recovery steps.

## O. Security/privacy

Document secret handling, location-data handling, and anything that should be rotated/revoked.

## P. Known limitations

Be honest.

Explicitly document limitations around:

- data freshness
- spatial precision
- false positives/negatives
- connectivity
- GPS accuracy
- provider reliability
- emergency-service constraints
- demo-mode vs real-data differences

## Q. Decisions and rationale

For significant architecture choices, explain:

- what alternatives were considered
- why the selected approach was appropriate for this project

Do not create a giant essay. Focus on decisions that matter.

## R. Future improvements

Only include meaningful follow-ups that fit the product.

Do not bloat the roadmap with unrelated features.

---

# 29. Definition of Done

Do not declare completion until you have attempted to verify all of the following:

### Product

- [ ] Android app launches.
- [ ] Onboarding works.
- [ ] Location permission flow works.
- [ ] Trusted contacts can be configured.
- [ ] User can select a destination.
- [ ] Route planning works.
- [ ] Route risk is displayed.
- [ ] User can start a trip.
- [ ] Active location tracking works within platform constraints.
- [ ] Risk transition can be demonstrated.
- [ ] Safety check-in appears.
- [ ] "I'm Safe" clears the warning.
- [ ] Missed check-in reaches the escalation system.
- [ ] Manual SOS reaches the escalation system.
- [ ] Escalation uses real provider status where applicable.
- [ ] Location/trip context can be shared with trusted contacts.
- [ ] User can end the trip.
- [ ] App handles common dependency failures without silently claiming success.

### Backend/n8n

- [ ] Current workflow has been audited.
- [ ] Broken HIGH/MEDIUM branching is corrected.
- [ ] Telegram polling is removed from the native safety-check path.
- [ ] Trip/session identity is explicit.
- [ ] Hardcoded contacts are removed.
- [ ] Exposed secrets are removed from new artifacts.
- [ ] Routing is actually implemented or explicitly mocked in demo mode.
- [ ] Risk calculation is explainable.
- [ ] Geographic relevance is handled correctly.
- [ ] Twilio escalation is status-aware.
- [ ] Workflows are modular enough to debug.
- [ ] Importable JSON artifacts exist if direct n8n deployment was unavailable.

### Testing

- [ ] Core risk-engine tests pass.
- [ ] Important trip-state transitions are tested.
- [ ] Android app builds successfully.
- [ ] APK/debug install is verified.
- [ ] End-to-end happy path is verified.
- [ ] At least one failure/escalation path is verified.
- [ ] Demo path is repeatable.

### Documentation

- [ ] `SHE_SHIELD_MASTER.md` exists.
- [ ] Architecture is documented.
- [ ] n8n changes are documented precisely.
- [ ] Setup is reproducible.
- [ ] Environment variables are documented.
- [ ] Testing instructions are documented.
- [ ] Demo script is documented.
- [ ] Known limitations are documented.
- [ ] No secrets are present in the documentation.

---

# 30. Zero-Cost Default

Unless a feature genuinely requires paid infrastructure, implement and document the hackathon system so that it can run with:

- local n8n Community Edition
- the developer's laptop as the backend/orchestration host
- Android Studio + emulator/physical device
- free/public data
- free API quotas where available
- trial or minimal pay-as-you-go telephony only when necessary

Do not add production cloud infrastructure simply because it is a conventional architecture.

# 31. Final Working Style

Work autonomously.

Do not stop at "here is what you could build".

Actually build it.

When something is incomplete, either:

- implement it,
- create a clearly labelled demo-mode substitute,
- or document the exact blocker and provide the closest working alternative.

Do not silently fake functionality.

Do not claim external services were successfully tested if you could not access them.

Run builds/tests before finishing.

Inspect your own changes.

Prefer fixing root causes over patching symptoms.

Keep the project hackathon-friendly and understandable.

At the end, provide a concise final engineering report containing:

1. What you built.
2. What you changed in n8n.
3. What data/risk limitations you discovered.
4. What commands/tests you ran.
5. What remains externally configurable.
6. Exact path to `SHE_SHIELD_MASTER.md`.
7. Exact path to the revised n8n workflow JSON(s).
8. Any issue that genuinely could not be verified.

Your success criterion is not "the UI exists".

Your success criterion is:

**A judge can watch a person plan a journey, see route-risk reasoning, start the trip, enter an elevated-risk segment, receive a safety check-in, intentionally miss it, observe escalation, and understand exactly how the app, risk engine, n8n, routing system, crime data, and telephony fit together — without the system relying on unexplained AI magic or pretending that imperfect crime data is perfect.**
