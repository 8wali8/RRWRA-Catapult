import cv2
import time
import streamlink
import os

def stream_to_url(url, quality='best'):
    streams = streamlink.streams(url)
    if streams and quality in streams:
        return streams[quality].to_url()
    else:
        raise ValueError("No streams were available or specified quality not found")

def main(url, quality='best', fps=1.0):  # 1 fps = 1 frame every second
    print("[INFO] Getting stream URL...")
    stream_url = stream_to_url(url, quality)
    print(f"[INFO] Stream URL: {stream_url}")

    cap = cv2.VideoCapture(stream_url)
    if not cap.isOpened():
        print("[ERROR] Could not open video stream.")
        return

    print("[SUCCESS] Connected to the stream!")
    
    filename = f"Captures/frame-{streamer}.jpg"
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 3)
    
    last_capture_time = time.time()  # To track the last capture time

    while True:
        current_time = time.time()
        ret, frame = cap.read()
        if not ret:
            print("[WARNING] Failed to grab frame. Retrying...")
            continue
        
        # Save or process frame
        timestamp = int(current_time)
        
        if current_time - last_capture_time >= 5:
        
            os.remove(filename)
            cv2.imwrite(filename, frame)
            print(f"[INFO] Saved frame to {filename} at {timestamp}")
            last_capture_time = current_time

    cap.release()

if __name__ == "__main__":
    streamer = "caedrel"  # Replace with the actual Twitch name
    twitch_url = f"https://twitch.tv/{streamer}"
    main(twitch_url)
