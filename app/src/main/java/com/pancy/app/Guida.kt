package com.pancy.app

/**
 * Contenuti fissi della sezione Consigli, presi da fonti ufficiali (elencate in [Guida.fonti]).
 * Il calendario degli esami segue i LEA (DPCM 12 gennaio 2017, allegato 10B): sono gli esami
 * gratuiti per una gravidanza senza complicazioni. Aggiornare qui se cambiano le regole.
 */

/** Un esame o controllo previsto in un periodo, dalla settimana [da] alla settimana [a] (comprese). */
data class Esame(
    val chiave: String,
    val titolo: String,
    val da: Int,
    val a: Int,
    val quando: String,
    val testo: String,
    val tipo: Tipo,
    val soloSe: String? = null,
)

data class Fonte(val titolo: String, val url: String)

object Guida {
    val esami = listOf(
        Esame(
            "prima-visita", "Prima visita ostetrica", 5, 13, "Entro la 13ª settimana",
            "Il primo controllo con ginecologo o ostetrica: storia clinica, pressione, peso, e la prescrizione " +
                "degli esami gratuiti della gravidanza (con l'esenzione dal ticket).",
            Tipo.VISITA,
        ),
        Esame(
            "esami-1", "Esami del primo trimestre", 5, 13, "Entro la 13ª settimana",
            "Emocromo, gruppo sanguigno e fattore Rh, test di Coombs indiretto, glicemia, toxoplasmosi, rosolia, " +
                "HIV, sifilide, esame delle urine e urinocoltura. Epatite C, clamidia e gonorrea solo se ci sono fattori di rischio.",
            Tipo.ESAMI,
        ),
        Esame(
            "eco-1", "Ecografia del primo trimestre", 7, 13, "Entro la 13ª settimana",
            "Conferma la gravidanza e ne stabilisce l'epoca, da cui si calcola la data presunta del parto.",
            Tipo.ECOGRAFIA,
        ),
        Esame(
            "combinato", "Test combinato", 11, 13, "Tra 11+0 e 13+6 settimane",
            "Ecografia della translucenza nucale più un prelievo di sangue: stima la probabilità delle anomalie " +
                "cromosomiche più frequenti, come la sindrome di Down. È offerto a tutte, a qualsiasi età. " +
                "Alcune Regioni offrono anche il test del DNA fetale.",
            Tipo.ESAMI,
        ),
        Esame(
            "esami-2", "Esami tra la 14ª e la 18ª settimana", 14, 18, "Tra la 14ª e la 18ª settimana",
            "Toxoplasmosi e rosolia, se non immune. Per chi ha un rischio alto di diabete (diabete gestazionale in una " +
                "gravidanza precedente, IMC di partenza 30 o più, glicemia alta a inizio gravidanza) qui si fa già la curva glicemica.",
            Tipo.ESAMI,
        ),
        Esame(
            "morfologica", "Ecografia morfologica", 19, 21, "Tra 19+0 e 21+0 settimane",
            "Controlla lo sviluppo degli organi del bambino, la placenta e il liquido amniotico.",
            Tipo.ECOGRAFIA,
        ),
        Esame(
            "curva", "Curva glicemica (OGTT 75 g)", 24, 28, "Tra la 24ª e la 28ª settimana",
            "Cerca il diabete gestazionale. Si fa solo con almeno un fattore di rischio: 35 anni o più, IMC di partenza " +
                "25 o più, un figlio nato di 4,5 kg o più, diabete gestazionale in passato, genitori o fratelli con diabete " +
                "di tipo 2, origine da Paesi dove il diabete è molto diffuso.",
            Tipo.ESAMI, soloSe = "Solo con fattori di rischio",
        ),
        Esame(
            "toxo-24", "Toxoplasmosi", 24, 27, "Tra la 24ª e la 27ª settimana",
            "Solo se non immune: il test si ripete ogni 4-6 settimane fino al parto.",
            Tipo.ESAMI, soloSe = "Solo se non immune",
        ),
        Esame(
            "corso", "Corso di accompagnamento alla nascita", 24, 32, "Di solito dal 7° mese",
            "È gratuito: informatevi per tempo al consultorio o al punto nascita dove pensate di partorire.",
            Tipo.CORSO,
        ),
        Esame(
            "pertosse", "Vaccino contro la pertosse (dTpa)", 27, 36, "Tra la 27ª e la 36ª settimana, meglio verso la 28ª",
            "Raccomandato in ogni gravidanza: gli anticorpi passano al bambino e lo proteggono nei primi mesi, " +
                "prima che possa essere vaccinato.",
            Tipo.ALTRO,
        ),
        Esame(
            "esami-28", "Esami tra la 28ª e la 32ª settimana", 28, 32, "Tra la 28ª e la 32ª settimana",
            "Emocromo, test di Coombs indiretto e toxoplasmosi (se non immune). Se il gruppo sanguigno è Rh negativo, " +
                "verso la 28ª settimana si fa anche la profilassi anti-D.",
            Tipo.ESAMI,
        ),
        Esame(
            "esami-33", "Esami tra la 33ª e la 37ª settimana", 33, 37, "Tra la 33ª e la 37ª settimana",
            "Emocromo, epatite B, sifilide, HIV (se non fatto negli ultimi 3 mesi) e toxoplasmosi (se non immune).",
            Tipo.ESAMI,
        ),
        Esame(
            "tampone", "Tampone vaginale e rettale", 36, 37, "Tra la 36ª e la 37ª settimana",
            "Cerca lo streptococco di gruppo B. Se è positivo, durante il travaglio si fa un antibiotico che protegge il bambino.",
            Tipo.ESAMI,
        ),
        Esame(
            "oltre-41", "Controlli dalla 41ª settimana", 41, 42, "Dalla 41ª settimana, se il parto non è ancora avvenuto",
            "Ecografia per valutare il liquido amniotico e tracciato cardiotocografico (CTG).",
            Tipo.ALTRO,
        ),
    )

