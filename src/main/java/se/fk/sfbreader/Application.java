package se.fk.sfbreader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import se.fk.sfbreader.model.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hello world!
 */
public class Application {
    private static final Pattern AVDELNING_RE = Pattern.compile("^AVD\\.\\s+([A-Z])\\s+(.+)$");
    private static final Pattern KAPITEL_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s+kap\\.\\s+(.+)$");
    private static final Pattern PARAGRAF_RE = Pattern.compile("^(\\d+\\s*[a-z]?)\\s*§$");
    private static final Pattern ANCHOR_RE = Pattern.compile("K(\\d+[a-zA-Z]?)P(\\d+[a-zA-Z]?)S(\\d+)");

    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: ... <html-file>");
            System.exit(1);
        }

        File file = new File(args[0]);
        if (!file.exists() || !file.canRead()) {
            System.err.println("Can't access file: " + file.getAbsolutePath());
            System.exit(2);
        }

        try (InputStream is = Files.newInputStream(file.toPath())) {
            pullFromStream(is, "http://nope.local", StandardCharsets.UTF_8, Application::process);

        } catch (IOException e) {
            System.err.println("Can't read file: " + file.getName() + ": " + e.getMessage());
            System.exit(3);
        }
    }

    private static boolean process(Document doc) {
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
                    // case "br" -> stycke(stack, element);
                    // case "pre" -> stycke4(stack, element);
                    case "a" -> ankare(stack, element);
                    case "i" -> referens(stack, element);
                    case "div", "p", "br", "pre" -> {
                        System.out.println("Ignoring " + nodeName);
                    }
                    default -> System.out.println("???? " + node);
                }
            } else if (node instanceof TextNode textNode)
                text(stack, textNode);
            else
                System.out.println("???? " + node);

        });

        Layer top = stack.elementAt(0);
        if (null != top) {
            System.out.println("TOP: " + top);
            top.prune();
            System.out.println(gson.toJson(top));
        }
        return true;
    }

    private static void ankare(Stack<Layer> stack, Element element) {
        Attribute name = element.attribute("name");
        Matcher matcher = ANCHOR_RE.matcher(name.getValue());
        if (matcher.find()) {
            String chapter = matcher.group(1);
            String paragraph = matcher.group(2);
            String part = matcher.group(3);

            System.out.println("[ankare] Kapitel " + chapter + ", paragraf " + paragraph + ", stycke " + part);

            boolean stop = stack.empty();
            if (!stop) {

                int lastStyckeNummer = 0;

                // We have a new part (Stycke) coming soon, so we want to pop anything lower than paragraph (Paragraf)
                do {
                    Layer layer = stack.peek();
                    switch (layer.type()) {
                        case "Stycke" -> {
                            Stycke stycke = (Stycke) stack.pop();
                            System.out.println("[ankare] Pop: " + stycke);
                            lastStyckeNummer = stycke.nummer();
                        }
                        default /* "Paragraf", "Kapitel", "Avdelning", "Lag" */ -> {
                            System.out.println("[ankare] Keeping: " + layer);
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
                    System.out.println("[ankare] Push: " + nyttStycke);
                }
            }
        }
    }

    private static void avdelning(Stack<Layer> stack, Element element) {
        System.out.println("[avdelning] >> " + element);

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
                        case "Stycke", "Paragraf", "Kapitel", "Avdelning" -> System.out.println("[avdelning] Pop: " + stack.pop());
                        default /* "Lag" */ -> {
                            System.out.println("[avdelning] Keeping: " + layer);
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
                System.out.println("[avdelning] Push: " + avdelning);
            }
        } else {
            System.out.println("UNDERAVDELNING: " + element.text());

            // TODO ???
        }
    }

    private static void kapitel(Stack<Layer> stack, Element element) {
        System.out.println("[kapitel] >> " + element);
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
                        case "Stycke", "Paragraf", "Kapitel" -> System.out.println("[kapitel] Pop: " + stack.pop());
                        default /* "Avdelning", "Lag" */ -> {
                            System.out.println("[kapitel] Keeping: " + layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);


                if (!stack.empty() && stack.peek() instanceof Avdelning avdelning) {
                    avdelning.addKapitel(kapitel);
                }
                stack.push(kapitel);
                System.out.println("[kapitel] Push: " + kapitel);
            }
        }
    }

    private static void rubrik(Stack<Layer> stack, Element element) {
        System.out.println("[rubrik] >> " + element.text());
        System.out.println("---");
        stack.forEach(System.out::println);
        System.out.println("---");

        String text = element.text();
        //
        boolean stop = stack.empty();
        if (!stop) {
            Rubrik rubrik = new Rubrik(text);

            // We have a new chapter section (Rubrik), so we want to pop anything lower than chapter (Kapitel)
            do {
                Layer layer = stack.peek();
                switch (layer.type()) {
                    case "Stycke", "Paragraf" -> System.out.println("[rubrik] Pop: " + stack.pop());
                    default /* "Kapitel", "Avdelning", "Lag" */ -> {
                        System.out.println("[rubrik] Keeping: " + layer);
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
            System.out.println("[rubrik] Push: " + rubrik);
        }
    }

    private static void paragraf(Stack<Layer> stack, Element element) {
        System.out.println("[paragraf] >> " + element);
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
                        case "Stycke", "Paragraf" -> System.out.println("[paragraf] Pop: " + stack.pop());
                        default /* "Kapitel", "Avdelning", "Lag" */ -> {
                            System.out.println("[paragraf] Keeping: " + layer);
                            stop = true;
                        }
                    }
                    stop |= stack.empty();
                } while (!stop);

                if (stack.peek() instanceof Kapitel kapitel) {
                    kapitel.addParagraf(paragraf);
                }

                stack.push(paragraf);
                System.out.println("[paragraf] Push: " + paragraf);

                // Prepare the first stycke in this paragraph
                Stycke nyttStycke = new Stycke(1);
                paragraf.add(nyttStycke);

                stack.push(nyttStycke);
                System.out.println("[paragraf] Push: " + nyttStycke);
            }
        }
    }

    private static void text(Stack<Layer> stack, TextNode textNode) {
        String text = textNode.text().strip();
        System.out.println("[text] >> " + text);

        if (!stack.isEmpty()) {
            Layer current = stack.peek();
            switch (current.type()) {
                case "Referens" -> {
                    Referens referens = (Referens) stack.pop();
                    System.out.println("[text#referens] Pop: " + referens);

                    if (stack.peek() instanceof Stycke stycke) {
                        stycke.add(referens);
                    }
                }
                case "Direktiv" -> {
                    Direktiv direktiv = (Direktiv) stack.pop();
                    System.out.println("[text#direktiv] Pop: " + direktiv);
                }
                case "Stycke" -> {
                    Stycke stycke = (Stycke) current;

                    if (stycke.isEmpty()) {
                        // Avoid "1 §"
                        Matcher matcher = PARAGRAF_RE.matcher(text);
                        if (!matcher.find()) {
                            stycke.add(text);
                            System.out.println("[text#stycke] " + text);
                        }
                    } else {
                        stycke.add(text);
                        System.out.println("[text#stycke] " + text);
                    }
                }
                case "Paragraf" -> {
                    Paragraf paragraf = (Paragraf) current;

                    if (paragraf.isEmpty()) {
                        // No Stycke yet, this text goes into first Stycke
                        Stycke nyttStycke = new Stycke(1);
                        paragraf.add(nyttStycke);

                        stack.push(nyttStycke);
                        System.out.println("[text#paragraf] Push: " + nyttStycke);
                    }
                }
                case "Rubrik" -> {
                    Rubrik rubrik = (Rubrik) stack.pop();
                    System.out.println("[text#rubrik] Pop: " + rubrik);
                }
                default /* "Kapitel", "Avdelning", "Lag" */ -> {
                    System.out.println("[text] Ignoring at " + current);
                }
            }
        }
    }

    private static void referens(Stack<Layer> stack, Element element) {
        System.out.println("[referens || direktiv] >> " + element);
        String text = element.text();
        if (!stack.isEmpty()) {
            if (text.startsWith("/")) {
                if (stack.peek() instanceof Paragraf) {
                    Direktiv direktiv = new Direktiv(text);
                    stack.push(direktiv);
                    System.out.println("[direktiv#paragraf] Push: " + direktiv);
                }
            } else {
                if (stack.peek() instanceof Stycke) {
                    Referens referens = new Referens(text);
                    stack.push(referens);
                    System.out.println("[referens#stycke] Push: " + referens);
                }
            }
        }
    }

    private static boolean pullFromStream(InputStream is, String baseUri, Charset charset, ProcessRunnable runnable) throws IOException {
        Document doc = Jsoup.parse(is, charset.name(), baseUri);
        return runnable.run(doc);
    }

    private interface ProcessRunnable {
        boolean run(Document doc) throws IOException;
    }
}
