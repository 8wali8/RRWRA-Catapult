import streamlit as st
from pathlib import Path

st.set_page_config(page_title="Twitch Sponsor Monitor", layout="wide")

st.title("📺 Twitch Stream Analysis Dashboard")

# Display current frame
st.subheader("Live Frame")
frame_path = "stream_data/frame.jpg"
if Path(frame_path).exists():
    st.image(frame_path, caption="Current Stream Frame", use_column_width=True)
else:
    st.warning("No frame available yet.")

# Display transcript
st.subheader("Speech-to-Text")
transcript_path = "stream_data/transcript.txt"
if Path(transcript_path).exists():
    with open(transcript_path, "r") as f:
        transcript = f.read()
    st.text_area("Transcription", transcript, height=200)
else:
    st.warning("No transcript yet.")

# Display chat
st.subheader("Live Chat")
chatlog_path = "stream_data/chatlog.txt"
if Path(chatlog_path).exists():
    with open(chatlog_path, "r") as f:
        chatlog = f.read()
    st.text_area("Chat Log", chatlog, height=200)
else:
    st.warning("No chat messages yet.")

st.caption("Updated live every few seconds. Refresh manually or run Streamlit with autorefresh.")
