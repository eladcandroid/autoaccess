package dev.accessauto.sim

import dev.accessauto.grammar.Effect
import dev.accessauto.grammar.Option
import dev.accessauto.grammar.Prompt

/**
 * Canned driving situations for rehearsing the gestures. Nothing here is sent anywhere;
 * the real message, call and navigation hooks replace these later.
 */
object DemoScenarios {
    private const val DANA_ASKS = "מתי אתה מגיע?"

    private fun toDana(text: String, replyTo: String? = null) =
        Option(text, result = "נשלח לדנה: $text", effect = Effect.Message("דנה", text, replyTo))

    private fun music(label: String, command: Effect.MusicCommand.Command) =
        Option(label, result = "מוזיקה: $label", effect = Effect.MusicCommand(command))

    private val places = Prompt(
        "menu.navigate", "לאן לנווט?",
        listOf(
            Option("הביתה", result = "מנווט הביתה", effect = Effect.Navigation("מנווט הביתה", "18 דקות", "12 ק״מ")),
            Option("לעבודה", result = "מנווט לעבודה", effect = Effect.Navigation("מנווט לעבודה", "25 דקות", "19 ק״מ")),
            Option("תחנת דלק", result = "מנווט לתחנת הדלק הקרובה", effect = Effect.Navigation("מנווט לתחנת דלק", "4 דקות", "2 ק״מ")),
        ),
    )
    private val quickMessage = Prompt("menu.message", "מה לשלוח לדנה?", listOf(toDana("אני בדרך"), toDana("אני מאחר"), toDana("הגעתי")))
    private val musicMenu = Prompt(
        "menu.music", "מוזיקה",
        listOf(
            music("נגן או עצור", Effect.MusicCommand.Command.TOGGLE),
            music("השיר הבא", Effect.MusicCommand.Command.NEXT),
            music("השיר הקודם", Effect.MusicCommand.Command.PREVIOUS),
        ),
    )

    /** Opened by the driver with an open palm. */
    val menu = Prompt("menu", "מה תרצה לעשות?", listOf(Option("ניווט", next = places), Option("הודעה", next = quickMessage), Option("מוזיקה", next = musicMenu)))

    /** Things that happen to the driver, shown one after another. */
    val incoming = listOf(
        Prompt(
            "message", "דנה שואלת מתי תגיע",
            listOf(toDana("עוד 10 דקות", DANA_ASKS), toDana("אני מאחר", DANA_ASKS), toDana("אכתוב כשאעצור", DANA_ASKS)),
            timeoutMs = 12_000,
        ),
        Prompt(
            "call", "אמא מתקשרת",
            listOf(
                Option("דחה ושלח: אני נוהג", result = "השיחה נדחתה", effect = Effect.CallDeclined("אמא", "אני נוהג, כתבי לי")),
                Option("דחה ושלח: אחזור אלייך", result = "השיחה נדחתה", effect = Effect.CallDeclined("אמא", "אחזור אלייך כשאעצור")),
                Option("דחה בלי הודעה", result = "השיחה נדחתה", effect = Effect.CallDeclined("אמא")),
            ),
            timeoutMs = 12_000,
        ),
        Prompt(
            "reroute", "פקק לפניך. יש מסלול מהיר ב-7 דקות",
            listOf(
                Option("החלף מסלול", result = "המסלול הוחלף", effect = Effect.Navigation("המסלול הוחלף", "11 דקות", "חוסך 7 דקות", rerouted = true)),
                Option("הישאר במסלול", result = "נשארים במסלול"),
            ),
            timeoutMs = 12_000,
        ),
        Prompt(
            "arrived", "הגעת ליעד. לעדכן את דנה?",
            listOf(Option("שלח: הגעתי", result = "נשלח לדנה: הגעתי", effect = Effect.Message("דנה", "הגעתי")), Option("לא עכשיו", result = "לא נשלח")),
            timeoutMs = 12_000,
        ),
    )

    /** One result screen of each kind, by name, for looking at them without gesturing. */
    val previews: Map<String?, Effect> = mapOf(
        "message" to Effect.Message("דנה", "עוד 10 דקות", DANA_ASKS),
        "navigation" to Effect.Navigation("מנווט הביתה", "18 דקות", "12 ק״מ"),
        "reroute" to Effect.Navigation("המסלול הוחלף", "11 דקות", "חוסך 7 דקות", rerouted = true),
        "call" to Effect.CallDeclined("אמא", "אני נוהג, כתבי לי"),
        "music" to Effect.Music("מתנגן עכשיו", "שיר לדרך", "רשימת הנסיעה שלי", playing = true),
    )
}
