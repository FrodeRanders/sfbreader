package se.fk.sfbreader.model;

import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Kapitel implements Layer {
    private static final Logger log = LoggerFactory.getLogger(Kapitel.class);
    private static final Logger strukturLog = LoggerFactory.getLogger("STRUKTUR");

    protected final String nummer;
    protected final String namn;

    // Such as /Träder i kraft I:den dag som regeringen bestämmer/
    private String periodisering = null;

    @SerializedName(value = "paragraf")
    protected final Collection<Paragraf> paragrafer = new ArrayList<>();

    @SerializedName(value = "avdelning")
    private Avdelning aktuellAvdelning = null;

    @SerializedName(value = "underavdelning")
    private Underavdelning aktuellUnderavdelning = null;

    @SerializedName(value = "rubrik")
    private String aktuellKapitelrubrik = null;

    private transient List<Paragrafrubrik> paragrafrubriker = new ArrayList<>();
    private transient boolean paragrafrubrikRecentlySet = false;

    public Kapitel(String nummer, String namn) {
        this.nummer = nummer;
        this.namn = namn;
        strukturLog.info(indent(4, "Kapitel: " + nummer + " " + namn));
    }

    public String id() {
        return nummer;
    }

    public String namn() {
        return namn;
    }

    public void setPeriodisering(String periodisering) {
        this.periodisering = periodisering;
        paragrafrubrikRecentlySet = false;
    }

    public Optional<String> getPeriodisering() {
        return Optional.ofNullable(periodisering);
    }

    public void addParagraf(Paragraf p) {
        Objects.requireNonNull(p, "p");

        if (null != paragrafrubriker) {
            p.setParagrafrubriker(paragrafrubriker);
        }

        paragrafer.add(p);
        paragrafrubrikRecentlySet = false;

        strukturLog.info(
                indent(5,
                        String.format("K%sP%s", this.nummer, p.nummer()),
                        String.format("<a href=\"%s\">Paragraf: %s</a> %s",
                           String.format(" https://lagen.nu/2010:110#K%sP%s", this.nummer, p.nummer()),
                           p.nummer(), p.rubriker()
                        )
                )
        ); // logging here instead of in Paragraf ctor
    }

    public void setAktuellAvdelning(Avdelning aktuellAvdelning) {
        Objects.requireNonNull(aktuellAvdelning, "aktuellAvdelning");

        log.trace("Kapitel {} # avdelning <-- {}", nummer, aktuellAvdelning);
        this.aktuellAvdelning = aktuellAvdelning;

        paragrafrubrikRecentlySet = false;
    }

    public void setAktuellUnderavdelning(Underavdelning aktuellUnderavdelning) {
        Objects.requireNonNull(aktuellUnderavdelning, "aktuellUnderavdelning");

        log.trace("Kapitel {} # underavdelning <-- {}", nummer, aktuellUnderavdelning);
        this.aktuellUnderavdelning = aktuellUnderavdelning;

        paragrafrubrikRecentlySet = false;
    }

    public void setAktuellKapitelrubrik(Kapitelrubrik aktuellKapitelrubrik) {
        Objects.requireNonNull(aktuellKapitelrubrik, "aktuellKapitelrubrik");

        log.trace("Kapitel {} # rubrik <-- {}", nummer, aktuellKapitelrubrik);
        this.aktuellKapitelrubrik = aktuellKapitelrubrik.namn();

        paragrafrubrikRecentlySet = false;
    }

    public void setAktuellParagrafrubrik(Paragrafrubrik aktuellParagrafrubrik) {
        Objects.requireNonNull(aktuellParagrafrubrik, "aktuellParagrafrubrik");

        strukturLog.trace("Kapitel {} # paragrafrubrik <-- {} ({})", nummer, aktuellParagrafrubrik, paragrafrubriker.size());

        if (paragrafrubrikRecentlySet) {
            if (paragrafrubriker.size() > 1) {
                // Vi hade redan en huvud- och underrubrik (i paragrafrubriker) och stötte nyss
                // på en ny huvudrubrik (i och med att vi nu sett den efterföljande underrubriken)
                Paragrafrubrik nyHuvudrubrik = paragrafrubriker.removeLast();
                paragrafrubriker.clear();
                paragrafrubriker.add(nyHuvudrubrik);
            } else {
                assert paragrafrubriker.size() == 1 : "antal befintliga paragrafrubriker != 1";
            }
        }
        else if (paragrafrubriker.size() > 1) {
            // Här har vi ett problem!!!
            // Det finns inga indikationer i HTML-versionen av lagtexten som hjälper oss avgöra om denna
            // rubrik är en ny huvudrubrik (och alltså ersätter befintliga rubriker) eller en ny underrubrik.
            // Just nu antar vi att man fortsätter med huvud- och underrubriker ut kapitlet om man väl börjat,
            // fast vi vet att så inte är fallet!
            // Se 9 kap 8 § där "Särskilda personkategorier"/"Familjemedlemmar" plötsligt övergår till
            // "De bosättningsbaserade förmånerna"
            //
            // Förmodligen är ända sättet att hantera detta på ett tillförlitligt sätt, att tillhandahålla
            // information om samtliga förekomster av denna art genom hela lagtexten :(
            /*
            # Huvudrubrik: Särskilda personkategorier
            <p><a name="K5P3S3"></a></p>
            <h4 name="Särskilda personkategorier"><a name="Särskilda personkategorier">Särskilda personkategorier</a></h4>

            # Underrubrik: Statsanställda
            <p><a name="K5P3S4"></a></p>
            <h4 name="Statsanställda"><a name="Statsanställda">Statsanställda</a></h4>

            # Ny underrubrik: Diplomater m.fl.
            <p><a name="K5P4S3"></a></p>
            <h4 name="Diplomater m.fl."><a name="Diplomater m.fl.">Diplomater m.fl.</a></h4>

            # Ny underrubrik: Biståndsarbetare m.fl.
            <p><a name="K5P5S3"></a></p>
            <h4 name="Biståndsarbetare m.fl."><a name="Biståndsarbetare m.fl.">Biståndsarbetare m.fl.</a></h4>

            # Ny underrubrik: Utlandsstuderande m.fl.
            <p><a name="K5P6S3"></a></p>
            <h4 name="Utlandsstuderande m.fl."><a name="Utlandsstuderande m.fl.">Utlandsstuderande m.fl.</a></h4>

            # Ny underrubrik: Familjemedlemmar
            <p><a name="K5P7S3"></a></p>
            <h4 name="Familjemedlemmar"><a name="Familjemedlemmar">Familjemedlemmar</a></h4>

            # Ny huvudrubrik: De bosättningsbaserade förmånerna
            <p><a name="K5P8S2"></a></p>
            <h4 name="De bosättningsbaserade förmånerna"><a name="De bosättningsbaserade förmånerna">De bosättningsbaserade förmånerna</a></h4>
            */
            Paragrafrubrik r = paragrafrubriker.removeLast();
            strukturLog.debug("Pop: aktuell paragrafrubrik {}, keeping {} ({})", r, paragrafrubriker.getLast(), paragrafrubriker.size());
        }
        else {
            if (paragrafrubriker.size() == 1) {
                strukturLog.debug("Clear: aktuell paragrafrubrik {} ({})", paragrafrubriker.getFirst(), paragrafrubriker.size());
                paragrafrubriker.clear();
            }
        }
        paragrafrubriker.add(aktuellParagrafrubrik);
        strukturLog.debug("Now {} paragrafrubriker", paragrafrubriker.size());

        paragrafrubrikRecentlySet = true;
    }

    public Collection<Paragraf> get() {
        return paragrafer;
    }

    public void prune() {
        paragrafer.forEach(Paragraf::prune);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Kapitel{");
        buf.append("nummer=").append(nummer);
        buf.append(" namn=\"").append(namn).append("\"");
        if (null != aktuellAvdelning) {
            buf.append(" ").append(aktuellAvdelning);
        }
        if (null != aktuellUnderavdelning) {
            buf.append(" ").append(aktuellUnderavdelning);
        }
        if (null != aktuellKapitelrubrik && !aktuellKapitelrubrik.isEmpty()) {
            buf.append(" rubrik=\"").append(aktuellKapitelrubrik).append("\"");
        }
        buf.append("}");
        return buf.toString();
    }
}
