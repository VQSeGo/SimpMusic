# План удаления лишних функций (форк VQSeGo)

Составлен 2026-11-12 по решениям пользователя, ветка `redesign` (`345efae5`, `core` `a46b4f6`). Код не менялся.
Каждая задача — одна сессия и читается отдельно. Перед любой прочитайте раздел 3.

**Статус сведений.** Списки файлов получены поиском по ключевым словам; сами файлы автор плана не читал. Исполнитель
начинает каждую задачу с повторного поиска (команда дана в задаче) и идёт по результату, а не по списку.

## 1. Решения пользователя

**Удалить:** новости блога; уведомления о новых релизах и экран уведомлений; ИИ (перевод текстов через свой ключ);
«помочь собрать базу текстов»; раздел об авторе и пожертвованиях; Last.fm; SponsorBlock; Spotify Canvas; запрос
метаданных TIDAL и режим диджея в кроссфейде; распознавание музыки; комментарии; импорт плейлистов из других
приложений; эффект «жидкое стекло»; подкасты; автоматические резервные копии; романизация текстов.

**Оставить:** Discord, совместное прослушивание, синхронизация входа, отправка данных о прослушивании в Google,
«Итоги» (Wrapped) и статистика, умные загрузки и автозагрузка лайков, реверберация, задержка звука, пропуск тишины,
прокси, виджеты, эквалайзер с AutoEq, Spotify как источник текстов, простой кроссфейд (плавное затухание без подбора),
ручное сохранение и восстановление резервной копии, строка «Основано на SimpMusic (maxrave-dev), GPL-3.0».

**Проверка обновлений** не удаляется, а переводится на репозиторий форка: `OPTIMIZATION_PLAN.md`, задача 6.

**Анимированные обложки:** пользователь пока не знает, что это. Сначала показать в работе, потом спросить (задача G).

## 2. Порядок

```
A → B → C → D → E → F → G → H → I → J → K → M → N → O → L
```

Порядок по соотношению пользы и риска: сначала то, что мешает (уведомления), затем то, что делает сетевые запросы на
каждый трек (E, F, G, H), в конце самое объёмное по числу файлов (L). Задачи независимы, порядок можно менять.

**Пересечения с другими планами:**

- E, F, G, H удаляют сетевые вызовы при смене трека из `MediaServiceHandlerImpl`, `JvmMediaPlayerHandlerImpl`,
  `SharedViewModel` и `StreamRepositoryImpl`. Те же места правят `OFFLINE_PLAN.md` (задачи 0, 2, 3) и
  `OPTIMIZATION_PLAN.md` (задача 1). Лучше сделать E–H раньше них: кода станет меньше. Если H сделана, пункт 3 задачи 2
  в `OFFLINE_PLAN.md` (таймаут TIDAL) отпадает.
- C и D правят тексты песен в `SharedViewModel` и `LyricsCanvasRepositoryImpl`: не вести одновременно с задачей 1
  `OPTIMIZATION_PLAN.md` и задачей 6 `OFFLINE_PLAN.md`.
- Все задачи с правками в `core` сдвигают указатель сабмодуля: одна сессия в `core` за раз.

## 3. Общие правила для исполнителя

- Ветка `redesign`, мелкие коммиты, ответы по-русски, подробности в `CLAUDE.md`. `core/` — сабмодуль: коммит в `core`,
  затем отдельный коммит со сдвигом указателя.
- Проверка: `./gradlew :composeApp:compileKotlinJvm --no-configuration-cache`. Для задач с Android-кодом дополнительно
  собрать Android через `systemd-run` с ограничением памяти (команда в `CLAUDE.md`).
- **Порядок удаления одной функции:** строка в настройках и входы в интерфейсе → экран и навигация → ViewModel и запись
  в `di/ViewModelModule.kt` → репозиторий и его запись в Koin → ключи DataStore → сервисный модуль и его строка в
  `settings.gradle.kts` и зависимостях → строки ресурсов. После каждого шага компилировать.
- **Строки:** удалять ключ из всех `composeApp/src/commonMain/composeResources/values*/strings.xml`
  (`grep -rl 'name="<ключ>"' composeApp/src/commonMain/composeResources`).
