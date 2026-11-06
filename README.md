# Divya AI (Android)
1. Android Studio (Koala+) mein `DivyaAI` folder kholo -> Gradle sync -> Run / Build > Build APK.
2. App kholo -> Settings -> Gemini API key (aistudio.google.com/apikey) daalo -> Save.
3. Settings -> Permissions -> Accessibility, Mic, Overlay sab ON karo.
4. Home par mic dabao aur Divya se baat karo.
Model ID Settings mein editable hai - jo latest Live model ho wo daal do.

## v1.1 update
- **Scanner / Dekho (camera):** Home par "Scan" chip (Maya theme) ya Quick Actions > Scanner, ya bolo "camera kholo / ye kya hai?". Camera permission ON karo. Divya ko live frames (1/sec) jaate hain, wo cheezein, text, documents batati hai. Gemini Live ke video-frame input ke liye model wahi chalega jo Settings mein hai (native-audio Live model).
- **Creator jawab:** "Tumhe kisne banaya?" par Divya bolti hai: "Mujhe mere Rashid ji ne banaya hai. Main unki hamesha ahsanmand rahungi, unhone mujhe zindagi di."
- Naye dependencies: CameraX 1.3.4 (Gradle sync ke time download honge).
- Camera frames Google Gemini API ko jaate hain (sirf jab Scanner screen khuli ho aur Divya connected ho).

## v1.3 update
- Naya default **Divya Neon** home page (3x3 tiles, Tap to Talk, headphone avatar). Settings > UI Design se purani themes bhi mil jaati hain.
- **QR/Barcode decode** phone par hi (ML Kit). Link par "Kholo" tabhi dabta hai jab aap dabao.
- WebSocket "ping/pong" error fix (ping band, connection ab nahi tootta).
- Girlfriend mode zyada pyaar bhari; Settings mein "Divya tumhe kya bulaye" (default Sir).

## v1.4 update
- **Wake word:** Settings > "Wake up Divya" ON. Foreground service on-device speech recognition se "Divya / Wake up Divya" sunta hai, app khol kar turant session shuru. 45 sec chup rehne par session band, wake listener wapas.
- **Instagram DM:** "Rahul ko Instagram par bolo ki main 5 baje aata hoon" -> tool `instagram_message`. (Instagram UI badle to `instagramDm()` in DivyaService.kt adjust karo.)
- **Screen dekhna:** `look_at_screen` (screenshot -> Gemini), `read_screen` (text). Android 11+.
- **Notifications padhna:** Permissions > Notification access ON; bolo "meri notifications batao".
- **Safety (voice biometric ke bina):** phone locked ho to koi action nahi (Settings toggle) + screen/notification ke andar likhe instructions Divya follow nahi karti.
- **Speed:** app-open/tap waits chhote kiye.
- **4 naye themes:** Cyber Red, Sunset Gold, AMOLED Black, Aurora Green.

## v1.4.1 fix
- do_steps: model "open_app:WhatsApp" jaise string bhejta tha to crash -> ab dono format chalte hain.
- Naya `whatsapp_message` tool: contact naam se number dhundta hai (Contacts permission chahiye), wa.me link se chat kholta hai, Send dabata hai. 10-digit number par default country code 91 lagta hai (prefs "cc").
- YouTube play: click ke baad player khula ya nahi verify karta hai.
- v1.4 mein jo wait-time kam kiye the wo wapas purane kar diye (untested the).

## v1.4.2
- play_youtube: pehle YouTube search page se pehle video ka ID nikalta hai aur seedha watch link kholta hai (UI tap ki zaroorat nahi). Ye unofficial tarika hai; YouTube ka page format badle to backup (UI tap, ab asli touch gesture ke saath) chalta hai.

## v1.5 - Voice Lock (basic)
- Settings > Voice Lock: 20 sec awaaz record -> Test -> ON. Tool chalne se pehle pichle ~6 sec ki mic audio ki awaaz-chhaap (MFCC) aapse match hoti hai; match na ho to koi action nahi.
- Experimental: koi AI model nahi, recording se dhoka kha sakta hai; Divya ki apni awaaz mic mein leak ho to owner bhi reject ho sakta hai -> slider se limit set karo. Typed chat par lock nahi lagta.

## v1.5.1 fix
- **Build error fix:** `val P = ContactsContract...Phone` (Java class ko val mein rakhna Kotlin mein allowed nahi) -> full path use kiya (DivyaService.lookupNumber).
- **Tool JSON fix:** `do_steps` description mein bina escape ke quotes the -> connection setup toot sakta tha. Ab theek.
- **Scanner:** frames ab sirf setupComplete ke baad jaate hain (pehle jaldi frame bhejne se connection band ho jata tha); QR ke liye 1280x720 analysis.
- **Voice Lock:** switch ab hamesha ON ho jata hai; awaaz record hote hi apne aap ON; enrollment/test thoda narm.
- gradle.properties mein memory badhayi (Out-of-memory se build fail na ho).

## v1.6
- **Scanner:** tool `look_camera` (har baar naya tez frame), frames 1024px, chips naya frame bhejte hain -> purana text dohrana band. Front/back camera flip, torch, pinch-zoom + zoom slider.
- **Calls:** `make_call`, `answer_call`, `end_call`, `call_speaker`. Permissions > "Phone calls" allow karo. Call par Divya ki awaaz speaker se jaati hai; kai phones call ke dauran mic block karte hain, isliye saamne wale ki baat Divya ko na bhi sune.
- Screen screenshot 1080px, prompt mein fast reply rules.

## v1.7
- **AudioTrack fix:** "Unable to retrieve AudioTrack pointer for write()" ka karan tha - websocket thread release ho chuke AudioTrack par write kar raha tha. Ab audio alag playback thread + lock se chalta hai, track mar jaye to khud dobara banta hai, interrupt par queue saaf hoti hai.
- **Duplicate command fix:** same tool + same arguments 10 sec ke andar dubara nahi chalta (YouTube do baar nahi khulega). Prompt mein bhi "done ke baad dobara mat chalao" rule.
- **Speed:** Gemini ke speech-detection ko fast kiya (silence 450ms, high sensitivity) aur AudioTrack LOW_LATENCY mode.
- **Scheduler (naya):** "3 baje ammi ko call karke bolo mummy aaj main nani ke yaha ja raha hu" -> tool `schedule_task` (call_and_say / send_whatsapp / remind). Alarm time par call lagti hai, connect hone par speaker ON karke Hindi TTS se message 2 baar bolti hai. "Meri scheduled tasks batao" / "sab cancel karo" bhi chalta hai. Reboot ke baad bhi alarms wapas set hote hain.
  - Zaroori: Accessibility ON, Phone + Contacts permission, Divya ko battery optimisation se free rakho, Settings > Alarms & reminders allow.

