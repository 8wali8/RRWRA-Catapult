import irc.bot

# Configuration
CHANNEL = "#caedrel"  # Change to the streamer's channel name (e.g., "#ninja")
OAUTH_TOKEN = "oauth:sefzdi9vlhm4gi8dq1foej9d9s11ox"  # Replace with your OAuth token
USERNAME = "four__dots"
TWITCH_SERVER = "irc.chat.twitch.tv"
PORT = 6667

class TwitchBot(irc.bot.SingleServerIRCBot):
    def __init__(self):
        server = [("irc.chat.twitch.tv", 6667, OAUTH_TOKEN)]
        irc.bot.SingleServerIRCBot.__init__(self, server, USERNAME, USERNAME)

    def on_welcome(self, connection, event):
        print("[INFO] Connected, joining channel...")
        connection.join(CHANNEL)

    def on_pubmsg(self, connection, event):
        print(f"[CHAT] {event.source.split('!')[0]}: {event.arguments[0]}")

    def on_disconnect(self, connection, event):
        print("[INFO] Disconnected from Twitch chat.")

def main():
    # Create an instance of the TwitchBot with the channel and OAuth token
    bot = TwitchBot()
    bot.start()

if __name__ == "__main__":
    main()
