# SocialCard Studio

JavaFX-Desktop-App zum Zusammenstellen, Lesen und Schreiben von NDEF-Nachrichten
auf NTAG215-Tags. Die App verwendet [SocialCard 0.1.1](https://central.sonatype.com/artifact/io.github.vijiyarathan-rithush/socialcard)
für die NDEF-Codierung, den Zugriff auf den PC/SC-Leser und die Prüfung nach dem Schreiben.

## Starten

Du brauchst JDK 25 und Maven. Falls Maven ein anderes JDK verwendet, setze
`JAVA_HOME` auf dein JDK-25-Verzeichnis und prüfe es mit `mvn -version`.

```sh
mvn javafx:run
```

## Einen Tag vorbereiten

1. Wähle einen Typ aus und füge Links, Texte, Kontakte oder WLAN-Daten hinzu.
2. Prüfe die Datenmenge unter **Tag-Speicher**. Die Vorschau lässt sich ohne Hardware verwenden.
3. Schliess einen PC/SC-NFC-Leser an, klicke auf **Leser suchen** und lege einen beschreibbaren NTAG215 auf.
4. Klicke auf **Auf Tag schreiben** und bestätige das Ersetzen des Tag-Inhalts. **Tag lesen** lädt unterstützte Inhalte zurück in den Editor.

Die Einträge im Editor liegen im Arbeitsspeicher. WLAN-Zugangsdaten auf einem Tag
sind auslesbar. Der Leser benötigt einen PC/SC-kompatiblen Treiber.

## Layout und Farben

`studio.fxml` enthält das Layout und lässt sich mit Scene Builder öffnen.
In `theme.css` stehen die Farbwerte an einer Stelle. Cream ist die Arbeitsfläche,
Deep Teal trägt die Kopfzeile und Orange markiert die Schreibaktion.
