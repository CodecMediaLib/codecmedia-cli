package me.tamkungz.codecmedia;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import me.tamkungz.codecmedia.model.ConversionResult;
import me.tamkungz.codecmedia.model.ExtractionResult;
import me.tamkungz.codecmedia.model.PlaybackResult;
import me.tamkungz.codecmedia.model.ProbeResult;
import me.tamkungz.codecmedia.options.AudioExtractOptions;
import me.tamkungz.codecmedia.options.ConversionOptions;
import me.tamkungz.codecmedia.options.PlaybackOptions;
import me.tamkungz.codecmedia.options.ValidationOptions;

/**
 * Minimal command line interface for CodecMedia.
 */
public final class CodecMediaCli {

    private CodecMediaCli() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args == null || args.length == 0 || hasArg(args, "--help") || hasArg(args, "-h")) {
                printUsage(out);
                return 0;
            }

            String command = args[0].toLowerCase(Locale.ROOT);
            CodecMediaEngine engine = CodecMedia.createDefault();

            return switch (command) {
                case "probe" -> handleProbe(engine, args, out);
                case "validate" -> handleValidate(engine, args, out);
                case "convert" -> handleConvert(engine, args, out);
                case "extract-audio" -> handleExtractAudio(engine, args, out);
                case "play" -> handlePlay(engine, args, out);
                case "read-metadata" -> handleReadMetadata(engine, args, out);
                case "write-metadata" -> handleWriteMetadata(engine, args, out);
                default -> {
                    err.println("Unknown command: " + command);
                    printUsage(err);
                    yield 2;
                }
            };
        } catch (IllegalArgumentException ex) {
            err.println("Invalid arguments: " + ex.getMessage());
            return 2;
        } catch (CodecMediaException ex) {
            err.println("CodecMedia error: " + ex.getMessage());
            return 1;
        } catch (Exception ex) {
            err.println("Unexpected error: " + ex.getMessage());
            return 1;
        }
    }

    private static int handleProbe(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "probe <input>");
        ProbeResult result = engine.probe(Path.of(args[1]));
        printProbe(out, result);
        return 0;
    }

    private static int handleValidate(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "validate <input> [--strict] [--max-bytes <n>]");

        Path input = Path.of(args[1]);
        boolean strict = false;
        long maxBytes = ValidationOptions.defaults().maxBytes();

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--strict" -> strict = true;
                case "--max-bytes" -> {
                    String value = requiredValue(args, ++i, "--max-bytes requires a numeric value");
                    maxBytes = parseLong(value, "--max-bytes");
                }
                default -> throw new IllegalArgumentException("Unknown option for validate: " + arg);
            }
        }

        var result = engine.validate(input, new ValidationOptions(strict, maxBytes));
        out.println("valid=" + result.valid());
        out.println("warnings=" + result.warnings());
        out.println("errors=" + result.errors());
        return result.valid() ? 0 : 1;
    }

    private static int handleConvert(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 3, "convert <input> <output> [--format <fmt>] [--preset <preset>] [--overwrite]");

        Path input = Path.of(args[1]);
        Path output = Path.of(args[2]);
        String format = extensionOf(output);
        String preset = "balanced";
        boolean overwrite = false;

        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--format" -> format = requiredValue(args, ++i, "--format requires a value");
                case "--preset" -> preset = requiredValue(args, ++i, "--preset requires a value");
                case "--overwrite" -> overwrite = true;
                default -> throw new IllegalArgumentException("Unknown option for convert: " + arg);
            }
        }

        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Target format missing; provide --format or output extension");
        }

        ConversionResult result = engine.convert(input, output, new ConversionOptions(format, preset, overwrite));
        out.println("output=" + result.outputFile());
        out.println("format=" + result.format());
        out.println("reencoded=" + result.reencoded());
        return 0;
    }

    private static int handleExtractAudio(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 3, "extract-audio <input> <outputDir> [--format <fmt>] [--bitrate <kbps>] [--stream <index>]");

        Path input = Path.of(args[1]);
        Path outputDir = Path.of(args[2]);

        String format = extensionOf(input);
        Integer bitrate = 192;
        Integer stream = 0;

        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--format" -> format = requiredValue(args, ++i, "--format requires a value");
                case "--bitrate" -> bitrate = parseInt(requiredValue(args, ++i, "--bitrate requires a value"), "--bitrate");
                case "--stream" -> stream = parseInt(requiredValue(args, ++i, "--stream requires a value"), "--stream");
                default -> throw new IllegalArgumentException("Unknown option for extract-audio: " + arg);
            }
        }

        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Target format missing; provide --format or input extension");
        }

        ExtractionResult result = engine.extractAudio(input, outputDir, new AudioExtractOptions(format, bitrate, stream));
        out.println("output=" + result.outputFile());
        out.println("format=" + result.format());
        return 0;
    }

    private static int handlePlay(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "play <input> [--dry-run] [--allow-external-app]");

        Path input = Path.of(args[1]);
        boolean dryRun = false;
        boolean allowExternalApp = false;

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--dry-run" -> dryRun = true;
                case "--allow-external-app" -> allowExternalApp = true;
                default -> throw new IllegalArgumentException("Unknown option for play: " + arg);
            }
        }

        PlaybackResult result = engine.play(input, new PlaybackOptions(dryRun, allowExternalApp));
        out.println("started=" + result.started());
        out.println("backend=" + result.backend());
        out.println("mediaType=" + result.mediaType());
        out.println("message=" + result.message());
        return result.started() ? 0 : 1;
    }

    private static int handleReadMetadata(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "read-metadata <input>");
        var metadata = engine.readMetadata(Path.of(args[1]));
        out.println("metadata=" + metadata.entries());
        return 0;
    }

    private static int handleWriteMetadata(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 4, "write-metadata <input> --entry <key=value> [--entry <key=value> ...]");

        Path input = Path.of(args[1]);
        Map<String, String> entries = new LinkedHashMap<>();

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (!"--entry".equals(arg)) {
                throw new IllegalArgumentException("Unknown option for write-metadata: " + arg);
            }
            String kv = requiredValue(args, ++i, "--entry requires key=value");
            int idx = kv.indexOf('=');
            if (idx <= 0 || idx == kv.length() - 1) {
                throw new IllegalArgumentException("Invalid --entry value: " + kv + " (expected key=value)");
            }
            String key = kv.substring(0, idx).trim();
            String value = kv.substring(idx + 1).trim();
            if (key.isBlank()) {
                throw new IllegalArgumentException("Entry key must not be blank");
            }
            entries.put(key, value);
        }

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("At least one --entry key=value is required");
        }

        engine.writeMetadata(input, new me.tamkungz.codecmedia.model.Metadata(entries));
        out.println("metadata entries written=" + entries.size());
        return 0;
    }

    private static void printProbe(PrintStream out, ProbeResult result) {
        out.println("input=" + result.input());
        out.println("mimeType=" + result.mimeType());
        out.println("extension=" + result.extension());
        out.println("mediaType=" + result.mediaType());
        out.println("durationMillis=" + result.durationMillis());
        out.println("streams=" + result.streams());
        out.println("tags=" + result.tags());
    }

    private static boolean hasArg(String[] args, String target) {
        for (String arg : args) {
            if (target.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void requireLength(String[] args, int min, String usage) {
        if (args.length < min) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private static String requiredValue(String[] args, int index, String message) {
        if (index >= args.length) {
            throw new IllegalArgumentException(message);
        }
        return args[index];
    }

    private static int parseInt(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " must be an integer: " + value, ex);
        }
    }

    private static long parseLong(String value, String option) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " must be a long integer: " + value, ex);
        }
    }

    private static String extensionOf(Path output) {
        String name = output.getFileName() == null ? "" : output.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx <= 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1);
    }

    private static void printUsage(PrintStream out) {
        out.println("CodecMedia CLI");
        out.println("Usage:");
        out.println("  probe <input>");
        out.println("  validate <input> [--strict] [--max-bytes <n>]");
        out.println("  convert <input> <output> [--format <fmt>] [--preset <preset>] [--overwrite]");
        out.println("  extract-audio <input> <outputDir> [--format <fmt>] [--bitrate <kbps>] [--stream <index>]");
        out.println("  play <input> [--dry-run] [--allow-external-app]");
        out.println("  read-metadata <input>");
        out.println("  write-metadata <input> --entry <key=value> [--entry <key=value> ...]");
    }
}
