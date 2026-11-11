# Institution Funding Portfolio

A REST API that takes an institution name, finds funding awards associated with it in the
[Europe PMC Grants API](https://dev.europepmc.org/GristAPI) (Grist), and links each grant to publications in the
[Europe PMC Articles API](https://dev.europepmc.org/RestfulWebService). Callers need no grant IDs or Europe PMC
query syntax.

Java 21 · Spring Boot 4.1 · Maven

## Build, run and test

With Docker:

```bash
docker build -t institution-portfolio .
docker run --rm -p 8080:8080 institution-portfolio
```

Without Docker (needs Java 21; the Maven wrapper downloads Maven itself):

```bash
./mvnw spring-boot:run
./mvnw test     # no network needed: Europe PMC is replaced by a local stub
```

Then open **http://localhost:8080/** for Swagger UI. The OpenAPI document is at `/v3/api-docs`.

## Example requests

```bash
curl 'http://localhost:8080/institution-portfolio?institution=University%20of%20Cambridge&limit=5&publicationsPerGrant=3'
curl 'http://localhost:8080/institution-portfolio?institution=University%20of%20Manchester'
curl 'http://localhost:8080/institution-portfolio?institution=Imperial%20College%20London&limit=10&publicationsPerGrant=5'
```

| Parameter | Required | Default | Allowed | Meaning |
|---|---|---|---|---|
| `institution` | yes | – | up to 200 characters, at least one letter or digit | matched against the award holder's affiliation |
| `limit` | no | 5 | 1–10 | grants to return |
| `publicationsPerGrant` | no | 3 | 1–5 | publications to return per grant |

The defaults keep one call to at most 1 Grants request and 5 Articles requests. Out-of-range values get a 400 rather
than being silently capped.

| Status | When |
|---|---|
| 200 | Portfolio built. This includes when no grants match (empty `grants`) and when some publication lookups failed (see each grant's `publications.status`). |
| 400 | Missing or invalid parameter. The body is an RFC 9457 problem detail naming the parameter. |
| 502 | The Grants API failed or returned something unreadable. |
| 504 | The Grants API did not respond in time. |

Example response to the first request above, abridged. The Grants API was down during development (see
[Limitations](#limitations)), so the Grants fixture in `src/test/resources/fixtures` stood in for it. The publications
are real, from the live Articles API.

```json
{
  "institution": "University of Cambridge",
  "grantSearch": { "query": "aff:\"University of Cambridge\"", "totalMatchingGrants": 4, "returnedGrants": 4 },
  "grants": [
    {
      "rank": 1,
      "grantId": "217100/Z/19/Z",
      "funder": "Wellcome Trust",
      "institution": { "name": "University of Cambridge", "department": null, "rorId": "ror.org/013meh722" },
      "publications": {
        "status": "FOUND",
        "query": "GRANT_ID:\"217100/Z/19/Z\"",
        "totalMatches": 22,
        "items": [
          {
            "source": "MED", "id": "42791267", "doi": "10.1038/s42003-026-11011-3",
            "pmid": "42791267", "pmcid": "PMC13614940",
            "title": "Actin conformation dynamics precede force generation in cytotoxic T lymphocytes.",
            "journal": "Communications biology", "publicationYear": 2026,
            "url": "https://europepmc.org/article/MED/42791267",
            "association": "VERIFIED", "associationNote": null
          }
        ],
        "error": null
      }
    },
    {
      "rank": 3,
      "grantId": "12345",
      "funder": "Wellcome Trust",
      "publications": {
        "status": "UNCERTAIN",
        "query": "GRANT_ID:\"12345\" OR GRANT_ID:12345/*",
        "totalMatches": 83,
        "items": [
          {
            "source": "PPR", "id": "PPR1162769", "journal": "VeriXiv",
            "association": "UNCERTAIN",
            "associationNote": "The article attributes grant ID 12345 to Gates Foundation, not Wellcome Trust."
          }
        ]
      }
    }
  ],
  "summary": {
    "note": "Counts cover only the grants and publications in this response. They are not the institution's complete funding portfolio.",
    "grantsReturned": 4,
    "grantsByFunder": { "Wellcome Trust": 2, "Medical Research Council": 1, "British Heart Foundation": 1 },
    "grantsByPublicationStatus": { "FOUND": 2, "UNCERTAIN": 1, "NONE_FOUND": 1, "LOOKUP_FAILED": 0 },
    "publicationAssociations": 9,
    "verifiedAssociations": 6,
    "uncertainAssociations": 3,
    "distinctPublications": 9,
    "matchedInstitutions": [
      { "name": "University of Cambridge", "rorId": "ror.org/013meh722", "grants": 2 },
      { "name": "University of Cambridge", "rorId": null, "grants": 1 },
      { "name": "University of Cambridge Clinical School", "rorId": null, "grants": 1 }
    ]
  },
  "caveats": ["..."]
}
```

## Design

### Response and identity

- One entry per grant, in the Grants API's order, with its `rank`, ID, title, funder and reported institution
  (name, department, ROR ID). Each grant has a `publications.status`:
  - `FOUND`: at least one publication is verified.
  - `UNCERTAIN`: publications were found by grant ID, but none could be verified.
  - `NONE_FOUND`: no publications cite the grant ID.
  - `LOOKUP_FAILED`: the lookup errored or timed out, and `error` says why.
- Each publication has an `association` (`VERIFIED` or `UNCERTAIN`), and an `associationNote` explaining any
  uncertainty.
- A publication is identified by its Europe PMC `source` plus `id` (e.g. MED 42791267, PPR PPR1162769), which every
  record has. DOI, PMID and PMCID are included when present, plus a link to Europe PMC.
- `grantSearch.totalMatchingGrants` is Grist's hit count for the query, and `summary` counts only what is returned.
  Both are labelled so they are not read as institutional totals. `summary.matchedInstitutions` shows which
  institutions the name search actually matched.

### Institution matching

- The name is sent as one phrase, `aff:"University of Cambridge"`. Unquoted, Grist would read
  `aff:University of Cambridge` as `aff:University` AND keyword `of` AND keyword `Cambridge`.
- Grist takes its parameters in the URL **path** (`/rest/get/query=…&resultType=core&format=json`), not a query
  string. So `" \ & ; = / ? # %` and control characters are replaced with spaces, and the phrase is then
  percent-encoded as a path segment. User input cannot close the phrase, add search terms or break the URL. Search
  indexes split words on punctuation, so this should not change what matches; names such as "Texas A&M" are worth
  checking against the live API.
- `resultType=core` is required, because only core results include institution details. One page (up to 25 grants) is
  fetched, and the first `limit` grants are kept in Grist's order.
- Limits of name matching:
  - `aff:` searches institution names *and departments*.
  - It misses variants and abbreviations ("Cambridge University", "UCL").
  - It can include other organisations whose names contain the phrase.
  - It only covers awards from Europe PMC's participating funders.

  The ROR IDs in `matchedInstitutions` make these limits visible to the caller.

### Linking grants to publications

- Each grant is looked up with `GRANT_ID:"<id>"` (extended for bare IDs, see below), using `resultType=core` (only
  core results include the article's grant list) and `pageSize=publicationsPerGrant`, keeping the Articles API's
  order.
- **Grant IDs are not unique across funders.** `GRANT_ID:"12345"` matches 83 articles, funded by the Gates
  Foundation, KWF, NWO and others. So a publication is `VERIFIED` only if its own funding metadata lists both the
  grant ID (ignoring case and spaces) *and* a funder matching the grant's. Otherwise it is `UNCERTAIN`, with the
  reason, rather than dropped or trusted.
- **Some IDs are also cited in a longer form.** Wellcome grant `107740` is cited as `107740` by 1 article, and in
  longer forms such as `107740/Z/15/Z` by 60 more. So for a bare ID (letters and digits only, at least one digit)
  the query is `GRANT_ID:"107740" OR GRANT_ID:107740/*`, and the longer form counts as the same grant ID.
  - Without this, that grant's only match would be a paper attributing `107740` to the National Research
    Foundation, which is correctly `UNCERTAIN`.
  - With it, papers citing `107740/Z/15/Z` with Wellcome Trust as the funder are `VERIFIED`, while papers naming
    only the programme ("DELTAS Africa Initiative", "DELGEME") stay `UNCERTAIN`.
  - The collision-prone `12345` gained no extra matches, and exact matches still rank first.
  - IDs that already contain separators, such as `MR/P014178/1`, are matched exactly.
- **Funder names differ between the APIs**, e.g. "UKRI | Medical Research Council", or "Wellcome" vs "Wellcome Trust".
  Names are compared after normalising case and punctuation, with compound agencies split on `|`, and one name may
  contain the other as whole words. This is lenient on purpose, since the grant ID must already match.
  Grist's own `pubMedSearchTerm` for the funder is also accepted.
- Filtering by funder inside the query was rejected: `GRANT_AGENCY:"MRC"` finds 0 articles for `MR/P014178/1`, where
  the full name finds 9.
- **Deduplication** happens only within a grant, by `source` + `id`. It never happens across grants: one paper can
  acknowledge several grants (MED:37860838 cites both a Wellcome and an MRC grant), so it is listed under each. The
  summary reports `publicationAssociations` and `distinctPublications` separately.

### Concurrency, timeouts and failures

- **Concurrency:** after the grant search, one lookup per grant starts at once on a fixed pool of 4 threads
  (`europepmc.max-concurrent-lookups`). The pool is shared by all incoming requests, so it caps this service's
  concurrent calls to the Articles API whatever the load. Results are collected in grant order, so each lands under
  its own grant regardless of which finishes first.
- **Timeouts:** each HTTP request gets 3 s to connect and 15 s to read, and each lookup has a 20 s deadline that
  includes time spent queued.
  - Most Articles searches take 1–2 s, but uncached searches with no hits were measured at about 13 s. A shorter read
    timeout would turn many genuine `NONE_FOUND` results into failures.
  - Worst-case response time is therefore about 35 s: the grant search plus one lookup deadline.
- **Failures:**
  - A lookup that fails or misses its deadline marks only its own grant `LOOKUP_FAILED`, with the reason; the other
    grants are returned normally.
  - If the grant search itself fails, there is nothing to return, so the API responds 502 (504 for a timeout).
- **HTTP/1.1 only:** Europe PMC's servers drop the JDK client's HTTP/2 connections ("EOF reached while reading"),
  which made every lookup fail until diagnosed. The client is pinned to HTTP/1.1.
- **Trade-offs:**
  - A timed-out lookup is reported straight away, but if its HTTP call is already running, the call keeps its
    thread until the read timeout.
  - A lookup still waiting in the queue when its deadline passes is skipped: it never calls Europe PMC
    (tested in `PortfolioServiceTest`). The queue itself is unbounded, so under sustained overload requests would
    wait and report `LOOKUP_FAILED` rather than being turned away with a 503.
  - There are no retries.

All of these settings are in `src/main/resources/application.properties`. They can be overridden at run time, for
example `docker run --rm -p 8080:8080 institution-portfolio --europepmc.lookup-timeout=30s`, or with an environment
variable such as `EUROPEPMC_MAXCONCURRENTLOOKUPS=2`.

### Code layout

```
src/main/java/io/github/yazar/portfolio
├── api/        controller and error handling (HTTP in)
├── service/    PortfolioService: grant search → concurrent lookups → summary
├── linking/    VERIFIED/UNCERTAIN rules and funder-name matching
├── model/      the JSON response contract
├── europepmc/  Grants and Articles API clients (HTTP out)
└── config/     settings, HTTP client, OpenAPI information
```

## Tests

`./mvnw test` runs 39 tests without network access. On every push, GitHub Actions (`.github/workflows/ci.yml`)
runs them, builds the Docker image, and checks that the container starts and rejects invalid input.

| Test class | What it checks |
|---|---|
| `InstitutionQueryTest`, `ArticlesClientTest` | Query construction and URL encoding, including input that tries to break out of the phrase |
| `GristResponseParserTest` | Grist JSON variants: a single record as an object, several funders or institutions listed as an array, a missing record list, missing fields, the older string form of `Funder` |
| `AssociationVerifierTest` | `VERIFIED` vs `UNCERTAIN`, funder-name variants, IDs shared across funders, the longer cited form of bare IDs |
| `PortfolioServiceTest` | Results stay with their grant when lookups finish out of order; one failure is isolated; `NONE_FOUND` vs `LOOKUP_FAILED`; the concurrency cap; the lookup deadline, including that an expired queued lookup never calls Europe PMC; counts for a paper shared by two grants |
| `InstitutionPortfolioApiTest` | The whole application over HTTP against WireMock standing in for Europe PMC: statuses, 400/502/504 (including a response that stalls halfway through its body), the exact Grist URL, the OpenAPI document |

The Articles fixtures are real Europe PMC responses, recorded and trimmed. The Grants fixture follows Grist's real
format (see below), but its titles and dates are illustrative.

## Limitations

- **The Grants API was down during development.** Every request, including the examples in its own
  documentation, got an empty reply.
  - The Grist mapping is based on its documentation and on archived real responses from 2024–2025 (Wayback Machine).
  - Those responses showed `HitCount` as a string, a historical change in the shape of `Funder`, and a malformed ID
    ("107743Elliott"); the parser tolerates all three.
  - The mapping has not been checked against a live response.
- **The Articles API is sometimes slow.** During development some searches took 20 s or more. A lookup that
  passes the 15 s read timeout shows as `LOOKUP_FAILED` ("did not respond in time"), while the other grants are
  still returned. Trying again later usually works.
- **Not every form of a grant ID is searched.** A bare ID also finds its longer form, but a grant that Grist stores
  in the long form (`217100/Z/19/Z`) does not find articles citing only the short form, and IDs such as
  `MR/P014178/1` are matched exactly. Some linked publications are still missed.
- **Funders are compared by name.** Papers that name only a funding programme, such as "DELTAS Africa Initiative"
  for Wellcome money, stay `UNCERTAIN`. A mapping from programme to funder would fix this but is not built.
- **`NONE_FOUND` does not prove a grant has no publications**, because funding metadata on articles is incomplete.
- **Results are a sample, not a portfolio.** Only the first page of Grants results is used.
- **Not production-hardened:** no caching, no retries, and no rate limiting of this API's own callers.

## Review of my solution

<!-- To be written in my own words. -->

### Which design decision worked particularly well?

### What would you change or improve with more time?

### Which assumption about Europe PMC data would you validate before production?

### Which piece of technical debt did you deliberately accept?
