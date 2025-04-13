import subprocess
import whisper
import time
import numpy as np
import sys

if len(sys.argv) < 2:
    print("⚠️ No streamer username provided.")
    sys.exit(1)

streamer = sys.argv[1]
print(f"[DEBUG]: {streamer}")

# STREAMER = 'jynxzi'
TWITCH_URL = f"https://twitch.tv/{streamer}"  # Replace with your desired stream
CHUNK_SECONDS = 5
SAMPLE_RATE = 16000
BYTES_PER_SAMPLE = 2

model = whisper.load_model("base")

# streamlink command WITHOUT --audio-only
streamlink_cmd = [
    "streamlink", TWITCH_URL, "best", "-O"
]

# ffmpeg will pull just the audio, downsample it to 16kHz mono PCM
ffmpeg_cmd = [
    "ffmpeg", "-i", "-", "-vn", "-f", "s16le",
    "-acodec", "pcm_s16le", "-ac", "1", "-ar", str(SAMPLE_RATE), "-"
]

# Start both subprocesses
streamlink_proc = subprocess.Popen(streamlink_cmd, stdout=subprocess.PIPE)
ffmpeg_proc = subprocess.Popen(
    ffmpeg_cmd, stdin=streamlink_proc.stdout, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL
)

def transcribe_chunk():
    num_bytes = SAMPLE_RATE * BYTES_PER_SAMPLE * CHUNK_SECONDS
    audio_bytes = ffmpeg_proc.stdout.read(num_bytes)
    if not audio_bytes:
        return

    # Convert bytes to numpy array of int16, then float32 normalized
    audio_np = np.frombuffer(audio_bytes, np.int16).astype(np.float32) / 32768.0

    # Now pass it directly to Whisper
    result = model.transcribe(audio_np, language='en', fp16=False)
    with open(f"stream_data/transcript-{streamer}.txt", "a") as f:
        f.write(f"{result['text']}\n")
    # print("[TRANSCRIPT]", result['text'])

try:
    print("[INFO] Starting transcription...")
    while True:
        transcribe_chunk()
        time.sleep(0.1)
except KeyboardInterrupt:
    print("\n[INFO] Stopping...")
    streamlink_proc.terminate()
    ffmpeg_proc.terminate()
