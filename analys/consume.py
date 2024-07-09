
import json
import re

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
            if "stycke" in obj:
                for stycke in obj["stycke"]:
                    if isinstance(stycke, dict):
                        stycke_context = current_context.copy()
                        stycke_context["stycke"] = stycke["nummer"]
                        concatenated_text = " ".join(stycke["text"])
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


for item in texts_with_context:
    context = item["context"]
    text = item["text"]
    print(f"Kapitel: {context.get('kapitel')}, Avdelning: {context.get('avdelning_id')} {context.get('avdelning_namn')}, Underavdelning: {context.get('underavdelning_id')} {context.get('underavdelning_namn')}, Paragraf: {context.get('paragraf')}, Rubrik: {context.get('rubrik')}, Stycke: {context.get('stycke')}")
    print(f"Text: {text}")
    print("-"*80)

