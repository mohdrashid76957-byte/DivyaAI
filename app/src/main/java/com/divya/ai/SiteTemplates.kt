package com.divya.ai

import org.json.JSONObject

/**
 * INSTANT website: AI se poora code likhwane ki jagah, pehle se bana sundar responsive template mein
 * user ka naam / services / contact bhar diya jata hai. Phone par ye millisecond mein hota hai (AI ka 1 minute nahi).
 * Sirf chhota structured data Live model se aata hai.
 */
object SiteTemplates {
    class Spec(
        val name: String, val tagline: String, val about: String, val kind: String,
        val services: List<Pair<String, String>>, val reviews: List<Pair<String, String>>, val faqs: List<Pair<String, String>>,
        val phone: String, val whatsapp: String, val email: String, val address: String, val hours: String,
        val color: String, val dark: Boolean, val lang: String
    )

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** "Title: desc | Title: desc" -> pairs. */
    private fun pairs(s: String, sep: Char = ':'): List<Pair<String, String>> =
        s.split("|").map { it.trim() }.filter { it.isNotEmpty() }.map {
            val i = it.indexOf(sep)
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else it to ""
        }.take(12)

    fun specFrom(a: JSONObject): Spec {
        val kind = a.optString("kind").ifBlank { "business" }
        val name = a.optString("name").ifBlank { "Mera Business" }.take(60)
        val hi = a.optString("language").lowercase().startsWith("h")
        val services = pairs(a.optString("services")).ifEmpty {
            if (hi) listOf("Behtareen Quality" to "Hum har kaam mein quality ka poora dhyan rakhte hain.", "Sahi Daam" to "Imaandari se, sabke budget mein.", "Tez Service" to "Aapka samay keemti hai, kaam samay par.")
            else listOf("Top Quality" to "We care about quality in everything we do.", "Fair Pricing" to "Honest prices that fit your budget.", "Fast Service" to "Your time matters, we deliver on time.")
        }
        return Spec(
            name = name,
            tagline = a.optString("tagline").ifBlank { if (hi) "$name - aapka bharosemand saathi" else "$name - your trusted partner" }.take(140),
            about = a.optString("about").ifBlank { if (hi) "$name mein hum apne grahkon ko behtareen anubhav dene ke liye samarpit hain." else "At $name we are dedicated to giving our customers the best experience." }.take(900),
            kind = kind,
            services = services,
            reviews = pairs(a.optString("reviews")),
            faqs = pairs(a.optString("faqs"), '?').map { (q, ans) -> (q + "?") to ans },
            phone = a.optString("phone").filter { it.isDigit() || it == '+' },
            whatsapp = a.optString("whatsapp").filter { it.isDigit() },
            email = a.optString("email").trim(),
            address = a.optString("address").trim().take(200),
            hours = a.optString("hours").trim().take(100),
            color = a.optString("color"),
            dark = !a.optString("theme").lowercase().startsWith("l"),
            lang = if (hi) "hi" else "en"
        )
    }

    private fun accent(c: String): Pair<String, String> {
        val named = mapOf("red" to "#e11d48", "blue" to "#2563eb", "green" to "#16a34a", "orange" to "#f97316", "purple" to "#7c3aed", "pink" to "#ec4899",
            "gold" to "#d4a017", "yellow" to "#eab308", "teal" to "#0d9488", "black" to "#111827", "saffron" to "#ff9933", "cyan" to "#06b6d4", "lal" to "#e11d48",
            "neela" to "#2563eb", "hara" to "#16a34a", "peela" to "#eab308", "kala" to "#111827", "gulabi" to "#ec4899")
        val t = c.trim().lowercase()
        val hex = if (Regex("^#[0-9a-f]{6}$").matches(t)) t else named[t] ?: "#2563eb"
        val v = hex.substring(1).toInt(16)
        val r = ((v shr 16) and 255); val g = ((v shr 8) and 255); val b = v and 255
        fun mix(x: Int) = (x * 0.55).toInt().coerceIn(0, 255)
        val dk = "#%02x%02x%02x".format(mix(r), mix(g), mix(b))
        return hex to dk
    }

