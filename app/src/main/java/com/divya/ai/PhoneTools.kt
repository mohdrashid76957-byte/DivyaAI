package com.divya.ai

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * v2.22: web search / mausam / live location / Google Maps navigation / Play Store se app install.
 * Sab kuch seedha Android intents + API se (screen par type/tap karne par kam nirbhar), isliye pehle se zyada pakka.
 */
object PhoneTools {
    const val MAPS = "com.google.android.apps.maps"
    const val PLAY = "com.android.vending"

    private val http by lazy { OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build() }
    private fun prefs(c: Context) = c.getSharedPreferences("divya", 0)
    private fun nap(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) {} }

    private fun httpGet(url: String, ua: String = "DivyaAI/2.22 (Android)"): String? = try {
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).header("Accept-Language", "en-US,en;q=0.9").build())
            .execute().use { if (it.isSuccessful) it.body?.string() else null }
    } catch (_: Exception) { null }

    /** Activity shuru karo (Accessibility service se, taaki background se bhi khule - jaise open_app karta hai). */
    private fun launch(c: Context, i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { (DivyaService.instance ?: c).startActivity(i); true } catch (_: Exception) { false }
    }

    private fun appThere(c: Context, pkg: String) = try { c.packageManager.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }

    // ============================== WEB SEARCH ==============================

    @Volatile private var geminiSearchFailAt = 0L

    private fun plain(h: String): String = try {
        (if (Build.VERSION.SDK_INT >= 24) android.text.Html.fromHtml(h, android.text.Html.FROM_HTML_MODE_LEGACY) else android.text.Html.fromHtml(h)).toString().replace(Regex("\\s+"), " ").trim()
    } catch (_: Throwable) { h.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim() }

    private val UA_WEB = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private fun looksNews(q: String): Boolean {
        val t = q.lowercase()
        return listOf("news", "khabar", "khabre", "khabren", "headline", "samachar", "समाचार", "खबर").any { t.contains(it) }
    }

    /** Google News RSS: taaza headlines (key nahi chahiye). */
    private fun newsRss(q: String): List<String> {
        val generic = Regex("\\b(aaj|badi|bade|top|latest|taaza|breaking|india|desh|news|khabar|khabre|khabren|batao|bataiye|ki|ka|ke|kya|hai)\\b", RegexOption.IGNORE_CASE)
        val topic = q.replace(generic, " ").replace(Regex("\\s+"), " ").trim()
        val url = if (topic.length < 3) "https://news.google.com/rss?hl=hi&gl=IN&ceid=IN:hi"
        else "https://news.google.com/rss/search?q=" + Uri.encode(topic) + "&hl=hi&gl=IN&ceid=IN:hi"
        val x = httpGet(url, UA_WEB) ?: return emptyList()
        return Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(x).mapNotNull { m ->
            Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(m.groupValues[1])?.groupValues?.get(1)
        }.map { plain(it.replace("<![CDATA[", "").replace("]]>", "")) }.filter { it.isNotBlank() }.take(7).toList()
    }

    private fun ddg(q: String): List<String> {
        val h = httpGet("https://html.duckduckgo.com/html/?q=" + Uri.encode(q), UA_WEB) ?: return emptyList()
        val titles = Regex("class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(h).map { plain(it.groupValues[1]) }.toList()
        val snips = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(h).map { plain(it.groupValues[1]) }.toList()
        return titles.indices.take(6).map { i -> titles[i] + (if (i < snips.size) " - " + snips[i] else "") }
    }

    private fun bing(q: String): List<String> {
        val h = httpGet("https://www.bing.com/search?setlang=en&cc=IN&q=" + Uri.encode(q), UA_WEB) ?: return emptyList()
        return h.split("<li class=\"b_algo\"").drop(1).take(6).mapNotNull { ch ->
            val t = Regex("<h2[^>]*><a[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).find(ch)?.groupValues?.get(1)?.let { plain(it) } ?: return@mapNotNull null
            val sn = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(ch)?.groupValues?.get(1)?.let { plain(it) } ?: ""
            if (sn.isBlank()) t else "$t - $sn"
        }
    }

    private fun wiki(q: String): List<String> {
        val j = httpGet("https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=4&srsearch=" + Uri.encode(q)) ?: return emptyList()
        return try {
            val a = JSONObject(j).getJSONObject("query").getJSONArray("search")
            (0 until a.length()).map { i -> a.getJSONObject(i).let { plain(it.optString("title") + " - " + it.optString("snippet")) } }
        } catch (_: Exception) { emptyList() }
    }

    /** Gemini ka limit (429) / error aaye to bina key ke seedha web se nateeje (news RSS, DuckDuckGo, Bing, Wikipedia). */
    private fun fallbackSearch(q: String): String {
        var lines: List<String> = emptyList()
        if (looksNews(q)) lines = newsRss(q)
        if (lines.isEmpty()) lines = ddg(q)
        if (lines.isEmpty()) lines = bing(q)
        if (lines.isEmpty()) lines = wiki(q)
        if (lines.isEmpty()) return "failed: internet se koi nateeja nahi mila (net check karo)"
        return "done: Ye seedha web se mile nateeje hain (Gemini ka limit khatam tha). Inhe padhkar user ko simple Hinglish mein 3-5 chhoti lines mein batao, link mat bolo:\n" +
            lines.mapIndexed { i, l -> "${i + 1}) ${l.take(220)}" }.joinToString("\n")
    }

    /** Google Search se jude (grounded) Gemini jawab; limit/error par seedha web nateeje. Background thread par chalao. */
    fun webSearch(c: Context, query: String): String {
        if (query.isBlank()) return "failed: sawaal khali hai"
        if (System.currentTimeMillis() - geminiSearchFailAt > 10 * 60_000L) {
            try {
                val today = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH).format(java.util.Date())
                val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", query))))
                val sys = "You are a fast live-web research helper for a voice assistant in India. Today is $today (IST). Use Google Search and answer with the LATEST facts in simple Hinglish, " +
                    "maximum 6 short lines, plain text only (no markdown, no URLs, no asterisks). For news give the top 4-5 headlines with one short line each. If you could not find it, say so."
                val r = ProEngine.stream(c, sys, contents, 0, true, AtomicBoolean(false)) { }
                if (r.isNotBlank()) return "done: " + r.trim().take(1600)
            } catch (_: Throwable) { }
            geminiSearchFailAt = System.currentTimeMillis()   // 10 min tak Gemini search chhodke seedha web
        }
        return try { fallbackSearch(query) } catch (e: Throwable) { "failed: web search nahi chali: ${e.message}" }
    }

    /** Google search ke nateeje screen par dikhao (Google app / browser mein). */
    fun openGoogle(c: Context, query: String): String {
        if (query.isBlank()) return "failed: kya search karna hai?"
        val a = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (launch(c, a)) return "done: Google par '$query' ke nateeje screen par khul gaye"
        val b = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return if (launch(c, b)) "done: Google par '$query' ke nateeje khul gaye" else "failed: browser/Google nahi khula"
    }

    // ============================== MAUSAM ==============================

    private fun wmo(code: Int) = when (code) {
        0 -> "saaf aasmaan"; 1 -> "zyadatar saaf"; 2 -> "thode badal"; 3 -> "badal chhaye"
        45, 48 -> "kohra"
        51, 53, 55, 56, 57 -> "halki phuhaar"
        61, 63, 65, 66, 67 -> "baarish"
        71, 73, 75, 77 -> "barfbari"
        80, 81, 82 -> "tej baarish ki bauchhar"
        85, 86 -> "barfbari ki bauchhar"
        95, 96, 99 -> "bijli ke saath toofan"
        else -> "mausam badalta hua"
    }

    /** place khali ho to abhi ki location ka mausam. Open-Meteo (free, key nahi chahiye). */
    fun weather(c: Context, place: String): String {
        var lat: Double; var lng: Double; var name: String
        if (place.isBlank()) {
            val loc = currentLocation(c)
            if (loc != null) { lat = loc.latitude; lng = loc.longitude; name = "aapki current jagah" }
            else {
                val hl = prefs(c).getString("homeLat", "")?.toDoubleOrNull(); val hn = prefs(c).getString("homeLng", "")?.toDoubleOrNull()
                if (hl != null && hn != null) { lat = hl; lng = hn; name = "ghar ke paas" }
                else return "failed: location nahi mili. User se shehar ka naam poochho aur get_weather(place=shehar) chalao."
            }
        } else {
            val g = httpGet("https://geocoding-api.open-meteo.com/v1/search?name=" + Uri.encode(place) + "&count=1&language=en&format=json") ?: return "failed: internet ya mausam service nahi chali"
            val r = try { JSONObject(g).optJSONArray("results")?.optJSONObject(0) } catch (_: Exception) { null } ?: return "failed: '$place' naam ki jagah nahi mili"
            lat = r.optDouble("latitude"); lng = r.optDouble("longitude")
            name = listOf(r.optString("name"), r.optString("admin1")).filter { it.isNotBlank() }.joinToString(", ")
        }
        val u = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code&timezone=auto&forecast_days=2"
        val body = httpGet(u) ?: return "failed: mausam ka data nahi mila (internet check karo)"
        return try {
            val j = JSONObject(body)
            val cu = j.getJSONObject("current"); val d = j.getJSONObject("daily")
            fun day(i: Int) = "max ${d.getJSONArray("temperature_2m_max").optDouble(i).toInt()}°C / min ${d.getJSONArray("temperature_2m_min").optDouble(i).toInt()}°C, " +
                "baarish ki sambhavna ${d.getJSONArray("precipitation_probability_max").optInt(i)}%, ${wmo(d.getJSONArray("weather_code").optInt(i))}"
            "done: $name: abhi ${cu.optDouble("temperature_2m").toInt()}°C (mehsoos ${cu.optDouble("apparent_temperature").toInt()}°C), ${wmo(cu.optInt("weather_code"))}, " +
                "humidity ${cu.optInt("relative_humidity_2m")}%, hawa ${cu.optDouble("wind_speed_10m").toInt()} km/h. Aaj: ${day(0)}. Kal: ${day(1)}."
        } catch (e: Exception) { "failed: mausam ka data samajh nahi aaya: ${e.message}" }
    }

    // ============================== LOCATION ==============================

    fun hasLoc(c: Context) = c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Taaza location (GPS + network), na mile to sabse nayi purani location (30 min tak). Background thread par chalao. */
    @SuppressLint("MissingPermission")
    fun currentLocation(c: Context): Location? {
        if (!hasLoc(c)) return null
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provs = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { try { lm.isProviderEnabled(it) } catch (_: Exception) { false } }
        val got = CopyOnWriteArrayList<Location>()
        val done = AtomicInteger(0)
        val listeners = ArrayList<LocationListener>()
        for (p in provs) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    lm.getCurrentLocation(p, null, java.util.concurrent.Executor { r -> r.run() }) { l -> if (l != null) got.add(l); done.incrementAndGet() }
                } else {
                    val ls = object : LocationListener {
                        override fun onLocationChanged(l: Location) { got.add(l); done.incrementAndGet() }
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    listeners.add(ls)
                    lm.requestSingleUpdate(p, ls, Looper.getMainLooper())
                }
            } catch (_: Throwable) { done.incrementAndGet() }
        }
        val end = SystemClock.uptimeMillis() + 12_000
        var firstAt = 0L
        while (SystemClock.uptimeMillis() < end && done.get() < provs.size) {
            if (got.isNotEmpty()) { if (firstAt == 0L) firstAt = SystemClock.uptimeMillis(); if (SystemClock.uptimeMillis() - firstAt > 3000) break }
            nap(100)
        }
        for (ls in listeners) { try { lm.removeUpdates(ls) } catch (_: Throwable) {} }
        var best: Location? = got.minByOrNull { if (it.hasAccuracy()) it.accuracy else 9999f }
        if (best == null) {
            val now = System.currentTimeMillis()
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Throwable) { null } ?: continue
                if (now - l.time < 30 * 60_000L && (best == null || l.time > best.time)) best = l
            }
        }
        best?.let { prefs(c).edit().putString("lastLat", it.latitude.toString()).putString("lastLng", it.longitude.toString()).apply() }
        return best
    }

    /** Lat/lng se pata (Geocoder, na chale to OpenStreetMap). */
    fun addressOf(c: Context, lat: Double, lng: Double): String {
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val l = Geocoder(c, Locale("en", "IN")).getFromLocation(lat, lng, 1)
                if (!l.isNullOrEmpty()) {
                    val a = l[0]
                    val s = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
                    if (s.isNotBlank()) return s
                }
            }
        } catch (_: Throwable) {}
        val b = httpGet("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=en&lat=$lat&lon=$lng")
        return try { JSONObject(b ?: "").optString("display_name") } catch (_: Exception) { "" }
    }

    private fun locProblem(c: Context): String? {
        if (!hasLoc(c)) return "failed: Location permission nahi hai. User se bolo: app mein Settings > Permissions > 'Location' Allow kare."
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val on = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        if (!on) return "failed: phone ki Location (GPS) band hai. User se bolo notification panel se Location ON kare."
        return null
    }

    /** "Main kahan hoon" - pata + Maps link. showMap=true to Google Maps mein nishaan bhi dikhao. */
    fun whereAmI(c: Context, showMap: Boolean): String {
        locProblem(c)?.let { return it }
        val loc = currentLocation(c) ?: return "failed: location abhi nahi mili. User se kaho khuli jagah aaye aur GPS ON rakhe, phir dobara try karo."
        val addr = addressOf(c, loc.latitude, loc.longitude)
        val ll = "${loc.latitude},${loc.longitude}"
        if (showMap) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$ll?q=" + Uri.encode("$ll(Main yahan hoon)")))
            if (appThere(c, MAPS)) i.setPackage(MAPS)
            launch(c, i)
        }
        val age = ((System.currentTimeMillis() - loc.time) / 60000).toInt()
        return "done: abhi aap yahan hain: ${addr.ifBlank { "pata nahi mila, sirf coordinates" }} (lat/lng $ll, sahi hone ka andaza ~${loc.accuracy.toInt()} meter" +
            (if (age >= 2) ", location $age minute purani" else "") + "). Maps link: https://maps.google.com/?q=$ll" +
            (if (showMap) ". Maps mein nishaan dikha diya." else "")
    }

    // ============================== GHAR KA ADDRESS ==============================

    fun homeAddress(c: Context): String = prefs(c).getString("homeAddr", "")?.trim() ?: ""

    /** useHere=true: abhi ki location ko ghar bana do (sabse sahi). Warna bola hua address save. */
    fun saveHome(c: Context, address: String, useHere: Boolean): String {
        val p = prefs(c)
        if (useHere) {
            locProblem(c)?.let { return it }
            val loc = currentLocation(c) ?: return "failed: location nahi mili, ghar ka nishaan save nahi hua."
            val a = addressOf(c, loc.latitude, loc.longitude).ifBlank { address.trim() }
            p.edit().putString("homeAddr", a.ifBlank { "Mera ghar" }).putString("homeLat", loc.latitude.toString()).putString("homeLng", loc.longitude.toString()).apply()
            return "done: abhi ki jagah ko ghar ke roop mein save kar liya: $a"
        }
        if (address.isBlank()) return "failed: address bataiye"
        p.edit().putString("homeAddr", address.trim()).remove("homeLat").remove("homeLng").apply()
        return "done: ghar ka address save ho gaya: ${address.trim()}"
    }

    // ============================== GOOGLE MAPS ==============================

    private fun modeCode(m: String) = when (m.lowercase().trim()) {
        "walking", "walk", "paidal", "pedestrian" -> "w"
        "bicycling", "cycle", "bike", "cycling" -> "b"
        "two_wheeler", "scooter", "motorcycle", "bike_motor" -> "l"
        else -> "d"
    }

    /** Current location se destination tak Google Maps mein turn-by-turn rasta shuru karo. */
    fun navigate(c: Context, destination: String, mode: String): String {
        val dest = destination.trim()
        if (dest.isBlank()) return "failed: kahan jaana hai? jagah ya address bataiye"
        locProblem(c)?.let { return "$it (Maps ko bhi current location chahiye)" }
        val q = Uri.encode(dest)
        val hasMaps = appThere(c, MAPS)
        val transit = mode.equals("transit", true) || mode.equals("bus", true) || mode.equals("train", true)
        if (hasMaps && !transit) {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q&mode=${modeCode(mode)}")).setPackage(MAPS)
            if (launch(c, i)) { SystemClock.sleep(1500); return "done: Google Maps khul gaya aur aapki current location se '$dest' tak ka rasta (navigation) shuru ho gaya. User ko bolo screen par rasta dekhe; main bolkar bhi guide kar sakti hoon." }
        }
        val tm = when { transit -> "transit"; modeCode(mode) == "w" -> "walking"; modeCode(mode) == "b" -> "bicycling"; else -> "driving" }
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$q&travelmode=$tm"))
        if (hasMaps) i.setPackage(MAPS)
        return if (launch(c, i)) "done: '$dest' tak ka rasta ($tm) Maps mein khol diya. 'Start' dabane se navigation shuru hota hai." else "failed: Google Maps ya browser nahi khula"
    }

    fun navigateHome(c: Context, mode: String): String {
        val lat = prefs(c).getString("homeLat", "")?.toDoubleOrNull(); val lng = prefs(c).getString("homeLng", "")?.toDoubleOrNull()
        val addr = homeAddress(c)
        if (lat == null && addr.isBlank()) return "failed: ghar ka address abhi save nahi hai. User se address poochho aur save_home chalao (ya wo abhi ghar par ho to save_home(use_current_location=true))."
        val dest = if (lat != null && lng != null) "$lat,$lng" else addr
        val r = navigate(c, dest, mode)
        return if (r.startsWith("done")) "done: ghar ($addr) ka rasta Google Maps mein shuru ho gaya. User ko screen par rasta dikhao aur bolo ki rasta follow kare." else r
    }

    /** Paas ki jagah (hospital, ATM, petrol pump, police station...) Maps mein dikhao. */
    fun mapsSearch(c: Context, query: String): String {
        if (query.isBlank()) return "failed: kya dhoondhna hai?"
        val la = prefs(c).getString("lastLat", "")?.toDoubleOrNull(); val ln = prefs(c).getString("lastLng", "")?.toDoubleOrNull()
        val geo = if (la != null && ln != null) "geo:$la,$ln?q=" else "geo:0,0?q="
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(geo + Uri.encode(query)))
        if (appThere(c, MAPS)) i.setPackage(MAPS)
        return if (launch(c, i)) "done: Google Maps mein '$query' (aapke paas) dikha diya. User screen par list/pins dekh sakta hai; kisi par navigate karne ko bole to navigate_to chalao." else "failed: Maps nahi khula"
    }

    // ============================== PLAY STORE INSTALL ==============================

    /** Play Store ke search page se pehle app ka package naam nikalo. */
    private fun resolvePackage(q: String): String? {
        val html = httpGet("https://play.google.com/store/search?q=" + Uri.encode(q) + "&c=apps&hl=en&gl=IN",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36") ?: return null
        return Regex("/store/apps/details\\?id=([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)").find(html)?.groupValues?.get(1)
    }

    @Volatile private var installing = false
    @Volatile private var installCancelled = false
    @Volatile private var lastInstallPkg: String? = null
    @Volatile private var lastInstallAt = 0L

    /** Kya abhi/haal mein (30 min) koi install chala tha jise cancel kiya ja sake? */
    fun canCancelInstall(): Boolean = installing || (lastInstallPkg != null && System.currentTimeMillis() - lastInstallAt < 30 * 60_000)

    /** Install rokta hai: polling band + Play Store mein chalta download "Cancel" karta hai. */
    fun cancelInstall(c: Context): String {
        installCancelled = true
        val pkg = lastInstallPkg
        lastInstallPkg = null
        if (pkg == null || appThere(c, pkg)) return "done: koi chalta install nahi mila (ya app pehle se install hai)."
        val svc = DivyaService.instance
        if (svc == null) return "done: Divya ne install tracking band kar di. Accessibility OFF hai, isliye Play Store ka download khud Cancel karo."
        Thread {
            try {
                launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).setPackage(PLAY))
                svc.waitOpen(PLAY, 5000); SystemClock.sleep(1000)
                // download chal raha ho to Cancel dabao; confirm dialog aaye to usse bhi
                val hit = svc.tapAnyExact(listOf("Cancel", "CANCEL", "रद्द करें", "Stop"), 4000)
                if (hit != null) { SystemClock.sleep(600); svc.tapAnyExact(listOf("Cancel download", "OK", "Yes"), 1500) }
            } catch (_: Exception) {}
        }.start()
        return "done: install cancel kar diya, Play Store mein download rok rahi hoon."
    }

    /** Play Store khol kar app install karo. Lamba kaam: background thread par chalao. */
    fun installApp(c: Context, name: String): String {
        if (name.isBlank()) return "failed: kaun si app install karni hai?"
        val svc = DivyaService.instance ?: return "failed: Accessibility ON nahi hai (Settings > Permissions)"
        if (!appThere(c, PLAY)) return "failed: is phone mein Play Store nahi mila"
        if (installing) return "partial: ek install pehle se chal raha hai. Dobara install_app mat chalao; rokna ho to cancel_install chalao."
        installing = true; installCancelled = false
        try { return installInner(c, name, svc) } finally { installing = false }
    }

    private fun installInner(c: Context, name: String, svc: DivyaService): String {
        BgTasks.stage("App dhoondh rahi hoon…")
        val pkg = resolvePackage(name)
        if (installCancelled) return "done: install cancel ho gaya."
        if (pkg != null && appThere(c, pkg)) return "done: '$name' pehle se install hai (package $pkg). Chahe to open_app se khol sakti hoon."
        val i = if (pkg != null) Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        else Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(name) + "&c=apps"))
        i.setPackage(PLAY)
        if (!launch(c, i)) return "failed: Play Store khul nahi paaya"
        svc.waitOpen(PLAY, 6000)
        SystemClock.sleep(1200)
        if (pkg == null) return "partial: Play Store mein '$name' ka search khul gaya par exact app pakka nahi hua. read_screen se results dekho, sahi app par tap_text karo, phir install_app dobara mat chalao - 'Install' par tap_text karo. Screen: " + svc.readScreen(500)
        val page = svc.readScreen(260)
        if (installCancelled) return "done: install cancel ho gaya, Install nahi dabaya."
        // "Update" nahi dabate: wo purani app ko bina pooche update kar deta tha
        val hit = svc.tapAnyExact(listOf("Install", "INSTALL", "इंस्टॉल करें"), 9000)
        if (hit == null) {
            val s = svc.readScreen(700)
            return when {
                s.contains("Open", true) && appThere(c, pkg) -> "done: '$name' pehle se install hai."
                s.contains("Cancel", true) || s.contains("Pause", true) || s.contains("Installing", true) -> "partial: '$name' ka download pehle se chal raha hai ($pkg)."
                else -> "failed: Install button nahi mila (app paid / is desh mein available nahi / Google account sign-in chahiye ho sakta hai). Page: $page"
            }
        }
        lastInstallPkg = pkg; lastInstallAt = System.currentTimeMillis()
        BgTasks.stage("Download ho raha hai…")
        // Download poora hone tak (max 8 min) dekhte raho; cancel milte hi nikal jao
        val end = SystemClock.uptimeMillis() + 8 * 60_000
        while (SystemClock.uptimeMillis() < end) {
            if (installCancelled || BgTasks.isCancelled()) return "done: '$name' ka install cancel kar diya gaya."
            if (appThere(c, pkg)) return "done: '$name' install ho gayi ($pkg). User chahe to open_app se khol dein."
            nap(3000)
        }
        return "partial: Install dabaya aur download chal raha hai ($pkg) par 8 minute mein poora nahi hua (badi app ya slow net). Page: $page"
    }
}
