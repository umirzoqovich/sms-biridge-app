# SMS Bridge Pro — Standalone Android SMS Gateway & CRM Bridge

Android tizimi uchun ishlab chiqilgan professional **SMS Gateway** ilovasi. Mahalliy yoki bulutli **Python CRM** serveri bilan ulanib, navbatdagi SMSlarni avtomatik ravishda SIM karta orqali tarqatadi va yetkazilganlik hisobotlarini serverga yuboradi.

---

## 🚀 Asosiy Imkoniyatlar

1. **Zamonaviy Emerald / Neon Green Dark UI (Web CRM dizayniga mos):**
   - Asosiy fon ranglari: `#090D16` va `#111827`, neon emerald urg'ular: `#10B981` va `#34D399`.
   - Barcha tugmalar va matnlar sof o'zbek tilida (lotin alifbosida).
   - Real-vaqtli ulanish indikatori: `● Ulangan` (yashil) / `● Uzilgan` (qizil).
   - Jonli statistika: `"Jami yuborilgan SMSlar"` kattalashtirilgan hisoblagichi.
   - Terminal/Konsol jurnali: So'nggi 15 ta SMS va ularning holati (`Yuborildi` / `Xatolik`).

2. **Ikki SIM kartali (Dual-SIM) boshqaruv:**
   - `SIM 1`, `SIM 2` yoki `Avtomatik` rejimlarini tanlash.
   - Android `SubscriptionManager` orqali har bir SIM kartaning `subscriptionId`si aniqlanib, kerakli slotdan yuboriladi.

3. **Uzun va Kirill / Maxsus belgili matnlarni qo'llab-quvvatlash:**
   - O'zbek tilidagi `o'`, `g'` yoki Kirill alifbosidagi xabarlar ko'pincha 70 belgidan oshadi.
   - Android `SmsManager.divideMessage()` va `sendMultipartTextMessage()` orqali xabarlar avtomatik ravishda bo'laklarga ajratilib, 100% ishonchli yetkaziladi.

4. **Doimiy Fon Xizmati (Foreground Service & WakeLock):**
   - `PowerManager.PARTIAL_WAKE_LOCK` ekranni o'chirganda ham protsessorni uyg'oq saqlaydi.
   - Persistent Notification bilan Android 14+ (`dataSync` foreground service type) talablariga to'liq javob beradi.
   - Samsung, Xiaomi, Oppo tizimlaridagi batareya cheklovlarini chetlab o'tish bo'yicha yo'riqnoma va ilova ichidagi tezkor sozlama.
   - `usesCleartextTraffic="true"` parametri orqali lokal `http://192.168.x.x:8000` ulanishlari to'siqlarsiz ishlaydi.

---

## 📡 API Protokoli

Ilova fon xizmati yoqilgan paytda har 3 soniyada serverga so'rov yuboradi:

### 1. Ulanishni tekshirish (Ping)
- **So'rov:** `GET /api/mobile/ping`
- **Javob:**
  ```json
  {
    "status": "ok",
    "message": "Python CRM Server faol",
    "server_time": "2026-11-15T23:35:00"
  }
  ```

### 2. SMS Navbatini so'rash (Poll)
- **So'rov:** `GET /api/mobile/poll?sim_slot=1`
- **Yangi task mavjud bo'lganda:**
  ```json
  {
    "has_task": true,
    "task_id": "101",
    "phone": "+998901234567",
    "message": "Hurmatli mijoz! Sizning buyurtmangiz tayyor.",
    "sim_slot": 1
  }
  ```
- **Navbat bo'sh bo'lganda:**
  ```json
  {
    "has_task": false,
    "wait_seconds": 3
  }
  ```

### 3. Natija haqida hisobot (Report)
SMS yuborilgandan so'ng darhol serverga holat yuboriladi:
- **So'rov:** `POST /api/mobile/report`
- **Body:**
  ```json
  {
    "task_id": "101",
    "phone": "+998901234567",
    "status": "sent",
    "error": ""
  }
  ```
  *(Agar xatolik yuz bersa: `"status": "failed"`, `"error": "Xatolik sababi"`)*

---

## 🛠️ Loyiha Tuzilishi