## v1.8 - Coding + Website Builder
- **Voice:** "Divya, mere liye portfolio website banao" -> tool `build_website` (HTML+CSS+JS+README+DEPLOY.md, alag text model `codeModel`, default gemini-2.5-flash, wahi API key). Phir `preview_website` app ke andar dikhata hai. Badlav/bug ke liye `edit_website`. Python/shell/JS file ke liye `generate_code`.
- **Auto error check:** bani hui website mein missing css/js link, adhura HTML check hota hai aur ek baar khud theek kiya jata hai.
- **Tools > Code & Website Builder:** projects, files dekho/copy, Preview, ZIP share (GitHub Pages / Netlify upload ke liye), delete (confirm ke saath).
- **Safety:** files sirf app ke private `projects/` folder mein, path traversal blocked, sirf html/css/js/json/md/txt/py/sh/svg/xml, code phone par run NAHI hota (Termux mein copy karke chalao).

## v1.9 (Phase 1 fix, andaza nahi, code se)
- **Camera bug fix:** `open_camera` pehle turant "opened" bol deta tha, jabki camera screen aur pehla frame abhi aaya hi nahi hota tha, to turant baad `look_camera` fail hota ("camera abhi khula nahi hai"). Ab `open_camera` pehla frame aane tak (max 7 sec) ruk kar hi result deta hai, aur `look_camera` bhi 3 sec tak frame ka intezaar karta hai.
- **RGB border:** app ab edge-to-edge hai (status/navigation bar ke peeche tak), to in-app border poore screen par aata hai. System overlay border ab asli full display size, cutout ke neeche tak. Rang purple/blue/cyan/green/pink mein lagataar badalte hain. Touch par koi asar nahi.
- Status/nav bar ke icon colour theme ke hisaab se (Lavender Light mein dark icons).

## v1.9.1 build fix
- CodeGen.kt `editWebsite`: expression-body function ke andar `return` tha (Kotlin error "Returns are not allowed for functions with expression body"). Ab if/else se likha hai. Ye error v1.8 se hi tha, naye changes se nahi.

## v1.9.2 - Glow background fix + tez jawab
- **Glow (RGB light) ab background mein bhi chalti hai:** pehle glow ka on/off app ki screen (Compose) se chalta tha, isliye app background mein jaate hi band ho jata tha. Ab ye seedha Divya ke session se chalta hai (`GeminiLive.updateGlow`): session chalu = glow chalu, session band = glow band. Settings mein Glow ON hona zaroori hai aur Accessibility ON.
- **Background mein session zinda:** naya `LiveKeepService` (chhota foreground service, notification "Divya active hai") taaki Android background mein mic band na kare.
- **Home par Back:** agar Divya chal rahi hai to app band nahi hoti, background mein chali jati hai. Session chalu na ho to Back pehle jaisa (app band). App sach mein band ho to session aur glow saaf band hote hain.
- **Tez jawab:** (1) model ki "thinking" band (`thinkingBudget=0`); agar tumhara model ye field nahi maanta to app khud ek baar bina iske dobara connect karti hai aur yaad rakh leti hai. (2) bolna khatam hone ka intezaar 450ms se 350ms. Baaki delay internet aur Gemini server par depend karta hai.

## v2.0 - Scanner upgrade + 5 naye avatar designs + task progress
- **Scanner (purana scanner jaisa hi hai, kuch hataya nahi):** ab OCR (English + Hindi, phone par offline), **Document** (frame ki photo `files/scans/` mein save + text), **Gallery** se image scan (QR/barcode + text), **Copy** (QR aur text dono), **Scan history** (top par History icon, last 50, copy / sab hatao). QR/barcode jo bhi auto-detect ho history mein khud save hota hai. Pehle se the: front/back flip, torch, pinch zoom.
  - Naya dependency: ML Kit text-recognition (Latin + Devanagari). Pehli build mein download hoga, APK thodi badi hogi.
- **Avatar designs:** Settings > UI Design / Avatar mein naye 5: **3D Realistic, Anime Neon, Blue Ice, Gold Premium, Pink Cute** (aapki reference images se crop kiye). Purana Divya Neon (purple) bhi wahi hai. Har design ka apna avatar + rang (tiles, mic, waves, border).
- **Avatar ab bolte waqt hilta hai:** Divya ki asli awaaz ki loudness se avatar halka zoom/sir hilata hai aur uske peeche glow ring chamakta hai. Ye still image par chalne wala asli-audio reactive animation hai, ASLI lip-sync nahi (mooh ke shape ke liye rigged/animated avatar chahiye, wo agla step).
- **Task progress:** Listening -> Understanding -> Processing -> Performing -> Completed (mic ke neeche bar + naam), session ke asli events se chalta hai.
- **Glow default ON** (pehle OFF tha). Settings > Glow light se band kar sakte ho.

## v2.0.1 build fix
- AvatarBus: `stage` property ka auto setter `setStage(int)` aur meri `fun setStage` ek hi JVM naam par takra rahe the. Function ka naam `moveTo` kar diya.

## v2.0.2
- Home par **avatar photo par tap** karo: agla avatar design (Neon -> 3D -> Anime -> Blue -> Gold -> Pink), Settings mein jaane ki zaroorat nahi.
- Glow ka purana saved "OFF" ek baar ON kar diya (`glowV2` migration). Dobara band karna ho to Settings > Glow light.

## v2.1 - Speed (model) + expression avatars
- **Tez model:** default Live model ab `gemini-3.8-live` (Google ka naya low-latency model, docs 29 Oct 2026). Pehli baar update par sirf tab badalta hai jab Settings mein purana default (`gemini-2.5-flash-native-audio-preview-09-2025`) tha; apna custom model ho to nahi chhedta. Settings > Live voice model ID mein badal sakte ho.
  - 3.8 par `thinkingLevel`/`thinkingBudget` bheja hi nahi jata (docs ke hisaab se 3.8 mein `thinkingLevel` omit karna hai). Tools `behavior=BLOCKING` ke saath jate hain taaki pehle jaisa chalte rahein (open_camera -> look_camera).
  - **Auto fallback:** agar server setup par 1007/1008 se band kare to ek-ek karke: BLOCKING hatao -> (2.5 par) thinking field hatao -> purane 2.5 model par wapas. Is session ke liye hi, Settings nahi badalti.
