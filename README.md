# Collector

Android app that builds a local searchable index from paginated web archive pages.

## Current MVP

- Requests `BASE_URL/page/{number}`
- Extracts titles with a configurable CSS selector
- Includes a small heuristic fallback when the selector is blank
- Stores only `title` and `page` locally with Room/SQLite
- Searches titles locally, including Korean text
- Avoids duplicate `(title, page)` entries
- Configurable page range

## Use

1. Enter the site's base URL, without `/page/...`.
2. Enter the title CSS selector if the site's title element is known. Example: `.entry-title`.
3. Set the first and last page.
4. Start collection.
5. Search locally by title.

The app does not download video files.

## Important

Only use the collector against sites/content you are authorized to access and index. Respect the site's terms, robots rules, rate limits, and applicable law.
