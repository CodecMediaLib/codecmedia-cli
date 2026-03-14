package me.tamkungz.codecmedia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CodecMediaCliTest {

    @Test
    void shouldPrintUsageWhenNoArgs() {
        RunResult result = runCli(new String[0]);

        assertEquals(0, result.code());
        assertTrue(result.out().contains("CodecMedia CLI"));
        assertTrue(result.out().contains("Usage:"));
    }

    @Test
    void shouldReturnCode2ForUnknownCommand() {
        RunResult result = runCli(new String[] {"unknown-cmd"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Unknown command: unknown-cmd"));
    }

    @Test
    void shouldReturnCode2WhenValidateMaxBytesValueMissing() {
        RunResult result = runCli(new String[] {"validate", "file.mp3", "--max-bytes"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("--max-bytes requires a numeric value"));
    }

    @Test
    void shouldReturnCode2WhenValidateMaxBytesNotNumeric() {
        RunResult result = runCli(new String[] {"validate", "file.mp3", "--max-bytes", "abc"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("--max-bytes must be a long integer: abc"));
    }

    @Test
    void shouldReturnCode2WhenConvertTargetFormatMissing() {
        RunResult result = runCli(new String[] {"convert", "input.media", "output"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Target format missing"));
    }

    @Test
    void shouldReturnCode2WhenExtractAudioHasUnknownOption() {
        RunResult result = runCli(new String[] {"extract-audio", "input.mp4", "out", "--bad-option"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Unknown option for extract-audio: --bad-option"));
    }

    @Test
    void shouldReturnCode2WhenWriteMetadataEntryMalformed() {
        RunResult result = runCli(new String[] {"write-metadata", "in.mp3", "--entry", "bad"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Invalid --entry value: bad"));
    }

    @Test
    void shouldReturnCode2WhenWriteMetadataEntryValueMissing() {
        RunResult result = runCli(new String[] {"write-metadata", "in.mp3", "--entry"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Usage: write-metadata"));
    }

    @Test
    void shouldReturnCode2WhenWriteMetadataEntryValueBlank() {
        RunResult result = runCli(new String[] {"write-metadata", "in.mp3", "--entry", "artist=   "});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Entry value must not be blank"));
    }

    @Test
    void shouldReturnCode2WhenPlayHasUnknownOption() {
        RunResult result = runCli(new String[] {"play", "song.mp3", "--nope"});

        assertEquals(2, result.code());
        assertTrue(result.err().contains("Unknown option for play: --nope"));
    }

    private static RunResult runCli(String[] args) {
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        int code = CodecMediaCli.run(args, new PrintStream(outBuffer), new PrintStream(errBuffer));
        return new RunResult(
                code,
                outBuffer.toString(StandardCharsets.UTF_8),
                errBuffer.toString(StandardCharsets.UTF_8)
        );
    }

    private record RunResult(int code, String out, String err) {
    }
}
