package se.fk.sfbreader;

import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import se.fk.sfbreader.model.Lag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class Application {
    private final static Logger log = LogManager.getLogger(Application.class);

    public static void main(String[] args) {
        if (!System.getProperty("file.encoding").equals("UTF-8")) {
            System.out.println(
                    "Changing system encoding from '" + System.getProperty("file.encoding") + "' to 'UTF-8'"
            );
            System.setProperty("file.encoding", "UTF-8");
        }

        Options options = new Options();
        options.addOption(Option.builder("t")
                .required(true)
                .hasArgs()
                .desc("Template used when generating output")
                .longOpt("template")
                .build());

        options.addOption(Option.builder("d")
                .required(false)
                .hasArgs()
                .desc("Directory where output is produced")
                .longOpt("directory")
                .build());

        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine commandLine = parser.parse(options, args);

            Path inputFile = null;
            for (String _gedcomFile : commandLine.getArgs()) {
                Path path = Path.of(_gedcomFile);
                File file = path.toFile();
                if (!file.exists()) {
                    System.err.println("File does not exist: " + _gedcomFile);
                    System.exit(1);
                }
                if (!file.canRead()) {
                    System.err.println("Can't read file: " + _gedcomFile);
                    System.exit(1);
                }
                inputFile = path;
            }

            //
            final Collection<Path> templates = new ArrayList<>();
            for (String template : commandLine.getOptionValues("t")) {
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

            //
            File directory = new File("latex");
            String _directory = commandLine.getOptionValue("d");
            if (null != _directory && !_directory.isEmpty()) {
                directory = new File(_directory);
            }

            if (directory.exists()) {
                if (directory.isFile()) {
                    System.err.println("There exists a file where output was supposed to go: " + directory.getAbsolutePath());
                    System.err.flush();
                    System.exit(2);
                }
                System.out.println("WARNING: Output directory already exists: will replace output in " + directory.getAbsolutePath());

            } else if (!directory.mkdir()) {
                System.err.println("Could not create output directory: " + directory.getAbsolutePath());
                System.err.flush();
                System.exit(3);
            }

            if (!directory.canWrite()) {
                System.err.println("Not allowed to write to output directory: " + directory.getAbsolutePath());
                System.err.flush();
                System.exit(4);
            }

            //
            process(inputFile, templates, directory.toPath(), System.out);
        } catch (Throwable t) {
            System.err.println(t.getMessage());
        }
    }

    private static void process(final Path inputFile, final Collection<Path> templates, final Path directory, final PrintStream out) {
        Processor processor = new Processor();
        try (InputStream is = Files.newInputStream(inputFile)) {
            Optional<Lag> lag = pullFromStream(is, "http://nope.local", StandardCharsets.UTF_8, processor);
            if (lag.isPresent()) {
                Printer printer = new Printer();
                printer.process(lag.get(), templates, directory, out);
            }

        } catch (IOException e) {
            System.err.println("Can't read file: " + inputFile.getFileName() + ": " + e.getMessage());
            System.exit(3);
        }
    }

    private static Optional<Lag> pullFromStream(InputStream is, String baseUri, Charset charset, Processor processor) throws IOException {
        Document doc = Jsoup.parse(is, charset.name(), baseUri);
        return processor.process(doc);
    }
}
