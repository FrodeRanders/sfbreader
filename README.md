# sfbreader

Försök till "digitalisering" av Socialförsäkringsbalken (extrakt av text med struktur).
Det finns ett syskon-projekt [sfbanalys](https://github.com/FrodeRanders/sfbanalys), som kanske kan vara av intresse.

## Status
Det är ett iterativt arbete att lyckas gå från text (eller i förekommande fall HTML) till motsvarande strukturerat format (JSON). Vi är skapligt där just nu. 

## Användning

> curl -o sfs-2010-110.html https://data.riksdagen.se/dokument/sfs-2010-110.html

> java -jar target/sfbreader-1.0-SNAPSHOT.jar -t template/sfs.stg -- sfs-2010-110.html
