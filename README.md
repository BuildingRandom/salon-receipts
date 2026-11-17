# Salon Receipts (Android)

A personal Android app for tracking salon supply receipts. Take a photo of a receipt, and Claude reads the store, each item and its price, and the tax. Everything is saved on the phone, and at tax time the app builds two CSV reports: an item list and a summary by category and store.

- **No server:** the only thing that leaves the phone is the receipt photo, sent to Anthropic's API to be read.
- **Storage:** receipts are saved in a JSON file in the app's private storage, and photos are kept in `photos/` next to it.
- **Reports and backup:** **Send reports** and **Send a backup** open Android's share menu, so you can email the files or save them to Drive.

## 1. Open and run it

1. Unzip, then in Android Studio choose **File → Open** and pick the `SalonReceipts` folder.
2. Let the Gradle sync finish. The first sync downloads Gradle and the libraries. If Studio offers to upgrade the Android Gradle Plugin, that's fine to accept.
3. On the Pixel, turn on **Developer options → USB debugging** (or Wireless debugging). Plug it in, pick it in the device list, and press **Run ▶**.

To make an APK you can sideload instead: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The file lands in `app/build/outputs/apk/`. The release build is signed with the debug key so it installs without a keystore. That's fine for your own phone, but don't publish it.

## 2. Add your Claude API key

1. Go to https://console.anthropic.com. Add a payment method and a few dollars of credit, then create an API key. This is billed separately from a Claude.ai subscription, at roughly a cent or two per receipt.
2. Under **Limits**, set a monthly spending limit (for example $5).
3. In the app, tap **Settings**, paste the key and tap **Save and go back**.

Without a key, the app still works: it saves the photo and you type the details in.

The key is stored in the app's private settings on the phone. Don't share the APK with anyone once your key is entered.

## 3. Day to day

- **Snap a receipt:** the camera opens. Claude reads the photo in 10 to 30 seconds and fills in the review screen. Check it against the photo and pick **Salon job**, **Home chair** or **Personal**. For the home chair, tick **Client paid me back** when a friend covered the products. Then tap **Save**.
- **Choose a photo:** use a picture already in the gallery.
- **Enter by hand:** for a receipt with no photo.
- **Tap any receipt** to edit or delete it. Tap the photo to enlarge it.
- If the items don't add up to the subtotal, or the subtotal plus tax doesn't match the total, the review screen shows a warning.

## 4. Tax time

On the home screen, pick the tax year and tap **Send <year> reports (CSV)**. You get two files:

- `salon-receipts-items-<year>.csv`: one row per item, with date, store, what it was for, category, quantity, price each, line total, receipt tax and total, and whether it counts toward the claim.
- `salon-receipts-summary-<year>.csv`: totals by category (salon job and home chair kept separate), GST/HST and PST paid, and totals by store.

Personal receipts, and home-chair receipts a client paid back, are left out of the summary totals. They still appear in the item list, marked "No" under Claimable.

## 5. Back it up

The receipts live only on this phone. Every month or two, go to **Settings → Send a backup (zip)** and save the zip to Drive or email it to yourself. The zip holds `receipts.json`, every photo (named by date and store) and a full item list. Android's own Google backup may also copy the app's data automatically if it's turned on for your phone.

## Project layout

```
app/src/main/java/ca/home/salonreceipts/
  MainActivity.kt   screens, camera, photo picker, and when Claude is called
  ClaudeReader.kt   Anthropic API call, prompt, and parsing of the answer; key and model settings
  ReceiptStore.kt   on-phone storage (receipts.json and photos/)
  Model.kt          receipt and item data, categories, JSON
  Images.kt         turns photos upright and shrinks them before saving
  Export.kt         CSV reports, backup zip, share menu
  ui/               Home, review/edit and Settings screens, theme
```

To change the categories, edit `CATEGORIES` in `Model.kt`. Claude is told to choose from that same list.
