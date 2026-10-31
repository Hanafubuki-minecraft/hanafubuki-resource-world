# HanafubukiResourceWorld: примеры конфигурации

Отдельный мир ресурсов со сбросом по расписанию, напоминаниями и порталом на спавне.

## Файлы

| Файл | Что это |
|---|---|
| [`config.full.yml`](config.full.yml) | Полный `config.yml` по умолчанию со всеми комментариями |
| [`weekly-reset.yml`](weekly-reset.yml) | Сброс каждую неделю, маленький мир, предгенерация через Chunky |
| [`quiet-portal.yml`](quiet-portal.yml) | Тихий портал: без звука и лепестков, быстрее срабатывает |
| [`nether-resource-world.yml`](nether-resource-world.yml) | Мир ресурсов в виде Незера (для кварца, золота, незерита) |

## Как пользоваться

Готовые примеры — это **кусочки** конфига. Перенеси нужные строки в `plugins/HanafubukiResourceWorld/config.yml` (не заменяй весь файл), затем выполни `/rwadmin reload`.

## Все настройки

Таблица собрана из комментариев самого `config.yml`, поэтому совпадает с текущей версией плагина.

| Ключ | По умолчанию | Что делает |
|---|---|---|
| `language` | `en` | Language: en, ru — or any file you add to plugins/HanafubukiResourceWorld/lang/ Can also be changed in-game: /rwadmin language <code> |
| `world.name` | `resources` | Name of the resource world folder |
| `world.environment` | `NORMAL` | NORMAL, NETHER or THE_END |
| `world.border-size` | `8000` | World border size in blocks (0 = no border) |
| `reset.interval-days` | `30` | How often the world is wiped and regenerated with a new seed |
| `reset.notify-before-days` | `3` | From this many days before the reset, every player sees a personal reminder when they join |
| `reset.pregenerate-with-chunky` | `false` | After a reset, pregenerate chunks with Chunky (if installed) up to random-teleport.radius |
| `random-teleport.min-radius` | `200` | Players land at a random safe spot between min-radius and radius from the world spawn |
| `random-teleport.radius` | `3000` | Максимальное расстояние от спавна мира для случайной точки |
| `random-teleport.max-attempts` | `12` | Сколько раз искать безопасную точку |
| `rules.block-bed-spawn` | `true` | Beds and respawn anchors in the resource world don't set the respawn point |
| `rules.block-nether-portals` | `true` | Nether/End portals don't work in the resource world |
| `portal.enabled` | `false` | Optional portal to the resource world (e.g. at spawn). Vertical (an arch) or horizontal (a floor) — any box works. Set the area in-game: /rwadmin portal pos1, /rwadmin portal pos2, /rwadmin portal save |
| `portal.charge-seconds` | `4` | Stand in the portal this long to travel — like a Nether portal. Step out earlier and nothing happens. |
| `portal.cooldown-seconds` | `5` | Пауза после телепорта, прежде чем портал сработает снова |
| `portal.effects.enabled` | `true` | Visuals inside the portal opening (shown only when a player is nearby) |
| `portal.effects.veil` | `true` | A thin shimmering film right in the opening, fading from veil-color to veil-color-2 |
| `portal.effects.veil-color` | `"#F7A1C0"` | Цвет плёнки портала (начало перелива) |
| `portal.effects.veil-color-2` | `"#FFFFFF"` | Цвет плёнки портала (конец перелива) |
| `portal.effects.veil-density` | `2.0` | Плотность плёнки — больше значит гуще |
| `portal.effects.sparkles` | `true` | Tiny sparkles along the edge of the opening |
| `portal.effects.petals` | `true` | An occasional cherry petal drifting through |
| `portal.effects.sound` | `true` | A quiet amethyst chime now and then |
| `portal.effects.view-distance` | `32` | С какого расстояния видны эффекты портала (блоков) |
| **`commands`** | | Commands: name, aliases, enabled. Restart the server to apply. |
| `commands.rwtime` | `{ enabled: true, name: rwtime,  aliases: [] }` | Команда: `enabled` — включена ли, `name` — имя, `aliases` — алиасы. Меняется после рестарта |
| `commands.rwadmin` | `{ enabled: true, name: rwadmin, aliases: [] }` | Команда: `enabled` — включена ли, `name` — имя, `aliases` — алиасы. Меняется после рестарта |
| **`style`** | | Palette of all messages (MiniMessage tags in lang files: <accent> <text> <muted> <dim> <success> <error> <gold>, <symbol>) Hanafubuki style: https://github.com/Hanafubuki-minecraft/hanafubuki-brand/blob/main/STYLE.md |
| `style.symbol` | `"❀"` | Символ в префиксе сообщений (тег `<symbol>`) |
| `style.accent` | `"#F7A1C0"` | Акцент — сакура (тег `<accent>`) |
| `style.text` | `"#F4EEF6"` | Основной текст (`<text>`) |
| `style.muted` | `"#C9BED3"` | Приглушённый текст (`<muted>`) |
| `style.dim` | `"#9488A6"` | Тихий, служебный текст (`<dim>`) |
| `style.success` | `"#A8E6B5"` | Успех (`<success>`) |
| `style.error` | `"#FF8A9B"` | Ошибка (`<error>`) |
| `style.gold` | `"#FFCF6B"` | Золото, редкие акценты (`<gold>`) |
