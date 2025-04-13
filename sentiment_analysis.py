import numpy as np
import pandas as pd
import re
import string
import nltk
from nltk.corpus import stopwords
from nltk.stem import PorterStemmer, WordNetLemmatizer
from nltk.tokenize import RegexpTokenizer
from sklearn.model_selection import train_test_split
from tensorflow.keras.layers import LSTM, Activation, Dense, Dropout, Input, Embedding
from tensorflow.keras.models import Model
from tensorflow.keras.optimizers import RMSprop
from tensorflow.keras.preprocessing.text import Tokenizer
from tensorflow.keras.preprocessing import sequence

# Download NLTK resources
print("Downloading NLTK stopwords and wordnet...")
nltk.download("stopwords")
nltk.download("wordnet")


def preprocess_data(data_path, sample_size=20000):
    print("Loading data...")
    data = pd.read_csv(data_path, encoding="ISO-8859-1", engine="python")
    data.columns = ["label", "time", "date", "query", "username", "text"]
    print(f"Original dataset shape: {data.shape}")

    print("Filtering and relabeling data...")
    data = data[["text", "label"]]
    data["label"] = data["label"].replace(4, 1)

    print("Balancing classes...")
    data_pos = data[data["label"] == 1].sample(sample_size)
    data_neg = data[data["label"] == 0].sample(sample_size)
    data = pd.concat([data_pos, data_neg])
    print(f"Balanced dataset shape: {data.shape}")

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


def create_model(max_len=500):
    print("Building model...")
    inputs = Input(name="inputs", shape=[max_len])
    layer = Embedding(2000, 50, input_length=max_len)(inputs)
    layer = LSTM(64)(layer)
    layer = Dense(256, name="FC1")(layer)
    layer = Activation("relu")(layer)
    layer = Dropout(0.5)(layer)
    layer = Dense(1, name="out_layer")(layer)
    layer = Activation("sigmoid")(layer)
    model = Model(inputs=inputs, outputs=layer)
    model.compile(loss="binary_crossentropy", optimizer=RMSprop(), metrics=["accuracy"])
    print("Model built successfully.\n")
    return model


if __name__ == "__main__":
    print("Starting sentiment analysis pipeline...\n")

    # Preprocess data
    X, y = preprocess_data("training.1600000.processed.noemoticon.csv")

    # Split data
    print("Splitting data into training and test sets...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.3, random_state=2
    )
    print("Data split complete.\n")

    # Create and train model
    model = create_model()
    print("Training model...")
    history = model.fit(X_train, y_train, batch_size=80, epochs=6, validation_split=0.1)
    print("Model training complete.\n")

    # Save model weights
    print("Saving model weights...")
    model.save_weights("sentiment_model_weights.h5")
    print("Model weights saved successfully.\n")

    # Evaluate
    print("Evaluating model on test set...")
    loss, accuracy = model.evaluate(X_test, y_test)
    print(f"Test Accuracy: {accuracy:.2f}\n")

    # Generate predictions
    print("Generating predictions...")
    y_pred = (model.predict(X_test) > 0.5).astype("int32")
    print("Predictions complete.")