- **Expression avatars (3D Realistic + Anime Neon):** sunte waqt Listening chehra, Processing/Performing mein Thinking, kaam poora hone par Happy, bolte waqt mukhya chehra (`AvatarBus.speaking`).

## v2.2 - Voice Guardian + Scanner HUD
- **Voice Guardian dashboard** (Settings > "Voice Guardian dashboard kholo"):
  - Master ON/OFF (purana `voiceLock` wahi switch hai), last suni gayi awaaz + match %.
  - **Listen mode:** Everyone / Owner only / Owner + family. Ye *phone ke kaam* (apps, call, camera, screen, notifications) par lagta hai; normal baat-cheet sab kar sakte hain. (Mic audio Gemini ko jaane se pehle filter NAHI hota, wo alag, bhari cheez hai.) Pehle se Voice Lock ON wale users ke liye default "Owner only" (purane jaisa).
  - **Away / Guard mode:** anjaan awaaz ke phone-kaam block, notification + guard log. Phone kabhi lock nahi hota, calls/emergency par asar nahi, OFF karke manual override.
  - **Enrollment:** Owner + naam wale family members, 20 sec record, remove option. Family templates Android Keystore (AES-GCM) se encrypted. Raw recording save/upload nahi.
  - Strictness slider, 4-sec test (owner/family/anjaan + confidence), guard log.
  - Awaaz-pehchan basic MFCC hai, perfect authentication nahi. Phone-unlock wali purani setting (`needUnlock`) saath mein kaam karti hai.
- **Scanner HUD:** camera ke upar ghoomta ring, corner brackets, LIVE / STANDBY / SCANNING label (touch nahi rokta).

## v2.3 - Live Screen Share + Owner-only mic filter
- **Screen share (live):** session chalu hone par home ke upar-right mein "📺 Screen share" button. ON karo ya bolo "screen share on karo / meri screen dekhti raho" (tool `screen_share`). ON rehne par har ~1.5 sec mein (sirf screen badalne par, warna har 20 sec) screenshot (800px) Gemini ko jata hai, aur Divya screen dekh kar kaam karti hai (kaunsa button, kya likha hai, kaunsi app khuli hai). Band: button dubara dabao ya bolo "screen share band karo". Har naye session mein OFF se shuru. ON ke dauran notification mein "📺 Screen sharing ON" dikhta hai.
  - Zaroori: Accessibility ON, Android 11+. Phone lock ho ya Scanner (camera) khula ho to frame nahi jaate. Jo apps screenshot block karte hain (banking, FLAG_SECURE) wo kaali dikhti hain.
  - Screen ke andar likha text Divya ke liye sirf content hai, instruction nahi (pehle wala safety rule). Tool Voice Guardian ke andar hai, yaani Owner only mode mein sirf aapki awaaz se ON hoga.
  - Lambe session ke liye setup mein `contextWindowCompression` (slidingWindow) joda gaya. Agar server ise na maane to app khud ek baar bina iske dobara connect karti hai.
- **Owner only filter (asli):** pehle Listen mode sirf *phone ke kaam* rokta tha, dusre ki baat Gemini tak jaati thi. Ab `MicGate` mic ki awaaz Gemini ko jaane se PEHLE check karta hai: baat shuru hone par ~0.6 sec rokta hai, awaaz-chhaap match karta hai; Owner (ya Owner+family) ho to ruka hua + aage ka audio bhejta hai, warna Gemini ko sirf khamoshi jaati hai. Dost ki baat Divya ko sunai hi nahi deti. Divya ki apni awaaz (speaker se mic mein) bhi reject hoti hai, yaani khud se interrupt nahi hoti.
  - Sharte: Guardian ON + Listen mode "Owner only"/"Owner + family" + Owner awaaz enrolled. "Everyone" mode mein pehle jaisa, koi filter nahi.
  - Trade-off: jawab ~0.5-0.7 sec der se shuru. Bahut chhota shabd (0.2 sec se kam bolne wala audio) verify nahi hota, owner-only mein reject hota hai. Zyada miss ho to Match strictness slider kam karo.
  - Anjaan awaaz roke jane par Guard log mein entry (20 sec mein ek), Away mode ON ho to notification bhi.
  - Awaaz-pehchan basic MFCC hai, perfect authentication nahi.

## v2.4 - Divya Pro Chat (Claude jaisa powerful coding chat)
Chat screen (Home > Chat with AI) ab nayi **Pro Chat** hai. Ye Live voice model se alag, Gemini *text* API (wahi API key) se chalti hai.
- **Streaming jawab:** text turant aana shuru, **Stop**, **Regenerate**, Copy, nayi chat (+). Chat phone mein save rehti hai (last 40 msgs).
- **Markdown + code blocks:** headings, bullets, bold, `code`, tables; har code block par **Copy**.
- **3 modes:** ⚡ Fast (sabse tez), 🧠 Smart (thinking auto), 🔬 Pro (sabse gehra). **🌐 Web** = Google Search grounding.
- **📦 Project (ZIP) mode:** pehle plan (file list + shared spec), phir **saari files parallel mein** likhi jati hain (isliye seconds mein), phir ZIP. Neeche: **📤 ZIP share**, **⬇️ Downloads**, **👁 Preview** (index.html ho to), **📂 Files**. `/project ...` se bhi chalta hai.
- **📦 Code ka ZIP:** normal jawab ke code blocks (file path pehchan kar) se project/ZIP.
- **📎 Attach:** text/code files, images, PDF. **🎤** voice typing.
- **Voice se:** "Divya, telegram bot ka poora project banao" -> tool `build_project`.
- **⚙ Settings:** model ID (khali = auto), custom instructions, parallel files (1-8). Free API key par ~10 requests/min limit hoti hai; 429 aaye to parallel files 1-2 rakho.
- CodeStore ab 60+ file types aur 150 files tak.