- **База данных не меняется.** Таблицы и столбцы Room, связанные с удалённой функцией, остаются: удаление требует
  миграции и может стоить пользователю данных. Удаляется только код, который в них пишет и читает.
- **Ключи DataStore:** удалить свойство из `DataStoreManager` (domain) и `DataStoreManagerImpl` (data). Сохранённые
  значения в файле настроек остаются и никому не мешают.
- Общие помощники, которые нужны оставшемуся коду, выносить в отдельный файл, а не удалять вместе с функцией.
- Сокращения: `APP` = `composeApp/src/commonMain/kotlin/com/maxrave/simpmusic`,
  `DATA` = `core/data/src/commonMain/kotlin/com/maxrave/data`, `DOMAIN` = `core/domain/src/commonMain/kotlin/com/maxrave/domain`,
  `ANDROID` = `androidApp/src/main/java/com/maxrave/simpmusic`.
- В конце каждой задачи: поиск из задачи не находит ничего, кроме оговорённого; приложение запускается на десктопе;
  играет трек; открываются настройки.

## 4. Задачи

### A. Новости, уведомления о релизах, экран уведомлений

**Платформа:** Android (фоновые задачи), общий код (экран). Причина: приложение присылает новости блога оригинала.

**Поиск:** `grep -rnE "RssFeedNotifyWork|NotifyWork|NotificationHandler|blogNotification|blog_notification|NotificationScreen|NotificationViewModel|showNotificationsEntry|show_notifications_entry" composeApp/src androidApp/src core --include=*.kt --include=*.xml | grep -v /build/`

**Где.** `ANDROID/MainActivity.kt:194-225` (постановка `NotifyWork` раз в 12 часов и `RssFeedNotifyWork` раз в сутки),
`ANDROID/service/rss/RssFeedNotifyWork.kt` (лента `https://www.simpmusic.org/rss.xml`),
`ANDROID/service/test/notification/NotifyWork.kt` и `NotificationHandler.kt`,
`APP/ui/screen/home/NotificationScreen.kt`, `APP/viewModel/NotificationViewModel.kt`,
`APP/ui/navigation/graph/HomeScreenGraph.kt`, `APP/App.kt`, `APP/viewModel/SharedViewModel.kt`,
настройки `blogNotificationEnabled` и `show_notifications_entry` в `SettingScreen.kt` / `SettingsViewModel.kt`,
пункт «Notifications» в левой панели десктопа (`APP/ui/component/DesktopSidebar.kt`).

**Что сделать.** Удалить оба фоновых задания и их классы, экран уведомлений с навигацией и ViewModel, пункт в левой
панели и обе настройки. В `MainActivity` вместо постановки заданий один раз отменить уже поставленные:
`WorkManager.getInstance(this).cancelUniqueWork(<имя>)` для обоих имён (имена взять из удаляемого кода) — иначе у
установленных поверх сборок задания останутся в системе и упадут на отсутствующем классе. Разрешение на уведомления и
каналы, нужные плееру и загрузкам, не трогать: проверить `AndroidManifest.xml` и оставить всё, что использует
`SimpleMediaService` и `MusicDownloadService`.

**Как проверить.** `adb shell dumpsys jobscheduler | grep simpmusic` после запуска новой сборки не показывает заданий
RSS и релизов. Уведомление плеера и уведомления загрузок работают. В левой панели десктопа нет пункта уведомлений.
Обновить `CLAUDE.md` («Десктоп, левая панель»).

**Что может сломаться.** Таблица уведомлений в базе остаётся (правило о базе). `CreditScreen.kt` упоминает блог —
уходит в задаче B.

---

### B. Раздел об авторе и пожертвованиях

**Платформа:** общий код.

**Поиск:** `grep -rnE "buy_me_a_coffee|follow_me_on_x|maxrave_dev|developer_blog|donation|CreditScreen" composeApp/src --include=*.kt | grep -v /build/`

**Где.** `APP/ui/screen/home/SettingScreen.kt` (раздел `about_us`), `APP/ui/screen/other/CreditScreen.kt`,
`APP/ui/navigation/graph/HomeScreenGraph.kt`.

