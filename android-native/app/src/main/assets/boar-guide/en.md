# BOAR Guide

## What BOAR is
BOAR is an AI research assistant that runs entirely on your phone. It answers from a library stored on the phone, and every answer shows the sources it used, so you can check them. Once it is set up, it works without internet, and your questions never leave the phone. BOAR is the app's name; the wild boar is only its mascot.

## Working offline
BOAR uses the internet only to download what it needs: during setup (the answer model, the search model and a knowledge pack), and later only if you choose to download another model or pack. After that, everything runs on the phone. Airplane mode is fine. The ONLINE/OFFLINE pill at the top of the chat shows whether the phone has a connection; BOAR answers the same either way.

## Setting up BOAR
The first launch downloads an answer model (sized for your phone's memory), the search model and a knowledge pack. While it downloads, a short guide walks you through: the look (theme and text size), your own files, how BOAR answers, how to ask, a first question, a PIN, notifications, privacy and Boar Labs. Every page can be skipped, and every choice can be changed later in Settings. If you close BOAR before setup finishes, setup starts again from its first page with your choices kept, and the download continues where it stopped when you tap Install. You can run setup again from Settings → System Recovery & Danger Zone.

## Asking questions
Type a question at the bottom of the chat and tap Send. Ask in English or Portuguese. Clear, specific questions work best: name the thing you mean ("How do I treat a minor burn?", "When was the Eiffel Tower built?"). Each answer lists its sources underneath. Tap Stop while BOAR is writing to end the answer early. Start a new chat from the menu.

## Answer styles
The style button left of where you type, or Settings → How BOAR answers, picks how BOAR answers:
- Quick (Succinct & Direct): short replies.
- Detailed (Thorough & Detailed): full explanations, the default.
- Personal Files: answers only from your own files.
The style applies to the next questions until you change it.

## Deep Research
Deep Research is for big questions. Turn it on with the Deep Research button in the chat, or in Settings → How BOAR answers. BOAR splits the question into a few smaller ones, searches the library for each, then writes one longer answer. It is slower than a normal answer, especially on the smaller model. It works with any style; the style still sets the tone.

## Personal Files
Personal Files lets BOAR answer from your own documents: notes, PDFs and spreadsheets (.txt, .md, .csv, .json and .pdf with selectable text; scanned PDFs can't be read). Add them in Knowledge → Personal Files, or during setup. Then pick the Personal Files answer style and ask about them: ask about one file by name, or ask for a summary of all your files. If your files don't hold the answer, BOAR says so instead of guessing. Your files stay on the phone.

## Knowledge packs
Knowledge packs are the libraries BOAR searches, stored on the phone. The Wikipedia Vital Articles pack holds about 50,000 articles. Manage them in Knowledge → BOAR Knowledge: see what is installed, add or remove a pack. A pack needs to be downloaded once; after that it works offline.

## Sources under an answer
Under each answer, BOAR lists the sources it used: articles from the library, or your own files with Personal Files. Tap a source to see the passage. "Read the whole article" opens the full article or file, offline, with the passage BOAR used highlighted.

## Models
The answer model writes the answers. Setup picks one for your phone's memory: a smaller, faster model for phones under 10 GB of memory, a larger one for 12 GB phones. Change it in Models (from the menu): see what is installed, switch the model in use, or delete one you don't need. Bigger models answer better but more slowly and need more memory.

## Finding more models
Discover GGUF models (in Models) searches Hugging Face for more models. It needs internet only while it searches and downloads. BOAR warns when a model may not fit the phone's memory. Models that need a Hugging Face account can't be downloaded by BOAR.

## PIN lock
A PIN locks BOAR and protects your chats. Set it in Settings → PIN lock, or during setup. With a PIN on, BOAR asks for it when you open the app, your chats are stored encrypted with a key that only your PIN can open, and BOAR's screen is blank in recent apps and in screenshots.

## Forgot the PIN
If you forget your PIN, tap "Forgot the PIN?" on the PIN screen. Without the PIN, your chats can't be opened, not even by us, so BOAR erases them, together with your Personal Files and what the labs made, and then removes the PIN. Models and knowledge packs stay. If the erase can't finish, the PIN stays on so you can try again.

## Notifications
While BOAR writes an answer, it shows a "BOAR is answering…" notification, so the answer keeps going if you switch apps or lock the phone; tap it to come back. Android asks your permission once, during setup. Without it, answers still finish; you just won't see that BOAR is working. Turn it on later in Android's settings for BOAR.

## Look and text size
Settings → Display & Theme changes the theme, the text size and the screen rotation. Campfire and Moonlight are always available; more themes open as you use BOAR with Boarverse on. Paper is a light theme with dark text on white.

## Boarverse and Boar Avatars
Boarverse is an optional, playful layer, off by default: Settings → Experimental → Boarverse. With it on, using BOAR unlocks Boar Avatars, characters you can use as BOAR's picture in the chat and the menu. Some avatars also bring new themes. When you unlock one, BOAR says "New avatar unlocked". See yours and pick one in Settings → Boar Avatars; each locked avatar shows a short clue. Everything is counted on the phone and never leaves it. Turning Boarverse off brings back BOAR's own picture; your avatars stay waiting.

## Boar Labs
Boar Labs is a set of experiments in the 2.0 releases: Settings → Experimental → Boar Labs, and Boar Labs in the menu. Every lab starts off; open one to read what it does and switch it on. Labs are labelled live, beta (tested on phones) or alpha (early). A lab under review stays visible but parked. Some labs answer instantly without the model, and then the answer offers "Ask BOAR instead". If a lab misbehaves, switch it off there.

## The labs
- Boar Vitals: watch the phone work: heat, each core's clock, memory and the chip.
- Boar Calculator: exact answers to everyday calculations (battery, fuel, hiking, currency, water, temperature, durations), instantly.
- Boar Facts: one fact about a country, city, mountain, river, landmark or award.
- Boar Cohazard: a fixed carbon monoxide safety answer for running fuel-burning devices indoors.
- Boar Distance: how far apart two cities are, and how long to walk, cycle or drive it.
- Boar Polyglot: says so at once when you ask in a language BOAR doesn't speak yet.
- Boar Designer: draws an illustration from a few words, offline. It downloads its drawing model the first time.
- Boar Press: collect answers into a basket and compile them into a PDF or a Markdown file.
- Big Boar: runs a very large model from the phone's storage. Slow, for 12 GB phones.
- Boar Nomad: a compass from the phone's sensors, offline.
- Boar Lunar: the moon tonight and this month: phase, lit share, next full and new moon.
- Boar Guard: fixes a yes/no or winner that the model's own figures contradict.
- Boar Wisp: a tiny engine for a small model; under review and parked in this version.
- Daily Quiz, Sanctuary and Signboard: in testing, with no release date.

## Settings
Settings has these sections, folded, each with a one-line summary: How BOAR answers, Reasoning Models, Offline Knowledge Base (RAG), Memory & Context Settings, Display & Theme, Boar Avatars (with Boarverse on), Language, PIN lock, Voice Input Configuration, Hardware Telemetry & Compliance (in Advanced tweaks), Advanced tweaks, Experimental, and System Recovery & Danger Zone.

## Chats and memory
Your chats are kept on the phone and listed in the menu. Settings → Memory & Context Settings sets how many chats BOAR keeps, whether it names them by itself, and whether deleting a chat asks first. With a PIN on, chats are stored encrypted.

## Voice input
Tap the microphone next to where you type to speak a question. Turn voice input on or off in Settings → Voice Input Configuration.

## Language
BOAR's screens come in English and Portuguese: pick one in Settings → Language. You can ask in either language; BOAR also searches the English library for Portuguese questions.

## Privacy
BOAR runs on the phone: questions, answers, files and chats stay there. It contacts the internet only to download models and packs you choose, and never sends your questions anywhere.

## Close BOAR
Close BOAR in the menu frees the memory the model uses and closes the app. BOAR also frees the model on its own when it has been in the background for a while, so other apps have room.

## Recovery and starting over
Settings → System Recovery & Danger Zone has: run setup again, see the tour again, reset settings, and erase all data. Erasing all data deletes chats, files, models and packs; it can't be undone.

## When something goes wrong
- An answer is slow: try the Quick style, a smaller model in Models, or turn Deep Research off.
- A model failed to load: pick another in Models; a model too big for the phone's memory can fail.
- BOAR closed while you were away mid-answer: allow notifications so Android keeps it running.
- The download stopped in setup: tap Try again once the phone is online.
