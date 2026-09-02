# Спорт Пульс 3.7.0 (79)

## Подписанный Android App Bundle

- Файл: `SportPulse-3.7.0-79-signed.aab`
- Package name: `ru.sportpulse.info`
- Version name: `3.7.0`
- Version code: `79`
- SHA-256: `8fd178a029dc7c3c044cefe4563937e6cd4530ac0718e534efee539843e753ab`

Bundle собран задачей `bundleRelease` и проверен командой `jarsigner -verify -verbose -certs`: `jar verified`.

## Что нового

- двухэтапная «Квитанция решения»: выбор не пишет данные до отдельной команды фиксации;
- точное объяснение ограничений Контрракурса, Бюджета внимания, Контура дистанции, стартового окна и целостности журнала;
- новый авторский image-заголовок с тремя равноправными путями и отдельной пломбой;
- адаптивный заголовок «После свистка» и полный UI-аудит при системном шрифте 200%.

## Upload certificate

- Публичный сертификат: `sport-pulse-upload-certificate.pem`
- Alias: `sport-pulse-upload`
- Владелец: `CN=Sport Pulse Upload, O=Sport Pulse, C=RU`
- Действителен до: `2053-12-27`
- SHA-256 fingerprint: `55:41:EA:B9:95:F8:57:C5:DD:72:E6:07:0D:F8:0E:1B:8A:1F:E5:60:BD:A6:44:68:1E:FB:2C:74:A6:D9:6D:B8`

Приватный keystore и пароли намеренно не входят в репозиторий. Для следующих обновлений нужен локальный upload-ключ из `.local/sport-pulse-upload.jks` и `.local/release-signing.properties`; их необходимо хранить в защищённой резервной копии.