**Что сделать.** Удалить строки настроек: автор, X, блог разработчика, пожертвования, «купи кофе», и `CreditScreen` с
маршрутом, если на нём не остаётся ничего, кроме этого. **Оставить:** версию, «сторонние библиотеки и лицензии»,
журнал приложения, проверку обновлений. Упоминание автора оригинала и лицензии GPL-3.0 должно остаться на экране
лицензий или в строке версии: форк GPL-кода обязан сохранять указание авторства. Если такого упоминания после удаления
не остаётся, добавить одну строку «Основано на SimpMusic (maxrave-dev), GPL-3.0» рядом с версией.

**Как проверить.** В настройках в разделе «О приложении» остались версия, обновления, лицензии, журнал.

---

### C. «Помочь собрать базу текстов»

**Платформа:** общий код. В журнале ПК от этой функции сотни ошибок 429 от сервера оригинала.

**Поиск:** `grep -rnE "helpBuildLyricsDatabase|help_build_lyrics|contributorName|contributorEmail|contributor_(name|email)|insertSimpMusicLyrics|Insert Lyrics" composeApp/src core --include=*.kt | grep -v /build/`

**Где.** Настройки (`SettingScreen.kt`, `SettingsViewModel.kt`), `APP/viewModel/SharedViewModel.kt` (около `:1149` и в
ветках загрузки текста), `DATA/repository/LyricsCanvasRepositoryImpl.kt`, `DOMAIN/repository/LyricsCanvasRepository.kt`,
ключи DataStore.

**Что сделать.** Удалить отправку текстов и переводов на сервер SimpMusic, настройку и поля участника. **Не удалять**
чтение текстов с этого сервера (провайдер SimpMusic как источник) без отдельного решения: это источник текстов, а не
сбор базы. Голосование за тексты, если оно есть в интерфейсе (`vote`), удалить вместе с отправкой.

**Как проверить.** При смене десяти треков в `~/.simpmusic/logs/error.log` нет строк `Insert Lyrics Error`. Тексты
по-прежнему находятся.

---

### D. ИИ

**Платформа:** общий код. Используется для перевода текстов через ключ пользователя (Gemini, OpenAI, свой адрес).

**Поиск:** `grep -rnE "aiService|AiClient|AiService|AiRepository|aiProvider|aiApiKey|customModelId|useAITranslation|use_ai_translation|aiCustomHeaders|custom_headers" composeApp/src core settings.gradle.kts core/settings.gradle.kts --include=*.kt --include=*.kts | grep -v /build/`

**Где.** Модуль `core/service/aiService`, `DATA/di/DatabaseModule.kt` (создание `AiClient`),
`DATA/repository/LyricsCanvasRepositoryImpl.kt`, `DATA/repository/CommonRepositoryImpl.kt` (подписка
`aiCustomHeadersJob` около `:296-308`), `DATA/repository/TasteRepositoryImpl.kt`, `APP/viewModel/SharedViewModel.kt`,
настройки (раздел `ai`).

**Что сделать.** Удалить раздел настроек, перевод текста через ИИ, клиент, модуль `aiService` из `settings.gradle.kts`
и зависимостей `core/data`. Обычный перевод (язык перевода, перевод от провайдеров текстов) оставить.
`TasteRepositoryImpl` использует ИИ для чего-то ещё: **сначала прочитать, что именно**, и если экран вкусов без ИИ не
работает, остановиться и спросить пользователя (экран вкусов в решениях не упоминался).

**Как проверить.** В настройках нет раздела ИИ. Текст с переводом от провайдера показывается. Сборка не содержит модуля
`aiService` (`./gradlew projects | grep -i ai` пусто).

---

### E. Last.fm

**Платформа:** общий код, только полная сборка (`isFullBuild`): корневые модули `lastfm` и `lastfm-empty`.

**Поиск:** `grep -rniE "lastfm" composeApp/src androidApp/src desktopApp core settings.gradle.kts gradle --include=*.kt --include=*.kts --include=*.xml --include=*.toml | grep -v "/build/\|/values-"`

