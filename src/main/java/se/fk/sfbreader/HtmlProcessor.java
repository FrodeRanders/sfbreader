package se.fk.sfbreader;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.fk.sfbreader.model.*;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HtmlProcessor {
    private static final Logger log = LoggerFactory.getLogger(HtmlProcessor.class);

    private static final Pattern AVDELNING_RE = Pattern.compile("^AVD\\.\\s+([A-Z])\\s+(.+)$");
    private static final Pattern UNDERAVDELNING_RE = Pattern.compile("^([IVX]+)\\s+([A-ZÅÄÖ].+)$"); // may always have two (2) spaces after roman numeral???
    private static final Pattern KAPITEL_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s+kap\\.\\s+(.+)$");
    private static final Pattern PARAGRAF_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s*§$");
    private static final Pattern PARAGRAPH_ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)");
    private static final Pattern PART_ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)S(\\d+)");
    private static final Pattern PERIODISERING_RE = Pattern.compile("^/(.+)/$");

    public HtmlProcessor() {
    }

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
     * As h4 may be found at different levels, it signifies different things in the context of Avdelning, Kapitel
     * and Paragraf. We will be detecting the h4 and depending on context, we will discriminate among Underavdelning,
     * Kapitelrubrik and Paragrafrubrik.
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
                Element parent = element.parent(); // parent may be null!

                String nodeName = element.nodeName().toLowerCase();
                switch (nodeName) {
                    case "h2" -> {
                        log.trace("[avdelning] >> {}", element);
                        assert null == parent || "div".equals(parent.nodeName());

                        avdelning(stack, element.text());
                    }
                    case "h3" -> {
                        log.trace("[kapitel] >> {}", element);
                        Element firstChild = element.children().first();
                        assert "a".equals(firstChild.nodeName());

                        kapitel(stack, element.text());
                    }
                    case "h4" -> {
                        log.trace("[sektion] >> {}", element.text());
                        Element firstChild = element.children().first();
                        assert "a".equals(firstChild.nodeName());

                        sektion(stack, element.text()); // but not always :(
                    }
                    case "a" -> ankare(stack, parent, element);
                    case "i" -> referens(stack, element);
                    case "div", "p", "br", "pre", "b" -> {
                        String es = element.text().trim();
                        if (es.length() > 32) {
                            es = es.substring(0, 32) + "...";
                        }
                        log.trace("Ignoring {}: {}", nodeName, es);
                    }
                    default -> {
                        String es = element.text().trim();
                        if (es.length() > 32) {
                            es = es.substring(0, 32) + "...";
                        }
                        log.info("<????> {}: {}", node, es);
                    }
                }
            } else if (node instanceof TextNode textNode) { /* includes content of <pre> tags and such */
                String text = textNode.text().strip();
                log.trace("[text] >> {}", text);

                text(stack, text);
            }
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

    private void ankare(Stack<Layer> stack, Element parent, Element element) {
        Attribute clazz = element.attribute("class");
        Attribute id = element.attribute("id"); // id may indeed be null!
        if (/* necessary */ null == id || !id.hasDeclaredValue()) {
            id = element.attribute("name");
        }

        if (/* necessary */ null != clazz && "paragraf".equalsIgnoreCase(clazz.getValue())) {
            // --- paragraf ---
            assert "div".equals(parent.nodeName());

            // <a class="paragraf" name="K5P9"><b>9 §</b></a>
            Matcher matcher = PARAGRAPH_ANCHOR_RE.matcher(id.getValue());
            if (matcher.find()) {
                String chapter = matcher.group(1);
                String paragraph = matcher.group(2);

                log.debug("[ankare] Kapitel {}, paragraf {}", chapter, paragraph);
                paragraf(stack, parent, element, paragraph);
            }
            return;
        }

        Matcher matcher = PART_ANCHOR_RE.matcher(id.getValue());
        if (matcher.find()) {
            // --- stycke ---
            assert "p".equals(parent.nodeName());

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
                        case "Punkt", "Stycke" -> {
                            previous = (Stycke) popLayer("ankare", stack);
                        }
                        default /* "Paragraf", "Kapitel", ["Underavdelning",] "Avdelning", "Lag" */ -> {
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

    private void avdelning(Stack<Layer> stack, String text) {
        Matcher matcher = AVDELNING_RE.matcher(text);
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
                        case "Punkt", "Stycke", "Paragraf", "Kapitel", "Underavdelning", "Avdelning" ->
                                log.debug("[avdelning] Pop: {}", stack.pop());
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
        }
    }

    private void kapitel(Stack<Layer> stack, String text) {
        /* Ett kapitel kan detekteras med hjälp av flera indicier, men hur gör man här
         * där kapiteltexten brutits upp fel:
         *
         * Skall vara:
         * 87. kap. Allmänna bestämmelser om arbetsskadeersättning m.m. vid dödsfall
         *
         * <h3 name="K87"><a name="K87">87 kap. Allmänna bestämmelser om arbetsskadeersättning m.m.<br /></a></h3>
         * <p><a name="K86P1S2"></a></p>
         * <h4 name="vid dödsfall"><a name="vid dödsfall">vid dödsfall</a></h4>
         *
         * Här har "vid dödsfall" felaktigt markerats som en rubrik i kapitlet :(
         */


        Matcher matcher = KAPITEL_RE.matcher(text);
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
                        case "Punkt", "Stycke", "Paragraf", "Kapitel" -> popLayer("kapitel", stack);
                        default /* ["Underavdelning",] "Avdelning", "Lag" */ -> {
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

    private void sektion(Stack<Layer> stack, String text) {

        // log.trace("--- current stack ---------------------------------------------");
        // stack.forEach(l -> log.trace("{}", l));
        // log.trace("---------------------------------------------------------------");

        Matcher matcher = UNDERAVDELNING_RE.matcher(text);
        if (matcher.find()) {
            String id = matcher.group(1);
            String name = matcher.group(2);
            log.debug("Underavdelning: {} {}", id, name);

            boolean stop = stack.empty();
            if (!stop) {
                // This is an underavdelning, so we want to interrupt everything lower than Avdelning
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Punkt", "Stycke", "Paragraf", "Kapitel", "Underavdelning" -> popLayer("sektion", stack);
                        default /* "Avdelning", "Lag" */ -> {
                            log.debug("[sektion] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);
            }

            if (stack.peek() instanceof Avdelning avdelning) {
                Underavdelning underavdelning = new Underavdelning(id, name);
                avdelning.setAktuellUnderavdelning(underavdelning);
                pushLayer("sektion#underavdelning", stack, underavdelning);
            }
        } else {
            boolean stop = stack.empty();
            if (!stop) {
                // We have a new section. If we encounter a section within a Stycke or Paragraf,
                // we need to break these up -- we will pop anything lower than Kapitel
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Punkt", "Stycke", "Paragraf", "Paragrafrubrik" -> popLayer("sektion", stack);
                        default /* "Kapitel", ["Underavdelning",] "Avdelning", "Lag" */ -> {
                            log.debug("[sektion] Keeping: {}", layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);
            }

            //------------------------------------------------------------------------
            // TODO We can have multiple (or at least two) levels of rubrik within
            //      a kapitel, e.g.:
            //
            //  Kap 2
            //    10 d §  Regeringen eller den myndighet som...
            //
            //    <h4>Definitioner och förklaringar</h4>
            //
            //    <h4>Förmån</h4>
            //    11 §  Med förmåner avses i denna balk...
            //
            //------------------------------------------------------------------------
            if (stack.peek() instanceof Kapitel kapitel) {
                Paragrafrubrik rubrik = new Paragrafrubrik(text);
                kapitel.setAktuellParagrafrubrik(rubrik);
                pushLayer("sektion#paragrafrubrik", stack, rubrik);
            }
        }
    }

    private void paragraf(Stack<Layer> stack, Element parent, Element element, String paragraph) {
        log.trace("[paragraf] >> {}", element);
        assert "div".equals(parent.nodeName());

        //
        boolean stop = stack.empty();
        if (!stop) {
            Paragraf paragraf = new Paragraf(paragraph);

            // We have a new paragraph (Paragraf), so we want to pop anything lower than chapter (Kapitel)
            do {
                Layer layer = stack.peek();
                switch (layer.type()) {
                    case "Punkt", "Stycke", "Paragraf" -> popLayer("paragraf", stack);
                    default /* "Kapitel", ["Underavdelning",] "Avdelning", "Lag" */ -> {
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

    private void text(Stack<Layer> stack, String text) {
        if (!stack.isEmpty()) {
            Layer current = stack.peek();

            if (!"Underavdelning".equals(current.type())) {
                /* Avdelning C, underavdelning VI följer inte mönstret med <h4>-tag,
                 * exempelvis
                 *   <h4 name="III  Efterlevandeförmåner från arbetsskadeförsäkringen m.m."><a name="III  Efterlevandeförmåner från arbetsskadeförsäkringen m.m.">III  Efterlevandeförmåner från arbetsskadeförsäkringen m.m.</a></h4>
                 * utan förekommer som ren text :(
                 *
                 * <a class="paragraf" name="K44P6"><b>6 §</b></a>
                 * Bestämmelserna [..] krigsskadeersättning.<br />
                 * VI  Särskilda förmåner vid smitta, sjukdom eller skada
                 * <p><a name="K44P6S2"></a></p><br />
                 * <h3 name="K45">...</h3>
                 */
                Matcher matcher = UNDERAVDELNING_RE.matcher(text);
                if (matcher.find()) {
                    sektion(stack, text);
                    return;
                }
            }

            switch (current.type()) {
                case "Referens" -> {
                    Referens referens = (Referens) popLayer("text#referens", stack);

                    List<Layer> reversedStack = stack.reversed();
                    Layer nextToLast = reversedStack.get(1);

                    if (nextToLast instanceof Paragraf paragraf) {
                        paragraf.add(referens);
                    }
                }
                case "Direktiv" -> {
                    Direktiv direktiv = (Direktiv) popLayer("text#direktiv", stack);
                }
                case "Stycke" -> {
                    Stycke stycke = (Stycke) current;

                    Matcher periodiseringMatcher = PERIODISERING_RE.matcher(text);
                    if (periodiseringMatcher.find()) {
                        String periodisering = periodiseringMatcher.group(1);
                        log.info("[text#periodisering] {}", periodisering);

                        List<Layer> reversedStack = stack.reversed();
                        assert (current == reversedStack.get(0));

                        // This actually belong to the parent paragraf
                        Layer p = reversedStack.get(1);
                        if (p instanceof Paragraf paragraf) {
                            paragraf.setPeriodisering(periodisering);
                        }
                    } else if (stycke.isEmpty()) { /* First text in Stycke */
                        // Avoid "1 §"
                        Matcher paragrafMatcher = PARAGRAF_RE.matcher(text);
                        if (!paragrafMatcher.find()) {
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

                    Matcher periodiseringMatcher = PERIODISERING_RE.matcher(text);
                    if (periodiseringMatcher.find()) {
                        String periodisering = periodiseringMatcher.group(1);
                        log.info("[text#periodisering] {}", periodisering);

                        List<Layer> reversedStack = stack.reversed();
                        assert (current == reversedStack.get(0));

                        // This actually belong to the parent kapitel
                        Layer p = reversedStack.get(1);
                        if (p instanceof Kapitel kapitel) {
                            kapitel.setPeriodisering(periodisering);
                        }
                    } else if (paragraf.isEmpty()) {
                        // This text goes into first Stycke
                        Stycke nyttStycke = new Stycke();
                        paragraf.add(nyttStycke);

                        pushLayer("text#paragraf", stack, nyttStycke);
                    }
                }
                case "Underavdelning" -> {
                    popLayer("text#sektion( Underavdelning )", stack);
                }
                case "Kapitelrubrik" -> {
                    popLayer("text#sektion( Kapitelrubrik )", stack);
                }
                case "Paragrafrubrik" -> {
                    popLayer("text#sektion( Paragrafrubrik )", stack); // TODO
                }
                case "Kapitel" -> {
                    Matcher periodiseringMatcher = PERIODISERING_RE.matcher(text);
                    if (periodiseringMatcher.find()) {
                        String periodisering = periodiseringMatcher.group(1);
                        log.info("[text#periodisering] {}", periodisering);

                        Kapitel kapitel = (Kapitel) current;
                        kapitel.setPeriodisering(periodisering);

                    } else {
                        log.debug("[text] (superfluous) ignored at {}", current);
                    }
                }
                default /* "Kapitel", "Avdelning", "Lag" */ -> {
                    Matcher periodiseringMatcher = PERIODISERING_RE.matcher(text);
                    if (periodiseringMatcher.find()) {
                        String periodisering = periodiseringMatcher.group(1);
                        log.warn("[text#periodisering] <<<OBS>>> {}", periodisering);
                    }
                    log.debug("[text] (superfluous) ignored at {}", current);
                }
            }
        }
    }
}