    const val NOTA_ESAMI =
        "L'elenco segue i LEA, cioè gli esami gratuiti per una gravidanza senza complicazioni (DPCM 12 gennaio 2017). " +
            "Le linee guida ISS aggiornate dal 2023 hanno cambiato alcune indicazioni: per esempio propongono a tutte il test " +
            "per il citomegalovirus e non più quello per la rosolia. Quali esami fare lo decidono il medico o l'ostetrica."

    /** Coppie (titolo in grassetto, testo). */
    val vaccini = listOf(
        "Pertosse (dTpa):" to "in ogni gravidanza, tra la 27ª e la 36ª settimana, meglio verso la 28ª.",
        "Influenza:" to "raccomandata durante la stagione influenzale, in qualsiasi momento della gravidanza.",
        "COVID-19:" to "le circolari del Ministero raccomandano una dose di vaccino aggiornato, in qualsiasi trimestre.",
        "Virus respiratorio sinciziale (RSV):" to "per proteggere il neonato si può fare il vaccino in gravidanza " +
            "(tra la 24ª e la 36ª settimana) dove è offerto, oppure dare al bambino un anticorpo dopo la nascita. " +
            "Chiedete al consultorio cosa offre la vostra Regione.",
        "Morbillo, parotite, rosolia, varicella:" to "sono vaccini vivi e in gravidanza non si fanno.",
    )

    val stileDiVita = listOf(
        "Acido folico:" to "0,4 mg al giorno fino alla fine del primo trimestre.",
        "Alcol:" to "niente, per tutta la gravidanza. Non esiste una dose sicura.",
        "Fumo:" to "niente, ed è meglio evitare anche il fumo passivo.",
        "Toxoplasmosi e listeria:" to "evitare carne, pesce e uova crudi o poco cotti, latte non pastorizzato e formaggi " +
            "molli; lavare bene frutta e verdura. Se non è immune alla toxoplasmosi, evitare anche i salumi crudi.",
        "Pesce:" to "3-4 volte a settimana, meglio pesci piccoli come sgombro, sarde e alici. Tonno e pesce spada " +
            "al massimo 1-2 volte a settimana.",
        "Caffè, tè, cola, energy drink:" to "ridurli: in gravidanza la dose sicura di caffeina è circa la metà di quella abituale.",
        "Sale:" to "iodato, ma con moderazione.",
        "Integratori:" to "solo se servono e su indicazione del medico. Evitare quelli con vitamina A.",
        "Peso:" to "le linee guida ISS consigliano di calcolare l'IMC alla prima visita; se il peso di partenza è " +
            "nella norma non serve pesarsi a ogni controllo.",
    )

    val urgenze = listOf(
        "Perdite di sangue dai genitali.",
        "Perdita di liquido (rottura delle acque).",
        "Contrazioni regolari e dolorose prima della 37ª settimana.",
        "Movimenti del bambino diminuiti o assenti: dal terzo trimestre sono un segno importante del suo benessere.",
        "Mal di testa forte, disturbi della vista, gonfiore improvviso di viso e mani.",
        "Febbre alta, oppure dolore alla pancia forte e che non passa.",
    )

    val fonti = listOf(
        Fonte("ISS – Gli esami gratuiti in gravidanza (LEA 2017)", "https://www.epicentro.iss.it/itoss/pdf/PROFESSIONISTI_WEB.pdf"),
        Fonte(
            "ISS – Linea guida Gravidanza fisiologica (2025)",
            "https://www.epicentro.iss.it/materno/pdf/SNLG%201_2025%20Gravidanza-fisiologica%20Parte-2.pdf",
        ),
        Fonte("ISS – Vaccinazioni in gravidanza", "https://www.epicentro.iss.it/vaccini/donne-gravidanza"),
        Fonte(
            "ISS – Decalogo per l'alimentazione in gravidanza",
            "https://www.iss.it/documents/20126/1936883/Il_Decalogo_per_la_gravidanza_slides.pdf/c8e4969d-9a74-8a2d-a97b-e095ab07a1b4",
        ),
    )
}
