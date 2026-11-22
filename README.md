<div align="center">

# SocialCard Studio

A JavaFX desktop app for composing, reading and writing NFC tags with [SocialCard](https://github.com/Vijiyarathan-Rithush/socialcard).

![Java 25](https://img.shields.io/badge/Java-25-111111?style=for-the-badge)
[![Windows x64](https://img.shields.io/badge/Windows-x64-ff743d?style=for-the-badge)](https://github.com/Vijiyarathan-Rithush/socialcard-studio/releases)
[![SocialCard 1.0.3](https://img.shields.io/badge/SocialCard-1.0.3-111111?style=for-the-badge)](https://central.sonatype.com/artifact/io.github.vijiyarathan-rithush/socialcard/1.0.3)
[![Verify workflow](https://img.shields.io/github/actions/workflow/status/Vijiyarathan-Rithush/socialcard-studio/verify.yml?branch=main&style=for-the-badge&label=Verify)](https://github.com/Vijiyarathan-Rithush/socialcard-studio/actions/workflows/verify.yml)

</div>

## App preview

https://github.com/user-attachments/assets/d6f39e24-288f-430d-b630-1b96b6e4aa5f

## What it does

- Combine links, text, contacts and Wi-Fi credentials in one NDEF message.
- Preview how much space your draft uses before connecting a reader.
- Read supported records from an NTAG215 tag and load them into the editor.
- Confirm before overwriting a tag and verify the write by reading it back.

## Installation

### Windows x64

Download `socialcard-studio-windows-x64.zip` from [GitHub Releases](https://github.com/Vijiyarathan-Rithush/socialcard-studio/releases), extract it and open `SocialCard Studio.exe` in the extracted `SocialCard Studio` folder. Keep the folder together so the launcher can find the bundled Java runtime.

### Run from source

Install JDK 25 and Maven. If Maven uses a different JDK, set `JAVA_HOME` to your JDK 25 directory and check it with `mvn -version`.

```sh
mvn javafx:run
```

## Quick start

1. Add a record and check the size under **Tag storage**. Shorten the draft if it exceeds the 496-byte limit shown by the editor.
2. Connect a PC/SC-compatible NFC reader with its driver installed, then click **Scan for readers**.
3. Place a writable NTAG215 tag on the reader. Click **Write to tag** and confirm. Your reader must support reading the tag's UID for writes.
4. Scan the tag with your phone or click **Read tag** to load its supported records back into the editor.

Drafts are kept in memory. Anyone who scans a tag can read any Wi-Fi credentials stored on it.

## Build and verify

Run the tests with `mvn verify`. On x64 Windows, build the portable app with:

```powershell
mvn -Pwindows-exe clean package
```

The launcher is at `target/SocialCard Studio/SocialCard Studio.exe`. The portable build does not require WiX. The [Verify workflow](https://github.com/Vijiyarathan-Rithush/socialcard-studio/actions/workflows/verify.yml) checks Linux and Windows builds and uploads the portable Windows ZIP.

## Built with SocialCard

Studio uses the SocialCard Java library for NDEF encoding and NFC reader communication. See the [SocialCard Java library docs](https://docs.rithush.dev/socialcard/) for installation, Java examples and the API reference.

## Layout and colors

The layout lives in [`studio.fxml`](src/main/resources/com/rvi/studio/studio.fxml) and can be edited in Scene Builder. The charcoal surfaces and orange accents are defined in [`theme.css`](src/main/resources/com/rvi/studio/theme.css).