**Где.** `DATA/lastfm/LastfmScrobbler.kt`, вызовы `lastfmScrobbler.onTrackStarted` в обоих обработчиках плеера
(`MediaServiceHandlerImpl.kt` около `:685` и `:712`, то же в `JvmMediaPlayerHandlerImpl.kt`),
`APP/ui/screen/login/LastfmLoginScreen.kt`, `APP/ui/navigation/destination/login/LastfmLoginDestination.kt`,
`LoginScreenGraph.kt`, `LogInViewModel.kt`, настройки, `DesktopDeepLinkHandler.kt`, `DesktopApp.kt`,
`AndroidManifest.xml` (ссылка возврата после входа), `SimpMusicApplication.kt`,
`DATA/loginsync/LoginSyncPayload.kt` и `LoginSyncStore.kt`.

**Что сделать.** Удалить скробблер и его вызовы, экран входа, настройки, обработку ссылки возврата, модули `lastfm` и
`lastfm-empty` с их строками в `settings.gradle.kts`. В синхронизации входа (она остаётся) поля Last.fm в
`LoginSyncPayload` сделать необязательными и игнорируемыми, а не удалять: старая версия приложения на втором
устройстве может прислать их, и разбор не должен падать.

**Как проверить.** Смена трека на обеих платформах: в журнале нет обращений к `audioscrobbler`. Синхронизация входа
между ПК и телефоном проходит. `DiscordOAuthLoginScreen.kt` попал в поиск: проверить, что там только общий код входа.

---

### F. SponsorBlock

**Платформа:** общий код. Запрос к `sponsor.ajay.app` на каждый трек; в журнале ПК 36 ошибок.

**Поиск:** `grep -rnE "sponsorBlock|SponsorBlock|getSkipSegments|SponsorSkipSegments|skipSegment|_skipSegments|sponsor_block" composeApp/src androidApp/src core --include=*.kt | grep -v /build/`

**Где.** Оба обработчика (`MediaServiceHandlerImpl.kt`: `getSkipSegments` `:753-769`, вызов `:744-746`,
`skipSegment` `:905-911`, `getSkipSegmentsJob`; то же в `JvmMediaPlayerHandlerImpl.kt`),
`DATA/repository/StreamRepositoryImpl.kt:377-386`, `DOMAIN/repository/StreamRepository.kt`,
`DOMAIN/mediaservice/handler/MediaPlayerHandler.kt` (поле `skipSegments`),
`DOMAIN/data/model/mediaService/SponsorSkipSegments.kt`, `DATA/mapping/Mapping.kt`, `core/common/.../Config.kt`,
`Ytmusic.kt` и `YouTube.kt` (запрос), `MpvPlayerAdapter.kt`, настройки и выбор категорий (`AllExt.kt`,
`ComposeResUtils.kt`), `MainActivity.kt`, `DesktopApp.kt`.

**Что сделать.** Удалить запрос, хранение сегментов, перемотку по сегментам, настройки и категории.

**Как проверить.** Десять треков подряд: в журнале нет `sponsor.ajay.app`. Перемотка и переход между треками работают.

**Что может сломаться.** Перемотка по сегментам вызывается из цикла обновления позиции: убедиться, что цикл остался
целым (`startProgressUpdate`).

---

### G. Spotify Canvas и анимированные обложки

**Платформа:** общий код. Spotify как источник текстов остаётся.

**Поиск:** `grep -rnE "getCanvas|CanvasEntity|canvasCache|CANVAS_CACHE|enable_canvas|spotifyCanvas|SpotifyCanvas|canvas_info|spotify_canvas_cache" composeApp/src core --include=*.kt | grep -v /build/`

**Где.** `APP/viewModel/SharedViewModel.kt` (`getCanvas` `:364-430`, вызов из подписки на трек около `:2041`),
`DATA/repository/LyricsCanvasRepositoryImpl.kt`, `DOMAIN/repository/LyricsCanvasRepository.kt`,
`core/service/spotify/.../Spotify.kt` и `SpotifyClient.kt` (метод canvas), Android: кэш `CANVAS_CACHE` в
`core/media/media3/.../di/Media3ServiceModule.kt` и `repository/CacheRepositoryImpl.kt`,
`core/media/media3-ui/.../MediaPlayerView.kt`, настройки (переключатель и строка размера кэша),
`SettingsViewModel.android.kt`.

**Сначала показать, потом резать (условие пользователя).** Анимированные обложки нельзя удалять, пока пользователь
не увидел их в работе и не сказал «удалять». Исполнитель до любых правок по обложкам:

