import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BoothTimeClock extends JFrame {
    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a");
    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("h:mm:ss a");
    private static final DateTimeFormatter FILE_STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Color INK = new Color(0xF7F4EC);
    private static final Color MUTED = new Color(0xAAA79F);
    private static final Color DIM = new Color(0x76736D);
    private static final Color BACKGROUND = new Color(0x0B0D10);
    private static final Color CARD = new Color(0x171410);
    private static final Color FIELD = new Color(0x080A08);
    private static final Color LINE = new Color(0x4C4638);
    private static final Color GREEN = new Color(0x44FF88);
    private static final Color RED = new Color(0xFF5F4F);
    private static final Color GOLD = new Color(0xFFBF47);
    private static final Color AMBER = new Color(0xFF8C2A);
    private static final Color LED_ON = new Color(0xFFB13D);
    private static final Color LED_GLOW = new Color(0x8C320B);
    private static final Color LED_OFF = new Color(0x2D1609);

    private final JLabel timerLabel = new JLabel("00:00:00", SwingConstants.CENTER);
    private final SevenSegmentDisplay ledTimerDisplay = new SevenSegmentDisplay();
    private final JLabel wallClockLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Session not started", SwingConstants.CENTER);
    private final JLabel startLabel = new JLabel("Started at: --", SwingConstants.CENTER);
    private final JTextField artistField = new JTextField();
    private final JTextField producerField = new JTextField();
    private final JTextArea logArea = new JTextArea();
    private final AnalogClockPanel analogClockPanel = new AnalogClockPanel();
    private final CardLayout clockLayout = new CardLayout();
    private final JPanel clockDeck = new JPanel(clockLayout);
    private final Timer timer;
    private final Timer wallClockTimer;
    private final JMenu invoiceMenu = new JMenu("Invoice");
    private final JMenuItem invoiceStatusItem = new JMenuItem("No invoice created");
    private final JMenuItem invoiceDestinationItem = new JMenuItem();
    private final JCheckBoxMenuItem invoicePanelItem = new JCheckBoxMenuItem("Show invoice panel");
    private final JMenu folderMenu = new JMenu("Folder");
    private final JMenuItem baseFolderItem = new JMenuItem();
    private final JMenuItem sessionFolderItem = new JMenuItem("No session folder yet");
    private final JCheckBoxMenuItem analogClockItem = new JCheckBoxMenuItem("Analog clock");
    private final JPanel invoicePanel = new RoundedPanel(new Color(0x0B0B0B), 8);
    private final DefaultListModel<Path> invoiceListModel = new DefaultListModel<>();
    private final JList<Path> invoiceList = new JList<>(invoiceListModel);
    private final JTextArea invoicePreview = new JTextArea();

    private long sessionStartNanos;
    private boolean running;
    private String currentTimerText = "00:00:00";
    private String lockedArtist = "";
    private String lockedProducer = "";
    private LocalDateTime sessionStartTime;
    private Path invoicePath;
    private Path musicBaseFolder = Paths.get(System.getProperty("user.home"), "Music", "Booth Time Sessions");
    private Path customInvoiceFolder;
    private Path sessionFolder;

    public static void main(String[] args) {
        if (args.length >= 2 && "--screenshot".equals(args[0])) {
            SwingUtilities.invokeLater(() -> renderScreenshot(args[1], args.length >= 3 && "invoices".equals(args[2])));
            return;
        }

        SwingUtilities.invokeLater(BoothTimeClock::new);
    }

    private static void renderScreenshot(String outputPath, boolean showInvoices) {
        try {
            BoothTimeClock app = new BoothTimeClock();
            app.setSize(showInvoices ? 1280 : 1040, 620);
            app.invoicePanelItem.setSelected(showInvoices);
            app.invoicePanel.setVisible(showInvoices);
            app.refreshInvoiceList();
            app.validate();

            BufferedImage image = new BufferedImage(app.getWidth(), app.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            app.paint(graphics);
            graphics.dispose();

            ImageIO.write(image, "png", new File(outputPath));
            app.dispose();
        } catch (IOException e) {
            System.err.println("Could not write screenshot: " + e.getMessage());
        } finally {
            System.exit(0);
        }
    }

    public BoothTimeClock() {
        setTitle("Booth Time Clock");
        setSize(1040, 620);
        setMinimumSize(new Dimension(430, 360));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setJMenuBar(createMenuBar());

        JPanel root = new RackPanel();
        root.setLayout(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(14, 18, 16, 18));

        JLabel title = new JLabel("BOOTH TIME");
        title.setForeground(new Color(0xD8D1C2));
        title.setFont(new Font("Consolas", Font.BOLD, 22));

        JLabel subtitle = new JLabel("STUDIO SESSION CLOCK / INVOICE ROUTER");
        subtitle.setForeground(new Color(0x948A77));
        subtitle.setFont(new Font("Consolas", Font.BOLD, 12));

        wallClockLabel.setForeground(GOLD);
        wallClockLabel.setFont(new Font("Consolas", Font.BOLD, 18));
        wallClockLabel.setBorder(new EmptyBorder(7, 12, 7, 12));

        JPanel titleStack = new JPanel(new GridLayout(2, 1, 0, 2));
        titleStack.setOpaque(false);
        titleStack.add(title);
        titleStack.add(subtitle);

        RoundedPanel clockPill = new RoundedPanel(new Color(0x080808), 16);
        clockPill.setLayout(new BorderLayout());
        clockPill.setBorder(BorderFactory.createLineBorder(new Color(0x363028)));
        clockPill.add(wallClockLabel, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.add(titleStack, BorderLayout.CENTER);
        header.add(clockPill, BorderLayout.EAST);

        JPanel sessionInfo = new JPanel(new GridLayout(1, 2, 8, 0));
        sessionInfo.setOpaque(false);
        sessionInfo.add(createLabeledField("Artist", artistField));
        sessionInfo.add(createLabeledField("Producer", producerField));

        RoundedPanel timerPanel = new RoundedPanel(new Color(0x080808), 10);
        timerPanel.setLayout(new BorderLayout(0, 4));
        timerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x373737), 2),
                new EmptyBorder(12, 16, 12, 16)
        ));

        timerLabel.setForeground(GREEN);
        timerLabel.setFont(new Font("Consolas", Font.BOLD, 132));
        ledTimerDisplay.setValue(currentTimerText);

        statusLabel.setForeground(GREEN);
        statusLabel.setFont(new Font("Consolas", Font.BOLD, 16));

        startLabel.setForeground(DIM);
        startLabel.setFont(new Font("Consolas", Font.PLAIN, 12));

        clockDeck.setOpaque(false);
        clockDeck.add(ledTimerDisplay, "digital");
        clockDeck.add(analogClockPanel, "analog");

        timerPanel.add(clockDeck, BorderLayout.CENTER);
        timerPanel.add(statusLabel, BorderLayout.SOUTH);

        JButton sessionInButton = createButton("SESSION IN", new Color(0x1D7F45));
        JButton sessionOutButton = createButton("SESSION OUT", new Color(0x8D302B));
        JButton resetButton = createButton("RESET", new Color(0x3A3A3A));

        sessionInButton.addActionListener(e -> sessionIn());
        sessionOutButton.addActionListener(e -> sessionOut());
        resetButton.addActionListener(e -> resetTimer());

        JPanel controls = new JPanel(new GridLayout(1, 3, 8, 0));
        controls.setOpaque(false);
        controls.add(sessionInButton);
        controls.add(sessionOutButton);
        controls.add(resetButton);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setBackground(new Color(0x080808));
        logArea.setForeground(new Color(0xD8D1C2));
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(0, 58));
        logScroll.setMinimumSize(new Dimension(0, 42));
        logScroll.setBorder(BorderFactory.createLineBorder(new Color(0x343434)));
        logScroll.getViewport().setBackground(new Color(0x080808));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(timerPanel, BorderLayout.CENTER);

        JPanel sessionStrip = new JPanel(new BorderLayout(0, 6));
        sessionStrip.setOpaque(false);
        sessionStrip.add(sessionInfo, BorderLayout.CENTER);
        sessionStrip.add(startLabel, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(sessionStrip, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(controls, BorderLayout.NORTH);
        bottom.add(logScroll, BorderLayout.SOUTH);

        JPanel shell = new JPanel(new BorderLayout(0, 8));
        shell.setBackground(new Color(0x080808));
        shell.setBorder(new EmptyBorder(0, 0, 0, 0));
        shell.add(root, BorderLayout.CENTER);
        shell.add(bottom, BorderLayout.SOUTH);

        invoicePanel.setVisible(false);
        shell.add(createInvoicePanel(), BorderLayout.EAST);

        timer = new Timer(1000, e -> updateTimer());
        wallClockTimer = new Timer(1000, e -> updateWallClock());
        updateFolderMenu();
        updateInvoiceMenu();
        refreshInvoiceList();
        updateWallClock();
        wallClockTimer.start();

        add(shell);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateResponsiveType();
            }
        });
        updateResponsiveType();
        setVisible(true);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(new Color(0x111418));
        menuBar.setBorder(BorderFactory.createLineBorder(new Color(0x20252C)));
        invoiceMenu.setForeground(INK);
        folderMenu.setForeground(INK);
        JMenu viewMenu = new JMenu("View");
        viewMenu.setForeground(INK);

        baseFolderItem.addActionListener(e -> chooseMusicBaseFolder());
        sessionFolderItem.setEnabled(false);
        folderMenu.add(baseFolderItem);
        folderMenu.add(sessionFolderItem);

        analogClockItem.addActionListener(e -> setAnalogClockVisible(analogClockItem.isSelected()));
        viewMenu.add(analogClockItem);

        invoicePanelItem.addActionListener(e -> {
            invoicePanel.setVisible(invoicePanelItem.isSelected());
            refreshInvoiceList();
            revalidate();
            repaint();
        });
        invoiceDestinationItem.addActionListener(e -> chooseInvoiceDestinationFolder());
        invoiceStatusItem.setEnabled(false);
        invoiceDestinationItem.setText("Invoice destination: session folder");
        invoiceMenu.add(invoiceStatusItem);
        invoiceMenu.add(invoiceDestinationItem);
        invoiceMenu.add(invoicePanelItem);
        menuBar.add(folderMenu);
        menuBar.add(viewMenu);
        menuBar.add(invoiceMenu);
        return menuBar;
    }

    private JPanel createInvoicePanel() {
        invoicePanel.setLayout(new BorderLayout(0, 8));
        invoicePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        invoicePanel.setPreferredSize(new Dimension(330, 0));
        invoicePanel.setMinimumSize(new Dimension(260, 0));

        JLabel title = new JLabel("INVOICES");
        title.setForeground(GOLD);
        title.setFont(new Font("Consolas", Font.BOLD, 16));

        invoiceList.setBackground(new Color(0x050505));
        invoiceList.setForeground(INK);
        invoiceList.setFont(new Font("Consolas", Font.PLAIN, 12));
        invoiceList.setSelectionBackground(new Color(0x3A2A12));
        invoiceList.setSelectionForeground(GOLD);
        invoiceList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getFileName().toString());
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(5, 6, 5, 6));
            label.setFont(new Font("Consolas", Font.PLAIN, 12));
            label.setBackground(isSelected ? new Color(0x3A2A12) : new Color(0x050505));
            label.setForeground(isSelected ? GOLD : INK);
            return label;
        });
        invoiceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedInvoice();
            }
        });

        invoicePreview.setEditable(false);
        invoicePreview.setLineWrap(true);
        invoicePreview.setWrapStyleWord(true);
        invoicePreview.setBackground(new Color(0x050505));
        invoicePreview.setForeground(new Color(0xE8DDC8));
        invoicePreview.setFont(new Font("Consolas", Font.PLAIN, 12));
        invoicePreview.setBorder(new EmptyBorder(8, 8, 8, 8));

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(invoiceList),
                new JScrollPane(invoicePreview)
        );
        split.setResizeWeight(0.4);
        split.setDividerSize(6);
        split.setBorder(BorderFactory.createLineBorder(new Color(0x333333)));

        JButton refreshButton = createButton("REFRESH", new Color(0x3A3A3A));
        refreshButton.addActionListener(e -> refreshInvoiceList());

        invoicePanel.add(title, BorderLayout.NORTH);
        invoicePanel.add(split, BorderLayout.CENTER);
        invoicePanel.add(refreshButton, BorderLayout.SOUTH);
        return invoicePanel;
    }

    private JButton createButton(String text, Color background) {
        JButton button = new ActionButton(text, background);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Consolas", Font.BOLD, 15));
        button.setPreferredSize(new Dimension(0, 46));
        button.setMinimumSize(new Dimension(0, 42));
        return button;
    }

    private JPanel createLabeledField(String labelText, JTextField field) {
        RoundedPanel panel = new RoundedPanel(new Color(0x111111), 8);
        panel.setLayout(new BorderLayout(0, 4));
        panel.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel label = new JLabel(labelText);
        label.setForeground(new Color(0x8B8170));
        label.setFont(new Font("Consolas", Font.BOLD, 12));

        field.setBackground(new Color(0x070707));
        field.setForeground(GOLD);
        field.setCaretColor(GOLD);
        field.setDisabledTextColor(GOLD);
        field.setFont(new Font("Consolas", Font.BOLD, 15));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x38342C)),
                new EmptyBorder(7, 10, 7, 10)
        ));

        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void sessionIn() {
        if (running) {
            appendLog("Session already running. Current booth time: " + currentDuration());
            return;
        }

        if (!collectSessionNames()) {
            return;
        }

        sessionStartNanos = System.nanoTime();
        sessionStartTime = LocalDateTime.now();
        running = true;
        timer.start();
        updateTimer();
        setSessionFieldsLocked(true);

        String stamp = sessionStartTime.format(STAMP_FORMAT);
        statusLabel.setText("Session in progress");
        startLabel.setText("Started at: " + stamp);
        createSessionFolder();
        createInvoiceFile();
        appendLog("SESSION IN  " + stamp + lockedSessionNames());
    }

    private void sessionOut() {
        if (!running) {
            appendLog("No active session to end.");
            return;
        }

        String duration = currentDuration();
        String stamp = LocalDateTime.now().format(STAMP_FORMAT);
        running = false;
        timer.stop();
        setTimerText(duration);
        statusLabel.setText("Session ended");
        appendLog("SESSION OUT " + stamp + lockedSessionNames() + " | Booth time: " + duration);
        appendInvoiceLine("Session out: " + stamp);
        appendInvoiceLine("Total booth time: " + duration);
        appendInvoiceLine("");
        appendInvoiceLine("Invoice note: bill from the total booth time above.");
    }

    private void resetTimer() {
        if (running) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Reset the active booth timer?",
                    "Reset Timer",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        running = false;
        timer.stop();
        sessionStartNanos = 0L;
        lockedArtist = "";
        lockedProducer = "";
        sessionStartTime = null;
        invoicePath = null;
        sessionFolder = null;
        invoiceStatusItem.setText("No invoice created");
        sessionFolderItem.setText("No session folder yet");
        artistField.setText("");
        producerField.setText("");
        setSessionFieldsLocked(false);
        setTimerText("00:00:00");
        statusLabel.setText("Session not started");
        startLabel.setText("Started at: --");
        appendLog("Timer reset.");
    }

    private void updateTimer() {
        if (running) {
            setTimerText(currentDuration());
        }
    }

    private void updateWallClock() {
        wallClockLabel.setText(LocalDateTime.now().format(CLOCK_FORMAT));
        analogClockPanel.repaint();
    }

    private void updateResponsiveType() {
        int width = getWidth();
        int height = getHeight();
        int timerSize = Math.max(54, Math.min(156, Math.min(width / 6, height / 3)));
        int clockSize = width < 500 ? 14 : 18;
        timerLabel.setFont(new Font("Consolas", Font.BOLD, timerSize));
        wallClockLabel.setFont(new Font("Consolas", Font.BOLD, clockSize));
    }

    private void setTimerText(String value) {
        currentTimerText = value;
        timerLabel.setText(value);
        ledTimerDisplay.setValue(value);
    }

    private void setAnalogClockVisible(boolean visible) {
        clockLayout.show(clockDeck, visible ? "analog" : "digital");
    }

    private void chooseMusicBaseFolder() {
        JFileChooser chooser = new JFileChooser(musicBaseFolder.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose default music session folder");

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            musicBaseFolder = chooser.getSelectedFile().toPath().normalize().toAbsolutePath();
            updateFolderMenu();
            appendLog("Music session folder set.");
        }
    }

    private void chooseInvoiceDestinationFolder() {
        Path startFolder = customInvoiceFolder != null ? customInvoiceFolder : musicBaseFolder;
        JFileChooser chooser = new JFileChooser(startFolder.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose invoice destination folder");

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            customInvoiceFolder = chooser.getSelectedFile().toPath().normalize().toAbsolutePath();
            updateInvoiceMenu();
            refreshInvoiceList();
            appendLog("Invoice destination updated.");
        }
    }

    private void updateFolderMenu() {
        baseFolderItem.setText("Default music folder: " + musicBaseFolder.toAbsolutePath());
        if (sessionFolder != null) {
            sessionFolderItem.setText("Current session: " + sessionFolder.getFileName());
        }
    }

    private void updateInvoiceMenu() {
        if (customInvoiceFolder == null) {
            invoiceDestinationItem.setText("Invoice destination: session folder");
        } else {
            invoiceDestinationItem.setText("Invoice destination: " + customInvoiceFolder.toAbsolutePath());
        }
    }

    private void refreshInvoiceList() {
        invoiceListModel.clear();
        addInvoicesFromFolder(customInvoiceFolder);
        addInvoicesFromFolder(musicBaseFolder);
        addInvoicesFromFolder(Paths.get("booth-time-invoices"));

        if (invoicePath != null) {
            invoiceList.setSelectedValue(invoicePath, true);
        } else if (!invoiceListModel.isEmpty()) {
            invoiceList.setSelectedIndex(0);
        } else {
            invoicePreview.setText("No invoices found.");
        }
    }

    private void addInvoicesFromFolder(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }

        boolean includeAllTextFiles = customInvoiceFolder != null && folder.equals(customInvoiceFolder);

        try (var files = Files.walk(folder, 8)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .filter(path -> includeAllTextFiles
                            || path.getFileName().toString().toLowerCase().contains("invoice")
                            || path.getParent().getFileName().toString().equalsIgnoreCase("Invoices"))
                    .sorted()
                    .forEach(path -> {
                        if (!invoiceListModel.contains(path)) {
                            invoiceListModel.addElement(path);
                        }
                    });
        } catch (IOException e) {
            appendLog("Could not scan invoices: " + e.getMessage());
        }
    }

    private void showSelectedInvoice() {
        Path selected = invoiceList.getSelectedValue();
        if (selected == null) {
            return;
        }

        try {
            invoicePreview.setText(Files.readString(selected));
            invoicePreview.setCaretPosition(0);
        } catch (IOException e) {
            invoicePreview.setText("Could not read invoice: " + e.getMessage());
        }
    }

    private String currentDuration() {
        return formatDuration(System.nanoTime() - sessionStartNanos);
    }

    private static String formatDuration(long elapsedNanos) {
        long totalSeconds = Math.max(0L, elapsedNanos / 1_000_000_000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private boolean collectSessionNames() {
        JTextField artistInput = new JTextField(artistField.getText().trim(), 22);
        JTextField producerInput = new JTextField(producerField.getText().trim(), 22);

        JPanel promptPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        promptPanel.add(new JLabel("Artist name"));
        promptPanel.add(artistInput);
        promptPanel.add(new JLabel("Producer name"));
        promptPanel.add(producerInput);

        while (true) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    promptPanel,
                    "Create Booth Session",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) {
                return false;
            }

            String artist = artistInput.getText().trim();
            String producer = producerInput.getText().trim();

            if (!artist.isEmpty() && !producer.isEmpty()) {
                lockedArtist = artist;
                lockedProducer = producer;
                artistField.setText(lockedArtist);
                producerField.setText(lockedProducer);
                return true;
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Artist name and producer name are required before the session starts.",
                    "Names Required",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private void setSessionFieldsLocked(boolean locked) {
        artistField.setEditable(!locked);
        producerField.setEditable(!locked);
        artistField.setEnabled(!locked);
        producerField.setEnabled(!locked);
    }

    private String lockedSessionNames() {
        return " | Artist: " + lockedArtist + " | Producer: " + lockedProducer;
    }

    private void createSessionFolder() {
        try {
            Path artistFolder = musicBaseFolder.resolve(safeFilePart(lockedArtist));
            String sessionName = sessionStartTime.format(FILE_STAMP_FORMAT)
                    + "-" + safeFilePart(lockedProducer);
            sessionFolder = artistFolder.resolve(sessionName);

            Files.createDirectories(sessionFolder.resolve("Audio"));
            Files.createDirectories(sessionFolder.resolve("Projects"));
            Files.createDirectories(sessionFolder.resolve("Exports"));
            Files.createDirectories(sessionFolder.resolve("Invoices"));
            Files.writeString(
                    sessionFolder.resolve("SESSION-README.txt"),
                    "Artist: " + lockedArtist + System.lineSeparator()
                            + "Producer: " + lockedProducer + System.lineSeparator()
                            + "Session in: " + sessionStartTime.format(STAMP_FORMAT) + System.lineSeparator()
                            + "Use this folder for the artist's work from this booth session." + System.lineSeparator(),
                    StandardOpenOption.CREATE_NEW
            );
            updateFolderMenu();
        } catch (IOException e) {
            appendLog("Could not create artist session folder: " + e.getMessage());
        }
    }

    private void createInvoiceFile() {
        try {
            Path invoiceFolder = customInvoiceFolder != null
                    ? customInvoiceFolder
                    : sessionFolder != null
                    ? sessionFolder.resolve("Invoices")
                    : Paths.get("booth-time-invoices");
            Files.createDirectories(invoiceFolder);

            String fileName = sessionStartTime.format(FILE_STAMP_FORMAT)
                    + "-" + safeFilePart(lockedArtist)
                    + "-" + safeFilePart(lockedProducer)
                    + ".txt";
            invoicePath = invoiceFolder.resolve(fileName);

            String stamp = sessionStartTime.format(STAMP_FORMAT);
            String content = "Booth Time Invoice Session" + System.lineSeparator()
                    + "Artist: " + lockedArtist + System.lineSeparator()
                    + "Producer: " + lockedProducer + System.lineSeparator()
                    + "Session in: " + stamp + System.lineSeparator()
                    + "Status: session started" + System.lineSeparator();

            Files.writeString(invoicePath, content, StandardOpenOption.CREATE_NEW);
            invoiceStatusItem.setText("Current invoice: " + invoicePath.getFileName());
            refreshInvoiceList();
        } catch (IOException e) {
            invoiceStatusItem.setText("Invoice creation failed");
            appendLog("Could not create invoice file: " + e.getMessage());
        }
    }

    private void appendInvoiceLine(String line) {
        if (invoicePath == null) {
            return;
        }

        try {
            Files.writeString(
                    invoicePath,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            showSelectedInvoice();
        } catch (IOException e) {
            appendLog("Could not update invoice file: " + e.getMessage());
        }
    }

    private static String safeFilePart(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        safe = safe.replaceAll("-+", "-");
        safe = safe.replaceAll("^-|-$", "");

        if (safe.isEmpty()) {
            return "session";
        }

        return safe;
    }

    private void appendLog(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private static class RackPanel extends JPanel {
        RackPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0x111111));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(0x1E1E1E));
            for (int y = 0; y < getHeight(); y += 4) {
                g2.drawLine(0, y, getWidth(), y);
            }
            g2.setColor(new Color(0x303030));
            g2.fillOval(10, 10, 12, 12);
            g2.fillOval(getWidth() - 22, 10, 12, 12);
            g2.fillOval(10, getHeight() - 22, 12, 12);
            g2.fillOval(getWidth() - 22, getHeight() - 22, 12, 12);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class SevenSegmentDisplay extends JPanel {
        private static final boolean[][] SEGMENTS = {
                {true, true, true, true, true, true, false},
                {false, true, true, false, false, false, false},
                {true, true, false, true, true, false, true},
                {true, true, true, true, false, false, true},
                {false, true, true, false, false, true, true},
                {true, false, true, true, false, true, true},
                {true, false, true, true, true, true, true},
                {true, true, true, false, false, false, false},
                {true, true, true, true, true, true, true},
                {true, true, true, true, false, true, true}
        };

        private String value = "00:00:00";

        SevenSegmentDisplay() {
            setOpaque(false);
            setPreferredSize(new Dimension(820, 260));
        }

        void setValue(String value) {
            this.value = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(0x030303));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);

            String digitsOnly = value.replace(":", "");
            int digitCount = digitsOnly.length();
            int colonCount = Math.max(0, value.length() - digitCount);
            int gap = Math.max(6, getWidth() / 90);
            int colonWidth = Math.max(12, getWidth() / 42);
            int available = getWidth() - 28 - (digitCount - 1) * gap - colonCount * (colonWidth + gap);
            int digitWidth = Math.max(34, available / digitCount);
            int digitHeight = getHeight() - 34;
            int x = 14;
            int y = 16;

            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == ':') {
                    drawColon(g2, x, y, colonWidth, digitHeight);
                    x += colonWidth + gap;
                } else if (Character.isDigit(ch)) {
                    drawDigit(g2, ch - '0', x, y, digitWidth, digitHeight);
                    x += digitWidth + gap;
                }
            }

            g2.setColor(new Color(255, 180, 70, 26));
            g2.fillRoundRect(0, 0, getWidth(), getHeight() / 3, 8, 8);
            g2.dispose();
        }

        private void drawDigit(Graphics2D g2, int number, int x, int y, int w, int h) {
            int thickness = Math.max(8, Math.min(w, h) / 8);
            int slant = Math.max(4, thickness / 2);
            boolean[] on = SEGMENTS[number];

            drawSegment(g2, horizontal(x + thickness, y, w - thickness * 2, thickness, slant), on[0]);
            drawSegment(g2, vertical(x + w - thickness, y + thickness, thickness, h / 2 - thickness, slant), on[1]);
            drawSegment(g2, vertical(x + w - thickness, y + h / 2, thickness, h / 2 - thickness, slant), on[2]);
            drawSegment(g2, horizontal(x + thickness, y + h - thickness, w - thickness * 2, thickness, slant), on[3]);
            drawSegment(g2, vertical(x, y + h / 2, thickness, h / 2 - thickness, slant), on[4]);
            drawSegment(g2, vertical(x, y + thickness, thickness, h / 2 - thickness, slant), on[5]);
            drawSegment(g2, horizontal(x + thickness, y + h / 2 - thickness / 2, w - thickness * 2, thickness, slant), on[6]);
        }

        private Polygon horizontal(int x, int y, int w, int h, int slant) {
            Polygon p = new Polygon();
            p.addPoint(x + slant, y);
            p.addPoint(x + w - slant, y);
            p.addPoint(x + w, y + h / 2);
            p.addPoint(x + w - slant, y + h);
            p.addPoint(x + slant, y + h);
            p.addPoint(x, y + h / 2);
            return p;
        }

        private Polygon vertical(int x, int y, int w, int h, int slant) {
            Polygon p = new Polygon();
            p.addPoint(x + w / 2, y);
            p.addPoint(x + w, y + slant);
            p.addPoint(x + w, y + h - slant);
            p.addPoint(x + w / 2, y + h);
            p.addPoint(x, y + h - slant);
            p.addPoint(x, y + slant);
            return p;
        }

        private void drawSegment(Graphics2D g2, Shape segment, boolean on) {
            if (on) {
                g2.setColor(LED_GLOW);
                g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(segment);
                g2.setColor(LED_ON);
            } else {
                g2.setColor(LED_OFF);
            }
            g2.fill(segment);
        }

        private void drawColon(Graphics2D g2, int x, int y, int w, int h) {
            int dot = Math.max(10, w);
            int cx = x + w / 2 - dot / 2;
            g2.setColor(LED_GLOW);
            g2.fillOval(cx - 4, y + h / 3 - 4, dot + 8, dot + 8);
            g2.fillOval(cx - 4, y + h * 2 / 3 - 4, dot + 8, dot + 8);
            g2.setColor(LED_ON);
            g2.fillOval(cx, y + h / 3, dot, dot);
            g2.fillOval(cx, y + h * 2 / 3, dot, dot);
        }
    }

    private static class RoundedPanel extends JPanel {
        private final Color background;
        private final int radius;

        RoundedPanel(Color background, int radius) {
            this.background = background;
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class AnalogClockPanel extends JPanel {
        AnalogClockPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(520, 300));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 12;
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            int radius = Math.max(44, size / 2);

            g2.setColor(new Color(0x060806));
            g2.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
            g2.setStroke(new BasicStroke(5f));
            g2.setColor(AMBER);
            g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

            for (int tick = 0; tick < 60; tick++) {
                double angle = Math.toRadians(tick * 6 - 90);
                int outer = radius - 8;
                int inner = tick % 5 == 0 ? radius - 20 : radius - 13;
                int x1 = centerX + (int) (Math.cos(angle) * inner);
                int y1 = centerY + (int) (Math.sin(angle) * inner);
                int x2 = centerX + (int) (Math.cos(angle) * outer);
                int y2 = centerY + (int) (Math.sin(angle) * outer);
                g2.setStroke(new BasicStroke(tick % 5 == 0 ? 3f : 1f));
                g2.setColor(tick % 5 == 0 ? GOLD : new Color(0x77613A));
                g2.drawLine(x1, y1, x2, y2);
            }

            LocalDateTime now = LocalDateTime.now();
            double secondAngle = Math.toRadians(now.getSecond() * 6 - 90);
            double minuteAngle = Math.toRadians((now.getMinute() + now.getSecond() / 60.0) * 6 - 90);
            double hourAngle = Math.toRadians(((now.getHour() % 12) + now.getMinute() / 60.0) * 30 - 90);

            drawHand(g2, centerX, centerY, hourAngle, radius * 0.46, 6f, INK);
            drawHand(g2, centerX, centerY, minuteAngle, radius * 0.68, 4f, GREEN);
            drawHand(g2, centerX, centerY, secondAngle, radius * 0.76, 2f, RED);

            g2.setColor(GOLD);
            g2.fillOval(centerX - 6, centerY - 6, 12, 12);
            g2.dispose();
        }

        private void drawHand(Graphics2D g2, int centerX, int centerY, double angle, double length, float width, Color color) {
            int x = centerX + (int) (Math.cos(angle) * length);
            int y = centerY + (int) (Math.sin(angle) * length);
            g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(color);
            g2.drawLine(centerX, centerY, x, y);
        }
    }

    private static class ActionButton extends JButton {
        private final Color background;

        ActionButton(String text, Color background) {
            super(text);
            this.background = background;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getModel().isPressed() ? background.darker() : background);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(graphics);
        }
    }
}
