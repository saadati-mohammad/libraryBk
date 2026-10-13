# Book metadata sources

## Current lookup path

The staff catalog form can look up a book by ISBN or search by title/author. Results from independent providers are normalized, merged, and shown for staff review; a provider match never silently creates a catalog record.

| Provider | How it is used | Configuration | Coverage / limitations |
| --- | --- | --- | --- |
| Open Library | ISBN lookup and title/author search | `OPEN_LIBRARY_BASE_URL` (defaults to `https://openlibrary.org`) | Public, community-maintained catalog; records and language coverage vary. Respect its low-volume API guidance. |
| Google Books | ISBN and text search; Persian-language metadata is mapped when returned | `GOOGLE_BOOKS_BASE_URL` and `GOOGLE_BOOKS_API_KEY` | Google documents public-volume requests with an API identifier. This provider is skipped when the key is blank. Coverage depends on Google's indexed records and API access/quota. |

The production query is bounded to ten candidates per provider, has connect/read timeouts, deduplicates ISBN editions, and isolates provider failures. Persian and Arabic-Indic ISBN digits are normalized before lookup. An ISBN can be syntactically valid and still have no metadata record.

## Iranian catalog coverage

The National Library and Archives of Iran exposes its public OPAC at <https://opac.nlai.ir/opac-prod/index.jsp>. In the source review for this change, no public, documented API contract for that catalog was located, and the OPAC timed out during direct verification. This does not prove that no private or partner interface exists. The project will not scrape an undocumented catalog or claim complete Iranian/world coverage without permission, a stable API contract, and a live integration test.

Iranian titles can currently be returned if they exist in Open Library or Google Books. To enable Google Books, configure `GOOGLE_BOOKS_API_KEY`; without it the service deliberately avoids making requests that the provider documents as requiring an API identifier. When no provider has a title, staff can still enter the valid ISBN and book metadata manually.

## Verification and remaining work

Automated H2-backed provider tests cover Persian title mapping, Persian-digit ISBN normalization, ISBN query construction, API-key propagation, provider disablement without a key, deduplication, and provider error handling. These use controlled HTTP fixtures; they prove the integration contract, not that a particular Iranian publication is present in an external index.

Before marking regional metadata coverage complete, verify a real Iranian ISBN against a permitted live source. If a public API, partner credential, or licensed source becomes available, add it as an isolated provider with bounded requests, source attribution, configuration, and fixture plus live contract tests. Do not treat a timeout or a not-found response from one source as proof that a book does not exist.

## References

- [Google Books API: volumes.list](https://developers.google.com/books/docs/v1/reference/volumes/list)
- [Google Books API: using the API and credentials](https://developers.google.com/books/docs/v1/using)
- [Open Library API documentation](https://openlibrary.org/developers/api)
- [National Library and Archives of Iran OPAC](https://opac.nlai.ir/opac-prod/index.jsp)
