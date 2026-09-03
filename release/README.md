# Спорт Пульс 3.16.0 (88)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.16.0-88-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.16.0`
- Version code: `88`
- SHA-256: `7c18ed746c4aa6e8c00acbcf234c5d1039f4c3bc7353f30ad7f2939fb8916c7d`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Что нового

- новая adaptive-иконка построена вокруг арены проверки, белого маршрута данных и одной красной live-точки; она не использует мяч, кубок, деньги, коэффициенты или медицинскую линию пульса;
- значимая геометрия отцентрирована внутри безопасной зоны Android и проверена в системном лаунчере API 35: круглая и скруглённая маски не обрезают знак, а детали остаются различимыми в размере 48 px;
- для тематических иконок Android 13+ добавлен отдельный белый alpha-слой вместо повторного использования цветного foreground;
- исходная image-генерация и точный промпт сохранены в `docs`, а launcher-preview обновлён на фактический снимок установленного приложения;
- unit-тесты, debug/release lint и полный device-прогон `24/24` при системном шрифте 100% и `24/24` при 200% прошли без ошибок;
- release AAB содержит новые color/monochrome-ресурсы, имеет package `ru.sportpulse.info`, пустой клиентский endpoint и не содержит ключ провайдера.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
