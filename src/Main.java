import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

// Ошибка формата содержимого словаря.
class InvalidFileFormatException extends Exception {
    public InvalidFileFormatException(String message) {
        super(message);
    }
}

// Ошибка открытия или чтения файла.
class FileReadException extends Exception {
    public FileReadException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Одна запись словаря.
class DictionaryEntry {
    private final String source;
    private final String translation;

    public DictionaryEntry(String source, String translation) {
        this.source = source;
        this.translation = translation;
    }

    public String getSource() {
        return source;
    }

    public String getTranslation() {
        return translation;
    }
}

// Чтение и проверка словаря.
class DictionaryLoader {

    public static List<DictionaryEntry> load(Path path)
            throws FileReadException, InvalidFileFormatException {

        List<DictionaryEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8
        )) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Удаляем BOM, если редактор добавил его в начало файла.
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                int separator = line.indexOf('|');

                // В строке должен находиться ровно один разделитель.
                if (separator == -1
                        || separator != line.lastIndexOf('|')) {
                    throw new InvalidFileFormatException(
                            "Строка " + lineNumber
                                    + ": ожидается формат "
                                    + "\"слово или выражение | перевод\"."
                    );
                }

                String source =
                        line.substring(0, separator).trim();

                String translation =
                        line.substring(separator + 1).trim();

                if (source.isEmpty() || translation.isEmpty()) {
                    throw new InvalidFileFormatException(
                            "Строка " + lineNumber
                                    + ": левая и правая части "
                                    + "не должны быть пустыми."
                    );
                }

                entries.add(new DictionaryEntry(source, translation));
            }

            if (entries.isEmpty()) {
                throw new InvalidFileFormatException(
                        "Словарь не должен быть пустым."
                );
            }

        } catch (IOException | SecurityException exception) {
            throw new FileReadException(
                    "Не удалось прочитать файл: "
                            + path.toAbsolutePath(),
                    exception
            );
        }

        return entries;
    }
}

// Перевод текста по загруженному словарю.
class Translator {
    private final List<DictionaryEntry> entries;

    public Translator(List<DictionaryEntry> entries) {
        this.entries = new ArrayList<>(entries);

        // Сначала проверяем самые длинные выражения.
        this.entries.sort(
                Comparator.comparingInt(
                        (DictionaryEntry entry) ->
                                entry.getSource().length()
                ).reversed()
        );
    }

    public String translate(String text) {
        StringBuilder result = new StringBuilder();
        int position = 0;

        while (position < text.length()) {
            DictionaryEntry match = findMatch(text, position);

            if (match != null) {
                result.append(match.getTranslation());

                // Пропускаем всё переведённое выражение.
                position += match.getSource().length();
            } else {
                // Неизвестный фрагмент оставляем без изменений.
                // Копируем целую Unicode-кодовую точку.
                int codePoint = text.codePointAt(position);
                result.appendCodePoint(codePoint);
                position += Character.charCount(codePoint);
            }
        }

        return result.toString();
    }

    private DictionaryEntry findMatch(String text, int position) {
        for (DictionaryEntry entry : entries) {
            String source = entry.getSource();
            int end = position + source.length();

            if (end > text.length()) {
                continue;
            }

            // true означает сравнение без учёта регистра.
            if (!text.regionMatches(
                    true, position, source, 0, source.length()
            )) {
                continue;
            }

            // Не переводим часть другого слова:
            // например, cat внутри catalog.
            boolean startsInsideWord =
                    position > 0
                            && isWordCharacter(
                            text.codePointBefore(position)
                    )
                            && isWordCharacter(
                            source.codePointAt(0)
                    );

            boolean endsInsideWord =
                    end < text.length()
                            && isWordCharacter(
                            source.codePointBefore(source.length())
                    )
                            && isWordCharacter(
                            text.codePointAt(end)
                    );

            if (!startsInsideWord && !endsInsideWord) {
                return entry;
            }
        }

        return null;
    }

    private boolean isWordCharacter(int codePoint) {
        int type = Character.getType(codePoint);

        return Character.isLetterOrDigit(codePoint)
                || codePoint == '_'
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}

public class Main {
    public static void main(String[] args) {
        Path dictionaryPath = Paths.get("dictionary.txt");

        try {
            // Сначала читаем словарь, затем запрашиваем текст.
            List<DictionaryEntry> entries =
                    DictionaryLoader.load(dictionaryPath);

            Translator translator = new Translator(entries);

            System.out.println(
                    "Словарь загружен. Записей: " + entries.size()
            );

            try (Scanner scanner = new Scanner(
                    System.in, StandardCharsets.UTF_8.name()
            )) {
                System.out.println("Введите текст для перевода:");

                if (!scanner.hasNextLine()) {
                    System.out.println("Ввод завершён.");
                    return;
                }

                String text = scanner.nextLine();

                System.out.println("Перевод:");
                System.out.println(translator.translate(text));
            }

        } catch (InvalidFileFormatException exception) {
            System.err.println(
                    "Ошибка формата словаря: "
                            + exception.getMessage()
            );

        } catch (FileReadException exception) {
            System.err.println(
                    "Ошибка чтения: " + exception.getMessage()
            );
        }
    }
}