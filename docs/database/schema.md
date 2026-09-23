# DravyaSetu Database Schema

## Ownership
PostgreSQL is owned by Person 2.

## Starting application tables
The current mini-project starts with:
- `users`
- `plants`
- `identifications`
- `identification_images`
- `knowledge_documents`

Optional later tables include:
- `plant_translations`
- `chat_sessions`
- `chat_messages`

## Plant master
The `plants` table represents application data for the shared plant identities.

Minimum identity fields:
- `id`
- `plant_code` / shared `plant_id`
- `common_name`
- `scientific_name`
- application metadata as required

The authoritative supported-class mapping is `shared/constants/plant-classes.json`.

## Identification
`identifications` stores application-level identification records, including:
- id
- user_id
- plant_id
- status
- confidence
- model_version
- created_at

`identification_images` stores the images belonging to an identification:
- image reference
- plant part
- sequence number
- created_at

Person 1 performs multi-image evidence fusion. Person 2 stores the application record and image references; Person 2 does not recalculate AI results.

## Scope exclusion
The old SIH supply-chain tables such as batches, batch events, QR, collector/trader/wholesaler/manufacturer workflows and blockchain are not part of the current mini-project database unless the team explicitly reintroduces them later.
