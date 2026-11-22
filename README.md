# SocialCard Studio

A JavaFX desktop app for composing, reading, and writing NDEF messages on NTAG215 tags.
It uses [SocialCard 1.0.3](https://central.sonatype.com/artifact/io.github.vijiyarathan-rithush/socialcard/1.0.3)
for NDEF encoding, PC/SC reader access, and write verification.

## Run on Windows

From a GitHub release, download `socialcard-studio-windows-x64.zip`, extract it
and open `SocialCard Studio.exe` inside the `SocialCard Studio` folder. Keep
the entire folder together. The package includes its Java runtime.

For NFC operations, connect a PC/SC-compatible reader and use an NTAG215 tag.

## Run from source

Install JDK 25 and Maven. If Maven uses a different JDK, set `JAVA_HOME` to
your JDK 25 directory and check it with `mvn -version`.

```sh
mvn javafx:run
```

## Build a Windows executable

Build the portable application on x64 Windows with JDK 25 and Maven.

```powershell
mvn -Pwindows-exe clean package
```

Open `target/SocialCard Studio/SocialCard Studio.exe` to start the app.

Share the entire `SocialCard Studio` folder so the launcher can find the bundled
Java runtime and dependencies. Recipients can run it without installing Java
or Maven. The build creates a portable application folder and works without WiX.

## Verify

Run `mvn verify` to compile the application and run its tests. GitHub Actions
verifies the build on Linux and Windows and provides the Windows ZIP as a
workflow artifact.

## Prepare a tag

1. Choose a record type and add links, text, contacts, or Wi-Fi credentials.
2. Check the size under **Tag storage**. The size preview works without a reader.
   Shorten the draft if it exceeds 496 bytes to enable writing.
3. Connect a PC/SC NFC reader, click **Scan for readers**, and place a writable NTAG215 tag on it.
4. Click **Write to tag** and confirm. **Read tag** loads supported records into the editor.

The editor keeps drafts in memory and asks before discarding changes. Anyone who
scans a tag can read its Wi-Fi credentials. The NFC reader requires a
PC/SC-compatible driver and must support reading the NTAG215 UID for writes.

## Layout and colors

Open `studio.fxml` in Scene Builder to edit the layout. Change the palette in
`theme.css`. Warm charcoal surfaces sit on a black background. Orange highlights the write button and keyboard focus.
