
import json
# import re

file_path = "../data/SFB.json"

# Read the JSON data from the local file
with open(file_path, "r", encoding="utf-8") as file:
    data = json.load(file)

def extract_texts(data):
    texts = []

    def traverse(obj, context):
        if isinstance(obj, dict):
            current_context = context.copy()
            if "nummer" in obj:
                if "kapitel" in context:
                    current_context["paragraf"] = obj["nummer"]
                else:
                    current_context["kapitel"] = obj["nummer"]
            if "namn" in obj:
                current_context["namn"] = obj["namn"]
            if "avdelning" in obj:
                current_context["avdelning_id"] = obj["avdelning"].get("id", "")
                current_context["avdelning_namn"] = obj["avdelning"].get("namn", "")
            if "underavdelning" in obj:
                current_context["underavdelning_id"] = obj["underavdelning"].get("id", "")
                current_context["underavdelning_namn"] = obj["underavdelning"].get("namn", "")
            if "rubrik" in obj:
                current_context["rubrik"] = obj["rubrik"]
            if "underrubrik" in obj:
                current_context["underrubrik"] = obj["underrubrik"]
            if "referens" in obj:
                referens = obj["referens"]
                if referens:
                    current_context["referens"] = referens[0]
            if "periodisering" in obj:
                # if "kapitel" in context:
                #    current_context["paragraf_periodisering"] = obj["periodisering"]
                # else:
                #    current_context["kapitel_periodisering"] = obj["periodisering"]
                if "kapitel" in current_context and "paragraf" not in current_context:
                    current_context["kapitel_periodisering"] = obj["periodisering"]
                if "paragraf" in current_context:
                    current_context["paragraf_periodisering"] = obj["periodisering"]
            if "stycke" in obj:
                for stycke in obj["stycke"]:
                    if isinstance(stycke, dict):
                        stycke_context = current_context.copy()
                        stycke_context["stycke"] = stycke["nummer"]
                        concatenated_text = "\n".join(stycke["text"])
                        texts.append({"context": stycke_context, "text": concatenated_text})

            for key, value in obj.items():
                traverse(value, current_context)
        elif isinstance(obj, list):
            for item in obj:
                traverse(item, context)

    traverse(data, {})
    return texts

# Extract the texts with contextual information
texts_with_context = extract_texts(data)

def assemble_stycke(item):
    context = item["context"]
    avdelning = f"{context.get('avdelning_id')} {context.get('avdelning_namn')}"
    underavdelning = f"{context.get('underavdelning_id')} {context.get('underavdelning_namn')}"

    dict = {
        "lag": "Socialförsäkringsbalk (2010:110)",
        "avdelning": avdelning,
        "underavdelning": underavdelning,
        "kapitel": context.get('kapitel')
    }

    kapitel_periodisering = context.get('kapitel_periodisering')
    if kapitel_periodisering:
        dict["kapitel_periodisering"] = kapitel_periodisering

    rubrik = context.get('rubrik')
    if rubrik:
        dict["paragraf_rubrik"] = rubrik

    underrubrik = context.get('underrubrik')
    if underrubrik:
        dict["paragraf_underrubrik"] = underrubrik

    dict["paragraf"] = context.get('paragraf')

    paragraf_periodisering = context.get('paragraf_periodisering')
    if paragraf_periodisering:
        dict["paragraf_periodisering"] = paragraf_periodisering

    referens = context.get('referens')
    if referens:
        dict["referens"] = referens

    dict["stycke"] = context.get('stycke')
    dict["text"] = item["text"]

    return dict

with open("sfb-flat.json", "w", encoding="utf-8") as file:
    file.write('[\n')
    post_count = 0
    for item in texts_with_context:
        if post_count > 0:
            file.write(",\n")

        file.write(json.dumps(assemble_stycke(item), ensure_ascii=False))
        post_count += 1

    file.write("]")

