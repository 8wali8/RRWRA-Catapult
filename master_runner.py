import subprocess
import time
import sys
import os
import glob

def clear_old_streamer_files(streamer):
    print(f"🧹 Clearing old data for streamer: {streamer}")
    pattern = f"stream_data/*{streamer}*"
    files = glob.glob(pattern)
    for file in files:
        try:
            os.remove(file)
            print(f"[INFO] Deleted {file}")
        except Exception as e:
            print(f"[WARNING] Could not delete {file}: {e}")

# Ask for streamer username
streamer = input("🎮 Enter the Twitch streamer username: ").strip()

if not streamer:
    print("⚠️ Streamer username cannot be empty.")
    sys.exit(1)
    
clear_old_streamer_files(streamer)

# Get absolute paths to avoid ambiguity
scripts = [
    os.path.abspath("videofeed_in.py"),
    os.path.abspath("voice_in.py"),
    os.path.abspath("chat_in.py")
]

processes = []

try:
    print(f"\n🚀 Launching stream data collectors for '{streamer}'...\n")

    for script in scripts:
        print(f"▶️  Starting {script} with argument '{streamer}'")
        # The key fix: pass args as a LIST, not a string
        p = subprocess.Popen([sys.executable, script, streamer])
        processes.append(p)
        time.sleep(1)

    print("\n✅ All scripts running. Leave this terminal open to keep them alive.\n")
    print("🔄 Use Ctrl+C to stop everything.\n")

    for p in processes:
        p.wait()

except KeyboardInterrupt:
    print("\n🛑 Stopping all processes...\n")
    for p in processes:
        p.terminate()
    print("✅ All processes terminated.")
