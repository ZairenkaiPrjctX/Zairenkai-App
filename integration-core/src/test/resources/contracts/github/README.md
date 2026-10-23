# GitHub Releases API

`releases-zairenkai-app-2026-10-23.json` is the real answer of
`GET https://api.github.com/repos/ZairenkaiPrjctX/Zairenkai-App/releases?per_page=5`
(header `Accept: application/vnd.github+json`, API version 2022-11-28), fetched with curl on
2026-10-23. It is an empty list: the app had no published release yet.

No populated answer could be captured here (no release exists, and this environment reaches
only the ZairenkaiPrjctX repositories). `UpdateTest` therefore builds its populated feeds in code,
named `synthetic`, with only the fields GitHub documents for a release (`tag_name`, `name`,
`body`, `draft`, `prerelease`, `published_at`, `html_url`, `assets[].name`,
`assets[].browser_download_url`, `assets[].size`, `assets[].state`). Replace them with a capture
once the first release is published.
