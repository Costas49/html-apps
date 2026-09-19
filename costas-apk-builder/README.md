# Costas APK Builder

Μόνιμος builder για εγκαταστάσιμα APK από HTML/Web εφαρμογές.

## Συσκευές
- Android tablet 10 (Android 10 / API 29)
- Android TV / TV Box 12
- Νεότερες Android συσκευές

Το ίδιο APK μπορεί να εγκατασταθεί και στις δύο κατηγορίες.

## Πού αλλάζεις την εφαρμογή
Το βασικό αρχείο είναι:

`costas-apk-builder/app/src/main/assets/index.html`

Μπορείς επίσης να βάλεις δίπλα CSS, JavaScript, εικόνες και άλλα τοπικά assets και να τα καλείς από το index.html.

## Αυτόματο build
Άνοιξε:
GitHub > html-apps > Actions > Costas APK Builder > Run workflow

Συμπλήρωσε:
- App name: όνομα εφαρμογής
- Package name: μοναδικό id, π.χ. com.costas.radio
- Version code: 1, 2, 3...
- Version name: 1.0, 1.1...
- APK file name: όνομα αρχείου χωρίς .apk

Μετά το build:
Actions > τελευταίο επιτυχημένο run > Artifacts > Costas-APK-Output

Το ZIP περιέχει το έτοιμο APK.

## Σημαντικό για ενημερώσεις
Ο τωρινός builder παράγει εγκαταστάσιμο debug-signed APK. Είναι κατάλληλο για sideload.
Για να εγκαθιστάς νέα έκδοση πάνω από την παλιά χωρίς απεγκατάσταση, χρειάζεται ένα μόνιμο ιδιωτικό signing key αποθηκευμένο ως GitHub Actions Secret. Μην ανεβάσεις ποτέ ιδιωτικό signing key σε δημόσιο repository.
