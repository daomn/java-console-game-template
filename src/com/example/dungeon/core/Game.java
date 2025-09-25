package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("help", (ctx, a) -> System.out.println("Команды: " + String.join(", ", commands.keySet())));
        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            System.out.println("Память: used=" + used + " free=" + free + " total=" + total);
        });
        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));
        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление (например: move north)");
            }
            String dir = a.getFirst().toLowerCase(Locale.ROOT);
            Room next = ctx.getCurrent().getNeighbors().get(dir);
            if (next == null) {
                throw new InvalidCommandException("Нет пути в направлении: " + dir);
            }
            ctx.setCurrent(next);
            System.out.println("Вы переместились в локацию: " + next.getName());
            System.out.println(next.describe());
        });

        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите предмет (например: take Ключ)");
            }
            String itemName = String.join(" ", a);
            Room room = ctx.getCurrent();
            var optItem = room.getItems().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst();

            if (optItem.isEmpty()) {
                throw new InvalidCommandException("Здесь нет предмета: " + itemName);
            }

            var item = optItem.get();
            ctx.getPlayer().getInventory().add(item);
            room.getItems().remove(item);
            System.out.println("Вы взяли предмет: " + item.getName());
        });

        commands.put("inventory", (ctx, a) -> {
            var inv = ctx.getPlayer().getInventory();
            if (inv.isEmpty()) {
                System.out.println("Инвентарь пуст.");
                return;
            }

            inv.stream()
                    .collect(Collectors.groupingBy(i -> i.getClass().getSimpleName()))
                    .forEach((type, items) -> {
                        System.out.println(type + ":");
                        items.stream()
                                .map(Item::getName)
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .forEach(name -> System.out.println("  - " + name));
                    });
        });

        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите предмет (например: use Зелье)");
            }
            String itemName = String.join(" ", a);
            var player = ctx.getPlayer();

            var optItem = player.getInventory().stream()
                    .filter(i -> i.getName().equalsIgnoreCase(itemName))
                    .findFirst();

            if (optItem.isEmpty()) {
                throw new InvalidCommandException("У вас нет предмета: " + itemName);
            }

            optItem.get().apply(ctx);
        });

        commands.put("fight", (ctx, a) -> {
            Room room = ctx.getCurrent();
            Monster m = room.getMonster();
            Player p = ctx.getPlayer();

            if (m == null) {
                throw new InvalidCommandException("Здесь нет монстра.");
            }

            // ход игрока
            m.setHp(m.getHp() - p.getAttack());
            System.out.println("Вы бьёте " + m.getName() + " на " + p.getAttack() + ". HP монстра: " + m.getHp());

            if (m.getHp() <= 0) {
                System.out.println("Монстр повержен!");
                room.setMonster(null);

                // выпадение лута
                room.getItems().add(new Weapon("Клык " + m.getName(), 1));
                System.out.println("Из монстра выпал предмет: Клык " + m.getName());

                ctx.addScore(10);
                return;
            }

            // ход монстра
            p.setHp(p.getHp() - m.getLevel());
            System.out.println("Монстр отвечает на " + m.getLevel() + ". Ваше HP: " + p.getHp());

            if (p.getHp() <= 0) {
                System.out.println("Вы погибли... Игра окончена.");
                System.exit(0);
            }
        });

        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Пока!");
            System.exit(0);
        });

        commands.put("alloc", (ctx, a) -> {
            try {
                // создаём массивы по 1 МБ, количество задаём аргументом или по умолчанию 10
                int count = a.isEmpty() ? 10 : Integer.parseInt(a.getFirst());
                List<byte[]> trash = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    trash.add(new byte[1024 * 1024]); // 1 МБ
                }
                System.out.println("Выделено " + count + " МБ памяти.");
            } catch (NumberFormatException e) {
                throw new InvalidCommandException("Некорректный аргумент. Используйте: alloc [число]");
            }
        });


    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        Room square = new Room("Площадь", "Каменная площадь с фонтаном.");
        Room forest = new Room("Лес", "Шелест листвы и птичий щебет.");
        Room cave = new Room("Пещера", "Темно и сыро.");
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        cave.getNeighbors().put("west", forest);

        forest.getItems().add(new Potion("Малое зелье", 5));
        forest.setMonster(new Monster("Волк", 1, 8));

        state.setCurrent(square);
    }

    public void run() {
        System.out.println("DungeonMini (TEMPLATE). 'help' — команды.");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                List<String> parts = Arrays.asList(line.split("\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());
                Command c = commands.get(cmd);
                try {
                    if (c == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    c.execute(state, args);
                    state.addScore(1);
                } catch (InvalidCommandException e) {
                    System.out.println("Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Непредвиденная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}
