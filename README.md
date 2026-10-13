# USB-Cinema WLAN-Freunde

Android-Projekt für Android 8.0+ (API 26). Die vorhandenen Dateien auf dem USB-Stick werden nicht verändert.

## Bauen mit GitHub Actions
1. Erstelle ein neues GitHub-Repository.
2. Lade den kompletten Inhalt dieses Ordners hoch (einschließlich `.github/workflows/build-apk.yml`).
3. Öffne auf GitHub **Actions** → **Build USB-Cinema Android APK** → **Run workflow**.
4. Öffne den erfolgreichen Lauf und lade das Artefakt `USB-Cinema-WLAN-Freunde-debug-apk` herunter.
5. ZIP entpacken und `app-debug.apk` auf Android installieren. Android kann nach Erlaubnis für die Installation aus dieser Quelle fragen.

## Benutzung
1. USB-Stick per OTG anschließen.
2. App öffnen → **USB-Filmordner auswählen** → Ordner mit den Filmen wählen.
3. **Streaming starten** antippen und die angezeigte Adresse an Freunde im selben WLAN weitergeben.
4. App geöffnet lassen. Mit **Server stoppen** beenden.

## Einschränkungen
- Dies ist eine erste Projektversion und sollte getestet werden; eine APK wurde hier nicht kompiliert.
- Die URL wird angezeigt und ist kopierbar; ein QR-Code ist noch nicht eingebaut.
- Der HTTP-Server ist nicht verschlüsselt und hat keine Anmeldung. Nur in vertrauenswürdigem WLAN nutzen.
- Keine HTTP-Range-Unterstützung: Springen innerhalb des Videos kann in Browsern eingeschränkt sein.
- Dateiauswahl, USB-OTG und unterstützte Dateisysteme hängen vom Android-Gerät ab. WLANs können Gerätekommunikation blockieren.