1. Находит настройку (`enable_animated_artwork` в `SettingScreen.kt`) и говорит пользователю, где она в интерфейсе.
2. Просит включить её и запустить трек из популярного свежего альбома крупного исполнителя (у таких анимированная
   обложка в Apple Music есть чаще всего). Если у трека обложки нет, в журнале будет строка с тегом
   `AM_ARTWORK_TAG` и текстом `Not found <videoId>`; тогда подобрать другой трек, пока обложка не появится.
3. Объясняет простыми словами, что пользователь видит: вместо неподвижной картинки альбома в плеере крутится короткое
   зацикленное видео, которое приложение ищет в Apple Music по названию альбома и исполнителю при каждой смене трека.
4. Спрашивает: оставить или удалить. Без ответа «удалять» часть про анимированные обложки не выполняется; Canvas
   удаляется в любом случае.

Если пользователь решил оставить: из этой задачи выполняется только Canvas, `getCanvas` в `SharedViewModel`
сохраняется с одной веткой анимированных обложек, и стоит дать ей таймаут, чтобы поиск не висел без сети.

**Что сделать.** Удалить запрос и показ Canvas, его кэш и настройки. Вместе с ним (после ответа «удалять») удалить
анимированные обложки (раздел 5): `getAMAnimatedArtwork` (`LyricsCanvasRepositoryImpl.kt:248`), настройку `amAnimatedArtwork`
(`DataStoreManagerImpl.kt:808-819`), показ в `APP/ui/screen/player/content/NowPlayingContentM3Expressive.kt`,
`NowPlayingExpressiveCards.kt`, `NowPlayingContentState.kt`. Поиск:
`grep -rnE "AMAnimatedArtwork|amAnimatedArtwork|animatedArtwork|AnimatedArtwork|enable_animated_artwork" composeApp/src core --include=*.kt | grep -v /build/`.
Файл `AnimatedArtworkPage.kt` по `CLAUDE.md` содержит общие помощники плеера: то, чем пользуется оставшийся код,
перенести в файл с нейтральным именем, остальное удалить. После этого функция `getCanvas` в `SharedViewModel` и её
вызов при смене трека удаляются целиком.

**Как проверить.** Плеер показывает обложку; при смене трека нет запросов canvas к Spotify; текст из Spotify приходит.

---

### H. Метаданные TIDAL и режим диджея

**Платформа:** общий код. Решение пользователя: режим диджея убрать (склеивает несочетаемые треки), простой кроссфейд
оставить; умный вариант, возможно, когда-нибудь потом.

**Поиск:** `grep -rnE "searchTidalMetadata|getTidalRemoteConfig|tidalRemoteConfig|TIDAL_|tidalClient|getTidalOAuthToken|crossfadeDjMode|crossfade_dj_mode|DjMode|AutoMix|audioMetaCache|TransitionPlanner|TrackStructure" composeApp/src core --include=*.kt | grep -v /build/`

**Где.**

- TIDAL: `DATA/repository/StreamRepositoryImpl.kt:202-232` (поиск темпа и тональности перед выдачей ссылки на поток),
  `DATA/repository/CommonRepositoryImpl.kt:200-226` (загрузка `remote-config.json` с учётными данными TIDAL из
  репозитория оригинала при каждом запуске), `Ytmusic.kt` (`getTidalOAuthToken` `:1272`, `getTidalRemoteConfig` `:1308`),
  `YouTube.kt:2298`, `models/response/RemoteConfig.kt`, ключи DataStore.
- Режим диджея: `DOMAIN/mediaservice/automix/` (`TrackStructureAnalyzer.kt`, `TrackStructure.kt`, `TransitionPlanner.kt`),
  ветки AutoMix в `core/media/media3/.../exoplayer/CrossfadeExoPlayerAdapter.kt` (раздел «AutoMix Internal Logic» от
  `:2632`, `audioMetaCache`, `structureRetryAt`, фоновое измерение треков `:2556-2590`) и в
  `core/media/media-jvm/.../mpv/MpvPlayerAdapter.kt` (`:274`, `:2534-2590`), метод `structure` в
  `core/media/media3/.../waveform/WaveformReader.kt` и `MpvWaveformReader.kt` (файлы `.mix` в `~/.simpmusic/waveforms`),
  настройка `crossfade_dj_mode` в `SettingScreen.kt` / `SettingsViewModel.kt` / DataStore.

