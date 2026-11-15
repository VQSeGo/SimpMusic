# CLAUDE.md — VQSeGo/SimpMusic fork

Личный форк SimpMusic (maxrave-dev), клиент YouTube Music на Kotlin / Compose Multiplatform.
Цель: Material 3 Expressive, YouTube Music напрямую (InnerTube, без своего сервера), Android + десктоп
(Linux в приоритете). GPL-3.0.

Полный справочник автора (архитектура плеера, кроссфейд, эквалайзер, Last.fm, лирика и т.д., ~300 КБ)
лежит в `docs/upstream-CLAUDE.md`. Читай его точечно, когда трогаешь соответствующую область.

## Как работать

- Отвечай по-русски.
- Работаем в ветке `redesign` (от `dev`), мелкими коммитами. В `dev` напрямую не коммитим.
- Сборку и запуск приложения запускать МОЖНО. Для проверки правок достаточно
  `./gradlew :composeApp:compileKotlinJvm --no-configuration-cache`.
- Сначала десктоп, потом те же правки на Android. Код общий (`commonMain`), десктоп-специфичное
  ветвится через `getPlatform() == Platform.Desktop`.
- Не возвращай то, от чего пользователь отказался (см. «Решения по дизайну»).

## Структура

- `composeApp/` — общий UI и логика (`commonMain`, `androidMain`, `jvmMain`).
- `androidApp/`, `desktopApp/` — точки входа и упаковка. `desktopApp` собирает AppImage через Conveyor.
- `core/` — git-сабмодуль (общие модули: `common`, `domain`, `data`, `media`, `service`).
  URL сабмодуля переопределён на форк `VQSeGo/core` в локальном `.git/config`; в `.gitmodules`
  указан форк.
- Слои: UI (`composeApp`) → `domain` → `data` → `service/*` → `common`. Нижний слой не знает о верхнем.
- Playback: Android — Media3/ExoPlayer (`core/media/media3`), десктоп — libmpv через JNA
  (`core/media/media-jvm`). Обработчики очереди и состояния плеера у платформ раздельные
  (`MediaServiceHandlerImpl` и `JvmMediaPlayerHandlerImpl`): баг в одном почти всегда есть в другом.

## Оригинал (maxrave-dev)

Синхронизацию с оригиналом прекратили 2026-11-07 на `87f38a49` (основной репозиторий) и `514216c` (`core`): всё до них у нас
есть. Форк разошёлся, больше половины их коммитов трогают наш интерфейс, поэтому:

- Основной репозиторий и `core` целиком не сливаем. Понравившуюся функцию переносим отдельно, как обычную задачу.
- Из `core` берём отдельные коммиты, только слой YouTube: `service/kotlinYtmusicScraper` (`cipher`, `extractor`, `visitor data`,
  `authuser`) и получение потока. Без них однажды пропадёт звук. Остальное не трогаем.
- Смотрим туда по поводу: перестало играть, слетел вход, пустая главная. Сначала проверяем, не починили ли это у них.
- В `core` есть remote `upstream` (локальный, только чтение): `git -C core fetch upstream multiplatform`, затем
  `git -C core log <наша база>..upstream/multiplatform -- service/kotlinYtmusicScraper` и `git cherry-pick <коммит>`.

## Сборка и запуск (Linux)

Нужны JDK 21 (подходит скачанный Gradle в `~/.gradle/jdks/eclipse_adoptium-21-*`; системный 27 не
используем), `ANDROID_HOME=~/Android/Sdk`, Conveyor в `PATH` (`~/.local/opt/conveyor/conveyor-22.0/bin`).

- Запуск с hot reload: `./gradlew :desktopApp:hotRunJvm --auto --no-configuration-cache`.
  Обычный `:desktopApp:run` окно не открывает. Koin-синглтоны (обработчик плеера) hot reload не
  подхватывает, после правок в `core` нужен перезапуск.
- AppImage одной командой: `scripts/build-appimage.sh`. Результат копируется в
  `~/TorvaldsVPN/Music/builds/SimpMusic-<ветка>-x86_64.AppImage` (папку можно сменить через `OUT_DIR`).
  Шаги вручную: `:composeApp:mpvSetupAll`, `:desktopApp:writeConveyorConfig`, затем
  `conveyor --agree-to-license=1 -Kapp.machines=linux.amd64.glibc make linux-app`, затем
  `:desktopApp:packageConveyorAppImage --no-configuration-cache`.
  Сырой результат: `desktopApp/build/appimage/conveyor/SimpMusic-x86_64.AppImage`.

## Релизы