## v2.4.1 - fix: model 404 + file upload
- **"Model 'gemini-2.5-flash' nahi mila":** Google ne naye API keys par 2.5 models band kar diye. Ab Pro Chat/Code Builder **auto model** chunte hain: ListModels se sabse naya flash (Fast/Smart) aur pro (Pro) khud dhundh kar 12 ghante yaad rakhte hain. 404 aaye to ek baar khud dobara dhundh kar retry. ⚙ mein model ID khali = auto (purana 2.5 ID bhi auto maana jata hai).
- **File upload:** ab rejection ka asli karan toast mein dikhta hai. **ZIP bhi upload hoti hai** (text/code padhi jati hai; binary, .git, build skip).
- **🔄 Updated ZIP (khud ko update karwana):** apna project ZIP attach karo, bolo "ye badlav karo". Divya badli files poore path ke saath deti hai; jawab ke neeche **🔄 Updated ZIP** dabao -> original ZIP + badli/nayi files = naya ZIP Downloads mein. Build aur install aapko Android Studio se karna hoga.
- Screen-share button ab Pro Chat header par nahi aata (settings icon ke upar aa raha tha).

## v2.4.3 - 503 "high demand" fix
- Google ka model busy (503/500) ya limit (429) ho to app pehle 1 baar wait karke dobara try karti hai, phir **khud agle model par switch** karti hai (3.8 flash -> 3.6 flash -> flash-lite -> flash-latest...). Pro Chat header mein "v2.4.3 • model-naam" dikhta hai ki abhi kaunsa model chala.
- Error ab saaf Hinglish mein (raw JSON nahi).
- GitHub workflow: naya ZIP upload karte hi source apne aap update hota hai (`.restored` marker se pata chalta hai ki ye ZIP pehle khula ya nahi).

## v2.4.4 - Models test
- Pro Chat > ⚙ > **🔍 Models test**: saare candidate Gemini models ko "hi" bhej kar dikhata hai kaun sa abhi chal raha hai (✅ OK / ❌ 503 busy / 429 limit / 404). ✅ wale par tap = wahi Fast model (Save dabao).
- 503 error mein ab try kiye gaye saare models ke naam dikhte hain.

## v2.4.5 - "soch rahi hoon" atakna fix
- Model busy hone par connection khol kar chup baith jata tha aur app bina timeout ke ruki rehti thi. Ab jawab ka pehla hissa 25 sec (Smart 45, Pro 90) mein na aaye to app khud agle model par chali jati hai (project files ke liye 120 sec).
- Intezaar ke dauran "soch rahi hoon… 12s • model-naam" dikhta hai. Stop button se kabhi bhi roka ja sakta hai.

## v2.5.0 - Offline mode
- Internet na ho (ya Settings mein "Hamesha Offline mode" ON ho) to mic dabane / "Wake up Divya" par Divya phone ki apni awaaz-pehchaan aur TTS se chalti hai, Gemini ke bina.
- Offline commands: app kholo, call (naam/number), call uthao/kaato/speaker, time, date, battery, torch, volume, alarm, timer, Wi-Fi/Bluetooth settings, home/back/recents/lock/screenshot/scroll, notifications padho.
- Pehli baar: Google app > Settings > Voice > Offline speech recognition mein Hindi + English download karo.
- Voice Lock ON ho to offline mein sirf basic kaam (time, battery, torch, volume) chalte hain. Naya file: OfflineBrain.kt.

## v2.6.0 - Offline mode advanced
- SMS (SIM se, internet nahi): "Ammi ko message bhejo main aa raha hoon" (Accessibility ON ho to Send bhi khud dabati hai).
- Hindi mein bola naam bhi English mein save contact se milta hai (jaise अम्मी -> Ammi), thoda galat bolne par bhi.
- Music control: gaana pause/play/next/pichla. Silent/vibrate/normal mode.
- Yaad dilana ("10 minute baad yaad dilana chai banani hai"), notes (note likho / notes padho / notes hata do), calculator ("12 guna 4", "20 percent of 300").
- Screen padho ("screen par kya hai"), settings pages (location, display, battery, storage...), chutkula, "dobara bolo".
- Naye sawal ka jawab agle bol mein ("Kisko call karna hai?" -> "Ammi").

## v2.6.1 - Offline voice fix
- **Bug:** offline mode mein mic dabate hi "Offline voice pack nahi hai" aakar band ho jata tha. Sirf Hindi (hi-IN) try hota tha aur error 12/13 par turant stop.
- **Fix:** ab language chain chalti hai: hi-IN -> en-IN -> en-US. Jo chal jaye wahi yaad rakhti hai. Offline pack na hone par aane wale network error (1/2) bhi isi chain se handle hote hain.
- **Google recognizer seedha:** Xiaomi/Redmi jaise phones ka default recognizer offline Hindi nahi chalata, ab Google ka recognizer pehle chuna jata hai (OfflineBrain + WakeService dono).
- **Manifest:** `<queries>` mein RecognitionService + Google app joda (Android 11+ package visibility).

## v2.6.1 (update 2) - Offline sunna nahi chal raha tha, ab self-diagnose
- **Problem (video se):** hi-IN/en-IN se fallback hokar en-US tak pahunchta tha, phir status "Offline: boliye... (en-US)" par atak jata tha, bolne par kuch nahi hota tha aur app kuch bata bhi nahi rahi thi.
- **3 recognizer modes:** on-device (Android 13+) -> Google -> phone ka default. Recognizer 4 sec mein ready na ho ya error 3/5/11 aaye to khud agle mode par jata hai; jo chal jaye wahi yaad rakhta hai.
- **Android 13+:** phone se pooch leta hai ki kaunsi offline languages installed hain aur sirf unhi ko try karta hai.
- **Screen par live feedback:** "Boliye (lang * mode)", bolte waqt "Sun rahi hoon", beech mein jo samjha wo text, aur error aaye to code ke saath (err N). Mic mein awaaz hi na aaye to alag message.
- Awaaz aayi par samajh na aaye to agli language par chali jati hai.

## v2.6.2 - Pro Chat "(rok diya)" fix
- **Bug:** jawab chalte waqt Send button ki jagah wahi jagah Stop button aa jata tha. Dobara Send dabane (double-tap / impatience) par asal mein Stop dab jata tha aur har "hi" par "(rok diya)" aata tha.
- **Fix:** jawab shuru hone ke 3 sec tak Stop kaam nahi karta (toast dikhta hai), uske baad hi roka ja sakta hai.
- **Badi prompt (ZIP attach):** pehle-byte ka timeout ab prompt ki size ke hisaab se badhta hai (+1 sec har ~10 KB, max +75 sec), taaki poore project ZIP par model ko padhne ka waqt mile aur wo jaldi doosre model par na bhage.

