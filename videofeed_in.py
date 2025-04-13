import cv2
import time
import streamlink
import sys

# STREAMER = 'jynxzi'

if len(sys.argv) < 2:
    print("⚠️ No streamer username provided.")
    sys.exit(1)

streamer = sys.argv[1]

print(f"[DEBUG]: {streamer}")

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
    
    filename = f"stream_data/frame-{streamer}.jpg"
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 3)
    
    last_capture_time = time.time()  # To track the last capture time

    while True:
        current_time = time.time()
        ret, frame = cap.read()
        if not ret:
            print("[WARNING] Failed to grab frame. Retrying...")
            continue
        
        try:
            # If you want to resize or process the frame, ensure it's valid
            frame_resized = cv2.resize(frame, (1920, 1080))  # Example size
            # Process the frame, display, etc.
        except cv2.error as e:
            print(f"Error processing frame: {e}")
            continue
        
        # Save or process frame
        timestamp = int(current_time)
        
        if current_time - last_capture_time >= 5:
            cv2.imwrite(filename, frame_resized)
            print(f"[INFO] Saved frame to {filename} at {timestamp}")
            last_capture_time = current_time
            
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

    cap.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    twitch_url = f"https://twitch.tv/{streamer}"
    main(twitch_url)
