# Спорт Пульс 3.9.0 (81)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.9.0-81-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.9.0`
- Version code: `81`
- SHA-256: `6644c3525ff4e3ba1a4d6bdf068ce4535b9918c54c716ec0148e4aae21d3a36f`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Что нового

- полностью пересобранная image-шапка «Матч-дня» без пересечения плашки, заголовка и динамических строк;
- новый авторский пульт с тремя физическими линиями `сейчас / сегодня / проверить` и безопасной зоной под текст;
- адаптивная высота для короткого экрана и системного шрифта 100%, 150% и 200% без потери первого действия события;
- геометрический UI-тест шапки, полный прогон `19/19` и расширенный аудит `13/13` при системном шрифте 200%.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
