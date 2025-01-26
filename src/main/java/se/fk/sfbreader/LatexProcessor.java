package se.fk.sfbreader;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import se.fk.sfbreader.model.*;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Optional;

public class LatexProcessor {
    private static final Logger log = LoggerFactory.getLogger(LatexProcessor.class);

    // Cf. se.fk.sfbreader.model.Stycke IS_ITEMIZED_RE
    private static final String NEEDS_EXTRA_SPACING_RE = "^(-\\s|\\d+(\\s?[a-z])?\\.\\s|[a-z]\\.\\s).+";

    public void process(
            final Lag lag,
            final Collection<Path> templates,
            final Path directory,
            final PrintStream out
    ) {
        final STGroup group =  new STGroup();
        for (Path template : templates) {
            String resource = "file:" + template.toAbsolutePath();
            group.loadGroupFile(/* absolute path is "relative" to root :) */ "/", resource);
        }

        Path latexFile = directory.resolve("output.tex");
        try (FileWriterWithEncoding s = FileWriterWithEncoding.builder()
                .setPath(latexFile)
                .setAppend(false)
                .setCharsetEncoder(StandardCharsets.UTF_8.newEncoder())
                .get()) {

            // preamble(date)
            {
                ST template = group.getInstanceOf("preamble");
                template.add("namn", lag.namn());
                template.add("id", lag.id());
                LocalDate date = LocalDate.now();
                template.add("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                s.append(template.render());
            }

            processLag(lag, group, s);

            // postamble(date)
            {
                ST postamble = group.getInstanceOf("postamble");
                s.append(postamble.render());
            }
        } catch (IOException ioe) {
            String info = "Failed to produce output: " + ioe.getMessage();
            log.error(info, ioe);
            out.println(info);
            out.flush();
        }
    }

    private void processLag(
            final Lag lag,
            final STGroup group,
            final FileWriterWithEncoding writer
    ) throws IOException {

        for (Avdelning avdelning : lag.get()) {
            // avdelning(id, namn)
            {
                ST template = group.getInstanceOf("avdelning");
                Optional<String> id = avdelning.id();
                id.ifPresent(s -> template.add("id", s));
                String namn = avdelning.namn();
                template.add("namn", namn);
                writer.append(template.render());
            }

            processAvdelning(avdelning, group, writer);
        }
    }

    private void processAvdelning(
            final Avdelning avdelning,
            final STGroup group,
            final FileWriterWithEncoding writer
    ) throws IOException {

        for (Kapitel kapitel : avdelning.get()) {
            // kapitel(nummer, namn)
            {
                ST template = group.getInstanceOf("kapitel");
                template.add("id", kapitel.id());
                template.add("namn", kapitel.namn());
                writer.append(template.render());
            }

            // periodisering(info)
            Optional<String> periodisering = kapitel.getPeriodisering();
            if (periodisering.isPresent()) {
                ST template = group.getInstanceOf("periodisering");
                template.add("kontext", "detta kapitel");
                template.add("info", periodisering.get());
                writer.append(template.render());
            }

            processKapitel(kapitel, group, writer);
        }
    }

    private void processKapitel(
            final Kapitel kapitel,
            final STGroup group,
            final FileWriterWithEncoding writer
    ) throws IOException {

        for (Paragraf paragraf : kapitel.get()) {
            // paragraf(nummer)
            {
                ST template = group.getInstanceOf("paragraf");
                template.add("nummer", paragraf.nummer());
                writer.append(template.render());
            }

            // periodisering(info)
            Optional<String> periodisering = paragraf.getPeriodisering();
            if (periodisering.isPresent()) {
                ST template = group.getInstanceOf("periodisering");
                template.add("kontext", "denna paragraf");
                template.add("info", periodisering.get());
                writer.append(template.render());
            }

            processParagraf(kapitel, paragraf, group, writer);
        }
    }

    private void processParagraf(
            final Kapitel kapitel,
            final Paragraf paragraf,
            final STGroup group,
            final FileWriterWithEncoding writer
    ) throws IOException {

        int styckeNummer = 0;
        for (Stycke stycke : paragraf.get()) {
            // stycke(kapitelnummer,paragrafnummer,styckenummer)
            {
                ST template = group.getInstanceOf("stycke");
                template.add("kapitelnummer", kapitel.id());
                template.add("paragrafnummer", paragraf.nummer());
                template.add("styckenummer", ++styckeNummer);
                writer.append(template.render());
            }

            // periodisering(info)
            Optional<String> periodisering = stycke.getPeriodisering();
            if (periodisering.isPresent()) {
                ST template = group.getInstanceOf("periodisering");
                template.add("kontext", "detta stycke");
                template.add("info", periodisering.get());
                writer.append(template.render());
            }

            int writtenLines = 0;

            for (String text : stycke.get()) {
                if (text.matches(NEEDS_EXTRA_SPACING_RE) && writtenLines > 0) {
                    writer.append("\\newline ");
                }

                writer.append(text).append("\n");
                writtenLines++;
            }
        }
    }

    /*
    kapitelsektion(namn) ::= <<
    paragrafsektion(namn) ::= <<
    */

    private String toHex(String s) {
        StringBuilder sb = new StringBuilder();

        final int bytesPerLine = 16;
        final byte[] bytes = s.getBytes();

        long count = 0;
        for (byte b : bytes) {
            if (count++ % bytesPerLine == 0)
                sb.append("\n");
            else
                sb.append(" ");

            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}
