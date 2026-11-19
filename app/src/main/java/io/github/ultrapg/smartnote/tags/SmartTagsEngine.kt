package io.github.ultrapg.smartnote.tags

import android.view.textclassifier.TextClassifier
import io.github.ultrapg.smartnote.ai.ClassifiedTextEntity
import io.github.ultrapg.smartnote.ai.DetectedImageLabel
import io.github.ultrapg.smartnote.domain.model.NoteBlock
import io.github.ultrapg.smartnote.domain.model.NoteBlockType
import io.github.ultrapg.smartnote.domain.model.NoteSummary
import io.github.ultrapg.smartnote.domain.model.TagSource
import io.github.ultrapg.smartnote.domain.util.TextHashing
import io.github.ultrapg.smartnote.links.DetectedLink
import java.util.Locale

data class SmartTagSuggestion(
    val name: String,
    val confidence: Float,
    val reason: String
)

class SmartTagsEngine {

    private data class CategorySpec(
        val tagName: String,
        val prototypeText: String,
        val weightedTerms: Map<String, Float>,
        val compoundRoots: Set<String> = emptySet(),
        val phrases: Map<String, Float> = emptyMap()
    )

    private val domainRules: Map<String, List<String>> = mapOf(
        "github.com" to listOf("Development", "Code"),
        "gitlab.com" to listOf("Development"),
        "stackoverflow.com" to listOf("Programming", "Error"),
        "developer.android.com" to listOf("Android", "Development"),
        "kotlinlang.org" to listOf("Kotlin", "Development"),
        "arxiv.org" to listOf("Research", "Reading"),
        "scholar.google.com" to listOf("Research"),
        "figma.com" to listOf("Design"),
        "docs.google.com" to listOf("Work"),
        "youtube.com" to listOf("Watchlist", "Bookmark"),
        "youtu.be" to listOf("Watchlist", "Bookmark"),
        "wikipedia.org" to listOf("Reading"),
        "amazon.com" to listOf("Shopping"),
        "amazon.de" to listOf("Shopping"),
        "ebay.com" to listOf("Shopping"),
        "ebay.de" to listOf("Shopping"),
        "rewe.de" to listOf("Shopping"),
        "dm.de" to listOf("Shopping"),
        "idealo.de" to listOf("Shopping"),
        "geizhals.de" to listOf("Shopping"),
        "kleinanzeigen.de" to listOf("Shopping"),
        "ikea.com" to listOf("Home", "Shopping"),
        "chefkoch.de" to listOf("Recipe"),
        "allrecipes.com" to listOf("Recipe"),
        "seriouseats.com" to listOf("Recipe"),
        "booking.com" to listOf("Travel"),
        "airbnb.com" to listOf("Travel"),
        "bahn.de" to listOf("Travel"),
        "expedia.com" to listOf("Travel"),
        "skyscanner.com" to listOf("Travel"),
        "lufthansa.com" to listOf("Travel"),
        "imdb.com" to listOf("Watchlist"),
        "letterboxd.com" to listOf("Watchlist"),
        "themoviedb.org" to listOf("Watchlist"),
        "netflix.com" to listOf("Watchlist"),
        "spotify.com" to listOf("Music"),
        "music.apple.com" to listOf("Music"),
        "soundcloud.com" to listOf("Music"),
        "goodreads.com" to listOf("Reading"),
        "medium.com" to listOf("Reading"),
        "substack.com" to listOf("Reading"),
        "reddit.com" to listOf("Reading"),
        "heise.de" to listOf("Tech"),
        "theverge.com" to listOf("Tech"),
        "notebookcheck.com" to listOf("Tech"),
        "komoot.com" to listOf("Fitness"),
        "strava.com" to listOf("Fitness"),
        "steampowered.com" to listOf("Gaming"),
        "twitch.tv" to listOf("Gaming"),
        "linkedin.com" to listOf("Work"),
        "notion.so" to listOf("Work"),
        "atlassian.net" to listOf("Work")
    )

