cuzdan adında bir "Bottom Navigation Views Activity" projesi açtım android studioda projeyi bunun üzerine kuracağız

İşte "Cüzdan" uygulamasını kusursuz yapacak o teknik ve UX/UI detayları:


1. Mimari ve Temel Altyapı
MVVM (Model-View-ViewModel) + Clean Architecture: Ekran tasarımı (XML) ile veri çekme (API) işini kesinlikle ayırıyoruz. ViewModel sadece veriyi hazırlar, XML sadece gösterir.

Dependency Injection (Hilt): Sınıfları birbirine manuel bağlamak yerine Hilt kullan. API servisleri, veritabanı (Room) gibi yapıları tek bir merkezden yönetmeni ve uygulamanın çok daha performanslı çalışmasını sağlar.

StateFlow / SharedFlow: Veri değişimlerini ekrana yansıtmak için eski LiveData yerine Kotlin'in modern yapısı Flow'ları kullanmalısın. Anlık veri takibi için çok daha etkilidir.


2. Veri Yönetimi ve Ağ (Network)
Single Source of Truth (SSOT) Prensibi: Kullanıcı uygulamayı açtığında ekran, veriyi her zaman Room Database'den (lokalden) okumalıdır. API'den gelen yeni veriler ekrana değil, önce veritabanına yazılır. Veritabanı güncellenince ekran otomatik güncellenir. Böylece internet kopsa bile kullanıcı asla boş ekran görmez.

OkHttp Interceptor & Retry Mechanism: Ücretsiz API'ler bazen "429 Too Many Requests" (Çok fazla istek attın) hatası verir. Araya bir Interceptor yazarak, istek başarısız olduğunda 2-3 saniye bekleyip arkadan çaktırmadan tekrar denemesini sağlamalısın.

Akıllı Caching (Önbellekleme): Borsalar hafta sonu kapalıdır. Cuma akşamki kapanış fiyatını hafta sonu boyunca API'ye sormanın mantığı yoktur. Uygulamaya "Bugün hafta sonuysa veya piyasa kapalı saatteyse API'ye gitme, elimdeki son veriyi göster" mantığını kurmalısın.


3. Finansal Matematik ve Güvenlik (Çok Kritik!)
Küsürat Hatalarına Son (BigDecimal): Finansal uygulamalarda asla Double veya Float değişken tipleri kullanılmaz. 0.1 + 0.2 yaparsın, sana 0.300000000004 verir, bakiyeler şaşar. Bütün para ve miktar değişkenlerini Java/Kotlin'in BigDecimal sınıfı ile tutmalısın.

Biyometrik Giriş (BiometricPrompt): Cüzdan uygulaması olduğu için, kullanıcı uygulamayı her açtığında (veya arka plandan öne aldığında) FaceID veya Parmak İzi istemelisin.

Güvenli Saklama (EncryptedSharedPreferences): Kullanıcının API limitlerine takılmamak için kendi API key'ini girmesine izin verebilirsin. Bu tarz hassas verileri normal ayarlarda değil, şifrelenmiş shared preferences içinde tutmalısın.

4. Kullanıcı Deneyimi (UI/UX) - XML Tarafı
ViewBinding: findViewById devri çoktan bitti. Ekran elemanlarına güvenli ve hızlı erişim için kesinlikle ViewBinding kullanıyoruz.

Shimmer Effect (İskelet Yükleme Ekranı): Uygulama açılıp API'den veri beklenirken ortada dönen sıkıcı bir yuvarlak (ProgressBar) yerine, attığın ekran görüntülerindeki listelerin gri renkte yanıp sönen silüetlerini göstermelisin (YouTube veya banka uygulamalarındaki gibi).

Swipe-to-Refresh: Kullanıcı listeyi aşağı doğru çektiğinde API isteklerini tetikleyen ve yukarıda dönen bir ikon çıkan klasik güncelleme animasyonu.

