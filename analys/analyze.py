import spacy
import json
import re

file_path = "../data/SFB.json"

# Read the JSON data from the local file
with open(file_path, "r", encoding="utf-8") as file:
    data = json.load(file)

# pip install https://github.com/explosion/spacy-models/releases/download/sv_core_news_lg-3.7.0/sv_core_news_lg-3.7.0-py3-none-any.whl
nlp = spacy.load('sv_core_news_lg')

ignoredTokens = [
    '.', '§', ')', '-', 'a.', 'a', 'alkohol', 'allmänna', 'alla', 'andra', 'angelägenhet',
    'annan', 'annat', 'antal', 'antalet', 'andning', 'anmält', 'anses', 'antalet', 'användning',
    'användningen', 'april', 'artikel', 'augusti', 'automatiserat', 'av', 'avbrottet',
    'avdelning', 'avdelningarna', 'avled', 'avresa', 'avsikt', 'avsikten', 'avse', 'avser',
    'avses', 'avslås', 'avstående', 'avståendet', 'avvikelse', 'avvikelser', 'b', 'b.',
    'd', 'de', 'den', 'dem', 'denne', 'denna', 'dessa', 'detsamma', 'det', 'detta', 'då', 'där',
    'därefter', 'e', 'efter', 'eg', 'en', 'ett', 'f', 'fall', 'faktorn', 'fattades', 'februari',
    'felet', 'femårsperiod', 'ferier', 'fjärdedel', 'fjärdedels', 'fråga', 'förd', 'färden',
    'följande', 'följd', 'för', 'förelåg', 'före', 'först', 'första', 'från', 'fram', 'frågan', 'gjorts',
    'gäller', 'gång', 'gången', 'gånger', 'göras', 'han', 'hans', 'hela', 'helt', 'hel', 'hennes',
    'henne', 'hon', 'honom', 'har', 'i', 'inleddes', 'intresse', 'ingen', 'kap', 'kap.', 'kr',
    'kronor', 'kortare', 'kvar', 'legat', 'lämnas', 'längst', 'm.fl.', 'med', 'män', 'någon',
    'något', 'nio', 'nr', 'nytt', 'om', 'ordning', 'pengar', 'procent', 'räknat', 'senare', 'sig',
    'situationen', 'situations', 'sjuk', 'sjukt', 'ska', 'skedde', 'skillnaden', 'skriftligen',
    'skäligen', 'skötsel', 'solidariskt', 'som', 'sondmatning', 'stadigvarande', 'storlek',
    'storleken', 'strecksatsen', 'stycket', 'styckena', 'största', 'summan', 'syfte', 'säljer',
    'sätt', 'sådant', 'såvitt', 'tal', 'talet', 'till', 'tillbaka', 'tillfällen', 'tillfälligt',
    'tillgodoräknas', 'tillsyn', 'tillsynen', 'tillämpliga', 'tillämpligheten', 'timmar', 'timme',
    'tjugofjärde', 'tjänster', 'tolftedel', 'tolv', 'tre', 'tretton', 'tusental', 'underlåten',
    'unga', 'uppehåll', 'uppehälle', 'uppenbarligen', 'uppfyllt', 'upphävda', 'upphörande',
    'upphör', 'uppkommer', 'upplupen', 'upplysa', 'uppnådd', 'uppnår', 'uppnås', 'uppnått',
    'uteslutande', 'utförandet', 'utsträckning', 'vad', 'valt', 'var', 'vars', 'vart', 'vecka',
    'veckan', 'veckor', 'vikt', 'vilande', 'vilket', 'vi', 'vissa', 'vistas', 'vistelse',
    'vistelsen', 'vuxen', 'värdet', 'väsentligen', 'vårdat', 'vården', 'vård', 'yngsta',
    'ändrades', 'ändringar', 'ändringen', 'ändring', 'är', 'åren', 'året', 'år', 'återinsjuknande',
    'åtskild', 'åttondels', 'ökningen', 'ökning', 'öre', 'överklaga', 'överklagar', 'överstiger',
    'övervägs'
]

rangePattern = r'^\d+\-\d+$'
rangePattern2 = r'^[a-z]\-\d+$'
rangePattern3 = r'^[a-z]\-[a-z]+$'
decimalPattern = r'^\d+,\d+$'
decimalPattern2 = r'^\d+\.\d+$'
numberPattern = r'^\d+$'
lawPattern = r'^\d{4}:\d+$'

def extract_entities_and_relations(text, progress):
    doc = nlp(text)
    entities = []
    relations = []

    for token in doc:
        progress.write(token.text + " [" + token.dep_ + "] ")

        tokenText = token.text.lower()
        if tokenText not in ignoredTokens:
            if not (re.match(numberPattern, tokenText)
                    or re.match(decimalPattern, tokenText)   # 1,1234
                    or re.match(decimalPattern2, tokenText)  # 1.2
                    or re.match(rangePattern, tokenText)     # 1-100
                    or re.match(rangePattern2, tokenText)    # a-100
                    or re.match(rangePattern3, tokenText)):  # b-g
                # Identify noun phrases and named entities as potential entities
                if token.dep_ in ['nsubj', 'dobj', 'pobj', 'nmod', 'nsubj:pass']:
                    entities.append((tokenText, token.dep_, token.head.text.lower()))

                # Look for specific relations involving verbs and their arguments
                if token.dep_ == "nsubj" and token.head.dep_ == "ROOT":
                    subject = tokenText
                    verb = token.head.text
                    object = [child for child in token.head.children if child.dep_ == "obj" or child.dep_ == "dobj"]
                    if object:
                        relations.append((subject, verb, object[0].text.lower()))

    progress.write("\n")
    for entity in entities:
        progress.write("Entity: " + entity[0] + "\n")
    for relation in relations:
        progress.write("Relation: " + relation[0] + " " + relation[1] + " " + relation[2] + "\n")
    progress.write("\n")

    return entities, relations

def process_json(data, progress):
    texts = []
    def extract_texts(obj):
        if isinstance(obj, dict):
            for key, value in obj.items():
                if key == "text":
                    texts.append(' '.join(value))
                else:
                    extract_texts(value)
        elif isinstance(obj, list):
            for item in obj:
                extract_texts(item)
        return texts

    all_texts = extract_texts(data)

    all_entities = []
    all_relations = []

    for text in all_texts:
        entities, relations = extract_entities_and_relations(text, progress)
        all_entities.extend(entities)
        all_relations.extend(relations)

    return all_entities, all_relations

# Process the entire JSON data
print("Processing text...")
with open("progress.txt", "w", encoding="utf-8") as progress:
    entities, relations = process_json(data, progress)
    sorted_entities = sorted(set(entities), key=lambda x: x[0] + x[1] + x[2])
    sorted_relations = sorted(set(relations), key=lambda x: x[0] + x[1] + x[2])

    print("Entities:")
    for entity in sorted_entities:
        print(entity)

    print("\nRelations:")
    for relation in sorted_relations:
        print(relation)

# with open("analysis.txt", "w", encoding="utf-8") as f:
#     f.write("Entities: \n")
#     for entity in entities:
#         f.write("" + entity)
#         f.write("\n")
#
#     f.write("\n\nRelations: \n")
#     for relation in relations:
#         f.write("" + relation)
#         f.write("\n")