    private val taxonomy: List<CategorySpec> = listOf(
        CategorySpec(
            tagName = "Android",
            prototypeText = "android jetpack compose viewmodel room datastore workmanager material3 apk gradle",
            weightedTerms = mapOf(
                "android" to 2.6f, "jetpack" to 2.2f, "compose" to 2.0f, "activity" to 1.3f,
                "viewmodel" to 2.0f, "workmanager" to 2.1f, "gradle" to 1.9f, "adb" to 2.1f,
                "apk" to 2.1f, "manifest" to 1.6f, "material3" to 2.1f, "room" to 1.5f,
                "datastore" to 1.9f, "ndk" to 1.9f, "recyclerview" to 1.9f, "lifecycle" to 1.6f,
                "fragment" to 1.8f, "intent" to 1.5f, "proguard" to 2.0f, "r8" to 1.8f,
                "litert" to 1.9f
            ),
            compoundRoots = setOf("android", "compose", "viewmodel", "workmanager")
        ),
        CategorySpec(
            tagName = "Kotlin",
            prototypeText = "kotlin coroutines stateflow suspend sealed dataclass ksp kotlinx flow lambda",
            weightedTerms = mapOf(
                "kotlin" to 2.6f, "coroutine" to 2.1f, "coroutines" to 2.1f, "stateflow" to 2.2f,
                "suspend" to 1.9f, "sealed" to 1.6f, "dataclass" to 1.7f, "ksp" to 2.1f,
                "kotlinx" to 2.2f, "flow" to 1.3f, "lambda" to 1.5f, "sharedflow" to 2.1f
            ),
            compoundRoots = setOf("kotlin", "coroutine", "stateflow")
        ),
        CategorySpec(
            tagName = "Development",
            prototypeText = "software development api sdk git commit branch compiler refactor database sqlite fts5 docker",
            weightedTerms = mapOf(
                "development" to 2.5f, "developer" to 2.1f, "entwicklung" to 2.4f,
                "api" to 1.6f, "sdk" to 1.8f, "git" to 1.9f, "commit" to 1.6f, "branch" to 1.2f,
                "compiler" to 1.9f, "refactor" to 1.9f, "architecture" to 1.6f, "backend" to 1.8f,
                "frontend" to 1.8f, "database" to 1.8f, "datenbank" to 1.8f, "sqlite" to 2.0f,
                "fts5" to 2.1f, "json" to 1.6f, "yaml" to 1.6f, "docker" to 2.0f,
                "kubernetes" to 2.0f, "endpoint" to 1.7f, "repository" to 1.5f, "release" to 1.2f
            ),
            compoundRoots = setOf("entwicklung", "datenbank", "architecture")
        ),
        CategorySpec(
            tagName = "Programming",
            prototypeText = "programming coding python rust typescript javascript java cpp algorithm debug function class",
            weightedTerms = mapOf(
                "programming" to 2.5f, "programmieren" to 2.5f, "coding" to 2.2f,
                "function" to 1.4f, "class" to 1.2f, "interface" to 1.4f, "algorithm" to 1.9f,
                "python" to 2.2f, "rust" to 2.0f, "typescript" to 2.2f, "javascript" to 2.2f,
                "java" to 1.8f, "cpp" to 2.0f, "cargo" to 1.8f, "pytest" to 2.1f,
                "syntax" to 1.6f, "variable" to 1.4f, "recursion" to 1.9f, "debug" to 1.7f,
                "quellcode" to 2.1f
            )
        ),
        CategorySpec(
            tagName = "Error",
            prototypeText = "error crash exception stacktrace fatal bug traceback timeout segfault fehler",
            weightedTerms = mapOf(
                "error" to 2.4f, "fehler" to 2.3f, "exception" to 2.2f, "crash" to 2.1f,
                "absturz" to 2.2f, "stacktrace" to 2.4f, "fatal" to 2.0f,
                "nullpointerexception" to 2.5f, "illegalstateexception" to 2.5f, "failed" to 1.4f,
                "failure" to 1.7f, "timeout" to 1.8f, "outofmemoryerror" to 2.5f, "bug" to 1.9f,
                "segfault" to 2.4f, "traceback" to 2.3f, "errno" to 2.1f, "404" to 1.6f, "500" to 1.5f
            ),
            compoundRoots = setOf("exception", "fehler", "absturz", "stacktrace")
        ),
        CategorySpec(
            tagName = "Work",
            prototypeText = "work office sprint standup deadline deliverable roadmap stakeholder milestone jira project arbeit projekt",
            weightedTerms = mapOf(
                "work" to 2.0f, "arbeit" to 2.2f, "buero" to 2.0f, "sprint" to 1.9f,
                "standup" to 2.0f, "deadline" to 1.9f, "deliverable" to 1.9f, "roadmap" to 1.9f,
                "stakeholder" to 1.9f, "milestone" to 1.8f, "meilenstein" to 1.9f, "quarterly" to 1.7f,
                "jira" to 2.1f, "ticket" to 1.4f, "project" to 1.4f, "projekt" to 1.8f,
                "client" to 1.5f, "kunde" to 1.6f, "colleague" to 1.8f, "kollege" to 1.8f,
                "bewerbung" to 2.2f, "interview" to 2.0f, "lebenslauf" to 2.3f, "onboarding" to 2.0f,
                "quartal" to 1.8f, "gehaltserhoehung" to 2.2f
            ),
            compoundRoots = setOf("arbeit", "projekt", "meilenstein", "bewerbung")
        ),
        CategorySpec(
            tagName = "Meeting",
            prototypeText = "meeting agenda attendees minutes action items followup sync besprechung protokoll termin",
            weightedTerms = mapOf(
                "meeting" to 2.5f, "besprechung" to 2.5f, "protokoll" to 2.2f,
                "agenda" to 2.1f, "attendees" to 2.1f, "teilnehmer" to 2.0f, "minutes" to 1.5f,
                "actionitems" to 2.1f, "followup" to 1.8f, "sync" to 1.4f, "notes" to 0.8f,
                "discussion" to 1.5f, "diskussion" to 1.7f, "zoom" to 1.7f, "call" to 1.2f,
                "teams" to 1.3f, "standup" to 1.8f
            ),
            compoundRoots = setOf("meeting", "besprechung", "protokoll"),
            phrases = mapOf("meeting notes" to 2.8f, "action items" to 2.4f, "one on one" to 2.0f)
        ),
        CategorySpec(
            tagName = "Shopping",
            prototypeText = "shopping groceries supermarket buy milk eggs bread butter cheese einkauf einkaufsliste",
            weightedTerms = mapOf(
                "shopping" to 2.6f, "einkauf" to 2.5f, "einkaufen" to 2.5f, "einkaufsliste" to 2.8f,
                "groceries" to 2.5f, "grocery" to 2.5f, "supermarket" to 2.2f, "supermarkt" to 2.2f,
                "milk" to 1.9f, "milch" to 1.9f, "eggs" to 1.9f, "eier" to 1.9f,
                "bread" to 1.9f, "brot" to 1.9f, "coffee" to 1.5f, "kaffee" to 1.7f,
                "buy" to 1.4f, "kaufen" to 1.8f, "apples" to 1.8f, "aepfel" to 1.8f,
                "bananas" to 1.8f, "bananen" to 1.8f, "cheese" to 1.8f, "kaese" to 1.8f,
                "butter" to 1.8f, "rice" to 1.7f, "reis" to 1.7f, "pasta" to 1.6f,
                "detergent" to 2.0f, "waschmittel" to 2.0f, "toothpaste" to 2.0f, "zahnpasta" to 2.0f,
                "wishlist" to 2.0f, "wunschliste" to 2.1f, "bestellen" to 1.7f, "bestellung" to 2.1f,
                "rewe" to 2.2f, "edeka" to 2.2f, "aldi" to 2.2f, "lidl" to 2.2f, "rossmann" to 2.2f,
                "jacke" to 1.8f, "jacket" to 1.8f, "schuhe" to 1.9f, "shoes" to 1.9f
            ),
            compoundRoots = setOf("einkauf", "supermarkt", "wunschliste", "grocery", "bestellung"),
            phrases = mapOf("shopping list" to 3.2f, "grocery list" to 3.2f, "buy later" to 1.8f)
        ),
        CategorySpec(
            tagName = "Finance",
            prototypeText = "finance budget invoice tax bank salary expense payment transfer steuer konto ueberweisung",
            weightedTerms = mapOf(
                "finance" to 2.5f, "finanzen" to 2.5f, "invoice" to 2.2f, "rechnung" to 2.1f,
                "tax" to 2.0f, "steuer" to 2.2f, "steuern" to 2.2f, "budget" to 2.2f,
                "expense" to 2.0f, "ausgaben" to 2.0f, "payment" to 1.8f, "zahlung" to 1.9f,
                "bank" to 1.8f, "iban" to 2.3f, "konto" to 1.9f, "salary" to 2.1f,
                "gehalt" to 2.1f, "subtotal" to 1.9f, "total" to 1.1f, "usd" to 1.8f,
                "eur" to 1.8f, "euro" to 1.8f, "refund" to 2.0f, "ueberweisung" to 2.2f,
                "investment" to 2.1f, "etf" to 2.1f, "abo" to 2.0f, "subscription" to 2.1f,
                "versicherung" to 2.2f, "insurance" to 2.2f, "depot" to 2.0f, "aktien" to 2.1f,
                "nebenkosten" to 2.2f, "stromkosten" to 2.2f
            ),
            compoundRoots = setOf("steuer", "finanz", "ueberweisung", "rechnung", "konto", "versicherung"),
            phrases = mapOf("bank transfer" to 2.8f, "tax return" to 3.0f)
        ),
        CategorySpec(
            tagName = "Receipt",
            prototypeText = "receipt subtotal vat cashier merchant paid visa mastercard quittung kassenbon beleg mwst",
            weightedTerms = mapOf(
                "receipt" to 2.6f, "quittung" to 2.6f, "kassenbon" to 2.6f, "kassenzettel" to 2.6f,
                "beleg" to 2.0f, "subtotal" to 2.2f, "zwischensumme" to 2.3f,
                "vat" to 2.1f, "mwst" to 2.3f, "mehrwertsteuer" to 2.4f,
                "cashier" to 2.2f, "kassierer" to 2.1f, "merchant" to 2.1f,
                "order" to 1.2f, "paid" to 1.5f, "bezahlt" to 1.7f,
                "visa" to 1.7f, "mastercard" to 1.9f, "change" to 1.0f, "qty" to 1.8f
            ),
            compoundRoots = setOf("quittung", "kassenbon", "kassenzettel", "mehrwertsteuer", "zwischensumme")
        ),
        CategorySpec(
            tagName = "Travel",
            prototypeText = "travel flight boarding hotel itinerary passport luggage airport train booking reise urlaub flug",
            weightedTerms = mapOf(
                "travel" to 2.6f, "reise" to 2.5f, "urlaub" to 2.5f, "vacation" to 2.5f,
                "flight" to 2.3f, "flug" to 2.3f, "boarding" to 2.2f, "hotel" to 2.2f,
                "itinerary" to 2.3f, "passport" to 2.2f, "reisepass" to 2.3f,
                "luggage" to 2.2f, "gepaeck" to 2.2f, "koffer" to 2.1f,
                "airport" to 2.2f, "flughafen" to 2.2f, "terminal" to 1.5f,
                "train" to 1.7f, "bahn" to 1.9f, "reservation" to 1.8f, "reservierung" to 1.9f,
                "checkin" to 2.0f, "booking" to 1.9f, "buchung" to 2.0f, "packliste" to 2.4f,
                "ausflug" to 2.1f, "trip" to 1.9f, "mietwagen" to 2.3f, "ferienwohnung" to 2.4f,
                "visum" to 2.2f, "visa" to 1.7f
            ),
            compoundRoots = setOf("flug", "hotel", "urlaub", "reise", "koffer", "buchung", "airport"),
            phrases = mapOf("boarding pass" to 3.0f, "packing list" to 2.8f, "hotel booking" to 2.8f)
        ),
        CategorySpec(
            tagName = "Health",
            prototypeText = "health doctor dentist appointment pharmacy prescription vitamins symptom sleep medizin arzt zahnarzt",
            weightedTerms = mapOf(
                "health" to 2.5f, "gesundheit" to 2.5f, "doctor" to 2.3f, "arzt" to 2.4f,
                "aerztin" to 2.4f, "dentist" to 2.4f, "zahnarzt" to 2.5f,
                "appointment" to 1.8f, "arzttermin" to 2.7f, "prescription" to 2.3f,
                "medikament" to 2.3f, "medikamente" to 2.3f, "medication" to 2.3f,
                "vitamins" to 2.1f, "vitamine" to 2.1f, "symptom" to 2.2f, "symptome" to 2.2f,
                "pharmacy" to 2.3f, "apotheke" to 2.4f, "sleep" to 1.5f, "schlaf" to 1.6f,
                "hydration" to 1.9f, "therapie" to 2.1f, "impfung" to 2.2f, "krankenhaus" to 2.3f,
                "vorsorge" to 2.2f, "checkup" to 2.2f, "blutbild" to 2.3f, "allergie" to 2.2f,
                "krankenkasse" to 2.3f, "physiotherapie" to 2.4f, "optiker" to 2.2f
            ),
            compoundRoots = setOf("arzt", "zahnarzt", "apotheke", "medikament", "gesundheit", "krankenhaus", "therapie"),
            phrases = mapOf("doctor appointment" to 3.2f, "dental appointment" to 3.0f, "blood pressure" to 2.6f)
        ),
        CategorySpec(
            tagName = "Recipe",
            prototypeText = "recipe ingredients preheat bake simmer tablespoon teaspoon oven chop garlic flour rezept kochen backen zutaten",
            weightedTerms = mapOf(
                "recipe" to 2.6f, "recipes" to 2.6f, "rezept" to 2.6f, "rezepte" to 2.6f,
                "kochen" to 2.2f, "cooking" to 2.1f, "zutaten" to 2.4f, "ingredients" to 2.4f,
                "preheat" to 2.4f, "vorheizen" to 2.4f, "bake" to 2.0f, "backen" to 2.1f,
                "simmer" to 2.3f, "koecheln" to 2.3f, "tablespoon" to 2.3f, "essloeffel" to 2.3f,
                "teaspoon" to 2.3f, "teeloeffel" to 2.3f, "tbsp" to 2.2f, "tsp" to 2.2f,
                "oven" to 1.9f, "backofen" to 2.1f, "chop" to 1.8f, "garlic" to 1.9f,
                "knoblauch" to 1.9f, "olive" to 1.5f, "flour" to 1.9f, "mehl" to 1.9f,
                "sauce" to 1.8f, "pasta" to 1.7f, "abendessen" to 1.8f, "dinner" to 1.7f,
                "mittagessen" to 1.8f, "lunch" to 1.6f, "fruehstueck" to 1.8f, "breakfast" to 1.7f,
                "suppe" to 1.9f, "soup" to 1.9f, "pfanne" to 2.0f, "dessert" to 1.9f
            ),
            compoundRoots = setOf("rezept", "zutaten", "backofen", "knoblauch")
        ),
        CategorySpec(
            tagName = "Study",
            prototypeText = "study lecture exam homework chapter syllabus theorem quiz flashcards summary studium klausur lernen",
            weightedTerms = mapOf(
                "study" to 2.5f, "studium" to 2.5f, "uni" to 1.9f, "universitaet" to 2.1f,
                "lecture" to 2.2f, "vorlesung" to 2.3f, "exam" to 2.3f, "klausur" to 2.4f,
                "pruefung" to 2.4f, "homework" to 2.2f, "hausaufgabe" to 2.3f, "hausaufgaben" to 2.3f,
                "chapter" to 1.8f, "kapitel" to 1.8f, "syllabus" to 2.3f, "theorem" to 2.2f,
                "quiz" to 2.0f, "flashcards" to 2.2f, "karteikarten" to 2.3f,
                "summary" to 1.4f, "zusammenfassung" to 2.0f, "lernen" to 2.1f, "course" to 1.6f,
                "hausarbeit" to 2.4f, "essay" to 2.1f, "referat" to 2.3f, "seminar" to 2.1f,
                "semester" to 2.1f, "dozent" to 2.2f, "bachelor" to 2.0f
            ),
            compoundRoots = setOf("vorlesung", "klausur", "pruefung", "hausaufgabe", "karteikarten", "hausarbeit")
        ),
        CategorySpec(
            tagName = "Ideas",
            prototypeText = "ideas brainstorm concept prototype hypothesis inspiration sketch pitch idee ideen konzept",
            weightedTerms = mapOf(
                "ideas" to 2.5f, "idea" to 2.4f, "idee" to 2.4f, "ideen" to 2.5f,
                "brainstorm" to 2.4f, "brainstorming" to 2.4f, "concept" to 1.8f, "konzept" to 1.9f,
                "prototype" to 1.8f, "prototyp" to 1.8f, "hypothesis" to 1.9f,
                "inspiration" to 1.9f, "sketch" to 1.7f, "pitch" to 1.8f, "vision" to 1.6f
            )
        ),
        CategorySpec(
            tagName = "Design",
            prototypeText = "design typography palette figma wireframe layout spacing elevation contrast animation ux ui farben",
            weightedTerms = mapOf(
                "design" to 2.5f, "typography" to 2.2f, "typografie" to 2.2f,
                "palette" to 2.1f, "figma" to 2.3f, "wireframe" to 2.3f, "mockup" to 2.1f,
                "layout" to 1.7f, "spacing" to 1.7f, "elevation" to 1.8f, "contrast" to 1.7f,
                "animation" to 1.6f, "ux" to 2.0f, "ui" to 1.7f, "farben" to 1.7f
            ),
            compoundRoots = setOf("wireframe", "typograf", "gestaltung")
        ),
        CategorySpec(
            tagName = "Tasks",
            prototypeText = "tasks todo checklist finish submit prepare schedule urgent reminder aufgabe erledigen frist",
            weightedTerms = mapOf(
                "tasks" to 2.5f, "task" to 2.4f, "todo" to 2.4f, "aufgabe" to 2.3f,
                "aufgaben" to 2.4f, "erledigen" to 2.1f, "reminder" to 1.5f, "finish" to 1.3f,
                "submit" to 1.6f, "abgeben" to 1.7f, "review" to 1.3f, "prepare" to 1.5f,
                "schedule" to 1.4f, "urgent" to 1.8f, "dringend" to 1.9f, "frist" to 1.8f
            ),
            phrases = mapOf("to do" to 2.4f, "to-do" to 2.4f)
        ),
        CategorySpec(
            tagName = "Research",
            prototypeText = "research paper arxiv benchmark evaluation dataset citation abstract methodology forschung studie",
            weightedTerms = mapOf(
                "research" to 2.5f, "forschung" to 2.5f, "studie" to 2.1f,
                "paper" to 1.6f, "arxiv" to 2.3f, "benchmark" to 2.0f, "evaluation" to 1.8f,
                "dataset" to 2.0f, "datensatz" to 2.1f, "citation" to 2.1f,
                "abstract" to 1.8f, "methodology" to 2.0f, "quelle" to 1.6f
            ),
            compoundRoots = setOf("forschung", "benchmark", "datensatz")
        ),
        CategorySpec(
            tagName = "Fitness",
            prototypeText = "fitness workout gym cardio running jogging strength training calories protein yoga sport",
            weightedTerms = mapOf(
                "fitness" to 2.6f, "workout" to 2.5f, "gym" to 2.4f, "fitnessstudio" to 2.6f,
                "cardio" to 2.3f, "running" to 2.0f, "joggen" to 2.2f, "laufen" to 1.8f,
                "training" to 2.1f, "krafttraining" to 2.5f, "calories" to 2.0f, "kalorien" to 2.0f,
                "protein" to 1.9f, "yoga" to 2.2f, "stretching" to 2.1f, "marathon" to 2.3f,
                "sport" to 2.0f, "steps" to 1.4f, "bouldering" to 2.4f, "bouldern" to 2.4f,
                "climbing" to 2.2f, "klettern" to 2.2f, "cycling" to 2.2f, "radfahren" to 2.2f,
                "rennrad" to 2.3f, "swimming" to 2.1f, "schwimmen" to 2.1f, "wandern" to 2.0f,
                "hiking" to 2.0f
            ),
            compoundRoots = setOf("fitness", "workout", "training", "boulder")
        ),
        CategorySpec(
            tagName = "Reading",
            prototypeText = "reading book books novel author chapter article library kindle goodreads lesen buch buecher",
            weightedTerms = mapOf(
                "reading" to 2.5f, "lesen" to 2.3f, "book" to 2.0f, "books" to 2.2f,
                "buch" to 2.1f, "buecher" to 2.3f, "novel" to 2.1f, "roman" to 2.1f,
                "author" to 1.9f, "autor" to 1.9f, "article" to 1.9f, "artikel" to 1.9f,
                "library" to 2.0f, "bibliothek" to 2.1f, "kindle" to 2.3f, "ebook" to 2.2f,
                "goodreads" to 2.4f, "audiobook" to 2.2f, "hoerbuch" to 2.3f, "bookmark" to 1.6f
            ),
            phrases = mapOf("read later" to 2.6f, "to read" to 2.6f, "book club" to 2.6f)
        ),
        CategorySpec(
            tagName = "Watchlist",
            prototypeText = "watchlist movie movies series episode season cinema netflix imdb trailer film serie kino",
            weightedTerms = mapOf(
                "watchlist" to 2.7f, "movie" to 2.3f, "movies" to 2.4f, "film" to 2.3f,
                "filme" to 2.4f, "series" to 2.0f, "serie" to 2.2f, "serien" to 2.3f,
                "episode" to 2.1f, "staffel" to 2.2f, "season" to 1.8f, "cinema" to 2.2f,
                "kino" to 2.3f, "netflix" to 2.3f, "disney" to 1.9f, "trailer" to 2.0f,
                "imdb" to 2.3f, "documentary" to 2.1f, "doku" to 2.1f, "anime" to 2.2f
            ),
            phrases = mapOf("watch later" to 2.8f, "to watch" to 2.6f)
        ),
        CategorySpec(
            tagName = "Music",
            prototypeText = "music song songs album playlist concert lyrics guitar piano spotify band musik konzert lied",
            weightedTerms = mapOf(
                "music" to 2.6f, "musik" to 2.6f, "song" to 2.2f, "songs" to 2.3f,
                "album" to 2.1f, "playlist" to 2.3f, "concert" to 2.3f, "konzert" to 2.4f,
                "lyrics" to 2.3f, "lied" to 2.1f, "lieder" to 2.2f, "spotify" to 2.3f,
                "guitar" to 2.2f, "gitarre" to 2.2f, "piano" to 2.2f, "klavier" to 2.2f,
                "chords" to 2.2f, "akkorde" to 2.2f, "vinyl" to 2.0f
            )
        ),
        CategorySpec(
            tagName = "Gaming",
            prototypeText = "gaming game games steam nintendo playstation xbox quest boss fps rpg spiel zocken",
            weightedTerms = mapOf(
                "gaming" to 2.6f, "game" to 2.1f, "games" to 2.3f, "spiel" to 2.1f,
                "spiele" to 2.3f, "zocken" to 2.4f, "steam" to 2.2f, "nintendo" to 2.4f,
                "playstation" to 2.4f, "xbox" to 2.4f, "switch" to 1.4f, "quest" to 2.0f,
                "rpg" to 2.2f, "multiplayer" to 2.2f, "patchnotes" to 2.3f, "bossfight" to 2.3f
            )
        ),
        CategorySpec(
            tagName = "Home",
            prototypeText = "home apartment cleaning laundry repair furniture rent garden plants haushalt wohnung putzen miete",
            weightedTerms = mapOf(
                "home" to 1.8f, "haushalt" to 2.5f, "wohnung" to 2.3f, "haus" to 1.8f,
                "cleaning" to 2.2f, "putzen" to 2.4f, "laundry" to 2.3f, "waesche" to 2.3f,
                "repair" to 2.0f, "reparatur" to 2.2f, "furniture" to 2.2f, "moebel" to 2.3f,
                "rent" to 2.0f, "miete" to 2.2f, "garden" to 2.0f, "garten" to 2.1f,
                "plants" to 1.9f, "pflanzen" to 2.0f, "ikea" to 2.2f, "kueche" to 1.9f,
                "heizung" to 2.2f, "handwerker" to 2.3f, "hausmeister" to 2.3f,
                "balkon" to 1.9f, "renovieren" to 2.3f, "werkzeug" to 2.1f, "baumarkt" to 2.2f
            ),
            compoundRoots = setOf("haushalt", "wohnung", "reparatur", "moebel", "renovier")
        ),
        CategorySpec(
            tagName = "Car",
            prototypeText = "car vehicle mechanic tire fuel inspection garage oil change auto werkstatt reifen tanken",
            weightedTerms = mapOf(
                "car" to 2.3f, "auto" to 2.2f, "vehicle" to 2.2f, "fahrzeug" to 2.3f,
                "mechanic" to 2.3f, "werkstatt" to 2.5f, "tuev" to 2.5f, "inspection" to 2.0f,
                "inspektion" to 2.3f, "fuel" to 2.0f, "tanken" to 2.3f, "benzin" to 2.2f,
                "diesel" to 2.1f, "reifen" to 2.3f, "tires" to 2.2f, "kennzeichen" to 2.3f,
                "oelwechsel" to 2.5f, "parkplatz" to 2.0f
            ),
            compoundRoots = setOf("werkstatt", "fahrzeug", "inspektion", "reifen", "oelwechsel"),
            phrases = mapOf("oil change" to 3.0f)
        ),
        CategorySpec(
            tagName = "Journal",
            prototypeText = "journal diary reflection gratitude mood thoughts today felt tagebuch gedanken stimmung dankbar",
            weightedTerms = mapOf(
                "journal" to 2.6f, "diary" to 2.6f, "tagebuch" to 2.7f,
                "reflection" to 2.2f, "reflexion" to 2.3f, "gratitude" to 2.4f, "dankbarkeit" to 2.4f,
                "dankbar" to 2.1f, "mood" to 2.0f, "stimmung" to 2.1f, "mindfulness" to 2.2f,
                "gedanken" to 1.9f, "thoughts" to 1.6f, "rueckblick" to 2.2f, "grateful" to 2.2f
            )
        ),
        CategorySpec(
            tagName = "Events",
            prototypeText = "event birthday party wedding invitation tickets celebration calendar geburtstag hochzeit einladung feier",
            weightedTerms = mapOf(
                "event" to 2.3f, "events" to 2.5f, "veranstaltung" to 2.5f,
                "birthday" to 2.5f, "geburtstag" to 2.6f, "party" to 2.2f, "feier" to 2.1f,
                "wedding" to 2.5f, "hochzeit" to 2.6f, "invitation" to 2.2f, "einladung" to 2.3f,
                "gift" to 1.8f, "geschenk" to 2.1f, "anniversary" to 2.3f, "jubilaeum" to 2.3f,
                "festival" to 2.2f, "tickets" to 1.8f, "weihnachten" to 2.4f, "christmas" to 2.4f,
                "silvester" to 2.3f, "einweihungsfeier" to 2.5f
            ),
            compoundRoots = setOf("geburtstag", "hochzeit", "einladung", "veranstaltung", "geschenk", "weihnacht")
        ),
        CategorySpec(
            tagName = "Personal",
            prototypeText = "family mom dad parents friends sister brother visit call mama papa familie eltern freunde",
            weightedTerms = mapOf(
                "family" to 2.3f, "familie" to 2.4f, "mama" to 2.3f, "mom" to 2.2f,
                "papa" to 2.3f, "dad" to 2.2f, "eltern" to 2.3f, "parents" to 2.2f,
                "freunde" to 2.1f, "friends" to 2.1f, "freund" to 1.7f, "friend" to 1.7f,
                "schwester" to 2.1f, "sister" to 2.1f, "bruder" to 2.1f, "brother" to 2.1f,
                "partner" to 1.8f, "besuchen" to 1.7f, "anrufen" to 1.8f
            ),
            compoundRoots = setOf("familie", "eltern", "freunde"),
            phrases = mapOf(
                "call mom" to 2.9f, "call dad" to 2.9f,
                "mama anrufen" to 3.1f, "papa anrufen" to 3.1f
            )
        ),
        CategorySpec(
            tagName = "Tech",
            prototypeText = "laptop macbook thinkpad monitor keyboard headphones smartphone router wifi nas server printer firmware",
            weightedTerms = mapOf(
                "laptop" to 2.3f, "macbook" to 2.4f, "thinkpad" to 2.4f, "monitor" to 2.1f,
                "keyboard" to 2.2f, "tastatur" to 2.3f, "headphones" to 2.2f, "kopfhoerer" to 2.3f,
                "smartphone" to 2.2f, "iphone" to 2.3f, "pixel" to 1.9f, "samsung" to 2.0f,
                "router" to 2.2f, "wifi" to 2.1f, "wlan" to 2.2f, "nas" to 2.2f,
                "server" to 2.0f, "linux" to 2.2f, "windows" to 1.8f, "ssd" to 2.2f,
                "nvme" to 2.3f, "gpu" to 2.1f, "cpu" to 2.0f, "bluetooth" to 2.1f,
                "printer" to 2.1f, "drucker" to 2.2f, "akku" to 2.0f, "battery" to 1.8f,
                "firmware" to 2.3f, "hardware" to 2.1f, "homelab" to 2.4f, "smartwatch" to 2.2f
            ),
            compoundRoots = setOf("tastatur", "kopfhoerer", "smartphone", "drucker", "firmware", "homelab")
        ),
        CategorySpec(
            tagName = "Pets",
            prototypeText = "dog cat pet vet leash puppy kitten aquarium hund katze haustier tierarzt futter gassi",
            weightedTerms = mapOf(
                "dog" to 2.2f, "hund" to 2.4f, "hunde" to 2.4f, "cat" to 2.2f,
                "katze" to 2.4f, "katzen" to 2.4f, "pet" to 2.2f, "haustier" to 2.5f,
                "vet" to 2.4f, "tierarzt" to 2.6f, "tierklinik" to 2.6f, "futter" to 2.1f,
                "hundefutter" to 2.6f, "katzenfutter" to 2.6f, "leash" to 2.2f, "leine" to 1.8f,
                "gassi" to 2.4f, "puppy" to 2.3f, "welpe" to 2.4f, "kitten" to 2.3f,
                "aquarium" to 2.3f
            ),
            compoundRoots = setOf("haustier", "tierarzt", "hunde", "katzen", "futter")
        ),
        CategorySpec(
            tagName = "Photography",
            prototypeText = "photography photo camera lens aperture shutter lightroom portrait tripod fujifilm canon nikon foto kamera objektiv",
            weightedTerms = mapOf(
                "photography" to 2.6f, "fotografie" to 2.6f, "photo" to 1.9f, "foto" to 2.1f,
                "fotos" to 2.1f, "camera" to 2.3f, "kamera" to 2.4f, "lens" to 2.2f,
                "objektiv" to 2.4f, "aperture" to 2.3f, "blende" to 2.2f, "shutter" to 2.2f,
                "lightroom" to 2.4f, "tripod" to 2.3f, "stativ" to 2.3f, "fujifilm" to 2.5f,
                "canon" to 2.2f, "nikon" to 2.4f, "sony" to 1.9f
            ),
            compoundRoots = setOf("fotograf", "kamera", "objektiv")
        ),
        CategorySpec(
            tagName = "Quotes",
            prototypeText = "quote quotes poem aphorism proverb mantra motto wisdom zitat zitate gedicht sprichwort weisheit",
            weightedTerms = mapOf(
                "quote" to 2.4f, "quotes" to 2.5f, "zitat" to 2.5f, "zitate" to 2.5f,
                "poem" to 2.4f, "gedicht" to 2.5f, "aphorism" to 2.5f, "proverb" to 2.4f,
                "sprichwort" to 2.5f, "mantra" to 2.4f, "motto" to 2.3f, "weisheit" to 2.3f
            )
        )
    )

