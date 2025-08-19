package org.jabref.logic.layout.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jabref.logic.layout.LayoutFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RTFCharsTest {

    private LayoutFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new RTFChars();
    }

    @AfterEach
    void tearDown() {
        formatter = null;
    }

    @Test
    void basicFormat() {
        assertEquals("", formatter.format(""));

        assertEquals("hallo", formatter.format("hallo"));

        assertEquals(
            "R\\u233eflexions sur le timing de la quantit\\u233e",
            formatter.format("Réflexions sur le timing de la quantité")
        );

        assertEquals("h\\'e1llo", formatter.format("h\\'allo"));
        assertEquals("h\\'e1llo", formatter.format("h\\'allo"));
    }

    @Test
    void laTeXHighlighting() {
        assertEquals("{\\i hallo}", formatter.format("\\emph{hallo}"));
        assertEquals("{\\i hallo}", formatter.format("{\\emph hallo}"));
        assertEquals(
            "An article title with {\\i a book title} emphasized",
            formatter.format(
                "An article title with \\emph{a book title} emphasized"
            )
        );

        assertEquals("{\\i hallo}", formatter.format("\\textit{hallo}"));
        assertEquals("{\\i hallo}", formatter.format("{\\textit hallo}"));

        assertEquals("{\\b hallo}", formatter.format("\\textbf{hallo}"));
        assertEquals("{\\b hallo}", formatter.format("{\\textbf hallo}"));
    }

    @Test
    void complicated() {
        assertEquals(
            "R\\u233eflexions sur le timing de la quantit\\u233e {\\u230ae} should be \\u230ae",
            formatter.format(
                "Réflexions sur le timing de la quantité {\\ae} should be æ"
            )
        );
    }

    @Test
    void complicated2() {
        assertEquals("h\\'e1ll{\\u339oe}", formatter.format("h\\'all{\\oe}"));
    }

    @Test
    void complicated3() {
        assertEquals(
            "Le c\\u339oeur d\\u233e\\u231cu mais l'\\u226ame plut\\u244ot na\\u239ive, Lou\\u255ys r" +
            "\\u234eva de crapa\\u252?ter en cano\\u235e au del\\u224a des \\u238iles, pr\\u232es du m\\u228alstr" +
            "\\u246om o\\u249u br\\u251ulent les nov\\u230ae.",
            formatter.format(
                "Le cœur déçu mais l'âme plutôt " +
                "naïve, Louÿs rêva de crapaüter en canoë au delà des îles, près du mälström où brûlent les novæ."
            )
        );
    }

    @Test
    void complicated4() {
        assertEquals(
            "l'\\u238ile exigu\\u235e\n" +
            "  O\\u249u l'ob\\u232ese jury m\\u251ur\n" +
            "  F\\u234ete l'ha\\u239i volap\\u252?k,\n" +
            "  \\u194Ane ex a\\u233equo au whist,\n" +
            "  \\u212Otez ce v\\u339oeu d\\u233e\\u231cu.",
            formatter.format(
                "l'île exiguë\n" +
                "  Où l'obèse jury mûr\n" +
                "  Fête l'haï volapük,\n" +
                "  Âne ex aéquo au whist,\n" +
                "  Ôtez ce vœu déçu."
            )
        );
    }

    @Test
    void complicated5() {
        assertEquals(
            "\\u193Arv\\u237izt\\u369?r\\u337? t\\u252?k\\u246orf\\u250ur\\u243og\\u233ep",
            formatter.format("Árvíztűrő tükörfúrógép")
        );
    }

    @Test
    void complicated6() {
        assertEquals(
            "Pchn\\u261a\\u263c w t\\u281e \\u322l\\u243od\\u378z je\\u380za lub o\\u347sm skrzy\\u324n fig",
            formatter.format("Pchnąć w tę łódź jeża lub ośm skrzyń fig")
        );
    }

    @Test
    void specialCharacters() {
        assertEquals("\\'f3", formatter.format("\\'{o}")); // ó
        assertEquals("\\'f2", formatter.format("\\`{o}")); // ò
        assertEquals("\\'f4", formatter.format("\\^{o}")); // ô
        assertEquals("\\'f6", formatter.format("\\\"{o}")); // ö
        assertEquals("\\u245o", formatter.format("\\~{o}")); // õ
        assertEquals("\\u333o", formatter.format("\\={o}"));
        assertEquals("\\u335o", formatter.format("{\\uo}"));
        assertEquals("\\u231c", formatter.format("{\\cc}")); // ç
        assertEquals("{\\u339oe}", formatter.format("{\\oe}"));
        assertEquals("{\\u338OE}", formatter.format("{\\OE}"));
        assertEquals("{\\u230ae}", formatter.format("{\\ae}")); // æ
        assertEquals("{\\u198AE}", formatter.format("{\\AE}")); // Æ

        assertEquals("", formatter.format("\\.{o}")); // ???
        assertEquals("", formatter.format("\\vo")); // ???
        assertEquals("", formatter.format("\\Ha")); // ã // ???
        assertEquals("", formatter.format("\\too"));
        assertEquals("", formatter.format("\\do")); // ???
        assertEquals("", formatter.format("\\bo")); // ???
        assertEquals("\\u229a", formatter.format("{\\aa}")); // å
        assertEquals("\\u197A", formatter.format("{\\AA}")); // Å
        assertEquals("\\u248o", formatter.format("{\\o}")); // ø
        assertEquals("\\u216O", formatter.format("{\\O}")); // Ø
        assertEquals("\\u322l", formatter.format("{\\l}"));
        assertEquals("\\u321L", formatter.format("{\\L}"));
        assertEquals("\\u223ss", formatter.format("{\\ss}")); // ß
        assertEquals("\\u191?", formatter.format("\\`?")); // ¿
        assertEquals("\\u161!", formatter.format("\\`!")); // ¡

        assertEquals("", formatter.format("\\dag"));
        assertEquals("", formatter.format("\\ddag"));
        assertEquals("\\u167S", formatter.format("{\\S}")); // §
        assertEquals("\\u182P", formatter.format("{\\P}")); // ¶
        assertEquals("\\u169?", formatter.format("{\\copyright}")); // ©
        assertEquals("\\u163?", formatter.format("{\\pounds}")); // £
    }

    @ParameterizedTest(name = "specialChar={0}, formattedStr={1}")
    @CsvSource(
        {
            "ÀÁÂÃÄĀĂĄ, \\u192A\\u193A\\u194A\\u195A\\u196A\\u256A\\u258A\\u260A", // A
            "àáâãäåāăą, \\u224a\\u225a\\u226a\\u227a\\u228a\\u229a\\u257a\\u259a\\u261a", // a
            "ÇĆĈĊČ, \\u199C\\u262C\\u264C\\u266C\\u268C", // C
            "çćĉċč, \\u231c\\u263c\\u265c\\u267c\\u269c", // c
            "ÐĐ, \\u208D\\u272D", // D
            "ðđ, \\u240d\\u273d", // d
            "ÈÉÊËĒĔĖĘĚ, \\u200E\\u201E\\u202E\\u203E\\u274E\\u276E\\u278E\\u280E\\u282E", // E
            "èéêëēĕėęě, \\u232e\\u233e\\u234e\\u235e\\u275e\\u277e\\u279e\\u281e\\u283e", // e
            "ĜĞĠĢŊ, \\u284G\\u286G\\u288G\\u290G\\u330G", // G
            "ĝğġģŋ, \\u285g\\u287g\\u289g\\u291g\\u331g", // g
            "ĤĦ, \\u292H\\u294H", // H
            "ĥħ, \\u293h\\u295h", // h
            "ÌÍÎÏĨĪĬĮİ, \\u204I\\u205I\\u206I\\u207I\\u296I\\u298I\\u300I\\u302I\\u304I", // I
            "ìíîïĩīĭį, \\u236i\\u237i\\u238i\\u239i\\u297i\\u299i\\u301i\\u303i", // i
            "Ĵ, \\u308J", // J
            "ĵ, \\u309j", // j
            "Ķ, \\u310K", // K
            "ķ, \\u311k", // k
            "ĹĻĿ, \\u313L\\u315L\\u319L", // L
            "ĺļŀł, \\u314l\\u316l\\u320l\\u322l", // l
            "ÑŃŅŇ, \\u209N\\u323N\\u325N\\u327N", // N
            "ñńņň, \\u241n\\u324n\\u326n\\u328n", // n
            "ÒÓÔÕÖØŌŎ, \\u210O\\u211O\\u212O\\u213O\\u214O\\u216O\\u332O\\u334O", // O
            "òóôõöøōŏ, \\u242o\\u243o\\u244o\\u245o\\u246o\\u248o\\u333o\\u335o", // o
            "ŔŖŘ, \\u340R\\u342R\\u344R", // R
            "ŕŗř, \\u341r\\u343r\\u345r", // r
            "ŚŜŞŠ, \\u346S\\u348S\\u350S\\u352S", // S
            "śŝşš, \\u347s\\u349s\\u351s\\u353s", // s
            "ŢŤŦ, \\u354T\\u356T\\u358T", // T
            "ţŧ, \\u355t\\u359t", // t
            "ÙÚÛÜŨŪŬŮŲ, \\u217U\\u218U\\u219U\\u220U\\u360U\\u362U\\u364U\\u366U\\u370U", // U
            "ùúûũūŭůų, \\u249u\\u250u\\u251u\\u361u\\u363u\\u365u\\u367u\\u371u", // u
            "Ŵ, \\u372W", // W
            "ŵ, \\u373w", // w
            "ŶŸÝ, \\u374Y\\u376Y\\u221Y", // Y
            "ŷÿ, \\u375y\\u255y", // y
            "ŹŻŽ, \\u377Z\\u379Z\\u381Z", // Z
            "źżž, \\u378z\\u380z\\u382z", // z
            "Æ, \\u198AE", // AE
            "æ, \\u230ae", // ae
            "Œ, \\u338OE", // OE
            "œ, \\u339oe", // oe
            "Þ, \\u222TH", // TH
            "ß, \\u223ss", // ss
            "¡, \\u161!", // !
        }
    )
    void moreSpecialCharacters(String specialChar, String expectedResult) {
        String formattedStr = formatter.format(specialChar);
        assertEquals(expectedResult, formattedStr);
    }

    @Test
    void rtfCharacters() {
        assertEquals("\\'e0", formatter.format("\\`{a}"));
        assertEquals("\\'e8", formatter.format("\\`{e}"));
        assertEquals("\\'ec", formatter.format("\\`{i}"));
        assertEquals("\\'f2", formatter.format("\\`{o}"));
        assertEquals("\\'f9", formatter.format("\\`{u}"));

        assertEquals("\\'e1", formatter.format("\\'a"));
        assertEquals("\\'e9", formatter.format("\\'e"));
        assertEquals("\\'ed", formatter.format("\\'i"));
        assertEquals("\\'f3", formatter.format("\\'o"));
        assertEquals("\\'fa", formatter.format("\\'u"));

        assertEquals("\\'e2", formatter.format("\\^a"));
        assertEquals("\\'ea", formatter.format("\\^e"));
        assertEquals("\\'ee", formatter.format("\\^i"));
        assertEquals("\\'f4", formatter.format("\\^o"));
        assertEquals("\\'fa", formatter.format("\\^u"));

        assertEquals("\\'e4", formatter.format("\\\"a"));
        assertEquals("\\'eb", formatter.format("\\\"e"));
        assertEquals("\\'ef", formatter.format("\\\"i"));
        assertEquals("\\'f6", formatter.format("\\\"o"));
        assertEquals("\\u252u", formatter.format("\\\"u"));

        assertEquals("\\'f1", formatter.format("\\~n"));
    }

    @Test
    void rTFCharactersCapital() {
        assertEquals("\\'c0", formatter.format("\\`A"));
        assertEquals("\\'c8", formatter.format("\\`E"));
        assertEquals("\\'cc", formatter.format("\\`I"));
        assertEquals("\\'d2", formatter.format("\\`O"));
        assertEquals("\\'d9", formatter.format("\\`U"));

        assertEquals("\\'c1", formatter.format("\\'A"));
        assertEquals("\\'c9", formatter.format("\\'E"));
        assertEquals("\\'cd", formatter.format("\\'I"));
        assertEquals("\\'d3", formatter.format("\\'O"));
        assertEquals("\\'da", formatter.format("\\'U"));

        assertEquals("\\'c2", formatter.format("\\^A"));
        assertEquals("\\'ca", formatter.format("\\^E"));
        assertEquals("\\'ce", formatter.format("\\^I"));
        assertEquals("\\'d4", formatter.format("\\^O"));
        assertEquals("\\'db", formatter.format("\\^U"));

        assertEquals("\\'c4", formatter.format("\\\"A"));
        assertEquals("\\'cb", formatter.format("\\\"E"));
        assertEquals("\\'cf", formatter.format("\\\"I"));
        assertEquals("\\'d6", formatter.format("\\\"O"));
        assertEquals("\\'dc", formatter.format("\\\"U"));
    }
}
