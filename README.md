<p align="center">
  <img src="docs/himaya-logo.png" alt="Логотип Himaya" width="116">
</p>

<h1 align="center">Himaya · حماية</h1>

<p align="center"><strong>Меньше отвлечений. Больше внимания к важному.</strong></p>

<p align="center">
  <a href="https://github.com/belugahellihi-wq/Himaya/releases/download/v1.3.0/Himaya-1.3.0-preview.apk"><strong>Скачать APK 1.3</strong></a> ·
  <a href="https://github.com/belugahellihi-wq/Himaya/releases/tag/v1.3.0">Что нового</a> ·
  <a href="https://github.com/belugahellihi-wq/Himaya/releases">Все версии</a>
</p>

Himaya — DNS-фильтр для Android. Добавь сайты, от которых хочешь сделать перерыв, и включи фильтр. В настройках можно добавить напоминания с аятами.

## Что умеет Himaya

- **Блокировать выбранные сайты.** Добавь до 100 доменов. Правила распространяются и на их поддомены.
- **Показывать активность.** Смотри, сколько DNS-запросов заблокировано за 7 или 30 дней. На устройстве сохраняются дневные суммы.
- **Дать время на паузу.** В приложении есть 30-секундная пауза с анимацией.
- **Подстраивать интерфейс под экран.** В 1.3 обновили логотип, добавили компоновку для планшетов и параллакс при прокрутке. Анимации можно отключить.

## Как начать

1. Скачай APK и открой его на устройстве с Android 8.0 или новее.
2. Запусти Himaya и подтверди запрос Android на создание локального VPN.
3. Добавь свои домены в настройках фильтра.

Himaya 1.3 доступна как предварительная сборка. Если возникнет сбой, укажи модель устройства, версию Android и шаги, на которых он повторяется. [Сообщить об ошибке](https://github.com/belugahellihi-wq/Himaya/issues)

## Как работает фильтр

Himaya перехватывает системные DNS-запросы через локальный VPN. Запросы, которые пропускают локальные правила, передаются Cloudflare Families по DNS over TLS. Сторонний защищённый DNS и прямые IP-адреса могут обходить фильтр. [Исходники и технические подробности версии 1.3](https://github.com/belugahellihi-wq/Himaya/tree/v1.3.0#readme)

<details>
<summary>Проверить скачанный APK (SHA-256)</summary>

SHA-256 — контрольная сумма файла. Сравни её с результатом команды для скачанного APK. Совпадение подтверждает, что у файла те же байты, что и у APK в релизе.

**Windows PowerShell**

```powershell
Get-FileHash .\Himaya-1.3.0-preview.apk -Algorithm SHA256
```

**Ожидаемая сумма**

```text
63b076345fa646fbf2a221dc6f048fead3aab698d80a875d1519a8eff9bae398
```

[Файл SHA256SUMS.txt из релиза](https://github.com/belugahellihi-wq/Himaya/releases/download/v1.3.0/SHA256SUMS.txt)

</details>
