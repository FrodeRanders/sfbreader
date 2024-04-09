package se.fk.sfbreader;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.sfbreader.model.*;

import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Processor {
    private static final Logger log = LoggerFactory.getLogger(Processor.class);

    private static final Pattern AVDELNING_RE = Pattern.compile("^AVD\\.\\s+([A-Z])\\s+(.+)$");
    private static final Pattern KAPITEL_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s+kap\\.\\s+(.+)$");
    private static final Pattern PARAGRAF_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s*§$");
    private static final Pattern ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)S(\\d+)");

    public Processor() {}

    public Optional<Lag> process(Document doc) {
        Stack<Layer> stack = new Stack<>();
        stack.push(new Lag("Socialförsäkringsbalk", "2010:110")); // Socialförsäkringsbalk (2010:110)

        // want to ignore <div class="sfstoc">
        Element body = doc.select("div:not(.sfstoc)").first();

        body.forEachNode(node -> {
            if (node instanceof Element element) {
                String nodeName = element.nodeName().toLowerCase();
                switch (nodeName) {
                    case "h2" -> avdelning(stack, element);
                    case "h3" -> kapitel(stack, element);
                    case "h4" -> rubrik(stack, element);
                    case "b" -> paragraf(stack, element); // TODO Consider go via ankare
                    case "a" -> ankare(stack, element);
                    case "i" -> referens(stack, element);
                    case "div", "p", "br", "pre" -> {
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
        Attribute name = element.attribute("name");
        Matcher matcher = ANCHOR_RE.matcher(name.getValue());
        if (matcher.find()) {
            String chapter = matcher.group(1);
            String paragraph = matcher.group(2);
            String part = matcher.group(3);

            log.debug("[ankare] Kapitel {}, paragraf {}, stycke {}", chapter, paragraph, part);

            boolean stop = stack.empty();
            if (!stop) {

                int lastStyckeNummer = 0;

                // We have a new part (Stycke) coming soon, so we want to pop anything lower than paragraph (Paragraf)
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke" -> {
                            Stycke stycke = (Stycke) stack.pop();
                            log.debug("[ankare] Pop: {}", stycke);
                            lastStyckeNummer = stycke.nummer();
                        }
                        default /* "Paragraf", "Kapitel", "Avdelning", "Lag" */ -> {
                            log.debug("[ankare] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);

                if (stack.peek() instanceof Paragraf paragraf) {
                    Stycke nyttStycke = new Stycke(
                            lastStyckeNummer > 0 ? ++lastStyckeNummer : 1
                    );
                    paragraf.add(nyttStycke);

                    stack.push(nyttStycke);
                    log.debug("[ankare] Push: {}", nyttStycke);
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
                        case "Stycke", "Paragraf", "Kapitel" -> log.debug("[kapitel] Pop: {}", stack.pop());
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
                stack.push(kapitel);
                log.debug("[kapitel] Push: {}", kapitel);
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
                    case "Stycke", "Paragraf" -> log.debug("[rubrik] Pop: {}", stack.pop());
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

            stack.push(rubrik);
            log.debug("[rubrik] Push: {}", rubrik);
        }
    }

    private void paragraf(Stack<Layer> stack, Element element) {
        log.trace("[paragraf] >> {}", element);
        Matcher matcher = PARAGRAF_RE.matcher(element.text());
        if (matcher.find()) {
            String paragraph = matcher.group(1);

            //
            boolean stop = stack.empty();
            if (!stop) {
                Paragraf paragraf = new Paragraf(paragraph);

                // We have a new paragraph (Paragraf), so we want to pop anything lower than chapter (Kapitel)
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke", "Paragraf" -> log.debug("[paragraf] Pop: {}", stack.pop());
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

                stack.push(paragraf);
                log.debug("[paragraf] Push: {}", paragraf);

                // Prepare the first stycke in this paragraph
                Stycke nyttStycke = new Stycke(1);
                paragraf.add(nyttStycke);

                stack.push(nyttStycke);
                log.debug("[paragraf] Push: {}", nyttStycke);
            }
        }
    }

    private void text(Stack<Layer> stack, TextNode textNode) {
        String text = textNode.text().strip();
        log.trace("[text] >> {}", text);

        if (!stack.isEmpty()) {
            Layer current = stack.peek();
            switch (current.type()) {
                case "Referens" -> {
                    Referens referens = (Referens) stack.pop();
                    log.debug("[text#referens] Pop: {}", referens);

                    if (stack.peek() instanceof Stycke stycke) {
                        stycke.add(referens);
                    }
                }
                case "Direktiv" -> {
                    Direktiv direktiv = (Direktiv) stack.pop();
                    log.debug("[text#direktiv] Pop: {}", direktiv);
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
                        // No Stycke yet, this text goes into first Stycke
                        Stycke nyttStycke = new Stycke(1);
                        paragraf.add(nyttStycke);

                        stack.push(nyttStycke);
                        log.debug("[text#paragraf] Push: {}", nyttStycke);
                    }
                }
                case "Rubrik" -> {
                    Rubrik rubrik = (Rubrik) stack.pop();
                    log.debug("[text#rubrik] Pop: {}", rubrik);
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
                    stack.push(direktiv);
                    log.debug("[direktiv#paragraf] Push: {}", direktiv);
                }
            } else {
                if (stack.peek() instanceof Stycke) {
                    Referens referens = new Referens(text);
                    stack.push(referens);
                    log.debug("[referens#stycke] Push: {}", referens);
                }
            }
        }
    }
}