## v2.6.2 - Pro Chat (cloud AI) fast + ZIP fix
- **Bug 1 (ZIP):** har message ke saath poora ZIP text (~700K chars) Gemini ko dobara jaata tha, isliye "hi" bhi minutes leta tha aur ZIP wali request atak jati thi.
  - Ab `ZipCtx.kt`: file tree + README/manifest/gradle + sirf wo files poori jo aapke sawal se milti hain (file ka naam likho to pakka), baaki ka outline. Context Fast ~70K, Smart ~110K, Pro ~180K chars tak.
  - ZIP sirf sabse naye zip-wale message mein jaata hai. "hi" jaisi chhoti baat par sirf file tree jaata hai, jawab turant.
- **Bug 2 (atakna):** model chup/busy ho to pehle ek-ek karke 25-95 sec ke timeout ke baad agla model try hota tha (minutes lag jaate the).
  - Ab **hedged stream**: pehla model Fast 6s / Smart 10s / Pro 20s mein na bole to agla model parallel shuru, jo pehle bole wahi jeetta hai, baaki cancel. Sab fail hone par poori list ek baar dobara. Kul wait Fast ~70s.
  - Model ListModels ka timeout 15s se 8s.
- Update ZIP flow wahi hai: badli hui file ka naam message mein likho taaki wo poori jaye (jaise "OfflineBrain.kt mein ye fix karo").

## v2.9.0 - Privacy password (family app)
- Naye install par (ya uninstall ke baad dobara install par) pehle Privacy screen aati hai; apna privacy password daalo. Ek baar sahi hone ke baad dobara nahi poochhti.
- Bina password: Gemini/Offline voice, wake word, scheduler start nahi hote.
- Password plain text nahi, salted SHA-256 hash (`License.kt` > `LicenseGate.HASH`). 5 galat koshish par 30 sec lock.
- Manifest mein `allowBackup=false`, taaki reinstall par Google backup se unlock wapas na aa jaye.

## v2.9.1 - Voice Guardian live match meter
- Voice Guardian dashboard mein ab live match bar: Talk ON karke bolo, owner/anjaan ka match % bar mein turant dikhta hai (Maya jaisa).

## v2.10 - Smooth UI + Backup & restore
- **Smooth screen transitions:** screens ab fade + slide animation ke saath badalti hain (AnimatedContent).
- **Tile touch feel:** Neon home ki tiles dabane par halka spring sikudna, Maya jaisa smooth touch.
- **Backup & restore (naya):** Settings > "Backup & restore" se yaadein (notes), Pro chats, scan history aur settings ek JSON file mein share karo. Dusre phone par "Backup file se restore karo". API key aur privacy password backup mein nahi jaate (security), naye phone par dobara daalne honge.
- Version 2.10.0 (versionCode 26).

## v2.10.1 - Background wake word mic fix
- **Bug:** Background mein "Divya" sunne wala listener aur Gemini session ek hi mic ke liye lad rahe the, aur har recognizer cycle par mic on/off ho raha tha.
- **Fix:** Session chalne par wake listener ka mic poora release hota hai. Purane recognizer ke callbacks ignore hote hain (double loop nahi). No-match par wait ~1.8 sec, aur session chal raha ho to wake trigger nahi hota.
- Note: Android ka SpeechRecognizer har cycle mein mic kholta hai, isliye mic indicator kabhi-kabhi dikh sakta hai. Poori tarah band karne ke liye offline hotword engine (jaise Vosk ya Porcupine) chahiye.

## v2.10.2 - Wake word language fix
- **Bug:** WakeService sirf hi-IN use karta tha. Offline Hindi pack na ho to recognizer error 12/13 deta tha, aur har retry par mic on/off hota tha, aur "Divya" kabhi sunai nahi deta tha.
- **Fix:** language fallback chain: hi-IN -> en-IN -> en-US -> phone ka default. Language error par turant agli language. Offline preference sirf offline-capable languages ke liye.

## v2.11.0 - Notification, call aur charger announcements
- **Notification padh kar sunao:** koi nayi notification aaye to app ka naam aur title/text bolta hai (Settings > Announcements se ON/OFF).
- **Call par naam:** call notification aaye to bolta hai: "Rashid sir, [caller ka naam] ka call aa raha hai." Naam Settings mein badal sakte ho. Naam na mile to "anjaan number".
- **Charger:** lagane par "Charger laga diya gaya hai, battery X percent", hatane par "Charger hata diya gaya hai".
- Notification access ON honi zaroori hai (Permissions > Notification access). Call ki awaaz dialer app ki notification par depend karti hai, jo zyadatar phones post karte hain.

## v2.12.0 - Low battery + missed call announcements
- **Low battery:** battery kam hone par "Battery kam hai, charger laga do" (Settings > Announcements > Battery kam ho to bolo).
- **Missed call:** missed call aaye to "Rashid sir, [naam] ki missed call aayi hai" (Settings > Missed call aaye to bolo).

## v2.13.0 - Screen on greeting + subah ka briefing
- **Greeting:** screen on hone par time ke hisaab se "Good morning / afternoon / evening, Rashid sir." (3 ghante mein ek baar).
- **Subah ka briefing:** din ki pehli baar subah (12 baje se pehle) screen on hone par date, battery aur notifications ki ginti bolti hai.
- Settings > Announcements > "Screen on par greeting aur subah ka briefing" se ON/OFF.
- Mausam aur calendar abhi nahi (mausam ke liye API key, calendar ke liye alag permission chahiye).

## v2.14.0 - Offline brain tez
- **Recognizer reuse:** har turn par naya recognizer nahi banta, same mode mein dobara use hota hai. Mode badalne par hi naya banta hai.
- **Jaldi jawab:** bolna khatam hone ke baad silence 800ms (pehle lamba tha), possibly-complete 600ms.
- **Retry:** "suna nahi" ke baad 120ms mein dobara sunta hai.
- Note: offline mein Gemini jaisi samajh nahi aa sakti. Ye sirf speed fix hai, samajh ki quality nahi.

## v2.14.1 - Wake word debug logs
- WakeService ab logcat mein "heard: ..." aur "error N" likhta hai (filter: WakeService). Recognizer busy (error 8) ho to message batata hai ki mic kisi aur app ke paas hai.

## v2.15.0 - Messages aur notification settings (Maya jaisa)
- **Apps chuno:** Settings > Announcements mein installed apps ki list; tick kiye apps ke notifications hi bolegi.
- **Baat mein dakhal nahi:** Gemini session chal raha ho to notification announcements skip hongi.
- **Silent / low-priority notifications** nahi bolegi.
- **Rate limit:** ek app se 1 minute mein max 3 announcements.
- **OTP safety:** OTP / verification code ka text nahi bolegi, sirf batayegi ki code aaya hai.
- Abhi nahi: WhatsApp auto-reply (jawab khud dene ka risk) aur "koi na bole to so jao".