    private val stacktraceRegex = Regex(
        """(Exception|Error|Traceback|at\s+[\w.$]+\(|Caused by:|FATAL EXCEPTION|error\[E\d+\])"""
    )
    private val codeFenceOrKeywordsRegex = Regex(
        """(```|fun\s+\w+\(|class\s+\w+|import\s+[\w.]+|SELECT\s+.+\s+FROM|def\s+\w+\(|fn\s+\w+\()""",
        RegexOption.IGNORE_CASE
    )
    private val inlineHashtagRegex = Regex(
        """(?:^|[\s(\[,])#([\p{L}][\p{L}\p{N}_-]{1,30})(?=$|[\s.,;:!?)\]}])"""
    )

    fun categoryPrototypes(): Map<String, String> =
        taxonomy.associate { it.tagName to it.prototypeText }

    fun suggestTags(
        title: String,
        blocks: List<NoteBlock>,
        ocrTexts: List<String>,
        links: List<DetectedLink>,
        hasScreenshot: Boolean,
        existingUserVocabulary: List<String>,
        semanticTagScores: Map<String, Float> = emptyMap(),
        userTagProfiles: Map<String, Map<String, Float>> = emptyMap(),
        systemEntities: List<ClassifiedTextEntity> = emptyList(),
        imageLabels: List<DetectedImageLabel> = emptyList(),
        includeTopicKeywords: Boolean = false
    ): List<SmartTagSuggestion> {
        val scores = LinkedHashMap<String, Pair<Float, String>>()

        fun addScore(tag: String, delta: Float, reason: String) {
            val clean = tag.trim()
            if (clean.isBlank()) return
            val existing = scores[clean]
            val updated = (existing?.first ?: 0f) + delta
            scores[clean] = updated to (existing?.second ?: reason)
        }

        val bodyText = blocks.filter { it.type == NoteBlockType.TEXT }.joinToString("\n") { it.textContent }
        val checklistItems = blocks.filter { it.type == NoteBlockType.CHECKLIST }.map { it.textContent }
        val checklistText = checklistItems.joinToString("\n")
        val combinedOcr = ocrTexts.joinToString("\n")
        val allText = listOf(title, bodyText, checklistText, combinedOcr).joinToString("\n")
        val allTextLower = foldToAsciiLower(allText)

        val existingUserTagsByLower = existingUserVocabulary
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .associateBy { it.lowercase(Locale.ROOT) }

        // Stage 0: Explicit inline #hashtags authored in title/body/checklist (excluding Markdown headings & hex codes)
        val hashtagText = listOf(title, bodyText, checklistText).joinToString("\n")
        inlineHashtagRegex.findAll(hashtagText).forEach { match ->
            val rawTag = match.groupValues[1]
            if (!looksLikeHexColor(rawTag)) {
                val formatted = existingUserTagsByLower[rawTag.lowercase(Locale.ROOT)] ?: formatInlineHashtag(rawTag)
                addScore(formatted, 0.94f, "Hashtag #$rawTag")
            }
        }

        // Stage 1: Structural & pattern detectors
        if (hasScreenshot) {
            addScore("Screenshot", 0.88f, "Imported screenshot image")
        }

        if (stacktraceRegex.containsMatchIn(allText)) {
            addScore("Error", 0.86f, "Detected error or stacktrace pattern")
            addScore("Programming", 0.78f, "Detected technical diagnostic trace")
        }

        if (codeFenceOrKeywordsRegex.containsMatchIn(allText)) {
            addScore("Programming", 0.80f, "Detected source code pattern")
        }

        if (checklistItems.size >= 2) {
            addScore("Tasks", 0.68f, "Checklist note structure")
        }

        for (link in links) {
            val matchedDomainTags = domainRules[link.domain]
                ?: domainRules.entries.firstOrNull { link.domain.endsWith(".${it.key}") }?.value
            matchedDomainTags?.forEach { domainTag ->
                addScore(domainTag, 0.85f, "URL domain ${link.domain}")
            }
            if (matchedDomainTags == null) {
                addScore("Bookmark", 0.68f, "Saved web link")
            }
        }

        // Stage 1B: Android System TextClassifier entity signals
        var contactSignals = 0
        for (entity in systemEntities) {
            when (entity.type) {
                TextClassifier.TYPE_FLIGHT_NUMBER -> {
                    addScore("Travel", 0.88f, "Detected flight number")
                }
                "tracking_number" -> {
                    addScore("Shopping", 0.84f, "Detected tracking number")
                }
                TextClassifier.TYPE_ADDRESS -> {
                    if (allTextLower.contains("hotel") || allTextLower.contains("flight") || allTextLower.contains("flug") || allTextLower.contains("urlaub")) {
                        addScore("Travel", 0.76f, "Detected travel address")
                    } else {
                        addScore("Places", 0.74f, "Detected address")
                    }
                }
                TextClassifier.TYPE_EMAIL,
                TextClassifier.TYPE_PHONE -> {
                    contactSignals++
                }
            }
        }
        if (contactSignals >= 2) {
            addScore("Personal", 0.72f, "Detected contact info")
        }

        // Stage 1C: ML Kit Image Labeling visual category & object signals
        for (label in imageLabels) {
            val lowerLabel = label.text.trim().lowercase(Locale.ROOT)
            if (lowerLabel.isBlank()) continue
            val mappedCategory = IMAGE_LABEL_CATEGORY_MAP[lowerLabel]
                ?: IMAGE_LABEL_CATEGORY_MAP.entries.firstOrNull { (key, _) -> lowerLabel.contains(key) }?.value
            if (mappedCategory != null) {
                addScore(mappedCategory, (0.70f + (label.confidence * 0.16f)).coerceAtMost(0.88f), "Detected in image")
            } else if (label.confidence >= 0.78f && lowerLabel !in GENERIC_VISUAL_LABEL_BLOCKLIST) {
                val cleanLabel = formatTopicToken(label.text.trim())
                if (cleanLabel.length in 3..20) {
                    val conf = (0.68f + (label.confidence - 0.78f) * 0.6f).coerceAtMost(0.84f)
                    addScore(cleanLabel, conf, "Detected in image")
                }
            }
        }

        // Stage 2A: Weighted bilingual token + stem + umlaut + compound index
        val termWeights = HashMap<String, Float>()
        val rawTokensWithWeight = HashMap<String, Float>()

        fun accumulateText(text: String, weight: Float) {
            if (text.isBlank()) return
            for (raw in TextHashing.tokenizeWords(text)) {
                rawTokensWithWeight[raw] = (rawTokensWithWeight[raw] ?: 0f) + weight
                for (variant in expandTokenVariants(raw)) {
                    termWeights[variant] = (termWeights[variant] ?: 0f) + weight
                }
            }
        }
        accumulateText(title, 3.2f)
        accumulateText(links.joinToString(" ") { "${it.domain} ${it.rawUrl}" }, 2.0f)
        accumulateText(checklistText, 1.7f)
        accumulateText(bodyText, 1.5f)
        accumulateText(combinedOcr, 1.2f)

        val compoundTokens = rawTokensWithWeight.entries
            .filter { it.key.length >= 8 }
            .take(80)
            .map { foldToAsciiLower(it.key) to it.value }

        // User-vocabulary direct/morphological matching
        for ((lowerTag, displayTag) in existingUserTagsByLower) {
            val tagVariants = expandTokenVariants(lowerTag)
            val bestWeight = tagVariants.mapNotNull { termWeights[it] }.maxOrNull()
            if (bestWeight != null && bestWeight >= 1.5f) {
                addScore(displayTag, (0.64f + (bestWeight / 6.0f)).coerceAtMost(0.95f), "Matches your tag '$displayTag'")
            } else if (" " in lowerTag && allTextLower.contains(foldToAsciiLower(lowerTag))) {
                addScore(displayTag, 0.84f, "Matches your tag '$displayTag'")
            }
        }

        // Personal user-tag co-occurrence profile matching (learned from user's confirmed tagged notes)
        for ((tagName, profileTokens) in userTagProfiles) {
            if (profileTokens.isEmpty()) continue
            var overlapSum = 0f
            var overlapHits = 0
            for ((token, tokenSalience) in profileTokens) {
                val noteW = termWeights[token] ?: continue
                overlapHits++
                overlapSum += (noteW.coerceAtMost(4f) * tokenSalience.coerceAtMost(2.5f))
            }
            if ((overlapHits >= 2 && overlapSum >= 2.0f) || overlapSum >= 3.6f) {
                val resolvedName = existingUserTagsByLower[tagName.lowercase(Locale.ROOT)] ?: tagName
                val conf = (0.62f + (overlapSum / 11f)).coerceIn(0.68f, 0.93f)
                addScore(resolvedName, conf, "Similar to your #$resolvedName notes")
            }
        }

        // Stage 2B: Bilingual weighted taxonomy + compound roots + multi-word phrases + noise guards
        for (category in taxonomy) {
            var rawSum = 0f
            var distinctHits = 0
            var hasStrongAnchor = false

            for ((term, termWeight) in category.weightedTerms) {
                val freqWeight = termWeights[term] ?: continue
                distinctHits++
                if (termWeight >= 1.8f || (freqWeight >= 3.2f && termWeight >= 1.6f)) {
                    hasStrongAnchor = true
                }
                rawSum += (freqWeight.coerceAtMost(6f) * termWeight)
            }

            if (category.compoundRoots.isNotEmpty() && compoundTokens.isNotEmpty()) {
                val matchedRootsForCategory = HashSet<String>()
                for ((longTok, tokWeight) in compoundTokens) {
                    for (root in category.compoundRoots) {
                        if (longTok != root &&
                            longTok.contains(root) &&
                            termWeights[root] == null &&
                            matchedRootsForCategory.add(root)
                        ) {
                            distinctHits++
                            hasStrongAnchor = true
                            rawSum += (tokWeight.coerceAtMost(4.5f) * 2.4f)
                        }
                    }
                }
            }

            for ((phrase, boost) in category.phrases) {
                if (allTextLower.contains(phrase)) {
                    distinctHits++
                    hasStrongAnchor = true
                    rawSum += boost
                }
            }

            val qualified = (distinctHits >= 2 && rawSum >= 2.5f && hasStrongAnchor) ||
                (distinctHits >= 3 && rawSum >= 2.8f) ||
                (hasStrongAnchor && rawSum >= 3.15f)

            if (qualified) {
                val normalizedConfidence = (0.55f + (rawSum / 14f)).coerceIn(0.62f, 0.96f)
                addScore(category.tagName, normalizedConfidence, "Keyword relevance")
            }
        }

        // Stage 3: Neural semantic affinity scores from LiteRT embeddings
        for ((tagName, sim) in semanticTagScores) {
            if (sim >= 0.55f) {
                val delta = if (scores.containsKey(tagName)) {
                    sim * 0.45f
                } else if (sim >= 0.72f) {
                    (0.56f + (sim - 0.55f) * 0.78f).coerceAtMost(0.90f)
                } else {
                    0f
                }
                if (delta > 0f) {
                    addScore(tagName, delta, "Semantic affinity")
                }
            }
        }

        // Stage 4: Open-vocabulary salient topic keywords for explicit on-demand generation
        if (includeTopicKeywords) {
            val strongMatches = scores.entries.count { it.value.first >= 0.65f }
            if (strongMatches < 2) {
                val needed = (2 - strongMatches).coerceAtLeast(1)
                val coveredLower = scores.keys.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
                val topics = extractSalientTopicKeywords(
                    title = title,
                    bodyText = bodyText,
                    checklistText = checklistText,
                    ocrText = combinedOcr,
                    alreadyCoveredLower = coveredLower,
                    maxCount = needed
                )
                topics.forEach { (topicName, topicConf) ->
                    addScore(topicName, topicConf, "Key topic")
                }
            }
        }

        return scores.entries
            .map { (name, pair) ->
                SmartTagSuggestion(
                    name = name,
                    confidence = pair.first.coerceIn(0f, 0.99f),
                    reason = pair.second
                )
            }
            .filter { it.confidence >= 0.65f }
            .sortedByDescending { it.confidence }
            .distinctBy { it.name.lowercase(Locale.ROOT) }
            .take(4)
    }

