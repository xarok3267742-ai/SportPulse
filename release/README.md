# Спорт Пульс 3.3.0 (75)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.3.0-75-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.3.0`
- Version code: `75`
- SHA-256: `28f1b06f0ad1c2ef192949b8db97fd75aed1f7539b3a6a76f03d43e58ed5ac4e`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