`.github/workflows/release.yml`: кнопка Run workflow на вкладке Actions (спрашивает версию, тег создаётся сам) или тег `v*`
собирают APK, AppImage и установщик для Windows и выкладывают их в GitHub Releases форка. APK подписан тем же ключом, что и локальная сборка, поэтому встаёт поверх. Сборки оригинала удалены.

## Решения по дизайну (состояние форка)

- Now Playing: остался один стиль, M3 Expressive. Classic и Apple Music удалены. Лирика — всегда стиль
  Apple Music там, где работает размытие (Classic только запасной вариант для Android ниже 12).
- Десктоп: нижней панели с плеером нет. Правая панель Now Playing открыта, пока есть трек, без кнопки
  закрытия. В ней перемотка, кнопки воспроизведения и ряд shuffle / repeat / очередь.
  Инфо о треке и «в плейлист» — в меню `⋯`.
- Десктоп, левая панель: Home, Search, Settings, «+» для
  создания у заголовка библиотеки. Под заголовком фильтры (только если есть что фильтровать) и строка поиска
  с сортировкой. В шапке Home аватарки, «создать» и настроек нет, справа вверху кнопка обновления. Жеста
  pull-to-refresh на десктопе нет ни на одном экране (`TouchPullToRefreshBox`).
- Лирика (десктоп и Android): кнопок оценки и «поделиться» нет. Сердце одно, внизу слева, на бывшем месте кнопки
  информации; сама информация о треке в `⋯`. Смещение текста своё у каждого трека (таблица `lyrics_offset`).
- YouTube главный: приложением пользуются только с входом. Лайк один (сердце) и пишется в YouTube; `song.liked` лишь
  локальная копия, которую заменяет синхронизация с Liked Music. Подписки так же: `artist.followed` заменяется списком
  из аккаунта. Отдельных лайков SimpMusic, кнопки «плюс» и настроек зеркалирования нет. В библиотеке один пункт
  «Понравившаяся музыка». Альбомы и плейлисты пока локальные.
- Плеер: нажатие на название ведёт к альбому, на исполнителя — к нему или, если их несколько, в меню выбора. У клипа из
  радио альбома в очереди нет, его ищем поиском по названию и исполнителю. Вверху только то, что играет, без «Сейчас играет».
- Страница исполнителя показывает все альбомы и синглы (страницы за «Ещё»), видео остаются как есть. Экран альбома
  показывает всех исполнителей, выбор — то же меню, что в плеере.
- Spotify можно выбрать главным источником текстов, пока выполнен вход. Вкладка «Статистика» выключается в настройках.
- Android, Now Playing: ряд кнопок как на десктопе (shuffle / repeat / очередь), инфо о треке и «в плейлист» в
  меню `⋯`; вывод звука и комментариев в плеере нет. Нижний плеер, постоянная панель и регулятор громкости
  остаются десктопными. Шапка Home без аватарки и «+» (создание в библиотеке), всплывающих окон-просьб (отзыв, звезда, блог, тексты,
  промо-баннер) и экрана «Неофициальное приложение» нет.
- Отклонено пользователем: регулятор громкости, мини-плеер-окно, стоп, вывод звука и комментарии на
  десктопе. Эквалайзер с импортом AutoEq сохраняем.