    private fun extractSalientTopicKeywords(
        title: String,
        bodyText: String,
        checklistText: String,
        ocrText: String,
        alreadyCoveredLower: Set<String>,
        maxCount: Int
    ): List<Pair<String, Float>> {
        if (maxCount <= 0) return emptyList()
        data class TopicAccumulator(
            var score: Float = 0f,
            var displaySample: String = "",
            var mentions: Int = 0
        )

        val candidates = LinkedHashMap<String, TopicAccumulator>()
        val rawWordRegex = Regex("""[\p{L}][\p{L}\p{N}_-]{2,26}""")

        fun feedLines(raw: String, lineWeight: Float, detectHeadings: Boolean) {
            if (raw.isBlank()) return
            for (line in raw.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue
                val isHeading = detectHeadings && trimmed.startsWith("#") && trimmed.getOrNull(1)?.let { it == '#' || it.isWhitespace() } == true
                val cleaned = trimmed.replace(URL_IN_TEXT_REGEX, " ")
                val lineMultiplier = if (isHeading) 2.2f else lineWeight

                rawWordRegex.findAll(cleaned).forEachIndexed { idx, match ->
                    val token = match.value.trim('_', '-')
                    if (token.length !in 4..24) return@forEachIndexed
                    if (token.count { it.isDigit() } > token.length / 2) return@forEachIndexed
                    if (looksLikeHexColor(token)) return@forEachIndexed

                    val folded = foldToAsciiLower(token)
                    if (folded.length < 4 || folded in TOPIC_STOPWORDS || folded in alreadyCoveredLower) {
                        return@forEachIndexed
                    }
                    // Group inflections lightly by folded stem key when long enough
                    val stemKey = when {
                        folded.length >= 7 && folded.endsWith("ing") -> folded.dropLast(3)
                        folded.length >= 6 && folded.endsWith("s") && !folded.endsWith("ss") -> folded.dropLast(1)
                        else -> folded
                    }
                    if (stemKey in alreadyCoveredLower || stemKey in TOPIC_STOPWORDS) return@forEachIndexed

                    var boost = lineMultiplier
                    // Mid-line proper noun / German noun boost
                    val hasInitialCap = token.first().isUpperCase()
                    val isAcronymOrCamel = token.count { it.isUpperCase() } in 2..4
                    if (idx > 0 && hasInitialCap) boost += 1.0f
                    if (isAcronymOrCamel) boost += 0.6f
                    if (token.length >= 7) boost += 0.45f

                    val acc = candidates.getOrPut(folded) { TopicAccumulator(displaySample = formatTopicToken(token)) }
                    acc.score += boost
                    acc.mentions++
                    if (isAcronymOrCamel || (hasInitialCap && !acc.displaySample.first().isUpperCase())) {
                        acc.displaySample = formatTopicToken(token)
                    }
                }
            }
        }

        feedLines(title, 2.8f, detectHeadings = false)
        feedLines(bodyText, 1.35f, detectHeadings = true)
        feedLines(checklistText, 1.45f, detectHeadings = false)
        feedLines(ocrText, 0.95f, detectHeadings = false)

        return candidates.entries
            .mapNotNull { (_, acc) ->
                val totalScore = acc.score + ((acc.mentions - 1).coerceAtLeast(0) * 0.65f)
                if (totalScore >= 2.75f) {
                    val conf = (0.65f + (totalScore - 2.75f) * 0.045f).coerceIn(0.66f, 0.78f)
                    acc.displaySample to conf
                } else {
                    null
                }
            }
            .sortedByDescending { it.second }
            .distinctBy { it.first.lowercase(Locale.ROOT) }
            .take(maxCount)
    }

