# Спорт Пульс 3.8.0 (80)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.8.0-80-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.8.0`
- Version code: `80`
- SHA-256: `694c433e5e9577dfbdb37a9270e7bd3e13f7adbb4de05d343abcbfdf62ff0603`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Что нового

- ранний контроль «Глубина разбора» сразу после оперативного табло и до длинной Карты данных;
- авторский image-маркер с общим обзором и послойной проверкой на одном неизменном канале данных;
- контекстный возврат переключателя в кадр после выбора режима без пересчёта оценок, источников или журнала;
- отдельный UI-тест порядка и состояний, полный прогон `18/18` и расширенный аудит `11/11` при системном шрифте 200%.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
