# Kostas Android TV APK Builder

Αυτός ο φάκελος είναι το κοινό Android TV 12 «κέλυφος» για τις HTML εφαρμογές σου.

## Για νέα εφαρμογή
1. Δημιούργησε έναν νέο φάκελο μέσα στο `tv-apps/`, π.χ. `tv-apps/tavli`.
2. Βάλε μέσα δύο αρχεία:
   - `index.html` — η εφαρμογή σου.
   - `app.json` — όνομα, package id και έκδοση.
3. Πήγαινε **Actions → Build Any Android TV APK → Run workflow**.
4. Στο `app_folder` γράψε π.χ. `tv-apps/tavli`.
5. Όταν ολοκληρωθεί με πράσινο ✓, κατέβασε το Artifact APK.

## Παράδειγμα app.json
```json
{
  "app_name": "Kostas Tavli TV",
  "application_id": "com.kostas.tavlitv",
  "version_code": 1,
  "version_name": "1.0",
  "apk_name": "Kostas-Tavli-TV"
}
```

## Για αναβάθμιση της ίδιας εφαρμογής
Κράτησε ίδιο το `application_id` και αύξησε το `version_code` (1 → 2 → 3). Μπορείς επίσης να αλλάξεις το `version_name` (1.0 → 1.1).

## Για διαφορετική εφαρμογή
Χρησιμοποίησε διαφορετικό `application_id`, π.χ. `com.kostas.solitairetv`, ώστε να εγκαθίσταται δίπλα στις άλλες και να μην τις αντικαθιστά.

## Υπογραφή APK
Το workflow δημιουργεί εγκαταστάσιμο debug APK και κρατά το ίδιο debug keystore μέσω GitHub Actions cache (`kostas-tv-debug-keystore-v1`). Έτσι οι επόμενες εκδόσεις της ίδιας εφαρμογής μπορούν συνήθως να εγκαθίστανται ως update, αρκεί να κρατάς ίδιο `application_id` και να αυξάνεις το `version_code`.

Αν το GitHub διαγράψει κάποτε το cache λόγω μεγάλης αδράνειας, μπορεί να χρειαστεί απεγκατάσταση της παλιάς έκδοσης πριν από νέα εγκατάσταση. Για Google Play Store ή εμπορική διανομή χρειάζεται κανονικό ιδιωτικό release signing key σε GitHub Secrets — όχι μέσα στο δημόσιο repository.

## Έτοιμο πρότυπο
Υπάρχει ήδη ο φάκελος `tv-apps/_template` με έτοιμα `index.html` και `app.json`. Μπορείς να τον χρησιμοποιείς ως οδηγό για κάθε νέα εφαρμογή.
