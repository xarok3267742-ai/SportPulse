# Спорт Пульс 3.0.0 (72)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.0.0-72-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.0.0`
- Version code: `72`
- SHA-256: `b7441ac10cc141e69b4f4fefca1f6f8f7d34b1e2a28a54bfcfb3e89ff9784329`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
