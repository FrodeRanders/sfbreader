# sfbreader

Försök till "digitalisering" av Socialförsäkringsbalken (extrakt av text med struktur).
Det finns ett syskon-projekt [sfbanalys](https://github.com/FrodeRanders/sfbanalys), som kanske kan vara av intresse.

## Status
Det är ett iterativt arbete att lyckas gå från text (eller i förekommande fall HTML) till motsvarande strukturerat format (JSON). Vi är skapligt där just nu. 

## Användning

> curl -o sfs-2010-110.html https://data.riksdagen.se/dokument/sfs-2010-110.html

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -t template/sfs.stg -- sfs-2010-110.html

För XML-källa (`dokumentstatus`) med både `<text>` och `<html>`:

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -t template/sfs.stg -- data/sfs-2010-110.txt.xml

Val av källa:

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -s hybrid -t template/sfs.stg -- data/sfs-2010-110.txt.xml

`-s|--source-mode` accepterar `html`, `text` eller `hybrid` (default).
`-t|--template` är nu valfri: om den utelämnas produceras ingen LaTeX-utskrift, men `output.json` och övriga analysfiler skrivs fortfarande.

Välj rättslig "giltighetsdag" (filter för aktiva variant-paragrafer med `U:`/`I:`):

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -e 2028-07-01 -t template/sfs.stg -- data/sfs-2010-110.txt.xml

`-e|--effective-date` accepterar `YYYY-MM-DD`.
Det skapas även `effective-date-report.json` med urvalsstatistik.
Dessutom skapas `periodisering-schedule.json` med daterade övergångar och nästa övergångsdatum (för att signalera när ny körning kan behövas).

Validera periodiseringsmarkörer strikt:

> java -jar target/sfbreader-1.0-SNAPSHOT.jar --strict-periodisering -t template/sfs.stg -- data/sfs-2010-110.txt.xml

`--strict-periodisering` returnerar non-zero om ogiltiga/olösta markörer finns efter parsning/filtering.
Detaljer skrivs till `periodisering-validation.json`.

Alternativt kan läget sättas explicit:

> java -jar target/sfbreader-1.0-SNAPSHOT.jar --periodisering-mode strict -t template/sfs.stg -- data/sfs-2010-110.txt.xml

`--periodisering-mode` accepterar `strict`, `lenient` (default) eller `off`.
`--strict-periodisering` finns kvar för bakåtkompatibilitet och motsvarar `--periodisering-mode strict`.

Vid `hybrid` skrivs även:
- `reconciliation-report.txt`
- `reconciliation.json`
- `reconciliation-new-high.txt`

Baseline och CI-gating:

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -s hybrid -b data/reconciliation-baseline.txt -f -t template/sfs.stg -- data/sfs-2010-110.txt.xml

- `-b|--reconciliation-baseline` läser allowlist-nycklar (en per rad)
- `-f|--fail-on-new-high` returnerar non-zero om nya HIGH-fynd finns utanför baseline
- `-w|--write-reconciliation-baseline <fil>` skriver aktuell HIGH-baseline

Rekommenderad baselinefil i repo:

- `data/reconciliation-baseline.txt`

Snabb kontroll med baseline:

> tools/check_reconciliation.sh

Uppdatera periodiseringsbaselines från aktuell `reconciliation.json`:

> tools/update_periodisering_baselines.sh

Uppdatera alla baselines i ett steg (reconciliation + periodisering):

> tools/refresh_baselines.sh

Kontrollera om nästa periodiseringsövergång är inom ett visst intervall:

> tools/check_periodisering_schedule.sh data/periodisering-schedule.json 2026-02-26 7

Returnerar exit `14` om `nextTransitionDate <= referenceDate + windowDays`.

Kör alla kontroller i ett steg (tester + reconciliation + schedule guard):

> tools/check_all.sh

Alternativt med schedule-fönster i dagar:

> tools/check_all.sh data/sfs-2010-110.txt.xml 7

Kontrollera att `STATUS.md` innehåller baseline-snapshot som matchar baselinefiler:

> tools/check_baseline_note.sh

Skriptet gör nu även en separat regressionskontroll för:
- `paragraph_periodisering_mismatch` i `reconciliation.json`
- baselinevärde i `data/periodisering-mismatch-baseline.txt` (heltal)
- `paragraph_periodisering_unresolved` i `reconciliation.json`
- `paragraph_periodisering_invalid` i `reconciliation.json`
- baselinevärden i:
  - `data/periodisering-unresolved-baseline.txt`
  - `data/periodisering-invalid-baseline.txt`

Notera: om periodiseringsmarkörer (`/Upphör att gälla U:.../`, `/Träder i kraft I:.../`) blir kvar inne i paragraftext efter parsning, loggas varningar i `sfbreader.log`.
Varje paragrafvariant får även explicit versionsmetadata i `output.json`:
`versionStatus`, `versionKind`, `versionDate`, `versionIdentity`.
För lagar utan avdelning/kapitel (t.ex. med `P...`-ankare) exporteras paragrafnivå utan syntetiska `kapitel`-noder i `output.json`.

Generera RDF/Turtle från flattenad JSON (instansdata enligt `def:`-ontologin):

> tools/flat_json_to_ttl.py -i data/SFB-flat.json -o data/SFB-flat.ttl

För enkla akter utan avdelning/kapitel länkas `Paragraf` direkt till `Lag` (nya direkta egenskaper i ontologin).
Legacy-läge med syntetiskt kontextkapitel:

> tools/flat_json_to_ttl.py -i data/FL-flat.json -o /tmp/FL-flat.ttl --synthetic-context

Generera motsvarande Cypher (samma nod-id/struktur som TTL-exporten):

> tools/flat_json_to_cypher.py -i data/SFB-flat.json -o data/SFB-flat.cypher

Exempel på kapitel-lös akt med syntetiskt kontextkapitel (default):

> tools/flat_json_to_cypher.py -i data/FL-flat.json -o data/FL-flat.cypher

Legacy-läge med syntetiskt kontextkapitel:

> tools/flat_json_to_cypher.py -i data/FL-flat.json -o /tmp/FL-flat-synth.cypher --synthetic-context

Verifiera att TTL- och Cypher-export är konsistenta (regenerera + räknekontroll):

> tools/check_graph_exports.sh data/SFB-flat.json

Valfri output-katalog:

> tools/check_graph_exports.sh data/FL-flat.json /tmp

### Migration för konsumenter

Om du tidigare tolkade `periodisering` direkt i klientkod:

1. Läs i första hand:
   - `versionStatus`
   - `versionKind`
   - `versionDate`
   - `versionIdentity`
2. Använd `periodisering` endast som visningstext/fallback.
3. Bakåtkompatibilitet:
   - Om `version*` saknas (äldre JSON), fall tillbaka till tidigare logik på `periodisering`.
4. Rekommenderad tolkning:
   - `versionStatus = DATED` + `versionKind = I` => aktiv från och med `versionDate`
   - `versionStatus = DATED` + `versionKind = U` => aktiv till men exklusive `versionDate`
   - `versionStatus = UNRESOLVED` eller `INVALID` => behandla som manuell granskning/ej automatiskt beslutsbar

## Källor

### Rådata
https://data.riksdagen.se/dokument/sfs-2010-110.txt
https://data.riksdagen.se/dokument/sfs-2010-110.html

https://data.riksdagen.se/dokument/sfs-2017-900.txt


### Läsbart på webben
https://www.riksdagen.se/sv/dokument-och-lagar/dokument/svensk-forfattningssamling/socialforsakringsbalk-2010110_sfs-2010-110/
https://rkrattsbaser.gov.se/sfst?bet=2010:110
