# sfbreader

 ➜  java -jar target/sfbreader-1.0-SNAPSHOT.jar data/SFB.html > output.txt
 
```
AVDELNING: A ÖVERGRIPANDE BESTÄMMELSER
Push: Avdelning{id=A namn="ÖVERGRIPANDE BESTÄMMELSER"}
SUB-AVDELNING: I  Inledande bestämmelser, definitioner och förklaringar<br/>
KAPITEL: 1 Innehåll m.m.
Push: Kapitel{id=1 namn="Innehåll m.m."}
PARAGRAF: 1  (1 §)
Push: Paragraf{nummer=1 }
STYCKE(1): 1
Push: Stycke{nummer=1}
Denna balk innehåller bestämmelser om social trygghet
genom de sociala försäkringar samt andra ersättnings- och
bidragssystem som behandlas i avdelningarna B-G
(socialförsäkringen).

Pop: Stycke{nummer=1}
STYCKE(2): 2
Push: Stycke{nummer=2}
...
```

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