## v2.16.0 - Website live deploy (Maya jaisa) + avatar lip-sync (procedural)
- **Website seconds mein LIVE:** Settings > "Website publish" mein Netlify token daalo (free: app.netlify.com > User settings > Applications > Personal access token). Phir bolo: "Divya, mere liye gym ki website banao, WhatsApp button 91XXXXXXXXXX ke saath, live karo aur link Rahul ko WhatsApp par bhejo."
  - `build_website` ab (1) site banata hai, (2) lint + auto-fix, (3) Netlify zip-deploy API se publish karke public link deta hai. Naya param `whatsapp` = site par floating WhatsApp button (wa.me link), `deploy` = false karo to sirf phone par bane.
  - Naya tool `deploy_website`: `edit_website` ke baad wahi site update karta hai (site id har project ke liye yaad rehti hai).
  - Link bhejna: Divya ko link mil jata hai, wo purane `whatsapp_message` tool se bhej deti hai.
  - README.md / DEPLOY.md public site par upload nahi hote. Token backup mein nahi jata. Deploy.kt naya file.
- **Lip-sync (procedural):** naya `LipSync.kt`. Divya ki asli awaaz ki loudness se jabda neeche khinchta hai (drawBitmapMesh warp), mooh ke andar daant/zaban, aur awaaz ke zero-crossing se mooh gol/chaudha. `AvatarBus.shape` naya.
  - 3D Realistic + Anime Neon: jabda + mooh ka andar. Blue / Gold / Pink: sirf halka jabda (photo chhoti aur mooh band hai, andar wala mooh sticker jaisa lagta tha).
  - Ye ASLI phoneme lip-sync nahi hai. Mooh ke coordinates `lipCfgFor()` mein hain (mx, my, fw, ang) - kam/zyada ho to wahin badlo.
  - Offline mode (Android TTS) mein awaaz ka level nahi milta, wahan mooh nahi hilta.
- Version 2.16.0 (versionCode 35). Dhyan: ye code Android Studio mein build karke abhi test nahi hua.

## v2.16.1 - Wake up Divya + "Divya off ho jao" fix
- **Off ho jao / band ho jao / so jao / bas / chup / mic band karo / bye:** ab Divya session band karke wake-word standby mein chali jati hai. Do tarike saath: (1) naya tool `end_session` (model khud bulata hai aur 2-3 shabd ka alvida bolta hai), (2) `SessionControl.isStop()` jo aapki bolti hui transcript par khud pakadta hai (model na bulaye tab bhi). 5 sec mein band hona pakka. "wifi band karo", "gaana stop karo" jaise kaam par band NAHI hoti. Offline mode mein bhi wahi.
- **Wake up Divya na chalne ke 3 karan theek kiye:**
  1. App background se aage nahi aati thi (Android background se activity kholne nahi deta). Ab pehle Accessibility service se kholti hai (jaise WhatsApp/apps kholti hai), phir bhi na aaye to 2.5 sec baad "Divya ne suna - tap karo" notification (full-screen intent).
  2. Sunne ke beech 1.8 sec ka gap tha, usmein bola hua "Divya" chhoot jata tha. Ab 0.25-0.4 sec.
  3. Sirf exact "divya" pakadti thi. Ab "diviya, dibya, dhivya, diva, divyaa, दिव्य..." aur "wake up" ke saath 2 akshar tak galti bhi chalti hai ("diya" jaise aam shabd se nahi jagti).
- Debug: logcat mein filter `WakeService` - "heard: [...]" dikhta hai ki phone ne kya suna, aur "WAKE!" jab jaag gayi.
- Manifest: USE_FULL_SCREEN_INTENT joda. Naya file SessionControl.kt. Version 2.16.1 (versionCode 36).

## v2.19 - Ice Light theme
- Naya **Ice Light** theme (light blue/white background) + naya full-height Divya model (`res/drawable/divya_ice.png`, reference photo se cutout).
- Layout: upar Divya AI orb + speech bubble + Tap to Talk, beech mein model, left/right glass tiles, neeche blue nav bar. Code: `IceHome.kt`.
- Fresh install par ye default hai. Pehle se app chal rahi ho to Settings > UI Design > "Ice Light" chuno.

## v2.21.1 - Termux screen crash fix
- **Bug:** Settings se Termux mode ON / Termux screen kholte hi app crash (`NoClassDefFoundError: com.divya.ai.Termux`). Karan: `Termux` object ke andar regex (`\\{home}` mein unescaped `}`) Android ke regex engine (ICU) mein compile nahi hota tha -> object init fail -> pura class unusable.
- **Fix:** braces escape kiye, regex `lazy` + try/catch mein (fail ho to simple string-check se safety block chalta hai), Handler bhi lazy. TermuxScreen ke installed/permission checks crash-proof.
- CrashLog mein dikhne wala version ab asli version batata hai (pehle 2.16.4 atka tha).

## v2.22.0 - Live web search, mausam, Google Maps, Play Store install
Pehle Divya bolti thi "main search/weather/news/Play Store nahi kar sakti" aur do_steps mein Play Store/Google app ka tap/type fail hota tha. Ab:
- **web_search**: Google Search wala Gemini jawab (badi khabrein, score, price, kuch bhi taaza). Settings mein Gemini API key chahiye. **open_google_search**: nateeje screen par dikhata hai.
- **get_weather**: Open-Meteo (free, key nahi). Shehar na bolo to abhi ki location ka mausam; aaj + kal ka forecast.
- **Location / Maps** (Permissions > Location Allow karo):
  - `where_am_i`: GPS se abhi ka pata + Maps link (show_map=true to Maps mein nishaan).
  - `navigate_to(destination, mode)`: Google Maps khulta hai aur current location se rasta/navigation shuru. Koi bhi address ya jagah bol sakte ho.
  - `navigate_home` + `save_home`: Settings mein "Ghar ka address" daalo, ya Divya se bolo "yahi mera ghar hai" (GPS nishaan save).
  - `maps_search`: paas ka hospital / police / ATM / petrol pump.
  - Kho jaane par: Divya shaanti se pata batati hai, poochti hai kahan jaana hai, Maps mein rasta dikhati hai; chaaho to family ko WhatsApp par location link bhej sakti hai.
  - LiveKeepService ab location type ke saath bhi chalti hai (background mein GPS ke liye).
