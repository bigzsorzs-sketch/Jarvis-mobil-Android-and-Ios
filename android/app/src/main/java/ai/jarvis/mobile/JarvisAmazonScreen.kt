package ai.jarvis.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Amazon search handoff UI: all real prices and offer terms remain on Amazon.
 * Native product cards require authorized Amazon Product Search API access.
 */
@Composable
internal fun JarvisAmazonScreen(
    language: JarvisLanguage,
    onRequest: (AmazonAction) -> String
) {
    fun t(hu: String, en: String, de: String, fr: String, es: String, it: String, pl: String) =
        arrayOf(hu, en, de, fr, es, it, pl)[language.ordinal]
    var query by remember { mutableStateOf("") }
    var business by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf("") }

    Text(t("Amazon termékkeresés", "Amazon product search", "Amazon-Produktsuche",
        "Recherche de produits Amazon", "Buscar productos Amazon",
        "Ricerca prodotti Amazon", "Wyszukiwanie produktów Amazon"),
        style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(t("Alapértelmezett áruház: Amazon UK. Ugyanaz a kereső érhető el hanggal és írásban is.",
        "Default marketplace: Amazon UK. Voice and text use the same search.",
        "Standard-Marktplatz: Amazon UK. Suche per Sprache oder Text.",
        "Marché par défaut : Amazon UK. Recherche vocale ou textuelle.",
        "Mercado predeterminado: Amazon UK. Búsqueda por voz o texto.",
        "Negozio predefinito: Amazon UK. Ricerca vocale o testuale.",
        "Domyślny rynek: Amazon UK. Wyszukiwanie głosem lub tekstem."),
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(160) },
        label = { Text(t("Mit keressünk?", "Product search", "Produktsuche",
            "Recherche de produit", "Buscar producto", "Cerca prodotto", "Szukaj produktu")) },
        singleLine = true, modifier = Modifier.fillMaxWidth().testTag("amazon-query")
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Amazon Business", modifier = Modifier.weight(1f))
        Switch(checked = business, onCheckedChange = { business = it },
            modifier = Modifier.testTag("amazon-business"))
    }
    Button(
        onClick = {
            response = onRequest(AmazonAction.Search(query.trim(), business, AmazonMarketplace.UK))
        },
        enabled = query.trim().length in 2..160,
        modifier = Modifier.fillMaxWidth().testTag("amazon-search")
    ) {
        Text(t("Keresés az Amazonon", "Search on Amazon", "Bei Amazon suchen",
            "Rechercher sur Amazon", "Buscar en Amazon",
            "Cerca su Amazon", "Szukaj na Amazon"))
    }
    OutlinedButton(onClick = {
        response = onRequest(AmazonAction.OpenStore(business, AmazonMarketplace.UK))
    }, modifier = Modifier.fillMaxWidth().testTag("amazon-open")) {
        Text(t("Amazon megnyitása", "Open Amazon", "Amazon öffnen",
            "Ouvrir Amazon", "Abrir Amazon", "Apri Amazon", "Otwórz Amazon"))
    }
    if (response.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(response, modifier = Modifier.testTag("amazon-response"))
    }
    Spacer(Modifier.height(12.dp))
    Text(t(
        "A tényleges termékárat, készletet, szállítást és Business-árazást az Amazon oldalán nézd meg. " +
            "Jarvis nem fér hozzá hivatalos Amazon termékadat-API-hoz, ezért nem talál ki árakat.",
        "Check live product prices, stock, delivery and Business pricing on Amazon. " +
            "An authorized Amazon product API is not connected; Jarvis never fabricates prices.",
        "Preise, Bestand und Lieferung bei Amazon prüfen. Keine erfundenen Preise.",
        "Vérifiez prix, disponibilité et livraison sur Amazon. Aucun prix inventé.",
        "Comprueba precios, stock y envíos en Amazon. Sin precios inventados.",
        "Verifica prezzi e disponibilità su Amazon. Nessun prezzo inventato.",
        "Sprawdź ceny i dostępność w Amazon. Bez wymyślonych cen."
    ), style = MaterialTheme.typography.bodySmall)
}
