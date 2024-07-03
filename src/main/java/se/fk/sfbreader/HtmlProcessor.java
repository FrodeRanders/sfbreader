package se.fk.sfbreader;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.sfbreader.model.*;

import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HtmlProcessor {
    private static final Logger log = LoggerFactory.getLogger(HtmlProcessor.class);

    private static final Pattern AVDELNING_RE = Pattern.compile("^AVD\\.\\s+([A-Z])\\s+(.+)$");
    private static final Pattern KAPITEL_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s+kap\\.\\s+(.+)$");
    private static final Pattern PARAGRAF_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s*§$");
    private static final Pattern PARAGRAPH_ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)");
    private static final Pattern PART_ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)S(\\d+)");

    public HtmlProcessor() {}

    private void pushLayer(String where, Stack<Layer> stack, Layer layer) {
        Objects.requireNonNull(where, "where");

        stack.push(layer);
        log.debug("[{}] Push: {}", where, layer);

    }

    private Layer popLayer(String where, Stack<Layer> stack) {
        Objects.requireNonNull(where, "where");

        Layer layer = stack.pop();
        log.debug("[{}] Pop: {}", where, layer);
        return layer;
    }

    /*
     * Avdelningar are located in HTML like this (Note that superfluous trailing anchor <a name="S2">):
     * <pre>
     *   <h2>AVD. A ÖVERGRIPANDE BESTÄMMELSER</h2><p><a name="S2"></a></p>
     * </pre>
     * Currently detecting the h2...
     *
     * Underavdelingar are located in HTML like this (Note that superfluous trailing anchor <a name="S3">):
     * <pre>
     *   <h4 name="I  Bla bla bla"><a name="I  Bla bla bla">I  Bla bla bla</a></h4><p><a name="S3"></a></p><br />
     * </pre>
     * Currently detecting the h4... and treating as same kind of "rubrik" as found in chapters and paragraphs.
     *
     * Kapitel (chapters) are located in HTML like this:
     * <pre>
     *   <h3 name="K1"><a name="K1">1 kap. Something...
     * </pre>
     * Should we detect the h3.name or the a.name? Currently doing a RE on h3.name...
     *
     * Paragrafer (paragraphs) are located in HTML like this:
     * <pre>
     *   <a class="paragraf" name="K1P1"><b>1 §</b></a> &nbsp;&nbsp;Denna balk innehåller
     * </pre>
     * Should we detect the a.class or the b? Currently doing a RE on a.class...
     *
     * Stycken (parts) are located in HTML like this (Note how part 1 is implicit as part of paragraph):
     * <pre>
     *   <a class="paragraf" name="K26P29"><b>29 §</b></a> &nbsp;&nbsp;Stycke 1...<p><a name="K26P29S2"></a></p>Stycke 2...
     * </pre>
     *
     *
     */
    public Optional<Lag> process(Document doc) {
        Stack<Layer> stack = new Stack<>();
        stack.push(new Lag("Socialförsäkringsbalk", "2010:110"));

        // want to ignore <div class="sfstoc">
        Element body = doc.select("div:not(.sfstoc)").first();

        body.forEachNode(node -> {
            if (node instanceof Element element) {
                String nodeName = element.nodeName().toLowerCase();
                switch (nodeName) {
                    case "h2" -> avdelning(stack, element);
                    case "h3" -> kapitel(stack, element);
                    case "h4" -> rubrik(stack, element);
                    case "a" -> ankare(stack, element);
                    case "i" -> referens(stack, element);
                    case "div", "p", "br", "pre", "b" -> {
                        log.trace("Ignoring {}", nodeName);
                    }
                    default -> log.info("???? {}", node);
                }
            } else if (node instanceof TextNode textNode)
                text(stack, textNode);
            else
                log.debug("???? {}", node);

        });

        Layer top = stack.elementAt(0);
        if (null != top) {
            log.debug("TOP: {}", top);
            top.prune();
        }
        return Optional.ofNullable((Lag) top);
    }

    private void ankare(Stack<Layer> stack, Element element) {
        Attribute clazz = element.attribute("class");
        Attribute name = element.attribute("name");

        if (/* necessary */ null != clazz && "paragraf".equalsIgnoreCase(clazz.getValue())) {
            // --- paragraf ---

            // <a class="paragraf" name="K5P9"><b>9 §</b></a>
            Matcher matcher = PARAGRAPH_ANCHOR_RE.matcher(name.getValue());
            if (matcher.find()) {
                String chapter = matcher.group(1);
                String paragraph = matcher.group(2);

                log.debug("[ankare] Kapitel {}, paragraf {}", chapter, paragraph);
                paragraf(stack, element, paragraph);
            }
            return;
        }

        Matcher matcher = PART_ANCHOR_RE.matcher(name.getValue());
        if (matcher.find()) {
            // --- stycke ---

            // <a name="K5P8S3"></a>
            String chapter = matcher.group(1);
            String paragraph = matcher.group(2);
            String part = matcher.group(3);

            log.debug("[ankare] Kapitel {}, paragraf {}, stycke {}", chapter, paragraph, part);

            boolean stop = stack.empty();
            if (!stop) {

                // We have a new part (Stycke) coming soon, so we want to pop anything lower than paragraph (Paragraf)
                Stycke previous = null;
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke" -> {
                            previous = (Stycke) popLayer("ankare", stack);
                        }
                        default /* "Paragraf", "Kapitel", "Avdelning", "Lag" */ -> {
                            log.debug("[ankare] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);

                if (stack.peek() instanceof Paragraf paragraf) {
                    Stycke nyttStycke;
                    if (null != previous) {
                        nyttStycke = new Stycke(previous);
                    } else {
                        nyttStycke = new Stycke();
                    }
                    paragraf.add(nyttStycke);

                    pushLayer("ankare", stack, nyttStycke);
                }
            }
        }
    }

    private void avdelning(Stack<Layer> stack, Element element) {
        log.trace("[avdelning] >> {}", element);

        Matcher matcher = AVDELNING_RE.matcher(element.text());
        if (matcher.find()) {
            String id = matcher.group(1);
            String name = matcher.group(2);

            //
            Avdelning avdelning = new Avdelning(id, name);

            boolean stop = stack.empty();
            if (!stop) {

                // We have a new avdelning (Paragraf), so we want to pop anything lower than Law (Lag)
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke", "Paragraf", "Kapitel", "Avdelning" -> log.debug("[avdelning] Pop: {}", stack.pop());
                        default /* "Lag" */ -> {
                            log.debug("[avdelning] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);

                // We are assuming we have a law (Lag) on top of stack,
                // in which case we want to add avdelning to it.
                if (stack.peek() instanceof Lag lag) {
                    lag.add(avdelning);
                }

                stack.push(avdelning);
                log.debug("[avdelning] Push: {}", avdelning);
            }
        } else {
            log.debug("UNDERAVDELNING: {}", element.text());

            // TODO ???
        }
    }

    private void kapitel(Stack<Layer> stack, Element element) {
        log.trace("[kapitel] >> {}", element);
        Matcher matcher = KAPITEL_RE.matcher(element.text());
        if (matcher.find()) {
            String chapter = matcher.group(1);
            String name = matcher.group(2);

            //
            boolean stop = stack.empty();
            if (!stop) {
                Kapitel kapitel = new Kapitel(chapter, name);

                // We have a new chapter (Kapitel), so we want to pop anything lower than Avdelning
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke", "Paragraf", "Kapitel" -> popLayer("kapitel", stack);
                        default /* "Avdelning", "Lag" */ -> {
                            log.debug("[kapitel] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);

                if (!stack.empty() && stack.peek() instanceof Avdelning avdelning) {
                    avdelning.addKapitel(kapitel);
                }

                pushLayer("kapitel", stack, kapitel);
            }
        }
    }

    private void rubrik(Stack<Layer> stack, Element element) {
        log.trace("[rubrik] >> {}", element.text());
        log.trace("---");
        stack.forEach(l -> log.trace("{}", l));
        log.trace("---");

        String text = element.text();
        //
        boolean stop = stack.empty();
        if (!stop) {
            Rubrik rubrik = new Rubrik(text);

            // We have a new chapter section (Rubrik), so we want to pop anything lower than chapter (Kapitel)
            do {
                Layer layer = stack.peek();
                switch (layer.type()) {
                    case "Stycke", "Paragraf" -> popLayer("rubriK", stack);
                    default /* "Kapitel", "Avdelning", "Lag" */ -> {
                        log.debug("[rubrik] Keeping: {}", layer);
                        stop = true;
                    }
                }
                stop |= stack.empty();
            } while (!stop);

            if (stack.peek() instanceof Kapitel kapitel) {
                kapitel.addSubRubrik(rubrik);
            } else if (stack.peek() instanceof Avdelning avdelning){
                avdelning.addSubRubrik(rubrik);
            }

            pushLayer("rubrik", stack, rubrik);
        }
    }

    private void paragraf(Stack<Layer> stack, Element element, String paragraph) {
        log.trace("[paragraf] >> {}", element);

        //
        boolean stop = stack.empty();
        if (!stop) {
            Paragraf paragraf = new Paragraf(paragraph);

            // We have a new paragraph (Paragraf), so we want to pop anything lower than chapter (Kapitel)
            do {
                Layer layer = stack.peek();
                switch (layer.type()) {
                    case "Stycke", "Paragraf" -> popLayer("paragraf", stack);
                    default /* "Kapitel", "Avdelning", "Lag" */ -> {
                        log.debug("[paragraf] Keeping: {}", layer);
                        stop = true;
                    }
                }
                stop |= stack.empty();
            } while (!stop);

            if (stack.peek() instanceof Kapitel kapitel) {
                kapitel.addParagraf(paragraf);
            }

            pushLayer("paragraf", stack, paragraf);

            // Prepare the first stycke in this paragraph
            Stycke nyttStycke = new Stycke();
            paragraf.add(nyttStycke);

            pushLayer("paragraf", stack, nyttStycke);
        }
    }

    private void text(Stack<Layer> stack, TextNode textNode) {
        String text = textNode.text().strip();
        log.trace("[text] >> {}", text);

        if (!stack.isEmpty()) {
            Layer current = stack.peek();
            switch (current.type()) {
                case "Referens" -> {
                    Referens referens = (Referens) popLayer("text#referen", stack);

                    if (stack.peek() instanceof Stycke stycke) {
                        stycke.add(referens);
                    }
                }
                case "Direktiv" -> {
                    Direktiv direktiv = (Direktiv) popLayer("text#direktiv", stack);
                }
                case "Stycke" -> {
                    Stycke stycke = (Stycke) current;

                    if (stycke.isEmpty()) {
                        // Avoid "1 §"
                        Matcher matcher = PARAGRAF_RE.matcher(text);
                        if (!matcher.find()) {
                            stycke.add(text);
                            log.debug("[text#stycke] {}", text);
                        }
                    } else {
                        stycke.add(text);
                        log.debug("[text#stycke] {}", text);
                    }
                }
                case "Paragraf" -> {
                    Paragraf paragraf = (Paragraf) current;

                    if (paragraf.isEmpty()) {
                        // This text goes into first Stycke
                        Stycke nyttStycke = new Stycke();
                        paragraf.add(nyttStycke);

                        pushLayer("text#paragraf", stack, nyttStycke);
                    }
                }
                case "Rubrik" -> {
                    Rubrik rubrik = (Rubrik) popLayer("text#rubrik", stack);
                }
                default /* "Kapitel", "Avdelning", "Lag" */ -> {
                    log.debug("[text] Ignoring at {}", current);
                }
            }
        }
    }

    private void referens(Stack<Layer> stack, Element element) {
        log.trace("[referens || direktiv] >> {}", element);
        String text = element.text();
        if (!stack.isEmpty()) {
            if (text.startsWith("/")) {
                if (stack.peek() instanceof Paragraf) {
                    Direktiv direktiv = new Direktiv(text);
                    pushLayer("direktiv#paragraf", stack, direktiv);
                }
            } else {
                if (stack.peek() instanceof Stycke) {
                    Referens referens = new Referens(text);
                    pushLayer("referens#stycke", stack, referens);
                }
            }
        }
    }
}