```
sms-bridge-app/
├── .github/
│   └── workflows/
│       └── build-apk.yml           # GitHub Actions avtomatik APK yig'uvchi CI
├── app/
│   ├── build.gradle                # Android ilova konfiguratsiyasi & kutubxonalar
│   ├── proguard-rules.pro          # Proguard qoidalari
│   └── src/main/
│       ├── AndroidManifest.xml     # Ruxsatnomalar & Foreground Service deklaratsiyasi
│       ├── java/uz/smsbridge/pro/
│       │   ├── data/
│       │   │   ├── model/Models.kt            # PollResponse, ReportRequest, SmsLogEntry
│       │   │   └── preferences/AppPreferences.kt # SharedPreferences & jurnallar
│       │   ├── network/CrmApiClient.kt        # OkHttp orqali Ping, Poll, Report
│       │   ├── sms/SmsSender.kt               # SmsManager, Multipart, Dual-SIM
│       │   ├── service/SmsPollingService.kt   # Foreground Service & WakeLock
│       │   ├── receiver/BootReceiver.kt       # Telefon o'chib yoqilganda tiklanish
│       │   ├── util/SmsBridgeEvents.kt        # Real-vaqtli UI sinxronizatsiyasi
│       │   ├── ui/MainActivity.kt             # Boshqaruv ekrani
│       │   └── SmsBridgeApplication.kt        # Notification Channel initsializatsiyasi
│       └── res/
│           ├── drawable/           # Emerald neon foni, tugmalar, konsol dizayni
│           ├── layout/activity_main.xml # Dashboard UI
│           └── values/             # Ranglar, mavzu va o'zbekcha matnlar
├── mock_server/
│   ├── crm_mock_server.py          # Python CRM test serveri (Web dashboard bilan)
│   └── test_crm_handler_direct.py  # Protokol test skripti
├── gradlew & gradlew.bat           # Gradle Wrapper
├── build.gradle & settings.gradle  # Root Gradle loyiha konfiguratsiyasi
└── README.md
```

---

## ⚡ Ishga Tushirish Yo'riqnomasi

### 1. Python CRM Mock Serverini ishga tushirish:
Lokal kompyuteringizda:
```bash
python mock_server/crm_mock_server.py
```
- Server ishga tushgach, brauzeringizda `http://localhost:8000` manzilini oching.
- U yerdan turib yangi SMSlarni navbatga qo'shishingiz va Android ilovadan kelayotgan hisobotlarni jonli ko'rishingiz mumkin!

### 2. APK ni Yig'ish (Build):

#### Usul A: GitHub Actions orqali (Tavsiya etiladi)
1. Ushbu loyihani GitHub omboringizga (repository) `push` qiling.
2. GitHub avtomatik ravishda `.github/workflows/build-apk.yml` orqali Ubuntu muhitida APK ni yig'adi.
3. GitHub omboringizdagi **Actions** bo'limidan tayyor `SMS-Bridge-Pro-Debug-APK` faylini yuklab olib, to'g'ridan-to'g'ri telefonga o'rnatasiz.

#### Usul B: Lokal kompyuterda (Android Studio yoki Terminal)
Agar kompyuteringizda JDK 17 o'rnatilgan bo'lsa:
```bash
./gradlew assembleDebug
```
Tayyor APK quyidagi manzilda hosil bo'ladi:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📱 Android Telefonda Sozlash va Ishlatish

1. **Ilovani o'rnating** va oching.
2. Kerakli ruxsatlarni tasdiqlang:
   - **SMS yuborish (SEND_SMS)**
   - **Telefon holati (READ_PHONE_STATE)**
   - **Bildirishnomalar (POST_NOTIFICATIONS)**
3. **Server URL manzilini kiriting**:
   - Masalan: `http://192.168.1.105:8000` *(Telefon va kompyuter bitta Wi-Fi tarmog'iga ulangan bo'lishi lozim)*.
4. **"Ulanishni tekshirish"** tugmasini bosing:
   - Server bilan aloqa tekshiriladi va tepada **"● Ulangan"** belgisi yonadi.
5. **SIM kartani tanlang** (SIM 1 yoki SIM 2).
6. Katta yashil **"Xizmatni yoqish"** tugmasini bosing:
   - Ilova fonda har 3 soniyada CRM serverga ulanib, SMSlarni avtomatik yuborishni boshlaydi.
   - Yuborilgan har bir SMS jurnali ekranning pastki qismidagi qora konsol maydonida vaqti bilan birga aks etadi.

### 🔋 Samsung va Xiaomi telefonlari uchun muhim eslatma:
- Ilovadagi **"Cheklovni o'chirish"** tugmasi orqali akkumulyator parametrlaridan `Cheklovlarsiz (Unrestricted)` rejimini tanlang.
- Xiaomi (MIUI/HyperOS) da `Avto-ishga tushirish (Autostart)` ruxsatini yoqing. Shunda telefon ekrani o'chirilgan holatda ham tizim xizmatni aslo to'xtatmaydi.
