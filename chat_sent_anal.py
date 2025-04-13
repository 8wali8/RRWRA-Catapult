import numpy as np
import pandas as pd
import re
import string
import nltk
from nltk.corpus import stopwords
from nltk.stem import PorterStemmer, WordNetLemmatizer
from nltk.tokenize import RegexpTokenizer
from tensorflow.keras.preprocessing.text import Tokenizer
from tensorflow.keras.preprocessing import sequence

# Download NLTK resources
print("Downloading NLTK stopwords and wordnet...")
nltk.download("stopwords")
nltk.download("wordnet")


def preprocess_data(data_path, sample_size=20000):
    with open(data_path, "r") as f:
        lines = f.readlines()

    # Parse each line into username and message
    data = pd.DataFrame(data, columns=["text"])
    pattern = r"\[CHAT\] ([^:]+): (.+)"

    for line in lines:
        match = re.match(pattern, line.strip())
        if match:
            username = match.group(1).strip()
            message = match.group(2).strip()
            data['text'].append(message)

    def clean_text(text):
        text = text.lower()
        text = re.sub("@[^\\s]+", " ", text)
        text = re.sub("((www\\.[^\\s]+)|(https?://[^\\s]+))", " ", text)
        text = re.sub("[0-9]+", "", text)
        text = text.translate(str.maketrans("", "", string.punctuation))
        text = re.sub(r"(.)\1+", r"\1", text)
        return text

    print("Cleaning text...")
    data["text"] = data["text"].apply(clean_text)

    tokenizer = RegexpTokenizer(r"\w+")
    st = PorterStemmer()
    lm = WordNetLemmatizer()

    def process_tokens(text):
        tokens = tokenizer.tokenize(text)
        tokens = [st.stem(word) for word in tokens]
        tokens = [lm.lemmatize(word) for word in tokens]
        return tokens

    print("Tokenizing and processing tokens...")
    data["text"] = data["text"].apply(process_tokens)

    print("Removing stopwords...")
    stop_words = set(stopwords.words("english"))
    data["text"] = data["text"].apply(
        lambda x: [word for word in x if word not in stop_words]
    )

    print("Converting text to padded sequences...")
    max_len = 500
    tok = Tokenizer(num_words=2000)
    tok.fit_on_texts(data["text"])
    sequences = tok.texts_to_sequences(data["text"])
    sequences_matrix = sequence.pad_sequences(sequences, maxlen=max_len)

    print("Preprocessing complete.\n")
    return sequences_matrix, data["label"]

if __name__ == "__main__":
    preprocess_data('stream_data/chatlog-jynxzi.txt')