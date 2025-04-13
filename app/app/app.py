import streamlit as st
from pathlib import Path
import json
import os
import time
import random

# Configuration
DATA_DIR = "stream_data"
SPONSOR_DATA_FILE = "sponsor_data.json"
CHAT_REFRESH_INTERVAL = 2  # seconds
TRANSCRIPT_UPDATE_INTERVAL = 5  # seconds

st.set_page_config(page_title="Twitch Sponsor Monitor", layout="wide")
st.title("📺 Twitch Stream Analysis Dashboard")

# Initialize data storage
if not Path(DATA_DIR).exists():
    os.makedirs(DATA_DIR)
if not Path(SPONSOR_DATA_FILE).exists():
    with open(SPONSOR_DATA_FILE, "w") as f:
        json.dump([], f)

# Initialize session state
if 'streamer' not in st.session_state:
    st.session_state.streamer = ""
if 'chat_messages' not in st.session_state:
    st.session_state.chat_messages = []
if 'transcript_lines' not in st.session_state:
    st.session_state.transcript_lines = []
if 'last_transcript_update' not in st.session_state:
    st.session_state.last_transcript_update = 0
if 'sponsor_counts' not in st.session_state:
    st.session_state.sponsor_counts = {}
if 'sentiment_scores' not in st.session_state:
    st.session_state.sentiment_scores = []

# Simple sentiment analysis using keyword matching
def simple_sentiment(text):
    positive_words = ['great', 'awesome', 'love', 'good', 'nice', 'excellent', 'amazing']
    negative_words = ['bad', 'terrible', 'hate', 'awful', 'poor', 'boring']
    
    text_lower = text.lower()
    positive = sum(word in text_lower for word in positive_words)
    negative = sum(word in text_lower for word in negative_words)
    
    if positive > negative:
        return 1  # Positive
    elif negative > positive:
        return -1  # Negative
    return 0  # Neutral

# Sponsor configuration
with st.sidebar:
    st.header("Sponsor Configuration")
    with st.form("sponsor_form"):
        streamer_name = st.text_input("Streamer Name", key="streamer_input")
        company_name = st.text_input("Company Name")
        company_logo = st.file_uploader("Company Logo", type=["png", "jpg", "jpeg"])
        related_keywords = st.text_area("Related Keywords (comma separated)")
        submitted = st.form_submit_button("Save Sponsor")
        
        if submitted:
            with open(SPONSOR_DATA_FILE, "r") as f:
                sponsors = json.load(f)
            
            logo_path = ""
            if company_logo:
                logo_path = f"{DATA_DIR}/{company_name}_logo{Path(company_logo.name).suffix}"
                with open(logo_path, "wb") as f:
                    f.write(company_logo.getbuffer())
            
            sponsors.append({
                "streamer": streamer_name,
                "company": company_name,
                "logo": logo_path,
                "keywords": [kw.strip() for kw in related_keywords.split(",")] if related_keywords else []
            })
            
            with open(SPONSOR_DATA_FILE, "w") as f:
                json.dump(sponsors, f)
            
            st.success("Sponsor saved!")
            st.session_state.streamer = streamer_name
            st.session_state.transcript_lines = [
                f"{streamer_name}: Welcome everyone to the stream!",
                f"{streamer_name}: Today we're playing Valorant"
            ]
            st.session_state.sponsor_counts[company_name] = 0

# Main content
if st.session_state.streamer:
    # Twitch player and chat
    player_html = f"""
    <div style="display: flex; gap: 20px;">
        <div style="flex: 3;">
            <iframe
                src="https://player.twitch.tv/?channel={st.session_state.streamer}&parent=localhost"
                height="500"
                width="100%"
                frameborder="0"
                scrolling="no"
                allowfullscreen="true">
            </iframe>
        </div>
        <div style="flex: 1;">
            <iframe
                src="https://www.twitch.tv/embed/{st.session_state.streamer}/chat?parent=localhost"
                height="500"
                width="100%"
                frameborder="0"
                scrolling="yes">
            </iframe>
        </div>
    </div>
    """
    st.components.v1.html(player_html, height=520)
    
    # Analytics columns
    col1, col2 = st.columns(2)
    
    with col1:
        st.subheader("📊 Sponsorship Mentions")
        if st.session_state.sponsor_counts:
            for sponsor, count in st.session_state.sponsor_counts.items():
                st.metric(label=sponsor, value=count)
        else:
            st.info("No sponsor mentions yet")
    
    with col2:
        st.subheader("😊 Sentiment Analysis")
        if st.session_state.sentiment_scores:
            positive_count = sum(1 for score in st.session_state.sentiment_scores if score > 0)
            negative_count = sum(1 for score in st.session_state.sentiment_scores if score < 0)
            neutral_count = len(st.session_state.sentiment_scores) - positive_count - negative_count
            
            st.metric(label="Positive Messages", value=positive_count)
            st.metric(label="Negative Messages", value=negative_count)
            st.metric(label="Neutral Messages", value=neutral_count)
        else:
            st.info("No sentiment data yet")
    
    # Transcript section
    st.subheader("🗣️ Speech-to-Text Transcript")
    transcript_placeholder = st.empty()
    
    # Add new transcript lines periodically
    current_time = time.time()
    if current_time - st.session_state.last_transcript_update > TRANSCRIPT_UPDATE_INTERVAL:
        new_lines = [
            "Viewer: Great gameplay!",
            f"{st.session_state.streamer}: Thanks for the support!",
            "Viewer: When's the next tournament?",
            f"{st.session_state.streamer}: We'll announce it soon!",
            "Viewer: This is awesome!",
            "Viewer: I hate this game mode",
            "Viewer: The stream quality is poor today",
            "Viewer: You're amazing at this game!"
        ]
        new_line = random.choice(new_lines)
        st.session_state.transcript_lines.append(new_line)
        
        # Analyze sentiment using simple keyword matching
        sentiment = simple_sentiment(new_line)
        st.session_state.sentiment_scores.append(sentiment)
        
        # Check for sponsor mentions
        try:
            with open(SPONSOR_DATA_FILE, "r") as f:
                sponsors = json.load(f)
            for sponsor in sponsors:
                for keyword in sponsor['keywords']:
                    if keyword and keyword.lower() in new_line.lower():
                        st.session_state.sponsor_counts[sponsor['company']] += 1
        except:
            pass
        
        st.session_state.last_transcript_update = current_time
    
    # Keep only last 10 lines
    if len(st.session_state.transcript_lines) > 10:
        st.session_state.transcript_lines = st.session_state.transcript_lines[-10:]
    
    # Highlight sponsor mentions
    display_transcript = "\n".join(st.session_state.transcript_lines)
    try:
        with open(SPONSOR_DATA_FILE, "r") as f:
            sponsors = json.load(f)
        for sponsor in sponsors:
            for keyword in sponsor['keywords']:
                if keyword:
                    display_transcript = display_transcript.replace(keyword, f"**{keyword}**")
    except:
        pass
    
    transcript_placeholder.markdown(display_transcript)
    
    # Auto-refresh
    time.sleep(CHAT_REFRESH_INTERVAL)
    st.rerun()
else:
    st.warning("Please enter a streamer name in the sidebar to begin")
