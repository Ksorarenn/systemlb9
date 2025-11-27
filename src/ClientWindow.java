import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientWindow extends JFrame implements ActionListener, TCPConnectionListener {
    private static final String IP_ADDR = "127.0.0.1";
    private static final int PORT = 8189;
    private static final int WIDTH = 700;
    private static final int HEIGHT = 500;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientWindow());
    }

    private final JTextPane log = new JTextPane();
    private final JTextField fieldNickname = new JTextField("Гость");
    private final JTextField fieldInput = new JTextField();
    private final StyledDocument doc;
    private final SimpleAttributeSet myNickStyle;
    private final SimpleAttributeSet otherNickStyle;
    private final SimpleAttributeSet defaultStyle;
    private final SimpleAttributeSet systemInfoStyle;

    private TCPConnection connection;
    private String myNickname;
    private Color currentBgColor = Color.WHITE;
    private Color currentTextColor = Color.BLACK;

    // Карта цветов для команд (английские названия)
    private final Map<String, Color> colorMap = new HashMap<>() {{
        put("white", Color.WHITE);
        put("black", Color.BLACK);
        put("blue", Color.BLUE);
        put("green", Color.GREEN);
        put("yellow", Color.YELLOW);
        put("pink", Color.PINK);
        put("red", Color.RED);
        put("gray", Color.GRAY);
        put("orange", Color.ORANGE);
        put("cyan", Color.CYAN);
    }};

    // Карта тегов
    private final Map<String, String> tagMap = new HashMap<>() {{
        put("<b>", "</b>");
        put("<i>", "</i>");
        put("<u>", "</u>");
        put("<s>", "</s>");
        put("<hidden>", "</hidden>");
    }};

    // Для хранения скрытых текстов
    private final Map<Integer, HiddenTextInfo> hiddenTexts = new HashMap<>();
    private int hiddenTextCounter = 0;

    private static class HiddenTextInfo {
        final int start;
        final int end;
        final String text;

        HiddenTextInfo(int start, int end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private ClientWindow() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setTitle("Продвинутый Чат");

        // Инициализация стилей
        doc = log.getStyledDocument();

        myNickStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(myNickStyle, Color.BLUE);
        StyleConstants.setBold(myNickStyle, true);

        otherNickStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(otherNickStyle, Color.RED);
        StyleConstants.setBold(otherNickStyle, true);

        defaultStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(defaultStyle, currentTextColor);

        systemInfoStyle = new SimpleAttributeSet();
        StyleConstants.setForeground(systemInfoStyle, Color.MAGENTA);
        StyleConstants.setBold(systemInfoStyle, true);

        // Настройка компонентов
        add(fieldNickname, BorderLayout.NORTH);

        log.setEditable(false);
        log.setBackground(currentBgColor);
        log.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    revealHiddenText(e.getPoint());
                }
            }
        });
        add(new JScrollPane(log), BorderLayout.CENTER);

        fieldInput.addActionListener(this);

        // Добавляем слушатель для автодополнения тегов
        fieldInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent evt) {
                if (evt.getKeyChar() == '>') {
                    autoCompleteTag();
                }
            }
        });

        add(fieldInput, BorderLayout.SOUTH);

        setVisible(true);
        try {
            connection = new TCPConnection(this, IP_ADDR, PORT);
        } catch (IOException e) {
            printMessage("Ошибка подключения: " + e, defaultStyle);
        }
    }

    // Автодополнение тегов
    private void autoCompleteTag() {
        String text = fieldInput.getText();
        for (Map.Entry<String, String> entry : tagMap.entrySet()) {
            String openTag = entry.getKey();
            String closeTag = entry.getValue();
            if (text.endsWith(openTag)) {
                String newText = text + closeTag;
                fieldInput.setText(newText);
                fieldInput.setCaretPosition(text.length());
                break;
            }
        }
    }

    // Раскрытие скрытого текста
    private void revealHiddenText(Point point) {
        int pos = log.viewToModel(point);
        if (pos >= 0) {
            try {
                // Ищем скрытый текст в этой позиции
                for (HiddenTextInfo hidden : hiddenTexts.values()) {
                    if (pos >= hidden.start && pos <= hidden.end) {
                        // Заменяем скрытый текст на реальный
                        SimpleAttributeSet revealedStyle = new SimpleAttributeSet();
                        StyleConstants.setForeground(revealedStyle, Color.GRAY);
                        StyleConstants.setItalic(revealedStyle, true);

                        doc.remove(hidden.start, hidden.end - hidden.start);
                        doc.insertString(hidden.start, hidden.text, revealedStyle);

                        // Удаляем из карты скрытых текстов
                        hiddenTexts.values().removeIf(h -> h.start == hidden.start);
                        break;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String msg = fieldInput.getText();
        if (msg.equals("")) return;

        // Проверка на команды
        if (msg.startsWith("/")) {
            processCommand(msg);
            fieldInput.setText("");
            return;
        }

        fieldInput.setText("");
        myNickname = fieldNickname.getText();

        connection.sendString(myNickname + ": " + msg);
    }

    // Обработка команд
    private void processCommand(String command) {
        String[] parts = command.substring(1).split(" "); // Убираем / и разбиваем по пробелам
        if (parts.length < 1) return;

        switch (parts[0]) {
            case "help":
                showCommandsHelp();
                break;
            case "set_color":
                if (parts.length >= 2) {
                    setBackgroundColor(parts[1]);
                } else {
                    showColorHelp("set_color");
                }
                break;
            case "set_textcolor":
                if (parts.length >= 2) {
                    setTextColor(parts[1]);
                } else {
                    showColorHelp("set_textcolor");
                }
                break;
            case "colors":
                showAvailableColors();
                break;
            case "systemfetch":
                showSystemInfo();
                break;
            default:
                printMessage("Неизвестная команда: /" + parts[0] + ". Введите '/help' для списка команд.", defaultStyle);
                break;
        }
    }

    private void showColorHelp(String command) {
        StringBuilder help = new StringBuilder("Использование: /" + command + " [цвет]");
        help.append("\nДоступные цвета: ").append(String.join(", ", colorMap.keySet()));
        printMessage(help.toString(), defaultStyle);
    }

    private void showAvailableColors() {
        printMessage("Доступные цвета: " + String.join(", ", colorMap.keySet()), defaultStyle);
    }

    private void showCommandsHelp() {
        printMessage("=== Доступные Команды ===", defaultStyle);
        printMessage("/help - Показать эту справку", defaultStyle);
        printMessage("/set_color [цвет] - Изменить цвет фона", defaultStyle);
        printMessage("/set_textcolor [цвет] - Изменить цвет текста", defaultStyle);
        printMessage("/colors - Показать доступные цвета", defaultStyle);
        printMessage("/systemfetch - Показать информацию о системе", defaultStyle);
        printMessage("=== Теги Форматирования ===", defaultStyle);
        printMessage("<b>жирный</b> - Жирный текст", defaultStyle);
        printMessage("<i>курсив</i> - Курсивный текст", defaultStyle);
        printMessage("<u>подчеркнутый</u> - Подчеркнутый текст", defaultStyle);
        printMessage("<s>зачеркнутый</s> - Зачеркнутый текст", defaultStyle);
        printMessage("<hidden>секрет</hidden> - Скрытый текст (нажмите для раскрытия)", defaultStyle);
    }

    // Команда systemfetch - показывает информацию о системе
    private void showSystemInfo() {
        printMessage("", defaultStyle); // Пустая строка для разделения

        printMessage("=== Информация о Системе ===", systemInfoStyle);

        // Информация об ОС
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        printMessage("ОС: " + osName + " " + osVersion + " (" + osArch + ")", defaultStyle);

        // Информация о Java
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        String javaVm = System.getProperty("java.vm.name");
        printMessage("Java: " + javaVersion + " - " + javaVm, defaultStyle);
        printMessage("Производитель: " + javaVendor, defaultStyle);

        // Информация о пользователе
        String userName = System.getProperty("user.name");
        String userHome = System.getProperty("user.home");
        printMessage("Пользователь: " + userName, defaultStyle);
        printMessage("Домашняя папка: " + userHome, defaultStyle);

        // Информация о системе
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        int availableProcessors = runtime.availableProcessors();

        printMessage("Процессоры: " + availableProcessors + " ядер", defaultStyle);
        printMessage("Память: " + usedMemory + "МБ / " + totalMemory + "МБ (Макс: " + maxMemory + "МБ)", defaultStyle);

        // Информация о рабочей директории
        String workingDir = System.getProperty("user.dir");
        printMessage("Рабочая папка: " + workingDir, defaultStyle);

        // Информация о чате
        printMessage("Клиент чата: " + getTitle(), defaultStyle);
        printMessage("Сервер чата: " + IP_ADDR + ":" + PORT, defaultStyle);

        // Время работы JVM
        long uptime = System.currentTimeMillis();
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        printMessage("Время работы JVM: " + hours + "ч " + minutes + "м", defaultStyle);

        printMessage("========================", systemInfoStyle);
    }

    private void setBackgroundColor(String colorName) {
        Color color = colorMap.get(colorName.toLowerCase());
        if (color != null) {
            currentBgColor = color;
            log.setBackground(color);

            // Автоматическая настройка цвета текста для темных фонов
            if (isDarkColor(color)) {
                currentTextColor = Color.WHITE;
            } else {
                currentTextColor = Color.BLACK;
            }

            updateDefaultStyle();
            refreshAllText();

            printMessage("Цвет фона изменен на: " + colorName, defaultStyle);
        } else {
            printMessage("Неизвестный цвет: " + colorName + ". Введите '/colors' для списка цветов.", defaultStyle);
        }
    }

    private void setTextColor(String colorName) {
        Color color = colorMap.get(colorName.toLowerCase());
        if (color != null) {
            currentTextColor = color;
            updateDefaultStyle();
            refreshAllText();
            printMessage("Цвет текста изменен на: " + colorName, defaultStyle);
        } else {
            printMessage("Неизвестный цвет: " + colorName + ". Введите '/colors' для списка цветов.", defaultStyle);
        }
    }

    private boolean isDarkColor(Color color) {
        // Проверяем, является ли цвет темным (для автоматического выбора цвета текста)
        double darkness = 1 - (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue()) / 255;
        return darkness > 0.5;
    }

    private void updateDefaultStyle() {
        StyleConstants.setForeground(defaultStyle, currentTextColor);
        // Также обновляем цвет существующего текста по умолчанию
        doc.setCharacterAttributes(0, doc.getLength(), defaultStyle, false);
    }

    private void refreshAllText() {
        // Принудительно обновляем отображение
        log.repaint();
    }

    @Override
    public void onConnectionReady(TCPConnection tcpConnection) {
        printMessage("Подключение установлено...", defaultStyle);
    }

    @Override
    public void onReceiveString(TCPConnection tcpConnection, String value) {
        // Определяем, чье это сообщение
        if (value.contains(": ")) {
            String[] parts = value.split(": ", 2);
            String nick = parts[0];
            String message = parts[1];

            if (nick.equals(myNickname)) {
                printFormattedMessage("Я: " + message, myNickStyle);
            } else {
                printFormattedMessage(value, otherNickStyle);
            }
        } else {
            printMessage(value, defaultStyle);
        }
    }

    @Override
    public void onDisconnect(TCPConnection tcpConnection) {
        printMessage("Подключение закрыто", defaultStyle);
    }

    @Override
    public void onException(TCPConnection tcpConnection, IOException e) {
        printMessage("Ошибка подключения: " + e, defaultStyle);
    }

    private synchronized void printMessage(String message, AttributeSet style) {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.insertString(doc.getLength(), message + "\n", style);
                log.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private synchronized void printFormattedMessage(String message, AttributeSet nickStyle) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Разделяем ник и сообщение
                String[] parts = message.split(": ", 2);
                if (parts.length == 2) {
                    // Ник
                    doc.insertString(doc.getLength(), parts[0] + ": ", nickStyle);
                    // Сообщение с форматированием
                    applyMessageFormatting(parts[1]);
                    doc.insertString(doc.getLength(), "\n", defaultStyle);
                } else {
                    doc.insertString(doc.getLength(), message + "\n", defaultStyle);
                }
                log.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // Применение форматирования к сообщению
    private void applyMessageFormatting(String message) throws BadLocationException {
        Pattern pattern = Pattern.compile("(<(b|i|u|s|hidden)>)(.*?)(</\\2>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(message);
        int lastPos = 0;

        while (matcher.find()) {
            // Текст до тега
            if (matcher.start() > lastPos) {
                String before = message.substring(lastPos, matcher.start());
                doc.insertString(doc.getLength(), before, defaultStyle);
            }

            // Текст внутри тега
            String tag = matcher.group(2);
            String content = matcher.group(3);

            if ("hidden".equals(tag)) {
                // Обработка скрытого текста
                SimpleAttributeSet hiddenStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(hiddenStyle, currentBgColor);
                StyleConstants.setBackground(hiddenStyle, Color.LIGHT_GRAY);

                int startPos = doc.getLength();
                doc.insertString(doc.getLength(), "[скрытый текст - нажмите для раскрытия]", hiddenStyle);
                int endPos = doc.getLength();

                // Сохраняем информацию о скрытом тексте
                hiddenTexts.put(hiddenTextCounter++, new HiddenTextInfo(startPos, endPos, content));
            } else {
                // Обычные теги форматирования
                SimpleAttributeSet style = new SimpleAttributeSet(defaultStyle);

                switch (tag) {
                    case "b":
                        StyleConstants.setBold(style, true);
                        break;
                    case "i":
                        StyleConstants.setItalic(style, true);
                        break;
                    case "u":
                        StyleConstants.setUnderline(style, true);
                        break;
                    case "s":
                        StyleConstants.setStrikeThrough(style, true);
                        break;
                    default:
                        style = defaultStyle;
                        break;
                }

                doc.insertString(doc.getLength(), content, style);
            }

            lastPos = matcher.end();
        }

        // Остаток текста после последнего тега
        if (lastPos < message.length()) {
            String after = message.substring(lastPos);
            doc.insertString(doc.getLength(), after, defaultStyle);
        }
    }
}