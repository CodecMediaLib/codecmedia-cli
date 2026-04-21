# CodecMedia CLI

Command-line interface for CodecMedia, providing media probing, validation, sidecar metadata generation, audio extraction, playback workflow simulation, and conversion routing.

Version `1.2.0` aligns with `codecmedia-java:1.2.0`, including MP3 decode conversion routes (`mp3 -> pcm` / `mp3 -> wav`) and preset-driven decoder selection via `--preset decoder=javasound|pure-java|layer3`.

`extract-audio` now follows engine defaults when no extract flags are supplied, and supports nullable extract controls with `--bitrate auto` / `--stream auto`.

## Core library

Core library: **codecmedia**  
Website: https://codecmedia.tamkungz.me/  
Repository: https://github.com/CodecMediaLib/codecmedia-java

## Project repository

GitHub: https://github.com/CodecMediaLib/codecmedia-cli
SSH: git@github.com:CodecMediaLib/codecmedia-cli.git

## Build

```bash
mvn clean package
```

## Run

```bash
java -jar target/codecmedia-cli-1.2.0-all.jar
```

## Conversion preset examples

```bash
# MP3 -> WAV with experimental pure Java decoder
java -jar target/codecmedia-cli-1.2.0-all.jar convert in.mp3 out.wav --preset decoder=pure-java --overwrite

# PCM -> WAV with explicit sample rate/channels/bit depth
java -jar target/codecmedia-cli-1.2.0-all.jar convert in.pcm out.wav --preset sr=22050,ch=1,bits=16 --overwrite

# Extract audio using engine defaults
java -jar target/codecmedia-cli-1.2.0-all.jar extract-audio in.mp3 out

# Extract audio with nullable bitrate/stream
java -jar target/codecmedia-cli-1.2.0-all.jar extract-audio in.mp3 out --format mp3 --bitrate auto --stream auto
```