- Удалено по решению пользователя (не возвращать): новости блога оригинала, уведомления о новых релизах
  подписанных исполнителей, экран уведомлений (на Android и десктопе) с пунктом в левой панели. Задания WorkManager
  «Artist Worker» и «Blog RSS Worker» больше не ставятся, `MainActivity` при запуске отменяет их у сборок, установленных
  поверх старой. Таблицы `notification` и `followed_artist_single_and_album` остаются в базе без кода, который в них пишет.
  Канал и разрешение на уведомления плеера и загрузок не тронуты.
  Также: «помочь собрать базу текстов» (отправка текстов и переводов на сервер SimpMusic, голосование за тексты,
  настройка и поля имени и почты участника, ключи DataStore `help_build_lyrics_database`, `contributor_name`,
  `contributor_email`). Чтение текстов с сервера SimpMusic как источника остаётся (`SimpMusicLyricsClient.getLyrics` /
  `getTranslatedLyrics`).
  Также: ИИ (перевод текстов через свой ключ Gemini / OpenAI / свой адрес) и карточка «вкуса» в библиотеке, которая
  писалась через него: модуль `core/service/aiService`, раздел «ИИ» в настройках, `TasteViewModel`, `TasteRepository`,
  ссылка `simpmusic://taste`, ключи DataStore `ai_provider`, `ai_gemini_api_key`, `use_ai_translation`, `custom_model_id`,
  `custom_openai_base_url`, `custom_openai_headers`, `ai_taste_profile`. Перевод текста берётся только у провайдеров
  текстов (язык перевода остаётся).
  Также: SponsorBlock (запрос к `sponsor.ajay.app` на каждый трек, перемотка по сегментам в обоих обработчиках плеера,
  группа в настройках, выбор категорий, тост «пропущен сегмент»; `StreamRepository.getSkipSegments`, `YouTube.getSkipSegments`,
  `SponsorBlockType`, `SponsorSkipSegments`, ключи DataStore `sponsor_block_enabled` и девять ключей категорий `sponsor`,
  `selfpromo`, `interaction`, `intro`, `outro`, `preview`, `music_offtopic`, `poi_highlight`, `filler`).
  Также: Last.fm (скробблинг в обоих обработчиках плеера, вход, группа в настройках, модули `lastfm` и `lastfm-empty`, поля
  `lastfmApiKey` / `lastfmSecret` в BuildKonfig, ключи `LASTFM_API_KEY` / `LASTFM_SECRET` в `local.properties`, схема
  `wordbyword://` в манифесте, Conveyor и `.desktop`; ключи DataStore `lastfm_session_key`, `lastfm_username`,
  `lastfm_scrobble_enabled`; `LoginSyncService.LASTFM`). Синхронизация входа игнорирует поле `lastfm` от старого телефона.
  Также: Spotify Canvas и анимированные обложки альбомов Apple Music (запрос на каждый трек, полноэкранный режим плеера
  с прячущимися кнопками, `AnimatedArtworkPage.kt` с сеткой цветов `AppleMusicMeshBackdrop`, ряд «Most played» с картинками
  canvas в библиотеке, кэш `CANVAS_CACHE` на Android; `getCanvas` в `SharedViewModel`,
  `LyricsCanvasRepository.getCanvas` / `getAMAnimatedArtwork`, `SongRepository.getCanvasSong`, ключи DataStore
  `spotify_canvas` и `am_animated_artwork`, запрос `canvaz` в `core/service/spotify`, поиск альбомов и треков в клиенте
  Apple Music). Spotify остаётся источником текстов. Туда же анимированный фон исполнителя и логотип-имя из Apple Music на
  странице исполнителя (`getArtistEditorial`, `ArtistMotion`, переключатель видео и картинки, цвет кнопок по логотипу,
  весь клиент Apple Music в `core/service/lyricsService` с токеном, моделями и выбором HLS-варианта, `MediaPlayerView` по
  ссылке на обеих платформах, `MpvPlayer.setPanscan` / `setLooping`). Страница исполнителя показывает статичную обложку и
  имя текстом. Столбцы `song.canvasUrl` и `song.canvasThumbUrl`, таблица `artist_motion` (`ArtistMotionEntity`) и столбцы
  `artist.nameLogoUrl` / `nameLogoColor` остаются в базе без кода, который в них пишет и читает.
  Также: режим диджея в кроссфейде (переключатель и ключ DataStore `crossfade_dj_mode`, свип НЧ/ВЧ-фильтров
  `CrossfadeFilterAudioProcessor` / `BiquadFilter` на Android и `MpvCrossfadeFilter` на десктопе) и AutoMix (длительность
  «Авто», подгонка темпа и тональности уходящего трека, пакет `domain/mediaservice/automix`, анализ структуры трека и файлы
  `.mix` рядом с рисунком волны). Простой кроссфейд оставлен: фиксированная длительность из `crossfade_duration`
  (сохранённое «Авто», 0, читается как 5 с), «не делать переход внутри альбома», рисунок волны (`.wf`) не тронут.
  Также: запрос метаданных TIDAL (темп и тональность, `searchTidalMetadata` в `getStream`, клиент и ключи DataStore;
  столбцы `bpm`, `music_key`, `keyScale` в `NewFormatEntity` остаются в базе, пишется `null`, в окне информации о треке
  их больше нет), загрузка `remote-config.json` из репозитория оригинала при запуске и промо из него (`Promo`,
  `LyricsPromoLine` под текстами, ключи DataStore `remote_promos`, `promo_shown`). «Итоги» и ссылка
  `simpmusic://library?tab=wrapped` остались. Запрос `registry/player_configs.json` (шифр YouTube) не тронут.
  Также: комментарии YouTube (код удалён полностью: `CommentsViewModel`, `CommentsBottomSheet`, иконка `CommentOutline`,
  `SongRepository.getComments` / `postComment` / `performCommentAction`, `YouTube.comments` / `createComment`, `CommentsPage`,
  модели и строки). Кнопки комментариев в плеере уже не было.
  Также: Google Cast (передача на Chromecast и колонки): модули `cast` и `cast-empty`, `CastHandoffManager`,
  `CastStreamResolver`, обёртка `wrapWithCastPlayer` основного плеера и маршрутизация на приёмник в
  `CrossfadeExoPlayerAdapter`, `GenericCastState` / `MediaPlayerHandler.castState` / `onCastStateChanged`, `CastButton`
  (`expect` и оба `actual`), иконка `MusicCast`, строки `audio_output_cast` и `not_available_while_casting` (настройки
  эквалайзера и кроссфейда больше не гаснут), зависимости `media3-cast`, `play-services-cast-framework`, `mediarouter`.
  `MAIN_PLAYER` в Koin теперь сам `ForwardingPlayer` адаптера, его же получает `MediaLibrarySession`.
  Также: выбор канала обновлений и ветка F-Droid (запрос к `f-droid.org`, `FdroidResponse`, `checkForFdroidUpdate`, настройка
  «Канал обновлений», ключ DataStore `update_channel`, строка `update_channel`). Обновления берутся только из релизов
  форка `VQSeGo/SimpMusic`; новее считается тег с большими числами, суффикс (`-1`, `-hf`) не в счёт, поэтому перед
  релизом поднимают `version-name` в `gradle/libs.versions.toml`.
  Также: раздел об авторе и пожертвованиях (строки настроек «Автор», «Следите за мной на X», «Блог разработчика», «Купи кофе»,
  те же ссылки и кнопки сайта, блога и спонсорства на `CreditScreen`; строковые ключи `author`, `maxrave_dev`, `follow_me_on_x`,
  `buy_me_a_coffee`, `developer_blog`, `developer_blog_tagline`, `donation`, `paypal`, `visit_blog`, `website`; строка автора в
  `EndOfPage`). Авторство оригинала (GPL-3.0) остаётся: строка `based_on_simpmusic` рядом с версией в настройках и на `CreditScreen`,
  там же юридический текст. Кнопка Discord «Visit SimpMusic» ведёт на репозиторий форка, GitHub и «Проблемы» на `CreditScreen` тоже.
  Также: распознавание музыки через Shazam (Android: кнопка микрофона в поиске и лист `SongRecognitionSheet`, `SongRecognizer`,
  `ShazamSignature`, `expect`/`actual` `SongRecognition`, плитка шторки `RecognitionTileService`, ссылка `simpmusic://recognize` и
  `SharedViewModel.requestSongRecognition`, иконка `GraphicEq`, строки `recognize_*` и `qs_tile_recognize_song`, разрешение
  `RECORD_AUDIO` и `uses-feature` микрофона: других пользователей записи звука нет).
  Также: импорт плейлистов из других приложений (строка «Импортировать плейлист» в группе «Резервная копия» и подпись со ссылкой
  на `simpmusic.org/tools`, диалог прогресса, `ImportViewModel`, `ImportRepository` / `ImportRepositoryImpl`, модели `ImportData`,
  `readPickedFile` на обеих платформах, `insertLocalPlaylistWithTracks` в DAO, девять строк `import_*`). Сохранение и восстановление
  резервной копии (`restore_your_data`, `restoreNative`, `filePickerResult`) остались.
  Также: подкасты (экран `PodcastScreen`, `PodcastViewModel`, `PodcastEpisodeItem`, `PodcastDestination` и его запись в
  `ListScreenGraph`, `PodcastRepository` / `PodcastRepositoryImpl`, `PodcastBrowse` и разборщики `parsePodcast*`, фильтр
  «Подкасты» в поиске (`SearchType.PODCASTS`, `searchPodcast`, `getSearchDataPodcast`, `YouTube.SearchFilter.FILTER_PODCAST`,
  `SearchResult.listPodcast`, `PodcastItem`), чип «Любимые подкасты» в библиотеке (`LibraryChipType.FAVORITE_PODCAST`,
  `LibraryItemType.FavoritePodcasts`) и подкасты в боковой панели десктопа (`LibrarySidebarKind.PODCAST`), полка подкастов в
  чартах (`Chart.podcasts`, `ChartPodcasts`), карточка подкаста в `HomeItem`, `PAGE_TYPE_PODCAST`; строки `podcast`, `podcasts`,
  `favorite_podcasts`, `no_favorite_podcasts`). Подкасты отбрасываются в общих разборщиках: `parseMixedContent` (главная и её
  файл-кэш: карточки-шоу `isPodcast`, строки-эпизоды `musicMultiRowListItemRenderer`, полка, где остались одни подкасты,
  убирается целиком), `parseChart` и страницы «Ещё» в `YouTube.kt` (`isPodcast`). `PodcastsEntity` и `EpisodeEntity`, таблицы
  `podcast_table` / `podcast_episode_table` и их методы в `DatabaseDao` остаются в базе без кода, который в них пишет и читает
  (`deleteUnfavoritedPodcasts` по-прежнему чистит старые строки). Эпизод подкаста, открытый по ссылке или из очереди, играет как
  обычное видео. Описания «видео вместо аудио» и «язык аудиодорожки» больше не упоминают подкасты (en, ru).