Dinamik Renklendirme: Kâr/Zarar durumuna göre yazı renklerinin değişmesi. Değer pozitifse yeşil (#00C853), negatifse kırmızı (#D50000). Ayrıca karanlık mod (Dark Mode) desteğini baştan XML values-night klasörü ile tasarlamalısın.

Para Birimi Formatlayıcı (NumberFormatter): 1500000 yazmak yerine, kullanıcının seçtiği dile göre 1.500.000,00 ₺ veya $1,500,000.00 şeklinde virgül ve noktaları otomatik ayıran bir Extension Function yazmalısın.

5. Ekstra "Vay Canına" Dedirtecek Detaylar
Pasta Grafik (Pie Chart): Ana sayfaya kullanıcının yatırımlarının dağılımını (Örn: %40 Kripto, %30 BIST, %30 Döviz) gösteren şık bir grafik eklemek. (Bunun için MPAndroidChart kütüphanesi harikadır).

WorkManager ile Arka Plan Senkronizasyonu: Kullanıcı uygulamayı hiç açmasa bile, günde 1 kez (mesela sabah 09:00'da) arka planda güncel kurları çekip lokal veritabanını güncelleyen bir servis.
		
					GÜVENLİK

1. Sırların Saklandığı Yer: local.properties
Android Studio'da yeni bir proje açtığında, projenin ana dizininde otomatik olarak local.properties adında bir dosya oluşur. Bu dosya, senin bilgisayarına özeldir.

API anahtarlarını, şifreleri veya gizli kalması gereken her türlü Base URL bilgisini bu dosyaya düz metin olarak yazarsın.

Örnek: BINANCE_API_KEY=senin_gizli_anahtarin_buraya_gelecek

2. GitHub'dan Gizleme: .gitignore
Android Studio, projeyi oluştururken bir .gitignore dosyası da hazırlar. Bu dosyanın amacı, GitHub'a gönderilmeyecek (görmezden gelinecek) dosyaları belirlemektir.

local.properties dosyası, varsayılan olarak bu .gitignore listesinin içindedir.

Böylece sen git push yapsan bile, GitHub'daki repoda bu dosya asla yer almaz. Kodun public olur ama şifrelerin senin bilgisayarında kalır.

3. Koda Güvenli Aktarım: Secrets Gradle Plugin
Şifreyi GitHub'dan sakladık, peki Kotlin kodunun içinde bu şifreyi nasıl kullanacaksın? Eskiden bu iş için uzun Gradle kodları yazılırdı ama artık Google'ın kendi eklentisi var.

Projene Secrets Gradle Plugin'i dahil edersin.

Bu eklenti, projeyi derlediğinde (build ettiğinde) arka planda otomatik olarak bir BuildConfig sınıfı oluşturur ve local.properties içindeki şifrelerini bu sınıfın içine gömer.

Kodun içinde API'ye istek atarken şifreni şu şekilde güvenle çağırırsın: BuildConfig.BINANCE_API_KEY

Cursor veya Antigravity gibi yapay zeka editörlerine "Projeme Google Secrets Gradle Plugin'i ekle" dediğinde, gerekli Gradle bağımlılıklarını saniyeler içinde senin için yapılandıracaklardır.

4. Play Store (APK/AAB) Güvenliği: ProGuard / R8
GitHub tarafını hallettik ama uygulamayı Play Store'a yüklediğinde, kötü niyetli kişiler APK dosyasını indirip "Tersine Mühendislik (Reverse Engineering)" yaparak kodlarını okumaya çalışabilir.

Bunu engellemek için Android'in yerleşik R8 (eski adıyla ProGuard) kod karmaşıklaştırıcı aracını aktif etmeliyiz.

Bu araç, sen uygulamayı yayınlamadan önce kodlarını (değişken isimlerini, sınıf adlarını) anlamsız harflere (a.b.c gibi) dönüştürür.

Uygulamanın build.gradle dosyasında isMinifyEnabled = true yaparak bu korumayı tek satırla açabilirsin. Bu sayede Play Store'dan uygulamanı indiren biri kodlarını deşifre edemez.

Offline First (Çevrimdışı Çalışma) Bildirimi: SSOT (Single Source of Truth) mantığını kurduk, harika. Ancak kullanıcı interneti kapalıyken uygulamaya girdiğinde, ekrandaki fiyatların anlık olmadığını bilmeli.

Eklenecek Not: "Eğer cihazda internet bağlantısı yoksa, ana ekranın üst kısmında zarif bir kırmızı banner ile 'Çevrimdışı mod: Son güncellenme [Tarih/Saat]' yazısı çıkmalı."

Hata Yönetimi (Error Handling) Katmanı: API'ler bazen çöker veya Yahoo Finance geçici olarak yanıt vermeyebilir.

Eklenecek Not: "Retrofit ile dönen hatalar (500 Server Error, 404 Not Found vb.) direkt olarak uygulamayı çökertmemeli. ViewModel'da bir Result veya Resource wrapper class (mühürlü sınıf / sealed class) oluşturularak hatalar yakalanmalı ve ekranda (örneğin Shimmer yerine) 'Veriler geçici olarak alınamıyor' şeklinde kullanıcı dostu bir mesaj gösterilmeli."

Ortalama Maliyet (Average Cost) ve Kar/Zarar Hesaplama: Kullanıcı cüzdanına sadece elindeki BTC miktarını girmez, aynı zamanda o BTC'yi hangi fiyattan aldığını da bilmek ister.

Eklenecek Not: "Room Database'deki Asset tablosuna average_buy_price (ortalama alış fiyatı) sütunu eklenmeli. Güncel API fiyatı ile kullanıcının alış fiyatı karşılaştırılarak, anlık Kâr/Zarar yüzdesi (%) ve miktarı ekranda dinamik renklerle gösterilmeli."

2. Şunu da Eklesek Güzel Olur (UX/UI Bonusları)
Gizlilik Modu (Privacy Mode): İnsanlar kalabalık ortamlarda veya toplu taşımada cüzdan uygulamalarını açarken bakiyelerinin görünmesinden çekinir.

Eklenecek Not: "Ana sayfadaki 'Toplam Bakiye' yazısının yanına bir göz ikonu (👁️) koyalım. Tıklandığında tüm rakamlar '***' (yıldız) şeklinde gizlensin. Bu durum SharedPreferences'da kaydedilsin ki uygulama bir sonraki açılışında da gizli gelsin."

Varlık Arama ve Filtreleme (Search Bar): Kullanıcının 30 farklı hissesi ve coini olduğunda listeyi kaydırarak aramak zorlaşır.

Eklenecek Not: "Attığın ekran görüntülerindeki gibi, her kategorinin (BIST, Kripto vb.) en üstüne bir arama çubuğu (SearchView) eklenmeli. Listeyi anlık olarak filtreleyebilmeli."


					API KEY

Kripto = Binance Public API
Kullanım: Sadece bir URL'e istek atarak (örneğin https://api.binance.com/api/v3/ticker/price?symbol=BTCUSDT) anlık fiyatı JSON olarak saniyesinde çekebilirsin.

Bist = Yahoo Finance API
Kullanım: Hisse kodunun sonuna .IS (Istanbul) ekleyerek istek atarsın. Örneğin THY için THYAO.IS, Sasa için SASA.IS.
Örnek Endpoint: https://query1.finance.yahoo.com/v8/finance/chart/THYAO.IS (Bu URL sana doğrudan güncel fiyatı ve yüzdelik değişimi JSON olarak verir).

Döviz = Yahoo Finance API
Kullanım (Yahoo): Dolar/TL için TRY=X, Euro/TL için EURTRY=X, Spot Altın (Ons) için GC=F kodlarını kullanırsın. Gram altın hesaplayacaksan; Ons fiyatını 31.1'e bölüp, çıkan sonucu güncel Dolar/TL kuruyla çarptırarak gram altın fiyatını uygulamanın içinde matematikle kendin bulursun (Mükemmel bir yazılımcı hilesidir).

Fon = TEFAS Web Scraping
Nasıl Yapılır? GitHub üzerinde Türk geliştiricilerin yazdığı ücretsiz açık kaynaklı "TEFAS API" projeleri var. Örneğin, TEFAS'ın kendi web sitesinin arka planda grafikleri çizmek için kullandığı gizli uç noktaları (endpoint) bulup oraya istek atabilirsin.
Tavsiye: Uygulamada fon fiyatını çekmek için günde sadece 1 kez istek atman yeterlidir. Sürekli güncellemene gerek yoktur.

Türk Lirası = API Gerektirmez
Nakit para dalgalanan bir kur olmadığı için bunu tamamen uygulamanın içindeki Room Database'e kaydedeceksin. Kullanıcı "Kasama 5000 TL ekle" diyecek ve bu değer direkt lokalde duracak.