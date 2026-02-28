# JSON-schema och ELI-mappning

Detta dokument beskriver hur schemat i `schema/sfs-law.schema.json` kan mappas till ELI-koncept.

## 1) Övergripande modell

Schemat hanterar två serialiseringar av samma norminnehåll:
- `hierarchicalLaw`: trädstruktur (lag -> kapitel -> paragraf -> stycke -> punkt)
- `flatLaw`: radvis tillplattning för efterbearbetning

Båda kan mappas till samma ontologi genom att behandla `flatEntry` som en vy av en paragraf/stycke-resurs.

## 2) Rekommenderad mappning mot ELI

- `hierarchicalLaw.id` -> `eli:id_local` (nationell identifierare, t.ex. `2010:110`)
- `hierarchicalLaw.namn` -> `eli:title`
- Lagobjektet (`hierarchicalLaw`) -> `eli:LegalResource`
- Kapitel (`kapitel`) -> `eli:LegalResourceSubdivision` (nivå: chapter)
- Avdelning (`avdelning`) -> `eli:LegalResourceSubdivision` (nivå: part/division)
- Underavdelning (`underavdelning`) -> `eli:LegalResourceSubdivision` (nivå: subpart)
- Paragraf (`paragraf`) -> `eli:LegalResourceSubdivision` (nivå: article/section, beroende på er profil)
- Stycke (`stycke`) -> `eli:LegalResourceSubdivision` (nivå: paragraph)
- Punkt (`punkt`) -> `eli:LegalResourceSubdivision` (nivå: point)
- `referens[]` / `flatEntry.referens` -> relationer till ändrings- eller källakt (`eli:is_about`, `eli:based_on` eller egen profilrelation)
- `versionStatus`, `versionDate`, `versionIdentity`, `versionKind`, `periodisering` -> version/tidsdimension (uttrycksnivå), lämpligen via ELI-versionering + nationell profil

## 3) URI-strategi (förslag)

För stabil mappning bör varje nod få en kanonisk URI, t.ex.:
- lag: `/eli/se/law/{year}:{num}`
- kapitel: `/eli/se/law/{id}/kap/{kapitel}`
- paragraf: `/eli/se/law/{id}/kap/{kapitel}/p/{paragraf}` eller `/eli/se/law/{id}/p/{paragraf}` för lagar utan kapitel
- stycke: `.../sty/{stycke}`
- punkt: `.../pkt/{punkt}`

För `flatEntry` byggs samma URI deterministiskt från fälten `lag`, `kapitel`, `paragraf`, `stycke` och vid behov `avdelning`/`underavdelning`.

## 4) Varför detta undviker "enkel/komplex"

I stället för två ontologiklasser (enkel/komplex) används:
- en gemensam nodtyp för "subdivision"
- valfria nivåer i kedjan (avdelning/underavdelning/kapitel kan saknas)

Det gör modellen robust för alla fem lagtyper i datat utan specialfall i grundontologin.