- Ещё не сделано: редизайн настроек.

## Приёмы и ловушки

- Память: на машине 30 ГБ и нет подкачки, рядом часто идут сборки других проектов. Лишний набор JVM однажды намертво
  повесил компьютер. Второй экземпляр приложения не запускать, `JAVA_TOOL_OPTIONS` вокруг Gradle не ставить,
  `gradlew --stop` не вызывать (остановит чужие демоны). Сборка одной ограниченной JVM:
  `systemd-run --user --scope -p MemoryMax=7G -p MemorySwapMax=0 nice -n 10 ./gradlew <задача> --no-daemon --max-workers=4 -Pkotlin.compiler.execution.strategy=in-process --no-configuration-cache`.
- mpv сообщает об обрыве потока с протухшей ссылкой как об обычном конце файла, а не об ошибке.
- Страница исполнителя и канал его кнопки подписки в YouTube Music обычно разные id. Подписка и отписка идут на канал
  кнопки (`YouTube.subscriptionChannelId`), `artist.channelId` — это id страницы.
- Пейджер обложек следует за страницей по ключу и может уйти с играющего трека, хотя индекс не менялся. Плеер главнее:
  `stayOnPlayerTrack` возвращает пейджер на место.
- Загрузки на десктопе живут в очереди `DownloadUtils`, а не в экране: `downloadTrack` только ставит трек в очередь, как на
  Android. Строки в состоянии «качается» возобновляются при запуске. Обрывы googlevideo лечит докачка кусками
  (`downloadInRanges`). Незаконченный файл лежит в `downloads/.partial` и переносится в `downloads/<id>.webm` целиком: плеер
  берёт файл по точному имени, любой файл с таким именем считается готовым.
