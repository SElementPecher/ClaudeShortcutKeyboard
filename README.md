# Shortcut Keyboard

A phone keyboard that types **whole messages for you**.
You fill in a few **blanks**, and it does the rest.

---

## What it does

- You save a **shortcut**. A shortcut is a message you send a lot.
- The message has **blanks** in it, like `{child}` or `{time}`.
- When you use the shortcut, the keyboard asks for **one blank at a time**.
- Then it types the **whole message** for you.

### Example: "Medicine given"

The shortcut message is:

```
{child}, {medicine}, {dose}, {time} next dose not before {time+4h}
```

You tap **Sam**, then **Calpol**, then **5ml**. The time is filled in for you.

The keyboard types:

> Sam, Calpol, 5ml, 20:20 next dose not before 00:20

It worked out the **next dose time** for you: 4 hours later.

---

## Install on your phone

Do these steps **on your phone**.

1. Open this page on GitHub.
2. Find **Releases** and tap **Latest build**.
   (Or go straight to the [Latest build page](../../releases/tag/latest-build).)
3. Tap **ShortcutKeyboard.apk** to download it.
4. Open the file when it has downloaded.
5. Your phone may say it **cannot install apps from this source**.
   - Tap **Settings**.
   - Turn on **Allow from this source**.
   - Go back.
6. Tap **Install**.
7. Tap **Open**.

**Getting a new version:** do the same steps again.
Your saved shortcuts **stay safe**.

---

## Turn it on

Open the **Shortcut Keyboard** app. It shows you **2 steps**.

1. **Turn on Shortcut Keyboard.**
   Tap the button. Find **Shortcut Keyboard** in the list and switch it **on**.
   Your phone shows a warning. This is normal for every keyboard. Tap **OK**.
2. **Pick it as your keyboard.**
   Tap the button. Choose **Shortcut Keyboard**.

When both steps are done, you see **✓ Keyboard is ready**.

**Tip:** to swap back to your old keyboard, tap the **globe** key 🌐.
Or **press and hold** the space bar.

---

## Make a shortcut

1. Open the **Shortcut Keyboard** app.
2. Tap **+ New shortcut**.
3. **Name:** a short name, like **Medicine given**.
4. **Trigger word** (you can skip this): a word like **/meds**.
   Type the word, then a **space**, and the shortcut opens.
5. **Message:** write your message.
   Put blanks in **curly brackets**, like `{child}`.
6. Tap **+ Add a blank** to add a blank without typing the brackets.
7. Tap **Save**.

### Use a shortcut

- Tap its name in the **bar at the top of the keyboard**.
- Or type its **trigger word** and then a **space**.
- Answer each blank. Tap **Next**.
- Tap **Insert** to type the message.

The keyboard **remembers** your answers. New answers are added to the list for next time.

---

## Blanks cheat sheet

| Write this         | You get                                   |
| ------------------ | ----------------------------------------- |
| `{child}`          | **Pick from a list** (like Sam or Alex)   |
| `{time}`           | The **time now**, like 20:20              |
| `{time+4h}`        | **4 hours later**, like 00:20             |
| `{time+30m}`       | **30 minutes later**, like 20:50          |
| `{today}`          | **Today's date**, like 16 Oct 2026        |
| `{time\|h:mm a}`   | A **12-hour clock**, like 8:20 pm         |

**More tips:**

- Use **h** for hours, **m** for minutes, **d** for days.
- Use **-** for earlier: `{time-15m}` is **15 minutes before**.
- Mix them: `{time+1h30m}` is **1 hour 30 minutes later**.
- Want real curly brackets in your message? Type them **twice**: `{{` and `}}`.

---

## Privacy

- This keyboard has **no internet access**. The app does not ask for it.
- What you type **stays on your phone**.
- Your shortcuts are saved **only on your phone**.
- To keep a copy, use **Back up shortcuts** in the app menu.

---

## For developers

**Build the app:**

```
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`.

**Run the tests:**

```
./gradlew testDebugUnitTest
```

**Tech notes:**

- Kotlin. `minSdk` 26, `targetSdk` 35.
- **Platform only**: no AndroidX, no Material Components, no Compose, no other libraries.
  Screens use `android.app.Activity` and `android.widget`.
- No permissions at all, not even INTERNET.
- Code lives in `app/src/main/java/io/github/selementpecher/shortcutkeyboard/`:
  - `template/` reads messages and fills in blanks (`{time+4h}`, `{name|h:mm a}`).
  - `fill/` steps through the blanks one at a time, and spots trigger words.
  - `data/` saves shortcuts as JSON in app storage, and does backups.
  - `ime/` is the keyboard. `ui/` is the app screens.
- Unit tests are plain JVM tests (JUnit 4). Tests under `robolectric/` start the real screens and keyboard.
- Every push builds the app on GitHub Actions (`.github/workflows/build.yml`).
  Pushes to the default branch (and to the owner's working branch) replace the **Latest build** release.
- The APK is signed with a shared debug key in `keystore/`, so a new build installs over the old one.