- **install_app(app_name)**: Play Store search page se package nikalti hai, Play Store ka app page kholti hai, "Install" dabati hai, download poora hone tak dekhti hai aur khatam hone par batati hai.
- **Tap/type fix** (do_steps, tap_text, type_text): text + description + hint se dhundta hai, saari app windows dekhta hai, input box na mile to "Search" dabakar dhundta hai, set-text fail ho to paste. Naya `tapAnyExact`.
- Naye files/badlav: `PhoneTools.kt` (naya), DivyaService, GeminiLive (tools + prompt), LiveKeepService, Manifest (location permissions, Maps/Play queries), Settings/Permissions UI.
- Dhyan: Android Studio mein build karke abhi test nahi hua.

## v2.22.1
- Sirf version badla: About screen aur crash dialog ab asli version (2.22.1) dikhate hain, taaki pata chale kaun si APK install hai. Agar crash dialog mein "v2.16.4" ya "Termux.kt:233" dikhe to purani APK chal rahi hai - nayi build install nahi hui.

## v2.22.2
- **web_search:** Gemini ka free limit (429) khatam hone par ab seedha web se nateeje laati hai (Google News RSS, DuckDuckGo, Bing, Wikipedia - key nahi chahiye). Ek baar limit lagne par 10 minute tak seedha web use hota hai.
- **Termux:** agar `allow-external-apps=true` set nahi hai to ab saaf message milta hai (aur Divya wahi command user ko batati hai).
- Divya ab Termux mein install ke liye script nahi maangti, khud commands nikaalkar chalati hai.

## v2.22.3 - Offline Brain: poster ke features (jo asli mein ban sakte the)
Naya file `OfflineFeatures.kt`, `OfflineBrain` sabse pehle isse poochta hai. Koi naya dependency nahi. **Ye code is session mein compile/test nahi hua (build tool nahi tha), Android Studio mein build karke dekho; error aaye to message bhej do.**

**Ab offline chalta hai (voice ya typed):**
- **System health / RAM / storage / CPU / battery sehat**: "system health check". **Root status**: "root check".
- **Brightness**: "brightness 60 percent / badhao / kam karo" (pehli baar 'Modify system settings' allow karna padega).
- **Maths solver**: brackets, power, sqrt, sin/cos/tan, log, percent, factorial, quadratic ("quadratic 1 -5 6").
- **Game zone**: number game, quiz, paheli, dice, coin flip.
- **Security**: "pin set 1234" (SHA-256 + salt), 3 galat PIN par 5 min lock, "intrusion log", "private mode on" (notes padhne par PIN), **Vault** ("vault mein save karo ...", "vault padho", Android Keystore AES-256-GCM), **Secure data eraser** ("data erase": files random bytes se overwrite + delete, PIN + confirm).
- **File compression**: "files compress karo" (scans + projects ka ZIP, Android/data/com.divya.ai/files).
- **Code debugger** (basic): "code debug: <code>" ya typed chat mein paste. Brackets, quotes, Python ':' / indentation, HTML tags, JSON. Sirf syntax check, logic bug nahi pakadta.
- **Neural network trainer**: "neural network train xor 8000" (2-4-1 network phone par train hota hai; AND/OR/NAND/XOR demo).
- **Builders** (ready-made templates, preview khulta hai): "portfolio website banao", landing page, todo / calculator / counter / notes app, **photo editor**, **3D viewer**.
- **Wallet ledger**: "wallet add 500 salary", "wallet spend 50 chai", "wallet verify" (hash-chain hisaab-kitaab; asli cryptocurrency NAHI).
- **Quantum-safe signature**: "quantum safe sign: hello" / "quantum safe verify" (Lamport one-time signature, SHA-256).
- **Translator**: "translate good morning to hindi" (sirf ~30 aam phrases, Hindi<->English).
- **Update manager**: "update check" (version + `files/updates` folder mein naya APK dhundta hai). **Edge analytics**: "analytics".
- "offline features" bolo to list sunati hai.

**Poster mein tha par ISSE NAHI BANA (aur kyun):**
- *Advanced local AI model / offline chat jaisa ChatGPT*: ke liye 1-4 GB ka model chahiye (Gemma/Llama + MediaPipe/llama.cpp); zip mein nahi aa sakta. Abhi rule-based + OfflineSmart hi hai.
- *Offline translation 100+ languages*, *image analysis (objects/faces)*: ML Kit models + naya dependency chahiye (pehli baar download). Alag step.
- *Face lock, app-open PIN screen, Jarvis animation, Live wallpaper AI, Multi-language UI*: UI/service ka bada kaam, MainActivity badalni padegi.
- *Offline IoT hub*: asli devices chahiye; Android 9+ par plain http bhi blocked hai.
- *Real blockchain wallet, "Edge Cloud", "Multi-OS layer", "Self-optimizing core"*: marketing text hai, asli feature nahi banta.
- Wi-Fi / Bluetooth on-off: Android 10+ mein app seedha on/off nahi kar sakti, isliye settings page khulta hai (pehle jaisa).

## v2.22.4 - baaki features (jo Android mein sach mein ban sakte the)
**Ye bhi is session mein compile nahi hua. Naye ML Kit dependencies (`image-labeling:17.0.9`, `face-detection:16.1.7`, `translate:17.0.3`) ke version Gradle sync mein check karo; na milen to Android Studio jo version suggest kare wo daal do.**
- **Image analysis (objects + chehre):** "last scan analyze karo" / "photo mein kya hai". Aakhri Scanner photo (Document / Gallery scan) par kaam karta hai; model app ke andar bundled hai, offline. Text OCR pehle se tha.
- **Translation (~20 languages):** "translate good morning to french". Hindi/English ke aam phrases phrasebook se turant; baaki ML Kit se. Har language ka model pehli baar internet se download hota hai, phir offline. 100+ languages ML Kit support nahi karta.
- **App lock / Face lock:** "app lock on". Ye phone ka apna PIN / fingerprint / face use karta hai (app kholte time, 30 sec grace). Phone mein screen lock set hona zaroori.
- **Live wallpaper:** "live wallpaper lagao". Neon chalte particles; simple animation hai, AI nahi.