**Что сделать.** Два коммита.

1. TIDAL: удалить вызов `searchTidalMetadata` из `getStream`, загрузку `remote-config.json` при запуске, клиентский код
   TIDAL и ключи. Поля `bpm`, `musicKey`, `keyScale` в `NewFormatEntity` оставить (правило о базе), писать `null`.
2. Режим диджея: удалить настройку, пакет `automix`, ветки AutoMix в обоих адаптерах и анализ структуры трека.
   Кроссфейд должен остаться в том виде, в каком он работает при выключенном режиме диджея: фиксированная длительность
   из настройки `crossfade_duration`. Настройку «не делать переход внутри альбома» (`crossfade_skip_album`) оставить.
   Сам рисунок волны для перемотки (`WaveformReader.read`, файлы `.wf`) **не удалять**: он используется плеером
   независимо от диджея — проверить по вызовам `loadWaveform`.

**Как проверить.** Запуск приложения: нет запроса к `raw.githubusercontent.com/maxrave-dev/simpmusic-files`. Запуск
трека: нет запросов к TIDAL. Кроссфейд включён: переход между двумя треками плавный, заданной длительности, на обеих
платформах; выключен: переход без наложения. Полоса перемотки с волной рисуется. В настройках нет режима диджея.

**Что может сломаться.** Адаптеры большие (по 3,4 тысячи строк), логика диджея переплетена с обычным кроссфейдом:
удалять ветками, компилируя после каждой, а не одним проходом. У пользователей с включённым режимом переход изменится
на простой — это цель. `OPTIMIZATION_PLAN.md`, задача 6, требует «не трогать `remote-config.json`»: после этой задачи
оговорка снимается, исправить её там. Пункт 3 задачи 2 в `OFFLINE_PLAN.md` (таймаут TIDAL) отпадает.

---

### I. Распознавание музыки

**Платформа:** Android (плитка в шторке, распознаватель), общий код (кнопка).

**Поиск:** `grep -rnE "SongRecognizer|SongRecognition|RecognitionTileService|ShazamSignature|recognition" composeApp/src androidApp/src --include=*.kt --include=*.xml | grep -v /build/`

**Где.** `composeApp/src/androidMain/.../recognition/SongRecognizer.kt` и `ShazamSignature.kt`,
`APP/expect/ui/SongRecognition.kt` и его `actual` (Android, JVM), `ANDROID/service/RecognitionTileService.kt` и запись
в `AndroidManifest.xml`, входы в `APP/ui/screen/other/SearchScreen.kt`, `APP/App.kt`, `APP/utils/AppLinks.kt`,
`SharedViewModel.kt`.

**Что сделать.** Удалить распознаватель, плитку, кнопку в поиске, ссылку запуска. Разрешение на запись звука в манифесте
удалить, только если им не пользуется больше никто (`grep -rn RECORD_AUDIO`; визуализаторы и эквалайзер могут).
`WrappedBiggestDayCard.kt` попал в поиск по слову: проверить, что это совпадение.

**Как проверить.** Плитки нет в шторке после установки; в поиске нет кнопки микрофона; сборка Android проходит.

---

### J. Комментарии

**Платформа:** общий код. В плеере вход уже убран, остался код.

**Поиск:** `grep -rnE "CommentsViewModel|CommentsBottomSheet|getComments|CommentsPage|CommentData" composeApp/src core --include=*.kt | grep -v /build/`

**Где.** `APP/viewModel/CommentsViewModel.kt`, `APP/ui/component/CommentsBottomSheet.kt`, запись в `ViewModelModule.kt`,
`SongRepository.getComments` и реализация, `Mapping.kt`, `YouTube.kt` и `pages/CommentsPage.kt`.

**Что сделать.** Удалить всё перечисленное. Проверить, что нигде не осталось вызова нижнего листа комментариев.

---

### K. Импорт плейлистов из других приложений

**Платформа:** общий код.

**Поиск:** `grep -rnE "ImportViewModel|import_playlists_from_other_apps|import_data" composeApp/src core --include=*.kt | grep -v /build/`

