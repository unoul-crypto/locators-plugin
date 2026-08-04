# Locators

Плагин для Bukkit-совместимых серверов, добавляющий настраиваемые предметы-локаторы. При клике ПКМ локатор показывает
только использовавшему его игроку подходящие цели, примерное расстояние и относительные углы до них.

## Требования и сборка

- Minecraft 1.16.5 или новее
- Bukkit-совместимое ядро: Spigot, Paper, Purpur, Mohist и аналогичные
- Java, требуемая самим сервером; байткод плагина совместим с Java 8+
- Maven 3.9+

Сборка в PowerShell:

```powershell
$env:JAVA_HOME='C:\Users\Nikita\Documents\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'C:\Users\Nikita\Documents\apache-maven-3.9.16\bin\mvn.cmd' clean package
```

Готовый файл: `target/Locators-1.0.1.jar`. Поместите его в `plugins` сервера и перезапустите сервер.
При первом запуске появится `plugins/Locators/locators.json`.

Плагин компилируется против API 1.16.5, использует `api-version: 1.16` и не обращается к внутренним
классам CraftBukkit/NMS. Это позволяет одному JAR работать, в частности, на Mohist 1.20.1 и на
большинстве более новых Bukkit-совместимых ядер. Для предметов из новых версий Minecraft используйте
их ID только на тех серверах, где такие предметы существуют.

## Формат locators.json

```json
{
  "locators": [
    {
      "id": "basic",
      "item": "minecraft:compass",
      "showDistance": true,
      "distanceError": 10.0,
      "showYaw": true,
      "yawError": 5.0,
      "showPitch": false,
      "pitchError": 0.0,
      "maxDistance": 500.0,
      "targetTeam": "all",
      "cooldownSeconds": 30.0,
      "usePermission": "locators.use.basic",
      "givePermission": "locators.give.basic"
    }
  ]
}
```

- `id` — уникальный ID из строчных латинских букв, цифр, `_`, `.` и `-`.
- `item` — ID предмета Minecraft.
- `showDistance`, `showYaw`, `showPitch` — какие данные выводить.
- `distanceError`, `yawError`, `pitchError` — случайная погрешность `±` для соответствующего значения.
- `maxDistance` — максимальная дистанция до цели в блоках.
- `targetTeam` — имя scoreboard-команды цели либо `all`. Учитывается основная таблица счёта сервера.
- `cooldownSeconds` — cooldown этого типа локатора для каждого игрока, можно использовать дробное число.
- `usePermission` — право на использование; пустая строка разрешает использование всем.
- `givePermission` — право на выдачу этого типа; пустая строка разрешает выдачу всем.

Учитываются только другие онлайн-игроки в том же мире. Yaw показывается как угол поворота вправо/влево
от текущего взгляда, pitch — вниз/вверх. Cooldown хранится в памяти и сбрасывается при перезапуске сервера.

## Команды

- `/locators give <ник> <id> <количество>` — выдать локатор. Требует `givePermission` выбранного типа.
- `/locators reload` — атомарно перечитать JSON. Требует `locators.reload` (по умолчанию только OP).

Если инвентарь получателя заполнен, не поместившиеся предметы выпадут рядом с ним. Количество за одну
команду ограничено диапазоном от 1 до 6400.