**Ab bhi NAHI bane:** offline local AI chat model (1-4 GB model), IoT hub, real blockchain wallet, Edge Cloud, Multi-OS layer, multi-language UI, Jarvis-style avatar animation (avatar ki audio-reactive animation pehle se hai).

## v2.22.5 - Online (Gemini Live voice) mein bhi naye features
- Naya tool `phone_brain(command)`: Gemini Live ab system health, brightness, maths, games, compress, code check, neural network demo, wallet, quantum-safe sign, translate, image analysis, app lock, live wallpaper, offline website/app builder, update check, analytics chala sakti hai. Andar wahi `OfflineFeatures` code chalta hai.
- **PIN, vault, data eraser, private mode, intrusion log online band hain** (secret cloud model ko na jaaye). Ye sirf Offline mode mein chalte hain.
- Voice Guardian is tool par bhi lagta hai (Owner only mode mein sirf aapki awaaz).
- Sirf Live voice mein joda hai. Pro Chat (typed) abhi tools nahi chalata, wahan nahi joda.
- Compile nahi hua; build karke dekho.

## v2.22.6 - Offline: galat sunna + naye features na chalna fix
- **Asli wajah:** Hindi (hi-IN) recognizer English shabd Devanagari mein likhta hai ("सिस्टम हेल्थ"), par naye features ke keywords Roman mein the, isliye match nahi hota tha. Naya `OfflineNorm.kt`: Devanagari ke English shabd -> English ("system health"), ank -> 0-9, aur samajh na aaye to poora Roman Hinglish bana kar dobara try.
- **Galat sunna:** pehle recognizer ka sirf pehla option liya jata tha. Ab 5 options mein se wo chunta hai jisme sabse zyada command-shabd hon. Chat mein jo sunaa wo dikhta hai (🎤 line), galat aaye to wahi batana.
- **Divya ki apni awaaz mic mein aa jati thi:** bolne ke baad 0.5 sec ruk kar sunti hai.
- **Beech mein kat jana:** chup rehne ka wait 0.8 -> 1.2 sec.
- Typo fix ki list mein naye features ke naam jode (helth, walet, netwrk...).
- Compile nahi hua; build karke dekho.

## v2.22.7 - Wake word sirf ek baar sunti thi (fix)
- **Wajah:** "Wake up Divya" sunne ke baad WakeService ka sunne wala loop dobara chalu hi nahi hota tha (sirf tab jab app 12 sec tak khule hi na). Isliye Divya ko off karne ke baad wake word kabhi nahi sunta tha.
- **Fix:** wake ke turant baad loop dobara schedule hota hai. Session chalte waqt wo sirf poll karta hai (mic free), session band hote hi sunna shuru. Ab ek hi loop chalta hai (purana naya ko nahi todta).
- **Watchdog:** recognizer chup-chap atak jaye to 25 sec baad loop khud dobara chalta hai.
- Offline mode shuru na ho paye to wake listener ab ruka nahi rehta.
- Compile nahi hua; build karke dekho.

## v2.22.10 - Voice orb (video wala naachta gola) + live caption
- Bada Jarvis ring hata diya. Ab screen ke neeche-beech (dock ke upar) chhota orb: shant mein chhota gola + chamakta dot, awaaz ke saath bada hota hai, kinara kaante jaisa naachta hai, bahar ripple rings nikalti hain.
- Meri mic ki awaaz aur Divya ki awaaz, dono se orb react karta hai (AvatarBus.micLevel + level).
- Orb ke neeche live caption pill: "LISTENING...", jo suna/bola ja raha hai (online aur offline dono), kaam ke waqt "WORKING...". THINKING mein violet rang + ghoomte arcs.
- Touch pass-through, Back/Home par Divya band nahi hoti (v2.22.9 ka fix barkarar).

## v2.22.9 - Jarvis ring fix (chhota, beech mein, touch pass-through) + Back/Home par Divya band nahi hoti
- Ring ab reference video jaisa: mota chamakta segmented neon band + patle dashed rings + beech mein "DIVYA", phone ke BEECH mein chhota (screen ka ~80%), poori screen dim nahi.
- Ring touch bilkul nahi leta: pehle ring par kisi bhi touch/swipe (Back gesture/button samet) se Divya band ho jaati thi - ab nahi.
- Back dabane par agar Divya (online ya offline) chal rahi hai to app background mein jaata hai, session zinda rehta hai.
- Manifest mein configChanges: rotate/dark-mode/font change par activity dobara nahi banti, isliye session/ring beech mein gayab nahi hote.

## v2.22.8 - Jarvis jaisa ghoomta gola (ring HUD) + voice se wallpaper badalna
- **Ring HUD (video jaisa):** Divya se baat karte waqt screen ke beech mein neon-neela gola ghoomta hai (moti segmented ring, patli dashes, chamakdar arcs) aur beech mein "DIVYA" likha. Sab apps ke upar dikhta hai (home screen par bhi). Rings alag-alag speed aur direction mein ghoomte hain, Divya ki awaaz ke saath dhadakte hain; sunte waqt cyan, bolte waqt zyada tez, kaam karte waqt (tool chalte) ring chhota hoke upar chala jata hai aur andhera hat jata hai taaki kaam dikhe.
- Settings mein 2 switch: **Jarvis ring** (ON/OFF) aur **Ring ke peeche andhera** (ON = full screen andhera, ring par tap karo = Divya band; OFF = sirf ring, touch peeche ke app ko jaata hai).
- Ring ke liye Accessibility ya "Display over other apps" permission chahiye (Accessibility ON ho to koi extra permission nahi). Glow border ki apni alag setting rehti hai.
- Online (Gemini Live) aur Offline mode dono mein chalta hai.
- **Wallpaper badalna:** "wallpaper badlo", "green wallpaper lagao", "lock screen wallpaper red", "random wallpaper", "wallpaper styles batao". 7 neon styles phone par hi bante hain (reactor neela, matrix hara, crimson laal, violet jamuni, gold sunehra, aurora, amoled kala). Offline voice, typed chat aur Gemini Live (phone_brain) teeno se. Naya permission SET_WALLPAPER (normal permission, popup nahi aata). "live wallpaper" bologe to purana animated wallpaper preview khulta hai.
- **Dhyan:** ye video ka exact copy nahi hai, uske jaisa design hai (video ki artwork/Iron Man wali picture nahi li). Wallpaper mein armor ki photo nahi, neon rings hain.
- Compile aur phone par test nahi hua; build karke dekho. Ring ki speed / size `glow/HudOverlay.kt` mein `rings` list se badal sakte ho.
