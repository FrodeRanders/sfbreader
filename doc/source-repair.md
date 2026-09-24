# Source-guided boundary recovery

Hybrid mode now uses the text payload to recover missing structural markup in the HTML payload before creating the exported model. It continues to compare the resulting model with an independent text parse.

## Reproduce SFB output

Use the stored XML to keep the source snapshot fixed:

```sh
mvn test package
java -jar target/sfsreader-1.0-SNAPSHOT.jar -s hybrid -o 'data/Socialförsäkringsbalk (2010-110).json' -- data/sfs-2010-110.txt.xml
python3 tools/flatten_json.py -i 'data/Socialförsäkringsbalk (2010-110).json' -o 'data/Socialförsäkringsbalk (2010-110)-flat.json'
```

The Java reader writes `source-repair.json`, `reconciliation.json`, `reconciliation-report.txt`, `reconciliation-new-high.txt`, and the periodisering reports beside its input. Reports describe the source snapshot supplied to that run. They are not a determination of current legal applicability.

## Recovery policy

`TextStructure` records candidate section openings and preceding headings with line numbers. It rejects wrapped references with lowercase continuations and chapter references continuing the previous line. Chapter headings require a structural/blank-line boundary. It retains repeated section numbers as possible versions.

`HybridSourceRepair` compares these observations with the HTML anchors and text nodes in the same chapter. A missing section anchor is inserted only when:

- the section has one occurrence in the text observations;
- its opening matches one HTML text span in that chapter;
- that span begins at a source line boundary, or follows a heading separately observed immediately before the section in the text.

Existing HTML section anchors are retained except for the source-aligned chapter-address correction described below. The repair splits existing text nodes; it never invents missing legal wording or copies wording across representations. Ambiguous or absent matches produce unresolved findings. A heading is promoted only if an entire remaining HTML text node matches one observed text heading in that chapter. Heading detection remains a heuristic, recorded in the report for review; it does not establish the legal level of every heading.

`source-repair.json` records each action, its reason, decoded-text line number, HTML text-node content and offsets, and hashes of the text payload and the original parsed DOM. Text lines are one-based; offsets are Java UTF-16 offsets with an exclusive end. Section node ordinals refer to the original DOM; heading node ordinals refer to the DOM after section splitting. The report describes both conventions. Preserve the original XML alongside reports for reproducibility.

## Other corrections

- Text mode accepts raw text files as well as XML text payloads.
- Ordinary headings preceding sections are retained as headings instead of becoming the preceding provision's text.
- A wrapped `9 kap. / 2 § skatteförfarandelagen` reference stays in its provision.
- A wrapped `59 kap. Försäkringstiden ...` reference does not move subsequent sections into chapter 59.
- Stycken in text output receive increasing numbers. A blank line before a list does not detach the list from its introduction.
- Inline `Avdelning B ...` labels in benefit lists do not create new top-level divisions.
- Amendment identifiers under transition provisions are parsed as transition entries. Transition identifiers are local to each document so independent parses can align.
- Reconciliation includes chapters directly under an act, checks stycke boundaries and one-sided version markers, and no longer accepts a shorter prefix as equivalent text.
- Numeric changes (including decimals and ranges) cannot be dismissed by punctuation stripping as format-only changes.

## Scope and remaining review

Hybrid mode exports HTML structure with evidence-backed boundary repairs; it is not a general merger that silently chooses between conflicting legal wordings. Comparison findings remain separate from the repair report. Text heading and blank-line interpretation still involve heuristics. Unresolved periodisering and other content/structure differences must remain visible to downstream consumers.

Tests cover the actual SFB fixture, including 25 kap. 16 and 26 §§, exact text, headings, wrapped references, all restored section anchors, conservation of HTML wording, repeated execution, ambiguity, chapterless acts, transitions, and comparison sensitivity to omitted qualifiers and numeric changes. The existing fixture test now asserts detection of the original missing SGI sections before repair instead of requiring heading-contamination defects to persist.

## Innehållsöversikter och felaktigt uppdelade kapitelrubriker

Riksdagens separata `div.sfstoc` hoppas över. En innehållsöversikt som ingår i
lagtexten bevaras däremot i sin paragraf. Avdelningsetiketter som följs av
kapitelposter på formen `1 kap. - Lagens innehåll` ändrar inte avdelningskontexten.
Detta gäller både text- och HTML-läsning, oberoende av lagens namn eller nummer.
I hybridläget avmarkeras sådana `h2`-element till `span`; varje åtgärd registreras
som `contents_division_as_body`. SFB:s tidigare hantering av löpande
`Avdelning B ...`-etiketter finns kvar.

Två angränsande HTML-kapitelrubriker kan fogas samman endast om:

- deras sammanslagna text motsvarar en entydig, fullständig kapitelrubrik i textkällan;
- inga synliga texter ligger mellan rubrikerna;
- samtliga efterföljande paragrafankare fram till nästa kapitel/avdelning har den
  felaktiga kapiteladressen och deras öppningstexter matchar entydigt i rätt textkapitel.

Då flyttas rubrikens befintliga textnoder och ankarnas kapitelprefix rättas inom
just detta område. Inga paragraftexter kopieras mellan källorna. Rubrikåtgärden
registreras som `split_chapter_heading`, varje ändrad ankaradress som
`chapter_anchor_address`. Tvetydig paragrafmatchning ger ett olöst fynd.
Detta hanterar exempelvis avslutningen `52 kap. inkomstskattelagen` i rubriken
till Skatteförfarandelagens 21 kap., utan undantag hårdkodade för den lagen.

Texttolkningen behåller flerradiga kapitelrubriker. Tabulatoravgränsade tabellrader
med en paragrafhänvisning som första cell bildar inte nya paragrafer. Detsamma gäller
en ensam paragrafmarkör på nästa rad efter hänvisningsord som `i` eller `enligt`.

Rubrik-, avdelnings- och ankaråtgärder använder `-1` för textnodsindex och positioner
som inte är tillämpliga. För ankaråtgärder innehåller `originalHtmlText` den gamla
adressen och `sourceText` den nya. Källans hash och rubrikens textrad binder dem till
underlaget. Styckegränser och kvarstående textskillnader redovisas fortsatt separat.
