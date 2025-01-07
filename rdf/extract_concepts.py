import re
from lxml import etree

def dump_concepts(rdf_file, namespaces):
    # Load the RDF/XML file
    tree = etree.parse(rdf_file)


    # Find all skos:Concept elements
    concepts = tree.xpath("//skos:Concept", namespaces=namespaces)

    # Loop through each concept and extract the required elements
    for concept in concepts:
        # Extract <dc:identifier>
        identifier = concept.find("dc:identifier", namespaces)
        identifier_value = identifier.text if identifier is not None else "N/A"

        # Extract <skos:prefLabel xml:lang="sv">
        pref_label_sv = concept.xpath("skos:prefLabel[@xml:lang='sv']", namespaces=namespaces)
        pref_label_sv_value = pref_label_sv[0].text if pref_label_sv else "N/A"

        # Extract <skos:prefLabel xml:lang="en">
        pref_label_en = concept.xpath("skos:prefLabel[@xml:lang='en']", namespaces=namespaces)
        pref_label_en_value = pref_label_en[0].text if pref_label_en else "N/A"

        # Extract <skos:definition xml:lang="en">
        definition_en = concept.xpath("skos:definition[@xml:lang='en']", namespaces=namespaces)
        if definition_en:
            # Clean up line breaks and multiple spaces
            definition_en_value = re.sub(r'\s+', ' ', definition_en[0].text.strip())
        else:
            definition_en_value = "N/A"

        scope_note_en = concept.xpath("skos:scopeNote[@xml:lang='en']", namespaces=namespaces)
        if scope_note_en:
            scope_note_en_value = re.sub(r'\s+', ' ', scope_note_en[0].text.strip())
        else:
            scope_note_en_value = "N/A"

        # Print the extracted values
        print(f"{identifier_value};{pref_label_sv_value};{pref_label_en_value};{definition_en_value};{scope_note_en_value}")

if __name__ == '__main__':
    namespaces = {
        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "skos": "http://www.w3.org/2004/02/skos/core#",
        "dc": "http://purl.org/dc/elements/1.1/"
    }

    #rdf_file = sys.argv[1]
    rdf_file = "./subdivisions-skos.rdf"
    dump_concepts(rdf_file, namespaces)