    companion object {
        private val HEX_ONLY_REGEX = Regex("^[0-9a-fA-F]+$")
        private val URL_IN_TEXT_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

        private val GENERIC_VISUAL_LABEL_BLOCKLIST = setOf(
            "product", "font", "rectangle", "material property", "text", "line", "pattern",
            "symmetry", "screenshot", "brand", "logo", "event", "art", "design", "circle",
            "square", "color", "darkness", "light", "indoor", "outdoor", "thing", "object",
            "parallel", "space", "paper", "document"
        )

        private val IMAGE_LABEL_CATEGORY_MAP = mapOf(
            "dog" to "Pets", "cat" to "Pets", "pet" to "Pets", "puppy" to "Pets",
            "kitten" to "Pets", "animal" to "Pets",
            "food" to "Recipe", "dish" to "Recipe", "meal" to "Recipe", "dessert" to "Recipe",
            "baking" to "Recipe", "cuisine" to "Recipe", "drink" to "Recipe", "coffee" to "Recipe",
            "fruit" to "Recipe", "vegetable" to "Recipe", "cake" to "Recipe",
            "plant" to "Home", "flower" to "Home", "furniture" to "Home", "room" to "Home",
            "kitchen" to "Home", "house" to "Home", "interior" to "Home", "table" to "Home",
            "car" to "Car", "vehicle" to "Car", "wheel" to "Car", "automotive" to "Car",
            "laptop" to "Tech", "computer" to "Tech", "keyboard" to "Tech", "monitor" to "Tech",
            "mobile phone" to "Tech", "smartphone" to "Tech", "electronics" to "Tech",
            "mountain" to "Travel", "beach" to "Travel", "airplane" to "Travel", "luggage" to "Travel",
            "landmark" to "Travel", "vacation" to "Travel", "nature" to "Travel", "sky" to "Travel",
            "book" to "Reading", "library" to "Reading", "newspaper" to "Reading", "publication" to "Reading",
            "camera" to "Photography", "lens" to "Photography", "photography" to "Photography",
            "gym" to "Fitness", "bicycle" to "Fitness", "hiking" to "Fitness", "sports" to "Fitness",
            "running" to "Fitness",
            "guitar" to "Music", "piano" to "Music", "musical instrument" to "Music", "concert" to "Music",
            "receipt" to "Receipt", "invoice" to "Receipt"
        )

        private val USER_PROFILE_STOPWORDS = setOf(
            "the", "and", "for", "with", "from", "that", "this", "have", "are", "was", "were",
            "you", "your", "not", "but", "can", "will", "just", "about", "into", "more", "some",
            "der", "die", "das", "und", "fuer", "mit", "von", "auf", "ein", "eine", "einen",
            "ist", "sind", "war", "nicht", "auch", "noch", "nur", "oder", "aber", "als", "bei",
            "note", "notes", "notiz", "notizen", "http", "https", "www", "com"
        )

        private val TOPIC_STOPWORDS = USER_PROFILE_STOPWORDS + setOf(
            "that", "with", "this", "from", "have", "been", "being", "were", "will", "would",
            "could", "should", "shall", "might", "must", "need", "needs", "needed", "want",
            "wants", "make", "made", "making", "take", "took", "give", "gave", "keep", "kept",
            "know", "think", "thought", "look", "looking", "find", "found", "going", "getting",
            "using", "used", "work", "working", "very", "much", "many", "most", "such", "than",
            "then", "them", "they", "their", "there", "these", "those", "where", "when", "what",
            "which", "while", "whose", "whom", "here", "over", "under", "again", "still", "also",
            "only", "back", "down", "after", "before", "until", "since", "between", "through",
            "because", "about", "today", "tomorrow", "yesterday", "tonight", "week", "month",
            "year", "time", "times", "good", "great", "best", "nice", "easy", "fast", "quick",
            "total", "change", "changes", "review", "finish", "finished", "check", "checked",
            "update", "updated", "start", "started", "list", "item", "items", "stuff", "thing",
            "things", "part", "plan", "idea", "test", "example", "sample", "details", "info",
            "body", "text", "remains", "untitled", "remember", "forget", "please", "thank",
            "thanks", "hello", "eine", "einem", "einer", "eines", "dieser", "diese", "dieses",
            "habe", "haben", "hatte", "hatten", "wird", "werden", "wurde", "wurden", "kann",
            "koennen", "konnte", "muss", "muessen", "musste", "soll", "sollen", "sollte", "will",
            "wollen", "wollte", "moechte", "kein", "keine", "keinen", "keinem", "oder", "aber",
            "denn", "weil", "dass", "wenn", "wann", "dann", "schon", "wieder", "immer", "etwas",
            "nichts", "alles", "alle", "viele", "mehr", "sehr", "ganz", "einfach", "schnell",
            "heute", "morgen", "gestern", "woche", "monat", "jahr", "zeit", "bitte", "danke",
            "hallo", "vergessen", "brauche", "brauchen", "machen", "fertig", "pruefen", "liste",
            "dinge", "sachen", "thema", "punkt", "punkte", "unter", "ueber", "durch", "gegen",
            "ohne", "zwischen", "nach", "seit", "bevor", "sobald", "damit", "appointment", "termin"
        )

        fun buildUserTagContext(
            summaries: List<NoteSummary>,
            excludeNoteId: String
        ): Pair<Map<String, Map<String, Float>>, Map<String, List<String>>> {
            val rawTokenCounts = LinkedHashMap<String, MutableMap<String, Int>>()
            val tagNoteIds = LinkedHashMap<String, MutableList<String>>()
            val tagDocCounts = HashMap<String, Int>()

            for (summary in summaries) {
                if (summary.id == excludeNoteId || summary.isDeleted) continue
                val userTags = summary.tags.filter { it.source == TagSource.USER }
                if (userTags.isEmpty()) continue

                val noteTokens = TextHashing.tokenizeWords("${summary.title} ${summary.previewText}")
                    .map { foldToAsciiLower(it) }
                    .filter { it.length >= 3 && it !in USER_PROFILE_STOPWORDS }
                    .distinct()

                for (tag in userTags) {
                    val ids = tagNoteIds.getOrPut(tag.name) { mutableListOf() }
                    if (ids.size < 16) ids += summary.id
                    tagDocCounts[tag.name] = (tagDocCounts[tag.name] ?: 0) + 1
                    val counts = rawTokenCounts.getOrPut(tag.name) { mutableMapOf() }
                    for (tok in noteTokens) {
                        counts[tok] = (counts[tok] ?: 0) + 1
                    }
                }
            }

            val profiles = LinkedHashMap<String, Map<String, Float>>()
            for ((tagName, counts) in rawTokenCounts) {
                val docCount = (tagDocCounts[tagName] ?: 1).coerceAtLeast(1)
                val weighted = counts.entries
                    .sortedByDescending { it.value }
                    .take(12)
                    .associate { (tok, count) ->
                        val salience = (1.0f + (count.toFloat() / docCount.toFloat())).coerceIn(1.0f, 2.5f)
                        tok to salience
                    }
                if (weighted.isNotEmpty()) {
                    profiles[tagName] = weighted
                }
            }
            return profiles to tagNoteIds
        }

        private fun formatTopicToken(raw: String): String {
            val upperCount = raw.count { it.isUpperCase() }
            if (upperCount >= 2 && raw.length <= 20) return raw.take(24)
            return raw.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }.take(24)
        }

