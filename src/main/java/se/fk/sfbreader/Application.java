package se.fk.sfbreader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
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
        State state = new State();

        Stack<Layer> stack = new Stack<>();
        stack.push(new Lag("Socialförsäkringsbalk", "2010:110")); // Socialförsäkringsbalk (2010:110)

        Element body = doc.select("body>div").first(); // TODO

        body.forEachNode(node -> {
            if (node instanceof Element element) {
                String nodeName = element.nodeName().toLowerCase();
                switch (nodeName) {
                    case "h2" -> avdelning(stack, element);
                    case "h3" -> kapitel(stack, element);
                    case "h4" -> kapitelSektion(stack, element);
                    case "span" -> paragraf(stack, element);
                    case "br" -> stycke(state, stack, element);
                    case "pre" -> stycke4(state, stack, element);
                    case "i" -> referens(stack, element);
                    case "#document", "html", "body", "div" -> {
                        System.out.println(" <ignoring " + nodeName + ">");
                    }
                    default -> System.out.println("####### " + node);
                }
            } else if (node instanceof TextNode textNode)
                text(state, stack, textNode);
            else
                System.out.println("#### " + node);

        });
        Layer top = stack.elementAt(0);
        if (null != top) {
            System.out.println("TOP: " + top);
            top.prune();
            System.out.println(gson.toJson(top));
        }
        return true;
    }

    private static void avdelning(Stack<Layer> stack, Element element) {
        Matcher matcher = AVDELNING_RE.matcher(element.id());
        if (matcher.find()) {
            String id = matcher.group(1);
            String name = matcher.group(2);

            System.out.println("AVDELNING: " + id + " " + name);

            //
            Avdelning avdelning = new Avdelning(id, name);
            while (!stack.empty() && !(stack.peek() instanceof Lag)) {
                Layer l = stack.pop();
                System.out.println("Pop: " + l);
            }

            if (!stack.empty() && stack.peek() instanceof Lag) {
                Lag lag = (Lag) stack.peek();
                lag.add(avdelning);
            }
            stack.push(avdelning);
            System.out.println("Push: " + avdelning);

        } else {
            System.out.println("SUB-AVDELNING: " + element.id());
        }
    }

    private static void kapitel(Stack<Layer> stack, Element element) {
        Matcher matcher = KAPITEL_RE.matcher(element.id());
        if (matcher.find()) {
            String chapter = matcher.group(1);
            String name = matcher.group(2);

            System.out.println("KAPITEL: " + chapter + " " + name);

            //
            Kapitel kapitel = new Kapitel(chapter, name);
            while (!stack.empty() && !(stack.peek() instanceof Avdelning)) {
                Layer l = stack.pop();
                System.out.println("Pop: " + l);
            }

            if (!stack.empty() && stack.peek() instanceof Avdelning) {
                Avdelning avdelning = (Avdelning) stack.peek();
                avdelning.add(kapitel);
            }
            stack.push(kapitel);
            System.out.println("Push: " + kapitel);
        }
    }

    private static void kapitelSektion(Stack<Layer> stack, Element element) {
        System.out.println("KAPITEL-SEKTION: " + element.text());
    }

    private static void paragraf(Stack<Layer> stack, Element element) {
        Matcher matcher = PARAGRAF_RE.matcher(element.text());
        if (matcher.find()) {
            String paragraph = matcher.group(1);

            System.out.println("PARAGRAF: " + paragraph + " (" + element.text() + ")");

            //
            Paragraf paragraf = new Paragraf(paragraph);
            while (!stack.empty() && !(stack.peek() instanceof Kapitel)) {
                Layer l = stack.pop();
                System.out.println("Pop: " + l);
            }

            if (!stack.empty() && stack.peek() instanceof Kapitel) {
                Kapitel kapitel = (Kapitel) stack.peek();
                kapitel.add(paragraf);
            }

            stack.push(paragraf);
            System.out.println("Push: " + paragraf);
        }
    }

    private static void stycke(State state, Stack<Layer> stack, Element element) {
        if (stack.isEmpty()) {
            System.err.println("#### <br> and empty stack");
        } else {
            if (state.isBreaking) {
                if (stack.peek() instanceof Stycke) {
                    // Likely
                    Stycke lastStycke = (Stycke) stack.pop();
                    System.out.println("Pop: " + lastStycke);

                    assert (stack.peek() instanceof Paragraf);
                    Paragraf paragraf = (Paragraf) stack.peek();

                    Stycke nyttStycke = new Stycke(lastStycke.nummer() + 1);
                    paragraf.add(nyttStycke);

                    stack.push(nyttStycke);
                    System.out.println("STYCKE(2): " + ((Stycke) stack.peek()).nummer());
                    System.out.println("Push: " + nyttStycke);
                } else if (stack.peek() instanceof Paragraf paragraf) {
                    // Unlikely since paragraphs don't usually start with a <br>
                    Stycke nyttStycke = new Stycke(1);
                    paragraf.add(nyttStycke);

                    stack.push(nyttStycke);
                    System.out.println("STYCKE(3): " + ((Stycke) stack.peek()).nummer());
                    System.out.println("Push: " + nyttStycke);
                }
            } else {
                state.isBreaking = true;
            }
        }
    }

    private static void stycke4(State state, Stack<Layer> stack, Element element) {
        if (stack.isEmpty()) {
            System.err.println("#### <pre> and empty stack");
        } else {
            if (stack.peek() instanceof Stycke stycke) {
                String text = element.text();
                stycke.add(text);
                System.out.println("STYCKE(4): " + ((Stycke) stack.peek()).nummer());
                System.out.println(text);
            }
        }
    }

    private static void text(State state, Stack<Layer> stack, TextNode textNode) {
        String text = textNode.text().strip();

        if (!stack.isEmpty()) {
            Layer current = stack.peek();
            if (current instanceof Referens referens) {
                Layer l = stack.pop();
                System.out.println("Pop: " + l);

                if (stack.peek() instanceof Stycke stycke) {
                    stycke.add(referens);
                }
            } else if (current instanceof Direktiv direktiv) {
                Layer l = stack.pop();
                System.out.println("Pop: " + l);

                String d = direktiv.direktiv();
            } else {
                if (!text.isEmpty()) {
                    state.isBreaking = false;
                }

                if (current instanceof Stycke stycke) {
                    stycke.add(text);
                    System.out.println(text);
                } else if (current instanceof Paragraf paragraf) {
                    if (paragraf.get().isEmpty()) {
                        Stycke nyttStycke = new Stycke(1);
                        //nyttStycke.add(text); // Will be paragraph number
                        paragraf.add(nyttStycke);

                        stack.push(nyttStycke);
                        System.out.println("STYCKE(1): " + ((Stycke) stack.peek()).nummer());
                        System.out.println("Push: " + nyttStycke);
                    }
                }
            }
        }
    }

    private static void referens(Stack<Layer> stack, Element element) {
        String text = element.text();
        if (text.startsWith("/")) {
            System.out.println("DIREKTIV: " + text);
            if (!stack.isEmpty()) {
                if (stack.peek() instanceof Paragraf) {
                    Direktiv direktiv = new Direktiv(text);
                    stack.push(direktiv);
                    System.out.println("Push: " + direktiv);
                }
            }
        } else {
            System.out.println("REFERENS: " + text);
            if (!stack.isEmpty()) {
                if (stack.peek() instanceof Stycke) {
                    Referens referens = new Referens(text);
                    stack.push(referens);
                    System.out.println("Push: " + referens);
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

    private static class State {
        boolean isBreaking = false;
    }
}
