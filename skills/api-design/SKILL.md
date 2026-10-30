---
name: api-design
description: Standardizes API contracts, DTO-to-domain mapping, cursor pagination, retries, and offline caching.
---

# Skill: API Design & Integration

## Guidelines
1. **DTO vs. Domain**: Keep raw API response DTOs separate from immutable Domain Models. Map at the repository boundary.
2. **Typed Errors**: Map transport/HTTP errors into explicit domain failures (`Offline`, `Unauthorized`, `ValidationError`, `ServerError`).
3. **Pagination**: Prefer cursor-based pagination. Guard against duplicate page fetches in state holders.
4. **Offline Resilience**: Observe local database storage as the single source of truth while syncing remote data in the background.