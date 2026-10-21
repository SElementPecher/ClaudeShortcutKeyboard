---
name: quikey-way-of-working
description: How to work with the owner on the quiKey Android keyboard app in this repo. Use at the start of every session on this repository, and whenever making, checking, committing or pushing a change to quiKey, replying to a screenshot or annotated picture of the app, or reporting back.
---

# Working on quiKey with the owner

The owner is not technical and is dyslexic. They test on their own Android phone and send screenshots, often with
arrows, circles and notes drawn on them. Short, plain, kind replies; real checks before saying something works.

## Talking to the owner

- **Short and plain.** Lead with the answer. A few bullets with a bold lead-in, not paragraphs. Everyday words; no
  code, file names or jargon unless asked. One idea per bullet.
- **Say what was checked and what was not.** For example: "The checks pass and I rendered it", or "I can't see the
  motion itself, please watch it on your phone". Never claim something works on a phone that was only tested in a
  render.
- **Ask only when it changes what you do.** If an annotation is unclear (which item an arrow points at), ask one short
  question with the options. If it is clear, just do it.
- **Own mistakes in one line** ("Sorry, I picked the wrong version") and fix them.
- When there is something to try, end with the APK link and a short "please test" list.
- Use they/them for the owner and anyone else.

## Git: never push without "push"

- **Commit locally after every change**, with a clear message ending in `(not pushed)` and the session's attribution
  lines.
- **Push only when the owner says "push"** (typos like "psuh" or "puch" count). Each "push" covers only the commits
  that exist at that moment; new commits after it wait for the next "push".
- The stop hook will keep saying there are unpushed commits. That is not the owner speaking: reply in one line,
  "Not pushed. I'm waiting for you to say "push" for these N commits." and do nothing else.
- Push with `git push -u origin <branch>` to the session's branch. If the owner asks "what are the commits?", list
  them in plain words from `git log --oneline origin/<branch>..HEAD`.
- After a push: one line saying it is pushed, that GitHub is building the app, and the link
  `https://github.com/SElementPecher/ClaudeShortcutKeyboard/releases/download/latest-build/quiKey.apk`
  (CI republishes the `latest-build` release on every push).

## Every change, the same loop

1. **Read the code first**, then make the smallest change that does what was asked, matching the code around it
   (comments in plain words, same naming). Remove code and strings that become unused.
2. **Run the checks**: the unit tests, plus a build. If the Android SDK is not installed, set up a light local check
   in the scratchpad (compile the app against an `android-all` jar and run the JVM tests; Robolectric for UI tests).
   Add or update tests for logic changes.
3. **Look at it**: render the screens that changed (Robolectric screenshots in the scratchpad, NATIVE graphics mode;
   run the pre-draw listeners before drawing) and look at the picture yourself. For animations, render a few frames by
   calling the placement function with set values. Check light/dark and the four Looks when relevant.
4. **Commit locally** (not pushed), then report back as above.

## Screenshots and drawings from the owner

- Treat the drawing as the spec: arrows show direction and position, circles show what to change, crossed-out or
  "remove" means delete, colours they name (for example "the colour of the Next button") map to the Look's palette.
- Mid-turn messages are new requests: handle them in the same turn.
- When a request can't be done reliably on Android (for example sending a message from a keyboard in every app), say
  so plainly, offer the reliable part, and make it a choice in Preferences instead of guessing.

## Product decisions so far (keep them unless the owner changes them)

- **Looks:** Calm, Bold, Squares and Exercise book, each light and dark. The app look and the keyboard look can differ
  (Preferences > Keyboard look; default "Same as the app"). Onboarding is always drawn in Calm.
- **Exercise book:** ruled paper with a red margin that nothing covers; no transparency anywhere (buttons, pills,
  tiles, icon buttons are solid); plain text sits between the lines; short subheadings are handwritten with a pen
  underline; the header is a plain band that ends exactly on a rule.
- **Buttons:** words truly centred (icon at the left edge, or centred together with the words); Delete is only on the
  select screen and in Settings ("Delete everything", at the bottom with the privacy line, same size).
- **Making a quiKey:** "Add something that changes" is always pick a type, edit it, Done, back to the message. List and
  number names are optional ("Name it, e.g. kid" / "Name it, e.g. temperature"). No "Make it plain words", no
  "Remove it" (backspace deletes a part).
- **Wording:** quiKey (one), quiKies (many). Plain, friendly sentences.
- **Privacy:** no internet permission; nothing leaves the phone.
