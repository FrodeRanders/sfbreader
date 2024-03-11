# sfbreader

## Status
Denna version är inte verifierad att läsa HTML-filen samt bryta upp denna på rätt sätt. 
Det är till och med så att det finns tydliga indikationer på att detta inte görs rätt just nu,
men det är en början...

## Användning
 ➜ java -jar target/sfbreader-1.0-SNAPSHOT.jar data/SFB.html > output.txt
 
```
Push: Avdelning{id=A namn="ÖVERGRIPANDE BESTÄMMELSER"}
SUB-AVDELNING: I  Inledande bestämmelser, definitioner och förklaringar<br/>
Push: Kapitel{id=1 namn="Innehåll m.m."}
Push: Paragraf{nummer=1}
Push: Stycke{nummer=1}
Denna balk innehåller bestämmelser om social trygghet
genom de sociala försäkringar samt andra ersättnings- och
bidragssystem som behandlas i avdelningarna B-G
(socialförsäkringen).

Pop: Stycke{nummer=1}
Push: Stycke{nummer=2}

Pop: Stycke{nummer=2}
Pop: Paragraf{nummer=1}
Push: Paragraf{nummer=2}
Push: Stycke{nummer=1}
Balken är indelad i avdelningar, som betecknas med stora
bokstäver.

Pop: Stycke{nummer=1}
Push: Stycke{nummer=2}
Avdelningarna är indelade i underavdelningar, som betecknas
med romerska siffror.

Pop: Stycke{nummer=2}
...
```

I slutet spottas JSON ut, som är tänkt att representera en tolkad version av lagtexten.
```
{
  "namn": "Socialförsäkringsbalk",
  "id": "2010:110",
  "avdelning": [
    {
      "id": "A",
      "namn": "ÖVERGRIPANDE BESTÄMMELSER",
      "kapitel": [
        {
          "id": "1",
          "namn": "Innehåll m.m.",
          "paragraf": [
            {
              "nummer": "1 ",
              "stycke": [
                {
                  "nummer": 1,
                  "referens": [],
                  "text": [
                    "Denna balk innehåller bestämmelser om social trygghet",
                    "genom de sociala försäkringar samt andra ersättnings- och",
                    "bidragssystem som behandlas i avdelningarna B-G",
                    "(socialförsäkringen)."
                  ]
                }
              ]
            },
            ...
```