    fun render(s: Spec): Map<String, String> {
        val (acc, accDark) = accent(s.color)
        val hi = s.lang == "hi"
        val L = { en: String, h: String -> if (hi) h else en }
        val wa = if (s.whatsapp.length >= 8) "https://wa.me/${s.whatsapp}?text=" + java.net.URLEncoder.encode(L("Hello, I saw your website", "Namaste, maine aapki website dekhi"), "UTF-8").replace("+", "%20") else ""

        val svc = s.services.mapIndexed { i, (t, d) ->
            "<div class=\"card reveal\"><div class=\"ic\">${i + 1}</div><h3>${esc(t)}</h3><p>${esc(d)}</p></div>"
        }.joinToString("\n")
        val rev = if (s.reviews.isEmpty()) "" else "<section id=\"reviews\"><h2>${L("What customers say", "Grahak kya kehte hain")}</h2><div class=\"grid\">" +
            s.reviews.joinToString("\n") { (n, q) -> "<div class=\"card reveal\"><p class=\"q\">&ldquo;${esc(q)}&rdquo;</p><b>- ${esc(n)}</b></div>" } + "</div></section>"
        val faq = if (s.faqs.isEmpty()) "" else "<section id=\"faq\"><h2>${L("FAQ", "Aksar puche jaane wale sawaal")}</h2><div class=\"faq\">" +
            s.faqs.joinToString("\n") { (q, a) -> "<details class=\"reveal\"><summary>${esc(q)}</summary><p>${esc(a)}</p></details>" } + "</div></section>"

        val contactItems = buildList {
            if (s.phone.isNotBlank()) add("<a class=\"btn\" href=\"tel:${esc(s.phone)}\">&#128222; ${esc(s.phone)}</a>")
            if (wa.isNotBlank()) add("<a class=\"btn wa\" href=\"$wa\" target=\"_blank\" rel=\"noopener\">WhatsApp</a>")
            if (s.email.isNotBlank()) add("<a class=\"btn ghost\" href=\"mailto:${esc(s.email)}\">&#9993; ${esc(s.email)}</a>")
        }.joinToString("\n")
        val info = buildList {
            if (s.address.isNotBlank()) add("<p>&#128205; ${esc(s.address)}</p>")
            if (s.hours.isNotBlank()) add("<p>&#128339; ${esc(s.hours)}</p>")
        }.joinToString("\n")
        val heroCta = if (wa.isNotBlank()) "<a class=\"btn wa\" href=\"$wa\" target=\"_blank\" rel=\"noopener\">${L("Chat on WhatsApp", "WhatsApp par baat karo")}</a>"
            else if (s.phone.isNotBlank()) "<a class=\"btn\" href=\"tel:${esc(s.phone)}\">${L("Call now", "Abhi call karo")}</a>" else "<a class=\"btn\" href=\"#contact\">${L("Contact us", "Sampark karein")}</a>"
        val waFloat = if (wa.isNotBlank()) "<a class=\"float\" href=\"$wa\" target=\"_blank\" rel=\"noopener\" aria-label=\"WhatsApp\"><svg viewBox=\"0 0 32 32\" width=\"30\" height=\"30\" fill=\"#fff\"><path d=\"M16 3C9 3 3.4 8.6 3.4 15.5c0 2.4.7 4.7 1.9 6.6L3 29l7.1-1.9c1.9 1 4 1.6 6.2 1.6 6.9 0 12.6-5.6 12.6-12.5S23 3 16 3zm0 22.9c-2 0-3.9-.6-5.5-1.6l-.4-.2-4.2 1.1 1.1-4.1-.3-.4c-1.1-1.700-1.7-3.600-1.700-5.600C5 9.600 10 4.800 16 4.800s11 4.800 11 10.700-5 10.400-11 10.400zm6-7.800c-.3-.2-1.900-.9-2.200-1-.3-.1-.5-.2-.7.200-.2.300-.8 1-1 1.200-.2.200-.4.200-.7.100-.3-.2-1.400-.5-2.600-1.600-1-.9-1.600-1.900-1.800-2.200-.2-.3 0-.5.100-.7l.5-.5c.1-.2.200-.3.300-.5.100-.2 0-.4 0-.5l-1-2.300c-.2-.6-.5-.5-.7-.5h-.6c-.2 0-.5.100-.8.400-.3.300-1.100 1.100-1.100 2.600s1.100 3 1.300 3.200c.2.200 2.200 3.400 5.400 4.700 3.200 1.300 3.200.9 3.800.8.600-.1 1.900-.8 2.200-1.500.3-.7.300-1.400.2-1.500-.1-.1-.3-.2-.6-.4z\"/></svg></a>" else ""

        val bg = if (s.dark) "#0b1020" else "#ffffff"; val fg = if (s.dark) "#e8ecf8" else "#111827"
        val card = if (s.dark) "#141b33" else "#f5f7fb"; val mut = if (s.dark) "#9aa6c7" else "#5b6478"

        val css = """
:root{--acc:$acc;--accd:$accDark;--bg:$bg;--fg:$fg;--card:$card;--mut:$mut}
*{box-sizing:border-box;margin:0;padding:0}
html{scroll-behavior:smooth}
body{font-family:system-ui,-apple-system,"Segoe UI","Noto Sans Devanagari",Roboto,Arial,sans-serif;background:var(--bg);color:var(--fg);line-height:1.6}
a{color:inherit;text-decoration:none}
header{position:sticky;top:0;z-index:20;display:flex;align-items:center;justify-content:space-between;padding:14px 20px;background:color-mix(in srgb,var(--bg) 88%,transparent);backdrop-filter:blur(10px);border-bottom:1px solid rgba(128,128,128,.18)}
.logo{font-weight:800;font-size:1.15rem;color:var(--acc)}
nav{display:flex;gap:18px;font-size:.95rem}
nav a{color:var(--mut)}nav a:hover{color:var(--acc)}
.menu{display:none;background:none;border:0;color:var(--fg);font-size:1.6rem;cursor:pointer}
.hero{padding:84px 20px 72px;text-align:center;background:radial-gradient(1200px 500px at 50% -10%,var(--acc),transparent 70%),linear-gradient(180deg,var(--accd),var(--bg) 92%);color:#fff}
.hero h1{font-size:clamp(2rem,7vw,3.6rem);font-weight:800;line-height:1.15;max-width:900px;margin:0 auto 14px}
.hero p{max-width:680px;margin:0 auto 26px;font-size:1.1rem;opacity:.92}
.btn{display:inline-block;padding:13px 24px;border-radius:999px;background:var(--acc);color:#fff;font-weight:700;margin:6px;box-shadow:0 8px 24px rgba(0,0,0,.25);transition:transform .15s}
.btn:hover{transform:translateY(-2px)}
.btn.wa{background:#25d366}.btn.ghost{background:transparent;border:2px solid var(--acc);color:var(--fg);box-shadow:none}
section{padding:64px 20px;max-width:1080px;margin:0 auto}
h2{font-size:clamp(1.5rem,4.5vw,2.2rem);margin-bottom:22px;text-align:center}
h2:after{content:"";display:block;width:56px;height:4px;border-radius:4px;background:var(--acc);margin:10px auto 0}
.about p{max-width:760px;margin:0 auto;text-align:center;color:var(--mut);font-size:1.08rem}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(230px,1fr));gap:18px}
.card{background:var(--card);border:1px solid rgba(128,128,128,.16);border-radius:18px;padding:24px;transition:transform .2s,box-shadow .2s}
.card:hover{transform:translateY(-4px);box-shadow:0 14px 34px rgba(0,0,0,.18)}
.card h3{margin:10px 0 6px}.card p{color:var(--mut)}
.ic{width:42px;height:42px;border-radius:12px;background:var(--acc);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:800}
.q{font-style:italic;margin-bottom:10px;color:var(--fg)!important}
.faq{max-width:760px;margin:0 auto}
details{background:var(--card);border-radius:14px;padding:14px 18px;margin-bottom:10px;border:1px solid rgba(128,128,128,.16)}
summary{cursor:pointer;font-weight:700}details p{margin-top:8px;color:var(--mut)}
.contact{text-align:center}.contact p{color:var(--mut);margin:6px 0}
footer{text-align:center;padding:28px 20px;color:var(--mut);font-size:.9rem;border-top:1px solid rgba(128,128,128,.18)}
.float{position:fixed;right:16px;bottom:16px;width:58px;height:58px;border-radius:50%;background:#25d366;display:flex;align-items:center;justify-content:center;box-shadow:0 8px 24px rgba(0,0,0,.35);z-index:30}
.reveal{opacity:0;transform:translateY(18px);transition:opacity .6s,transform .6s}.reveal.in{opacity:1;transform:none}
@media(max-width:700px){nav{display:none;position:absolute;top:100%;left:0;right:0;flex-direction:column;background:var(--bg);padding:14px 20px;border-bottom:1px solid rgba(128,128,128,.2)}nav.open{display:flex}.menu{display:block}.hero{padding:64px 16px 56px}}
"""
        val js = """
var m=document.querySelector('.menu'),n=document.querySelector('nav');
if(m){m.addEventListener('click',function(){n.classList.toggle('open')});n.addEventListener('click',function(){n.classList.remove('open')})}
var els=document.querySelectorAll('.reveal');
if('IntersectionObserver' in window){var io=new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting){e.target.classList.add('in');io.unobserve(e.target)}})},{threshold:.12});els.forEach(function(e){io.observe(e)})}else{els.forEach(function(e){e.classList.add('in')})}
"""
        val nav = buildString {
            append("<a href=\"#about\">${L("About", "Hamare baare mein")}</a><a href=\"#services\">${L("Services", "Services")}</a>")
            if (s.reviews.isNotEmpty()) append("<a href=\"#reviews\">${L("Reviews", "Reviews")}</a>")
            if (s.faqs.isNotEmpty()) append("<a href=\"#faq\">FAQ</a>")
            append("<a href=\"#contact\">${L("Contact", "Sampark")}</a>")
        }
        val html = """<!DOCTYPE html>
<html lang="${s.lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(s.name)} - ${esc(s.tagline.take(60))}</title>
<meta name="description" content="${esc(s.tagline)}">
<meta property="og:title" content="${esc(s.name)}">
<meta property="og:description" content="${esc(s.tagline)}">
<meta name="theme-color" content="$acc">
<link rel="stylesheet" href="style.css">
</head>
<body>
<header><a class="logo" href="#top">${esc(s.name)}</a><button class="menu" aria-label="Menu">&#9776;</button><nav>$nav</nav></header>
<main id="top">
<div class="hero"><h1>${esc(s.name)}</h1><p>${esc(s.tagline)}</p>$heroCta<a class="btn ghost" style="color:#fff;border-color:#fff" href="#services">${L("Our services", "Hamari services")}</a></div>
<section id="about" class="about"><h2>${L("About us", "Hamare baare mein")}</h2><p>${esc(s.about)}</p></section>
<section id="services"><h2>${L("What we offer", "Hum kya dete hain")}</h2><div class="grid">
$svc
</div></section>
$rev
$faq
<section id="contact" class="contact"><h2>${L("Contact us", "Sampark karein")}</h2>
$info
<div>$contactItems</div></section>
</main>
<footer>&copy; ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} ${esc(s.name)}. ${L("All rights reserved.", "Sabhi adhikar surakshit.")}</footer>
$waFloat
<script src="script.js"></script>
</body>
</html>
"""
        return mapOf("index.html" to html, "style.css" to css.trim(), "script.js" to js.trim())
    }
}
