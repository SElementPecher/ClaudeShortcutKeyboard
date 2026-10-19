# Put quiKey on Google Play: the steps

Do them in order. Steps marked **YOU** need your Google login or your computer.
Everything else is already done in this repo.

## 1. Google Play account (YOU)
- Sign up at https://play.google.com/console (one-off fee, about $25).
- If it is a **personal** account made after Nov 2023, Google asks for a **closed test**
  first: at least **12 testers for 14 days**. A company account does not have this rule.

## 2. Make the upload key (YOU, once)
This key proves the app comes from you. **Never share it. Never lose it.**
On a computer with Java, run (change `YOURPASSWORD`):

```
keytool -genkeypair -v -keystore quikey-upload.keystore -alias quikey \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass YOURPASSWORD
```

Then turn it into text and copy it:

```
base64 -w0 quikey-upload.keystore
```

(On a Mac: `base64 -i quikey-upload.keystore`.)
Keep the `.keystore` file and the password in a **password manager**.

## 3. Give the key to GitHub (YOU, once)
GitHub repo > Settings > Secrets and variables > Actions > **New repository secret**.
Add three secrets:

| Name | Value |
|---|---|
| `QUIKEY_KEYSTORE_BASE64` | the long text from step 2 |
| `QUIKEY_KEYSTORE_PASSWORD` | your password |
| `QUIKEY_KEY_ALIAS` | `quikey` |

## 4. Build the Play file
GitHub > Actions > **Build** > Run workflow > tick **Also build the signed Play Store bundle**.
When it finishes, download **quiKey-play-bundle**. Inside is the `.aab` file.

## 5. Create the app in Play Console (YOU)
- Create app. Name: **quiKey**. Free. App (not game).
- Turn on **Play App Signing** (it is the default). Upload the `.aab` from step 4.
- Fill in the store page from `LISTING.md`. Pictures are in `graphics/`.
- **Privacy policy** (needed): host `PRIVACY.md` on a web page and paste its address.
  An easy way: GitHub repo > Settings > Pages, or paste it in a public Google Doc / Notion page.
- Answer **Data safety** and **Content rating** with the answers in `LISTING.md`.

## 4b. Know this
- The app id `io.github.selementpecher.shortcutkeyboard` becomes **permanent** after the first upload.
  Decide now if you want a different one (for example `com.akarigo.quikey`). Tell me and I will change it.
- Phones with the **test (GitHub) version** cannot update to the Play version (different signing key).
  Uninstall the test version first. Your quiKies are in the backup, so they come back.
- Each upload needs a higher version number. The build does this by itself.

## 6. Test, then release
Internal testing first (up to 100 people, no review wait), then closed testing, then Production.