        fun foldToAsciiLower(raw: String): String {
            val lower = raw.lowercase(Locale.ROOT)
            val sb = StringBuilder(lower.length + 4)
            for (ch in lower) {
                when (ch) {
                    'ä' -> sb.append("ae")
                    'ö' -> sb.append("oe")
                    'ü' -> sb.append("ue")
                    'ß' -> sb.append("ss")
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        internal fun expandTokenVariants(token: String): Set<String> {
            val lower = token.lowercase(Locale.ROOT)
            val folded = foldToAsciiLower(lower)
            val plainUmlaut = lower
                .replace('ä', 'a')
                .replace('ö', 'o')
                .replace('ü', 'u')
                .replace("ß", "ss")
            val variants = LinkedHashSet<String>(6)
            variants += lower
            variants += folded
            variants += plainUmlaut

            for (base in listOf(lower, folded, plainUmlaut)) {
                if (base.length >= 5) {
                    when {
                        base.endsWith("ies") && base.length >= 6 -> variants += base.dropLast(3) + "y"
                        base.endsWith("innen") && base.length >= 8 -> variants += base.dropLast(5)
                        base.endsWith("ing") && base.length >= 7 -> variants += base.dropLast(3)
                        base.endsWith("ung") && base.length >= 7 -> variants += base.dropLast(3)
                        base.endsWith("keit") && base.length >= 8 -> variants += base.dropLast(4)
                        base.endsWith("heit") && base.length >= 8 -> variants += base.dropLast(4)
                        base.endsWith("ern") && base.length >= 6 -> variants += base.dropLast(3)
                        base.endsWith("es") && base.length >= 6 -> variants += base.dropLast(2)
                        base.endsWith("ed") && base.length >= 6 -> variants += base.dropLast(2)
                        base.endsWith("en") && base.length >= 6 -> variants += base.dropLast(2)
                        base.endsWith("er") && base.length >= 6 -> variants += base.dropLast(2)
                    }
                    if (base.endsWith("n") && base.length >= 5) {
                        variants += base.dropLast(1)
                    }
                    if (base.endsWith("e") && base.length >= 5) {
                        variants += base.dropLast(1)
                    }
                    if (base.endsWith("s") && base.length >= 5) {
                        variants += base.dropLast(1)
                    }
                }
            }
            return variants
        }

        private fun looksLikeHexColor(token: String): Boolean {
            if (token.length !in setOf(3, 6, 8) || !HEX_ONLY_REGEX.matches(token)) return false
            return token.any { it.isDigit() } ||
                token.all { it in 'A'..'F' } ||
                token.equals("fff", ignoreCase = false)
        }

        private fun formatInlineHashtag(raw: String): String {
            if (raw.any { it.isUpperCase() }) return raw.take(32)
            return raw.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }.take(32)
        }
    }
}