**Где.** `APP/viewModel/ImportViewModel.kt`, запись в `ViewModelModule.kt`, строка в `SettingScreen.kt`. Экран или лист
импорта найти от строки настроек.

**Что сделать.** Удалить строку настроек, экран, ViewModel и код разбора чужих форматов. **Не путать** с
восстановлением из резервной копии (`restore_your_data`): оно остаётся.

---

### L. Эффект «жидкое стекло»

**Платформа:** общий код, около 30 файлов интерфейса. Самая объёмная задача, делать последней и отдельным днём.

**Поиск:** `grep -rnE "liquidGlass|LiquidGlass|liquid_glass" composeApp/src --include=*.kt | grep -v /build/`

**Где.** `APP/ui/component/LiquidGlassContainer.kt`, `LiquidGlassAppBottomNavigationBar.kt` и его JVM-вариант,
`AppBottomNavigationBar.kt`, `Theme.kt`, `App.kt`, настройка в `SettingScreen.kt` / `SettingsViewModel.kt` /
`SharedViewModel.kt`, и вызовы в экранах (главная, библиотека, альбом, исполнитель, плейлист, поиск, мини-плеер,
тексты, статистика, «Итоги», совместное прослушивание).

**Что сделать.** Убрать настройку. В каждом месте вызова оставить ветку «эффект выключен» (обычная поверхность
Material 3), ветку со стеклом удалить. Затем удалить сами компоненты и зависимость библиотеки эффекта, если она
отдельная (`grep -n -i "liquid\|haze\|glass" gradle/libs.versions.toml`). Размытие в текстах (стиль Apple Music) — это
другое, его не трогать.

**Как проверить.** Каждый затронутый экран открыть на десктопе и на Android, сравнить со снимком до правки при
выключенной настройке: вид не изменился. Нижняя панель на Android на месте.

**Что может сломаться.** Если у пользователя настройка была включена, вид изменится на «выключенный»: это и есть цель.

---

### M. Подкасты

**Платформа:** общий код, около 30 файлов. Решение пользователя: не слушает ни он, ни второй пользователь.

**Поиск:** `grep -rnE "[Pp]odcast" composeApp/src core --include=*.kt | grep -v /build/`

**Где.** `APP/ui/screen/other/PodcastsScreen.kt`, `APP/viewModel/PodcastViewModel.kt`,
`APP/ui/navigation/destination/list/PodcastDestination.kt`, `ListScreenGraph.kt`, `APP/ui/component/PodcastEpisodeItem.kt`,
записи в `ViewModelModule.kt` и `DATA/di/RepositoryModule.kt`, `DATA/repository/PodcastRepositoryImpl.kt`, входы в
поиске (`SearchViewModel.kt`, `SearchScreen.kt`, `SearchRepositoryImpl.kt`), библиотеке (`LibraryViewModel.kt`,
`LibrarySidebarViewModel.kt`, `LibraryScreen.kt`, `LibraryItem.kt`, `GridLibraryPlaylist.kt`), главной (`HomeScreen.kt`,
`HomeParser.kt`), разборщики (`ChartParser.kt`, `PlaylistParser.kt`), таблицы в `MusicDatabase.kt` / `DatabaseDao.kt`.

**Что сделать.** Удалить экран, ViewModel, репозиторий, фильтр «подкасты» в поиске и библиотеке, переходы на экран
подкаста. В разборщиках главной, чартов и поиска элементы-подкасты отбрасывать (не показывать), а не оставлять
карточки, ведущие в никуда. Таблицы подкастов в базе оставить (правило о базе), DAO-методы для них можно оставить
неиспользуемыми.

**Как проверить.** Поиск по названию известного подкаста: в выдаче нет раздела и карточек подкастов, поиск песен,
альбомов и исполнителей работает. Главная и чарты открываются без пустых карточек. Библиотека: фильтры на месте.

**Что может сломаться.** Эпизод подкаста технически — видео; если пользователь откроет его по ссылке, он должен
сыграть как обычный трек. Настройка «видео вместо аудио» упоминает подкасты в описании: поправить строку.

---

### N. Автоматические резервные копии

**Платформа:** Android (расписание), общий код (настройки). Ручное сохранение и восстановление остаются.

