import streamlit as st
import subprocess
import os
import time
from threading import Thread

# --- SETUP ---
stream_data_dir = "stream_data"

def clear_old_streamer_files(streamer):
    for file in os.listdir(stream_data_dir):
        if streamer in file:
            try:
                os.remove(os.path.join(stream_data_dir, file))
            except Exception as e:
                print(f"Failed to remove {file}: {e}")

def run_script(name, streamer):
    subprocess.Popen(["python", name, streamer])

# --- STREAMLIT UI ---
st.title("📡 Streamer Content Analysis Dashboard")

streamer = st.text_input("Enter Twitch streamer username:", value="")

if st.button("Start Monitoring") and streamer:
    clear_old_streamer_files(streamer)

    st.success(f"Started monitoring {streamer}!")
    
    # Launch all scripts
    Thread(target=run_script, args=("videofeed_in.py", streamer)).start()
    Thread(target=run_script, args=("voice_in.py", streamer)).start()
    Thread(target=run_script, args=("chat_in.py", streamer)).start()

    # Live-updating section
    st.markdown("---")
    img_placeholder = st.empty()
    
    # Create columns: left for transcript, right for chat
    left_column, right_column = st.columns([2, 3])  # Adjust proportions as needed

    with left_column:
        transcript_placeholder = st.empty()  # Empty container for the transcription

    with right_column:
        chat_placeholder = st.empty()  # Empty container for the chat log

    # Initialize the previous content variables
    previous_chat_text = ""
    previous_transcript_text = ""

    while True:
        # --- IMAGE FEED ---
        frame_path = f"{stream_data_dir}/frame-{streamer}.jpg"
        if os.path.exists(frame_path):
            img_placeholder.image(frame_path, caption="Latest Stream Frame")

        # --- CHAT FEED (Right Column) ---
        chat_path = f"{stream_data_dir}/chatlog-{streamer}.txt"
        if os.path.exists(chat_path):
            with open(chat_path, "r") as f:
                new_chat_text = f.read()
                if new_chat_text != previous_chat_text:  # Only update if new content exists
                    previous_chat_text = new_chat_text
                    chat_placeholder.text_area("💬 Live Chat", new_chat_text, height=200, 
                                               key=f"chat_{streamer}_{time.time()}")

        # --- VOICE TRANSCRIPTION (Left Column) ---
        transcript_path = f"{stream_data_dir}/transcript-{streamer}.txt"
        if os.path.exists(transcript_path):
            with open(transcript_path, "r") as f:
                new_transcript_text = f.read()
                if new_transcript_text != previous_transcript_text:  # Only update if new content exists
                    previous_transcript_text = new_transcript_text
                    transcript_placeholder.text_area("🗣️ Transcribed Audio", new_transcript_text, height=200, 
                                                     key=f"transcript_{streamer}_{time.time()}")

        time.sleep(2)
