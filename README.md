# Shortcut Keyboard

A phone keyboard made of **big tiles**. It types **whole messages for you**.
There are **no letter keys**. You tap tiles, and it does the rest.

---

## What it does

- You save a **shortcut**. A shortcut is a message you send a lot.
- The message has **blanks** in it, like `{child}` or `{time}`.
- Shortcuts live on **tiles**. Tiles can sit inside **folders**.
- When you use a shortcut, the keyboard asks **one question at a time**.
- Then it types the **whole message** for you.

### Example: "Medicine given"

The shortcut message is:

```
{child}, {medicine}, {dose}, {time} next dose not before {time+4h}
```

You tap **Sam**, then **Calpol**, then **5ml**, then **Now**.

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

**How to use it:** tap the **keyboard icon** at the bottom of your screen.
Choose **Shortcut Keyboard**. Your normal keyboard comes back by itself after you tap **Add**.

---

## Make a shortcut

1. Open the **Shortcut Keyboard** app.
2. Tap **＋ New shortcut**.
3. **Name it.** You can pick a little picture too.
4. **Type your message** like you would send it.
   For example: *Sam, Calpol, 5ml, 20:20 next dose not before 00:20*
5. **Tap the words that change.** For each one, pick:
   **A list of answers**, **A time**, **A date** or **A number**.
6. Tap a second time (like 00:20) and it offers **"4 hours after When"**.
7. Tap **Save**.

No curly brackets to type. The app does that part for you.

## Colours and pictures

- Give any **shortcut** or **folder** a **picture** and a **colour**.
- Choose from **16 colours**, or leave it on **Automatic colour**.
- For a folder: press and hold it, then tap **Rename**. For a shortcut: open it, on the first step.

## Pick a look

Open **Settings** (the ⚙ at the top of the app) and choose **Look**:
**Calm**, **Bold** or **Squares**. It changes the app and the keyboard straight away.

### Folders

- In the app, tap **+ New folder**. Folders can go **inside** folders.
- Press and **hold** a row for **Rename**, **Move to…** or **Delete**.
- Deleting a folder does **not** delete what is inside. It moves up one level.

### Use a shortcut

1. Open the keyboard. You see **6 tiles** at a time. Use the arrows for more.
2. Tap a **folder** to open it. Tap **◀** to go back.
3. Tap a **shortcut**. Answer each question by tapping.
4. Check the message. Tap **✓ Add**.

### Move tiles around

- **Press and hold** a tile, then **drag** it.
- Drop it on **another tile's place** to reorder.
- Drop it on the **middle of a folder** to put it inside.
- Drop it on **◀** to move it up a folder.
- Hold at the **edge** to go to the next page.

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
  - `fill/` steps through the blanks one at a time.
  - `model/` has shortcuts, folders, and the folder rules (`Library`).
  - `data/` saves shortcuts as JSON in app storage, and does backups.
  - `look/` has the three looks (colours, shapes, text sizes) and the chosen one.
  - `ime/` is the tile keyboard (grid, drag, question screens). `ui/` is the app screens; `ui/editor/` is the shortcut editor.
  - All screens are built in code from the current look.
- Unit tests are plain JVM tests (JUnit 4). Tests under `robolectric/` start the real screens and keyboard.
- Every push builds the app on GitHub Actions (`.github/workflows/build.yml`).
  Pushes to the default branch (and to the owner's working branch) replace the **Latest build** release.
- The APK is signed with a shared debug key in `keystore/`, so a new build installs over the old one.