**Поиск:** `grep -rnE "AutoBackup|autoBackup|auto_backup|backup_frequency|keep_backups|last_backup" composeApp/src androidApp/src core --include=*.kt | grep -v /build/`

**Где.** `ANDROID/service/backup/AutoBackupWorker.kt`, `AutoBackupScheduler.kt`,
`ANDROID/SimpMusicApplication.kt:89-91` (`autoBackupScheduler.observeAndSchedule()`), настройки `auto_backup`,
`backup_frequency`, `keep_backups`, `last_backup` в `SettingScreen.kt` / `SettingsViewModel.kt`, ключи DataStore.

**Что сделать.** Удалить планировщик, фоновое задание и четыре настройки. При запуске один раз отменить уже
поставленное задание (`cancelUniqueWork` с именем из удаляемого кода). Код самого создания копии, если он общий с
ручным сохранением, оставить.

**Как проверить.** Ручное сохранение создаёт файл, восстановление из него работает (проверить на десктопе и Android).
`adb shell dumpsys jobscheduler | grep simpmusic` не показывает задания копирования.

---

### O. Романизация текстов

**Платформа:** общий код и `core/service/lyricsService`. Запись текстов латиницей под оригиналом; на Android качает словарь.

**Поиск:** `grep -rnE "[Rr]omaniz|Kuromoji" composeApp/src core --include=*.kt --include=*.kts | grep -v /build/`

**Где.** `core/service/lyricsService/src/*/kotlin/org/simpmusic/lyrics/romanization/` (весь каталог во всех наборах
исходников), зависимости словаря в `core/service/lyricsService/build.gradle.kts`,
`DATA/lyrics/LyricsRomanizerRepositoryImpl.kt`, `DOMAIN/repository/LyricsRomanizerRepository.kt`,
`DOMAIN/data/model/lyrics/RomanizationDictionaryState.kt` и `RomanizationLanguage.kt`, запись в
`DATA/di/RepositoryModule.kt`, показ в `APP/ui/component/AppleMusicLyricsLines.kt`, `LyricText.kt`, `LyricsView.kt`,
`SharedViewModel.kt`, настройка `lyrics_romanization` в `SettingScreen.kt` / `SettingsViewModel.kt`, ключи DataStore.

**Что сделать.** Удалить настройку, показ строки романизации в трёх компонентах текста, репозиторий, каталог
`romanization` и зависимости словаря. Остальное в `lyricsService` (сам клиент текстов) не трогать. Скачанный ранее
словарь на Android удалить один раз при запуске (каталог взять из `KuromojiDictionary.kt` перед удалением файла).

**Как проверить.** Текст японской или корейской песни показывается в оригинале, без второй строки; перевод, если
включён, на месте; синхронизация строк и смещение работают. Размер APK уменьшился.

**Что может сломаться.** Вёрстка строки текста: место под романизацию не должно оставаться пустым отступом.

## 5. Решения, принятые по умолчанию

- **Анимированные обложки** (движущаяся обложка альбома, которую приложение ищет в Apple Music и показывает в плеере
  Material 3 Expressive вместо обычной). Пользователь ответил «не знаю». По коду настройка выключена по умолчанию
  (`DataStoreManagerImpl.kt:810`), в журнале ПК следов работы нет, реализация общая с Canvas и делает поиск у стороннего
  сервиса на каждый трек. Рекомендация автора плана — удалить, но решение за пользователем: задача G начинается с показа
  обложек в работе и вопроса, без ответа «удалять» они остаются.
- **Виджеты** оставлены: в голосовом сообщении фраза распозналась неоднозначно.

## 6. Что предполагалось и не было проверено

- Код перечисленных файлов не читался; номера строк есть только там, где место попадалось при составлении
  `OFFLINE_PLAN.md`.
- Что именно делает ИИ в `TasteRepositoryImpl` (задача D).
- Есть ли у «жидкого стекла» отдельная библиотека (задача L).
- Имена уникальных заданий WorkManager для отмены (задача A).
- Насколько логика диджея отделима от обычного кроссфейда в адаптерах (задача H).
- Используется ли рисунок волны где-то кроме диджея (задача H).
- Откуда читается провайдер текстов SimpMusic и не завязан ли он на удаляемую отправку (задача C).
