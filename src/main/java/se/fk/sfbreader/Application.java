package se.fk.sfbreader;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import se.fk.sfbreader.model.Lag;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Application {
    private final static Logger log = LogManager.getLogger(Application.class);

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) {
        if (!System.getProperty("file.encoding").equals("UTF-8")) {
            System.out.println(
                    "Changing system encoding from '" + System.getProperty("file.encoding") + "' to 'UTF-8'"
            );
            System.setProperty("file.encoding", "UTF-8");
        }

        // Enable assertions
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        loader.setDefaultAssertionStatus(true);

        Options options = new Options();
        options.addOption(Option.builder("t")
                .required(false)
                .hasArgs()
                .desc("Template used when generating output")
                .longOpt("template")
                .get());

        options.addOption(Option.builder("d")
                .required(false)
                .hasArgs()
                .desc("Directory where output is produced")
                .longOpt("directory")
                .get());
        options.addOption(Option.builder("s")
                .required(false)
                .hasArg()
                .desc("Source mode: html | text | hybrid (default)")
                .longOpt("source-mode")
                .get());
        options.addOption(Option.builder("b")
                .required(false)
                .hasArg()
                .desc("Baseline file with allowlisted reconciliation finding keys")
                .longOpt("reconciliation-baseline")
                .get());
        options.addOption(Option.builder("f")
                .required(false)
                .hasArg(false)
                .desc("Fail (non-zero exit) if new HIGH severity reconciliation findings are detected")
                .longOpt("fail-on-new-high")
                .get());
        options.addOption(Option.builder("w")
                .required(false)
                .hasArg()
                .desc("Write current HIGH severity finding keys as baseline file")
                .longOpt("write-reconciliation-baseline")
                .get());
        options.addOption(Option.builder("e")
                .required(false)
                .hasArg()
                .desc("Effective legal date for selecting active paragraph variants (YYYY-MM-DD)")
                .longOpt("effective-date")
                .get());
        options.addOption(Option.builder()
                .required(false)
                .hasArg(false)
                .desc("Fail if invalid/unresolved periodisering markers are present after parsing/filtering")
                .longOpt("strict-periodisering")
                .get());
        options.addOption(Option.builder()
                .required(false)
                .hasArg()
                .desc("Periodisering validation mode: strict | lenient | off (default: lenient)")
                .longOpt("periodisering-mode")
                .get());

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            Path inputFile = null;
            for (String _inputFile : commandLine.getArgs()) {
                Path path = Path.of(_inputFile);
                File file = path.toFile();
                if (!file.exists()) {
                    System.err.println("File does not exist: " + _inputFile);
                    System.exit(1);
                }
                if (!file.canRead()) {
                    System.err.println("Can't read file: " + _inputFile);
                    System.exit(1);
                }
                inputFile = path;
            }

            final Collection<Path> templates = new ArrayList<>();
            String[] templateValues = commandLine.getOptionValues("t");
            if (templateValues != null) {
                for (String template : templateValues) {
                    Path path = Path.of(template);
                    File file = path.toFile();
                    if (!file.exists()) {
                        System.err.println("Template does not exist: " + template);
                        System.exit(1);
                    }
                    if (!file.canRead()) {
                        System.err.println("Can't read template: " + template);
                        System.exit(1);
                    }
                    templates.add(path);
                }
            }

            Path directory = null;
            if (!templates.isEmpty()) {
                File outputDirectory = new File("latex");
                String _directory = commandLine.getOptionValue("d");
                if (null != _directory && !_directory.isEmpty()) {
                    outputDirectory = new File(_directory);
                }

                if (outputDirectory.exists()) {
                    if (outputDirectory.isFile()) {
                        System.err.println("There exists a file where output was supposed to go: " + outputDirectory.getAbsolutePath());
                        System.err.flush();
                        System.exit(2);
                    }
                    System.out.println("WARNING: Output directory already exists: will replace output in " + outputDirectory.getAbsolutePath());

                } else if (!outputDirectory.mkdir()) {
                    System.err.println("Could not create output directory: " + outputDirectory.getAbsolutePath());
                    System.err.flush();
                    System.exit(3);
                }

                if (!outputDirectory.canWrite()) {
                    System.err.println("Not allowed to write to output directory: " + outputDirectory.getAbsolutePath());
                    System.err.flush();
                    System.exit(4);
                }
                directory = outputDirectory.toPath();
            }

            SourceMode sourceMode = SourceMode.from(commandLine.getOptionValue("s"));
            ReconciliationOptions reconciliationOptions = ReconciliationOptions.from(commandLine);
            Optional<LocalDate> effectiveDate = parseEffectiveDate(commandLine.getOptionValue("e"));
            PeriodiseringMode periodiseringMode = PeriodiseringMode.from(
                    commandLine.getOptionValue("periodisering-mode"),
                    commandLine.hasOption("strict-periodisering")
            );

            //
            process(inputFile, templates, directory, System.out, sourceMode, reconciliationOptions, effectiveDate, periodiseringMode);
        } catch (Throwable t) {
            log.error(t.getMessage(), t);
            t.printStackTrace(System.err);
        }
    }

    private static void process(final Path inputFile, final Collection<Path> templates, final Path directory, final PrintStream out, final SourceMode sourceMode, final ReconciliationOptions reconciliationOptions, final Optional<LocalDate> effectiveDate, final PeriodiseringMode periodiseringMode) {
        try {
            DocumentSources sourceStreams = DocumentSources.from(inputFile, StandardCharsets.UTF_8);
            String lagName = sourceStreams.title().orElse("Unknown law");
            String lagId = sourceStreams.id().orElse("unknown");
            HtmlProcessor htmlProcessor = new HtmlProcessor(lagName, lagId);
            TextProcessor textProcessor = new TextProcessor(lagName, lagId);

            Optional<Lag> lagFromHtml = Optional.empty();
            if (sourceMode.parseHtml()) {
                Optional<InputStream> htmlStream = sourceStreams.openHtmlStream();
                if (htmlStream.isPresent()) {
                    try (InputStream is = htmlStream.get()) {
                        lagFromHtml = pullFromStream(is, "http://nope.local", StandardCharsets.UTF_8, htmlProcessor);
                    }
                }
            }

            Optional<Lag> lagFromText = Optional.empty();
            if (sourceMode.parseText()) {
                Optional<InputStream> textStream = sourceStreams.openTextStream();
                if (textStream.isPresent()) {
                    try (InputStream is = textStream.get();
                         Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        lagFromText = pullFromText(reader, textProcessor);
                    }
                }
            }

            Optional<Lag> _lag = sourceMode.selectPrimary(lagFromHtml, lagFromText);
            if (sourceMode == SourceMode.HYBRID && lagFromHtml.isPresent() && lagFromText.isPresent()) {
                log.info("Both HTML and text payload parsed from '{}'. Using HTML as primary structure source.",
                        inputFile.getFileName());

                HybridReconciler reconciler = new HybridReconciler();
                HybridReconciler.Result reconciliation = reconciler.reconcile(lagFromHtml.get(), lagFromText.get());
                log.info("Hybrid reconciliation produced {} findings for '{}'",
                        reconciliation.findingCount(), inputFile.getFileName());

                Path reportFile = inputFile.resolveSibling("reconciliation-report.txt");
                Files.writeString(reportFile, reconciliation.asText(), StandardCharsets.UTF_8);
                Path reportJson = inputFile.resolveSibling("reconciliation.json");
                Files.writeString(reportJson, gson.toJson(reconciliation), StandardCharsets.UTF_8);

                Set<String> baselineKeys = loadBaselineKeys(reconciliationOptions.baselinePath().orElse(null));
                List<HybridReconciler.Finding> newHigh = reconciliation.findings().stream()
                        .filter(f -> f.severity() == HybridReconciler.Severity.HIGH)
                        .filter(f -> !baselineKeys.contains(f.key()))
                        .toList();

                Path newHighReport = inputFile.resolveSibling("reconciliation-new-high.txt");
                writeNewHighReport(newHighReport, newHigh);

                if (reconciliationOptions.writeBaselinePath().isPresent()) {
                    Path baselineOut = reconciliationOptions.writeBaselinePath().get();
                    writeBaselineKeys(baselineOut, reconciliation.findings().stream()
                            .filter(f -> f.severity() == HybridReconciler.Severity.HIGH)
                            .toList());
                    log.info("Wrote reconciliation HIGH-severity baseline to: {}", baselineOut);
                }

                if (!baselineKeys.isEmpty()) {
                    log.info("Loaded reconciliation baseline with {} keys", baselineKeys.size());
                }

                if (reconciliationOptions.failOnNewHigh() && !newHigh.isEmpty()) {
                    System.err.println("New HIGH severity reconciliation findings not in baseline: " + newHigh.size());
                    System.err.println("See: " + newHighReport);
                    System.exit(10);
                }
            }

            if (_lag.isEmpty()) {
                System.err.println("No parseable payload for mode '" + sourceMode.mode() + "' in input file: " + inputFile.getFileName());
                System.exit(3);
                return;
            }

            Lag lag = _lag.get();
            if (effectiveDate.isPresent()) {
                EffectiveDateFilter.Report filterReport = EffectiveDateFilter.apply(lag, effectiveDate.get());
                Path effectiveDateReport = inputFile.resolveSibling("effective-date-report.json");
                Files.writeString(effectiveDateReport, gson.toJson(filterReport), StandardCharsets.UTF_8);
            }

            if (periodiseringMode != PeriodiseringMode.OFF) {
                PeriodiseringValidator.Result periodiseringValidation = PeriodiseringValidator.validate(lag);
                Path periodiseringReport = inputFile.resolveSibling("periodisering-validation.json");
                Files.writeString(periodiseringReport, gson.toJson(periodiseringValidation), StandardCharsets.UTF_8);
                if (periodiseringMode == PeriodiseringMode.STRICT
                        && (periodiseringValidation.invalidCount() > 0
                        || periodiseringValidation.unresolvedCount() > 0
                        || periodiseringValidation.inlineInTextCount() > 0)) {
                    System.err.println("Strict periodisering check failed: invalid="
                            + periodiseringValidation.invalidCount()
                            + ", unresolved=" + periodiseringValidation.unresolvedCount()
                            + ", inlineInText=" + periodiseringValidation.inlineInTextCount());
                    System.err.println("See: " + periodiseringReport);
                    System.exit(12);
                }
            }

            LocalDate scheduleReferenceDate = effectiveDate.orElse(LocalDate.now());
            PeriodiseringSchedule.Report scheduleReport = PeriodiseringSchedule.build(lag, scheduleReferenceDate);
            Path scheduleReportPath = inputFile.resolveSibling("periodisering-schedule.json");
            Files.writeString(scheduleReportPath, gson.toJson(scheduleReport), StandardCharsets.UTF_8);
            lag.prune();

            if (!templates.isEmpty()) {
                assert directory != null : "Expected output directory when templates are provided";
                LatexProcessor printer = new LatexProcessor();
                printer.process(lag, templates, directory, out);
            }

            //
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(inputFile.resolveSibling("output.json"), StandardCharsets.UTF_8))) {
                lag.prepareForSerialization();
                pw.write(gson.toJson(lag));
            }
        } catch (IOException e) {
            System.err.println("Can't read file: " + inputFile.getFileName() + ": " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            System.err.println("Can't parse file: " + inputFile.getFileName() + ": " + e.getMessage());
            System.exit(3);
        }
    }

    private static Optional<Lag> pullFromStream(InputStream is, String baseUri, Charset charset, HtmlProcessor processor) throws IOException {
        Document doc = Jsoup.parse(is, charset.name(), baseUri);
        return processor.process(doc);
    }

    private static Optional<Lag> pullFromText(Reader reader, TextProcessor processor) throws IOException {
        return processor.process(reader);
    }

    private static Set<String> loadBaselineKeys(Path baselinePath) throws IOException {
        if (baselinePath == null) {
            return Set.of();
        }
        if (!Files.exists(baselinePath)) {
            throw new IOException("Baseline file does not exist: " + baselinePath);
        }
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(baselinePath, StandardCharsets.UTF_8)) {
            String s = line.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            keys.add(s);
        }
        return keys;
    }

    private static Optional<LocalDate> parseEffectiveDate(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid --effective-date: " + value + " (expected YYYY-MM-DD)");
        }
    }

    private static void writeBaselineKeys(Path target, List<HybridReconciler.Finding> findings) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (HybridReconciler.Finding finding : findings) {
            keys.add(finding.key());
        }
        List<String> sorted = new ArrayList<>(keys);
        sorted.sort(Comparator.naturalOrder());
        Files.write(target, sorted, StandardCharsets.UTF_8);
    }

    private static void writeNewHighReport(Path target, List<HybridReconciler.Finding> newHigh) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("New HIGH severity reconciliation findings: " + newHigh.size());
        lines.add("");
        for (HybridReconciler.Finding finding : newHigh) {
            lines.add(finding.key() + " :: " + finding.message());
        }
        Files.write(target, lines, StandardCharsets.UTF_8);
    }

    private enum SourceMode {
        HTML("html"),
        TEXT("text"),
        HYBRID("hybrid");

        private final String mode;

        SourceMode(String mode) {
            this.mode = mode;
        }

        String mode() {
            return mode;
        }

        boolean parseHtml() {
            return this == HTML || this == HYBRID;
        }

        boolean parseText() {
            return this == TEXT || this == HYBRID;
        }

        Optional<Lag> selectPrimary(Optional<Lag> html, Optional<Lag> text) {
            return switch (this) {
                case HTML -> html;
                case TEXT -> text;
                case HYBRID -> html.isPresent() ? html : text;
            };
        }

        static SourceMode from(String value) {
            if (null == value || value.isBlank()) {
                return HYBRID;
            }
            String normalized = value.trim().toLowerCase();
            return switch (normalized) {
                case "html" -> HTML;
                case "text" -> TEXT;
                case "hybrid" -> HYBRID;
                default -> throw new IllegalArgumentException("Unsupported --source-mode: " + value + ". Expected html|text|hybrid");
            };
        }
    }

    private enum PeriodiseringMode {
        STRICT("strict"),
        LENIENT("lenient"),
        OFF("off");

        private final String mode;

        PeriodiseringMode(String mode) {
            this.mode = mode;
        }

        static PeriodiseringMode from(String value, boolean strictFlag) {
            PeriodiseringMode fromOption;
            if (value == null || value.isBlank()) {
                fromOption = LENIENT;
            } else {
                fromOption = switch (value.trim().toLowerCase()) {
                    case "strict" -> STRICT;
                    case "lenient" -> LENIENT;
                    case "off" -> OFF;
                    default -> throw new IllegalArgumentException("Unsupported --periodisering-mode: " + value + ". Expected strict|lenient|off");
                };
            }

            if (strictFlag && fromOption == OFF) {
                throw new IllegalArgumentException("Conflicting flags: --strict-periodisering cannot be combined with --periodisering-mode off");
            }
            if (strictFlag) {
                return STRICT;
            }
            return fromOption;
        }
    }

    private record ReconciliationOptions(
            Optional<Path> baselinePath,
            Optional<Path> writeBaselinePath,
            boolean failOnNewHigh
    ) {
        static ReconciliationOptions from(CommandLine commandLine) {
            String baseline = commandLine.getOptionValue("b");
            String writeBaseline = commandLine.getOptionValue("w");
            boolean failOnNewHigh = commandLine.hasOption("f");

            return new ReconciliationOptions(
                    baseline == null || baseline.isBlank() ? Optional.empty() : Optional.of(Path.of(baseline)),
                    writeBaseline == null || writeBaseline.isBlank() ? Optional.empty() : Optional.of(Path.of(writeBaseline)),
                    failOnNewHigh
            );
        }
    }
}
