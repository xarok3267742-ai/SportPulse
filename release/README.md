# Спорт Пульс 3.5.0 (77)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.5.0-77-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.5.0`
- Version code: `77`
- SHA-256: `898d6d8a5749a7fd0fb18bca59950411e0e8761eb42025c0d39e67b20c4d19c2`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
