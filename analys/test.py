import warnings
import spacy
from spacy.glossary import explain as spacy_explain
from spacy import displacy
from pathlib import Path

text = "Denna balk innehåller bestämmelser om social trygghet genom de sociala försäkringar samt andra ersättnings- och bidragssystem som behandlas i avdelningarna B-G (socialförsäkringen)."
text2 = """Den som får någon av följande förmåner är försäkrad för inkomstrelaterad sjukersättning och inkomstrelaterad aktivitetsersättning enligt 6 § 5, inkomstgrundad ålderspension enligt 6 § 8 och inkomstrelaterad efterlevandeförmån enligt 6 § 9 och 11:
1. omvårdnadsbidrag enligt 5 kap. 9 § 5,
2. arbetslöshetsersättning enligt lagen (2024:506) om arbetslöshetsförsäkring,
3. aktivitetsstöd till den som deltar i ett arbetsmarknadspolitiskt program,
4. ersättning till deltagare i teckenspråksutbildning för vissa föräldrar (TUFF),
5. dagpenning till totalförsvarspliktiga som tjänstgör enligt lagen (1994:1809) om totalförsvarsplikt och till andra som får dagpenning enligt de grunder som gäller för totalförsvarspliktiga, och
6. stipendium som enligt 11 kap. 46 § inkomstskattelagen (1999:1229) ska tas upp som intäkt i inkomstslaget tjänst.
Lag (2024:508).
"""

local_explanations = {
    "acl:relcl": "adverbial relative clause modifier",
    "nsubj:pass": "passive nominal subject"
}

def explain(term):
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        try:
            description = spacy_explain(term)
            if description is None:
                return local_explanations[term]
            return description
        except Exception:
            return term + "???"

# Load the text processing pipeline
nlp = spacy.load('sv_core_news_lg')

# Customizing tokenizer
# -- breaking up words
#special_case = [{ORTH: "lem"}, {ORTH: "me"}]
#nlp.tokenizer.add_special_case("lemme", special_case)

# -- fixing lemma for certain words
nlp.get_pipe("attribute_ruler").add([[{"TEXT": "avdelningarna"}]], {"LEMMA": "avdelning"})

print("="*80)
print("= Information about the language model:\n")
for item in nlp.meta["sources"]:
    for key, value in item.items():
        print(f"{key}: {value}")
    print()  # Print a blank line for separation between items

doc = nlp(text)

print("= 'doc' parts:")
print(dir(doc))
print()

# In case we have trouble understanding why text was tokenized a certain way
#tok_exp = nlp.tokenizer.explain(text)
#for t in tok_exp:
#    print(t[1], "\t", t[0])

print("="*80)
print(f"= Text: [{doc.lang_}]\n")
print(text)
print()

print("= Noun chunks:\n")
for chunk in doc.noun_chunks:
    print(chunk)
print()

print("= 'token' parts:")
print(dir(doc[0]))
print()

print("-"*80)
print("token.text                | explain(token.pos_)       | token.head.text           | explain(token.dep_)")
print("-"*80)
for token in doc:

    # Dumping information on part of speech (POS), dependency parsing (DEP)

    #lemma = ""
    #pos = token.pos_
    #match pos:
    #    case "VERB" | "NOUN":
    #        lemma = token.lemma_
    #
    #if lemma is None or lemma == '':
    #    print(f"{token.text:<25} | {token.pos_:<25} | {token.dep_:<25} | {token.head.text:<25} | {explain(token.pos_):<25} | {explain(token.dep_):<25}")
    #else:
    #    print(f"{token.text:<25} | {lemma:<25} | {token.pos_:<25} | {token.dep_:<25} | {token.head.text:<25} | {explain(token.pos_):<25} | {explain(token.dep_):<25}")

    match token.dep_:
        case "det": # determiner
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}" )
        case "obj": # object
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "case": # case marking
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "amod": # adjectival modifier
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "nmod": # nominal modifier
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "conj": # conjunct
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "nsubj": # nominal subject
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "nsubj:pass": # passive nominal subject
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case "acl:relcl": # relative clausal modifier of noun (adnominal clause)
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")
        case _:
            print(f"{token.text:<25} | {explain(token.pos_):<25} | {token.head.text:<25} | {explain(token.dep_):<25}")

print("-"*80)
entities = []
relations = []
rules = []
for token in doc:
    # Extract significant entities
    if token.pos_ in ["NOUN", "PROPN"]:
        entities.append((token.text, token.pos_))

    # Extract relationships
    if token.dep_ in ["nsubj", "dobj"]:
        subject = None
        if token.dep_ == "nsubj":
            subject = token
        objects = [child for child in token.head.children if child.dep_ in ["dobj", "pobj"]]
        for obj in objects:
            relations.append((subject.text if subject else token.head.text, token.head.lemma_, obj.text))

    # Extract rules
    if token.dep_ == "ROOT" or token.pos_ == "AUX":
        rules.append((token.text, token.lemma_, token.head.text, token.dep_))


print("-"*80)
print("Entities:")
for entity in entities:
    print(entity)

print("\nRelations:")
for relation in relations:
    print(relation)

print("\nRules:")
for rule in rules:
    print(rule)

#print("http://127.0.0.1:5001")
#spacy.displacy.serve(doc, style="dep", auto_select_port=True)

svg = displacy.render(doc, style='dep', jupyter=False)
filename = 'analysis.svg'
output_path = Path ('./' + filename)
output_path.open('w', encoding='utf-8').write(svg)


