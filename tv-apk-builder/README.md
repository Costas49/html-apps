# Kostas Android TV APK Builder

Αυτός ο φάκελος είναι το κοινό Android TV 12 «κέλυφος» για όλες τις HTML εφαρμογές.

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

### Για αναβάθμιση της ίδιας εφαρμογής
Κράτησε ίδιο το `application_id` και αύξησε το `version_code` (1 → 2 → 3). Μπορείς επίσης να αλλάξεις το `version_name` (1.0 → 1.1).

### Για διαφορετική εφαρμογή
Χρησιμοποίησε διαφορετικό `application_id`, π.χ. `com.kostas.solitairetv`, ώστε να εγκαθίσταται δίπλα στις άλλες και να μην τις αντικαθιστά.

## Προαιρετικό εικονίδιο/banner
Μπορείς να βάλεις στον φάκελο της εφαρμογής:
- `icon.png` για εικονίδιο.
- `banner.png` για Android TV banner.
Αν δεν υπάρχουν, χρησιμοποιείται το γενικό εικονίδιο του builder.

## Υπογραφή APK
Ο builder χρησιμοποιεί σταθερό προσωπικό sideloading key ώστε οι επόμενες εκδόσεις της ίδιας εφαρμογής να μπορούν να εγκαθίστανται ως update, εφόσον το `application_id` παραμένει ίδιο και το `version_code` αυξάνεται.

**Σημαντικό:** το repository είναι δημόσιο, άρα αυτό το signing key είναι κατάλληλο για προσωπικό sideloading στο δικό σου Android TV Box, όχι για Google Play Store ή ασφαλή εμπορική διανομή.