- Базовый профиль Android (`androidApp/src/main/generated/baselineProfiles/baseline-prof.txt`, коммитится) пишет модуль
  `:baselineprofile`; нужен подключённый разблокированный телефон (API 33+). Перегенерировать после заметных правок UI:
  `ANDROID_SERIAL=<телефон> ./gradlew :androidApp:generateBaselineProfile` (через `systemd-run`, как выше), ~7 мин. Ставится
  копия `com.maxrave.simpmusic.bp` без входа рядом с рабочим приложением; Gradle сам её удаляет, проверьте `pm list packages`.
  Замер старта с профилем и без: `:baselineprofile:connectedBenchmarkReleaseAndroidTest`.
- Иконки — сгенерированные `ImageVector` в `ui/icon/` (`SimpIcons.X` + отдельный импорт на каждую).
  Как добавить новую: скилл `simpmusic-icons`.
- Строки только в `composeApp/src/commonMain/composeResources/values*/strings.xml`. Остальные языки
  ведёт Crowdin, добавляем английский и русский.
- Настройки, не требующие нового ключа в `core`, храним через `dataStoreManager.getString/putString`.
- Общие помощники, которые нужны в нескольких местах, выносим в отдельный файл, а не оставляем в файле
  удалённого стиля (пример: `ArtworkPagerLogic.kt`).
- Приватность: FOSS-сборка без трекинга; Sentry и Discord OAuth только при `isFullBuild=true`.
- Отладочный журнал: Настройки → «Функции разработчика» → «Отладочный журнал» (ключ `debug_logging`, по умолчанию вкл,
  действует сразу; `Logger.debugLogging` гасит `Logger.d`, curl, Ktor `Logging`, OkHttp). Секреты (cookie, `SAPISIDHASH`, `sp_dc`,
  токены) маскируются всегда (`LogRedaction` в `core/common`); дампы пишите `Logger.d(tag) { Logger.clip(x) }`.
- В `.claude/skills/` лежат скиллы kotlin-footguns (228 ловушек Kotlin/Compose/десктоп-JVM) и общие
  Android-скиллы. Перед правкой плеера, Room, StateFlow или сборки стоит открыть подходящий скилл;
  `footgun-scan` проверяет дифф.
