package me.tamkungz.codecmedia;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
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
 * Minimal command-line interface for the CodecMedia library.
 *
 * <p>This class acts as the main entry point, parsing CLI arguments and
 * delegating to the appropriate {@link CodecMediaEngine} method. It is
 * {@code final} and non-instantiable; all interaction happens through
 * {@link #main(String[])} or the testable {@link #run(String[], PrintStream, PrintStream)}.
 *
 * <p><b>Supported commands:</b>
 * <pre>
 *   get            &lt;input&gt;
 *   probe          &lt;input&gt;
 *   validate       &lt;input&gt; [--strict] [--max-bytes &lt;n&gt;]
 *   convert        &lt;input&gt; &lt;output&gt; [--format &lt;fmt&gt;] [--preset &lt;preset&gt;] [--overwrite]
 *   extract-audio  &lt;input&gt; &lt;outputDir&gt; [--format &lt;fmt&gt;] [--bitrate &lt;kbps&gt;] [--stream &lt;index&gt;]
 *   play           &lt;input&gt; [--dry-run] [--allow-external-app] [--no-external-app]
 *   read-metadata  &lt;input&gt;
 *   write-metadata &lt;input&gt; --entry &lt;key=value&gt; [--entry &lt;key=value&gt; ...]
 * </pre>
 */
public final class CodecMediaCli {

    @FunctionalInterface
    private interface CommandHandler {
        int handle(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException;
    }

    private static final Map<String, CommandHandler> COMMANDS = createCommands();

    private CodecMediaCli() {
    }

    /**
     * Application entry point.
     *
     * <p>Delegates to {@link #run(String[], PrintStream, PrintStream)} and
     * calls {@link System#exit(int)} with a non-zero code if the command fails.
     *
     * @param args command-line arguments supplied by the OS
     */
    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /**
     * Parses and dispatches a CLI command.
     *
     * <p>The first element of {@code args} is treated as the command name
     * (case-insensitive). A fresh {@link CodecMediaEngine} is created via
     * {@link CodecMedia#createDefault()} for every invocation.
     *
     * <p>If {@code args} is {@code null}, empty, or contains {@code --help}
     * or {@code -h}, the usage text is printed to {@code out} and {@code 0}
     * is returned.
     *
     * @param args command-line arguments; may be {@code null} or empty
     * @param out  stream for normal command output
     * @param err  stream for error and diagnostic messages
     * @return {@code 0} on success,
     *         {@code 1} on engine or unexpected runtime errors,
     *         {@code 2} on invalid or unrecognised arguments
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args == null || args.length == 0 || hasArg(args, "--help") || hasArg(args, "-h")) {
                printUsage(out);
                return 0;
            }

            String command = args[0].toLowerCase(Locale.ROOT);
            CodecMediaEngine engine = CodecMedia.createDefault();

            CommandHandler handler = COMMANDS.get(command);
            if (handler == null) {
                err.println("Unknown command: " + command);
                printUsage(err);
                return 2;
            }
            return handler.handle(engine, args, out);
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

    private static Map<String, CommandHandler> createCommands() {
        Map<String, CommandHandler> handlers = new HashMap<>();
        handlers.put("get", CodecMediaCli::handleGet);
        handlers.put("probe", CodecMediaCli::handleProbe);
        handlers.put("validate", CodecMediaCli::handleValidate);
        handlers.put("convert", CodecMediaCli::handleConvert);
        handlers.put("extract-audio", CodecMediaCli::handleExtractAudio);
        handlers.put("play", CodecMediaCli::handlePlay);
        handlers.put("read-metadata", CodecMediaCli::handleReadMetadata);
        handlers.put("write-metadata", CodecMediaCli::handleWriteMetadata);
        return Collections.unmodifiableMap(handlers);
    }

    /**
     * Handles the {@code get} command.
     *
     * <p>Retrieves and prints probe information for the given media file.
     * Functionally identical to {@link #handleProbe}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} must be the input path
     * @param out    stream to write probe output to
     * @return {@code 0} on success
     * @throws CodecMediaException if the engine fails to read the file
     */
    private static int handleGet(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "get <input>");
        ProbeResult result = engine.get(Path.of(args[1]));
        printProbe(out, result);
        return 0;
    }

    /**
     * Handles the {@code probe} command.
     *
     * <p>Probes the given media file and prints detailed stream information.
     * Output fields: {@code input}, {@code mimeType}, {@code extension},
     * {@code mediaType}, {@code durationMillis}, {@code streams}, {@code tags}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} must be the input path
     * @param out    stream to write probe output to
     * @return {@code 0} on success
     * @throws CodecMediaException if probing fails
     */
    private static int handleProbe(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "probe <input>");
        ProbeResult result = engine.probe(Path.of(args[1]));
        printProbe(out, result);
        return 0;
    }

    /**
     * Handles the {@code validate} command.
     *
     * <p>Validates the given media file and prints {@code valid}, {@code warnings},
     * and {@code errors}. Accepts the following options:
     * <ul>
     *   <li>{@code --strict} — enables strict validation mode</li>
     *   <li>{@code --max-bytes <n>} — sets the maximum allowed file size in bytes</li>
     * </ul>
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} must be the input path
     * @param out    stream to write validation results to
     * @return {@code 0} if the file is valid, {@code 1} if it is invalid
     * @throws CodecMediaException  if the engine encounters an error
     * @throws IllegalArgumentException if an unrecognised option is supplied
     */
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

    /**
     * Handles the {@code convert} command.
     *
     * <p>Converts a media file to a different format. The target format is
     * inferred from the output file extension unless {@code --format} is given.
     * Accepted options:
     * <ul>
     *   <li>{@code --format <fmt>} — overrides the target format</li>
     *   <li>{@code --preset <preset>} — encoding preset (default: {@code balanced})</li>
     *   <li>{@code --overwrite} — allows overwriting an existing output file</li>
     * </ul>
     *
     * <p>Output fields: {@code output}, {@code format}, {@code reencoded}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} is input, {@code args[2]} is output
     * @param out    stream to write conversion results to
     * @return {@code 0} on success
     * @throws CodecMediaException      if conversion fails
     * @throws IllegalArgumentException if the target format cannot be determined or an unknown option is supplied
     */
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

    /**
     * Handles the {@code extract-audio} command.
     *
     * <p>Extracts an audio stream from the input file and writes it to
     * the specified output directory. Accepted options:
     * <ul>
     *   <li>{@code --format <fmt>} — output audio format (default from {@link AudioExtractOptions#defaults()})</li>
     *   <li>{@code --bitrate <kbps>} — target bitrate in kbps</li>
     *   <li>{@code --stream <index>} — zero-based index of the audio stream to extract</li>
     * </ul>
     *
     * <p>Output fields: {@code output}, {@code format}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} is input, {@code args[2]} is the output directory
     * @param out    stream to write extraction results to
     * @return {@code 0} on success
     * @throws CodecMediaException      if extraction fails
     * @throws IllegalArgumentException if an unrecognised option or non-integer value is supplied
     */
    private static int handleExtractAudio(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 3, "extract-audio <input> <outputDir> [--format <fmt>] [--bitrate <kbps>] [--stream <index>]");

        Path input = Path.of(args[1]);
        Path outputDir = Path.of(args[2]);

        AudioExtractOptions defaults = AudioExtractOptions.defaults();
        String format = defaults.targetFormat();
        Integer bitrate = defaults.bitrateKbps();
        Integer stream = defaults.streamIndex();

        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--format" -> format = requiredValue(args, ++i, "--format requires a value");
                case "--bitrate" -> bitrate = parseInt(requiredValue(args, ++i, "--bitrate requires a value"), "--bitrate");
                case "--stream" -> stream = parseInt(requiredValue(args, ++i, "--stream requires a value"), "--stream");
                default -> throw new IllegalArgumentException("Unknown option for extract-audio: " + arg);
            }
        }

        ExtractionResult result = engine.extractAudio(input, outputDir, new AudioExtractOptions(format, bitrate, stream));
        out.println("output=" + result.outputFile());
        out.println("format=" + result.format());
        return 0;
    }

    /**
     * Handles the {@code play} command.
     *
     * <p>Initiates playback of the given media file. Accepted options:
     * <ul>
     *   <li>{@code --dry-run} — simulates playback without actually starting it</li>
     *   <li>{@code --allow-external-app} — permits an external media player</li>
     *   <li>{@code --no-external-app} — restricts playback to the internal backend only</li>
     * </ul>
     *
     * <p>Output fields: {@code started}, {@code backend}, {@code mediaType}, {@code message}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} must be the input path
     * @param out    stream to write playback results to
     * @return {@code 0} if playback started, {@code 1} otherwise
     * @throws CodecMediaException      if the engine encounters an error
     * @throws IllegalArgumentException if an unrecognised option is supplied
     */
    private static int handlePlay(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "play <input> [--dry-run] [--allow-external-app] [--no-external-app]");

        Path input = Path.of(args[1]);
        boolean dryRun = false;
        boolean allowExternalApp = PlaybackOptions.defaults().allowExternalApp();

        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--dry-run" -> dryRun = true;
                case "--allow-external-app" -> allowExternalApp = true;
                case "--no-external-app" -> allowExternalApp = false;
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

    /**
     * Handles the {@code read-metadata} command.
     *
     * <p>Reads and prints all metadata entries from the given media file.
     * Output: {@code metadata=<map>}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} must be the input path
     * @param out    stream to write metadata to
     * @return {@code 0} on success
     * @throws CodecMediaException if reading metadata fails
     */
    private static int handleReadMetadata(CodecMediaEngine engine, String[] args, PrintStream out) throws CodecMediaException {
        requireLength(args, 2, "read-metadata <input>");
        var metadata = engine.readMetadata(Path.of(args[1]));
        out.println("metadata=" + metadata.entries());
        return 0;
    }

    /**
     * Handles the {@code write-metadata} command.
     *
     * <p>Writes one or more metadata key-value pairs to the given media file.
     * Each pair is supplied via {@code --entry key=value}; at least one entry
     * is required.
     *
     * <p>Validation rules for each {@code --entry} value:
     * <ul>
     *   <li>Must contain exactly one {@code =} separator</li>
     *   <li>Key (left of {@code =}) must not be blank</li>
     *   <li>Value (right of {@code =}) must not be blank</li>
     * </ul>
     *
     * <p>Output: {@code metadata entries written=<count>}.
     *
     * @param engine the engine to use
     * @param args   full argument array; {@code args[1]} is input,
     *               followed by one or more {@code --entry key=value} pairs
     * @param out    stream to confirm the number of entries written
     * @return {@code 0} on success
     * @throws CodecMediaException      if writing metadata fails
     * @throws IllegalArgumentException if no entries are supplied or an entry is malformed
     */
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

    /**
     * Prints all fields of a {@link ProbeResult} to the given stream.
     *
     * <p>Output lines: {@code input}, {@code mimeType}, {@code extension},
     * {@code mediaType}, {@code durationMillis}, {@code streams}, {@code tags}.
     *
     * @param out    stream to write to
     * @param result probe result to print
     */
    private static void printProbe(PrintStream out, ProbeResult result) {
        out.println("input=" + result.input());
        out.println("mimeType=" + result.mimeType());
        out.println("extension=" + result.extension());
        out.println("mediaType=" + result.mediaType());
        out.println("durationMillis=" + result.durationMillis());
        out.println("streams=" + result.streams());
        out.println("tags=" + result.tags());
    }

    /**
     * Returns {@code true} if {@code target} is present anywhere in {@code args}.
     *
     * @param args   the argument array to search
     * @param target the string to look for
     * @return {@code true} if found, {@code false} otherwise
     */
    private static boolean hasArg(String[] args, String target) {
        for (String arg : args) {
            if (target.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asserts that {@code args} has at least {@code min} elements.
     *
     * @param args  the argument array to check
     * @param min   minimum required length
     * @param usage usage string included in the exception message
     * @throws IllegalArgumentException if {@code args.length < min}
     */
    private static void requireLength(String[] args, int min, String usage) {
        if (args.length < min) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    /**
     * Returns {@code args[index]}, or throws if {@code index} is out of bounds.
     *
     * @param args    the argument array
     * @param index   index of the value to retrieve
     * @param message error message used when the index is out of bounds
     * @return the argument at {@code index}
     * @throws IllegalArgumentException if {@code index >= args.length}
     */
    private static String requiredValue(String[] args, int index, String message) {
        if (index >= args.length) {
            throw new IllegalArgumentException(message);
        }
        return args[index];
    }

    /**
     * Parses {@code value} as an {@code int}.
     *
     * @param value  the string to parse
     * @param option option name used in the error message
     * @return the parsed integer
     * @throws IllegalArgumentException if {@code value} is not a valid integer
     */
    private static int parseInt(String value, String option) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " must be an integer: " + value, ex);
        }
    }

    /**
     * Parses {@code value} as a {@code long}.
     *
     * @param value  the string to parse
     * @param option option name used in the error message
     * @return the parsed long
     * @throws IllegalArgumentException if {@code value} is not a valid long integer
     */
    private static long parseLong(String value, String option) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(option + " must be a long integer: " + value, ex);
        }
    }

    /**
     * Extracts the file extension from a {@link Path}.
     *
     * <p>Returns the substring after the last {@code .} in the filename.
     * Returns {@code null} if the filename has no extension, starts with a dot,
     * or ends with a dot.
     *
     * @param output the path whose extension is needed
     * @return the extension string (without the leading dot), or {@code null}
     */
    private static String extensionOf(Path output) {
        String name = output.getFileName() == null ? "" : output.getFileName().toString();
        int idx = name.lastIndexOf('.');
        if (idx <= 0 || idx == name.length() - 1) {
            return null;
        }
        return name.substring(idx + 1);
    }

    /**
     * Prints the full CLI usage summary to the given stream.
     *
     * @param out stream to write usage text to
     */
    private static void printUsage(PrintStream out) {
        out.println("CodecMedia CLI");
        out.println("Usage:");
        out.println("  get <input>");
        out.println("  probe <input>");
        out.println("  validate <input> [--strict] [--max-bytes <n>]");
        out.println("  convert <input> <output> [--format <fmt>] [--preset <preset>] [--overwrite]");
        out.println("  extract-audio <input> <outputDir> [--format <fmt>] [--bitrate <kbps>] [--stream <index>]");
        out.println("  play <input> [--dry-run] [--allow-external-app] [--no-external-app]");
        out.println("  read-metadata <input>");
        out.println("  write-metadata <input> --entry <key=value> [--entry <key=value> ...]");
    }
}
