
from langchain_core.prompts import ChatPromptTemplate
from langchain_community.chat_models import ChatOllama
from langchain_core.output_parsers import StrOutputParser

from langchain_community.llms import Ollama

# pip install langchain-community

#llm = Ollama(model="llama3")
llm = ChatOllama(model="llama3")

print(llm)

# prompt = ChatPromptTemplate.from_template("Extract all entities and relations from the following text: {text}")
prompt = ChatPromptTemplate.from_messages([
    ("system", "You are a professional ontologist and you excel at understanding Swedish law text. Present your answers in Swedish and do not use English."),
    ("user", "Analyze the following judicial text, extract entities and relations and attempt to formulate verifiable rules such as who could be eligible to the different insurances: {text}")
])


text2 = """Den som får någon av följande förmåner är försäkrad för inkomstrelaterad sjukersättning och inkomstrelaterad aktivitetsersättning enligt 6 § 5, inkomstgrundad ålderspension enligt 6 § 8 och inkomstrelaterad efterlevandeförmån enligt 6 § 9 och 11:
1. omvårdnadsbidrag enligt 5 kap. 9 § 5,
2. arbetslöshetsersättning enligt lagen (2024:506) om arbetslöshetsförsäkring,
3. aktivitetsstöd till den som deltar i ett arbetsmarknadspolitiskt program,
4. ersättning till deltagare i teckenspråksutbildning för vissa föräldrar (TUFF),
5. dagpenning till totalförsvarspliktiga som tjänstgör enligt lagen (1994:1809) om totalförsvarsplikt och till andra som får dagpenning enligt de grunder som gäller för totalförsvarspliktiga, och
6. stipendium som enligt 11 kap. 46 § inkomstskattelagen (1999:1229) ska tas upp som intäkt i inkomstslaget tjänst.
Lag (2024:508).
"""

chain = prompt | llm | StrOutputParser()
answer = chain.invoke({"text": text2})
print(answer)


