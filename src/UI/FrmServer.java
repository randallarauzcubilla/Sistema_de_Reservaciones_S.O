package UI;

import Server.ClientHandler;
import Core.Reservation;
import Persistence.ReservationPersistence;
import Concurrency.ReservationTTLThread;
import Security.RoleValidator;
import Server.ServerApp;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server Management Dashboard (GUI). Provides a real-time interface to monitor
 * auditorium capacity, equipment availability, and system logs.
 */
public class FrmServer extends JFrame {

    // === INSTITUTIONAL COLOR PALETTE (UNA) === 
    private static final Color UNA_RED = new Color(0xCD, 0x17, 0x19);
    private static final Color UNA_BLUE = new Color(0x03, 0x49, 0x91);
    private static final Color UNA_GRAY = new Color(0xA7, 0xA7, 0xA9);
    private static final Color UNA_WHITE = new Color(0xFF, 0xFF, 0xFF);
    private static final Color UNA_BLACK = new Color(0x1A, 0x1A, 0x1A);

    private static final Color BG_MAIN = new Color(0xF5, 0xF5, 0xF5);
    private static final Color BG_SIDEBAR = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_HEADER = new Color(0xCD, 0x17, 0x19);
    private static final Color BG_CARD = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_ROW_EVEN = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_ROW_ODD = new Color(0xF0, 0xF4, 0xF9);
    private static final Color BG_LOG = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_FIELD = new Color(0xF8, 0xF8, 0xF8);

    // === TEXT COLORS ===
    private static final Color TEXT_HEADER = new Color(0xFF, 0xFF, 0xFF);
    private static final Color TEXT_DARK = new Color(0x1A, 0x1A, 0x1A);
    private static final Color TEXT_MEDIUM = new Color(0x55, 0x55, 0x66);
    private static final Color TEXT_MUTED = new Color(0x88, 0x88, 0x99);
    private static final Color TEXT_LOG = new Color(0x8B, 0x00, 0x00);

    // === BORDERS ===
    private static final Color BORDER_CARD = new Color(0xE0, 0xE4, 0xEA);
    private static final Color BORDER_TABLE = new Color(0xD0, 0xD8, 0xE8);

    // === FUNCTIONAL ACCENTS ===
    private static final Color COLOR_ACTIVE = new Color(0x03, 0x49, 0x91);
    private static final Color COLOR_INACTIVE = new Color(0xCD, 0x17, 0x19);
    private static final Color COLOR_CONFIRM = new Color(0x22, 0x8B, 0x22);
    private static final Color COLOR_TEMP = new Color(0xD4, 0x7B, 0x00);
    private static final Color COLOR_AMBAR = new Color(0xD4, 0x7B, 0x00);

    // === STATUS ===
    private boolean isServerRunning = false;
    private Thread serverThread;
    private Timer updateTimer;
    private java.net.ServerSocket activeSocket;

    // === LABELS ===
    private JLabel lblStatusValue;
    private JLabel lblCapacityValue;
    private JLabel lblReservationsValue;
    private JLabel lblProjectorValue;
    private JLabel lblMicrophoneValue;
    private JLabel lblSoundValue;
    private JLabel lblFullSetValue;

    // === TABLES ===
    private JTable calendarTable;
    private DefaultTableModel tableModel;

    // === BUTTONS ===
    private JButton btnStart;
    private JButton btnStop;
    private JButton btnLog;
    private JButton btnEditReservation;
    private JButton btnCancelReservation;

    // === LOG ===
    private JTextArea txtLogArea;

    /**
     * Initializes the Server Frame. Sets up the window properties,
     * institutional branding, and ensures data persistence upon closing.
     */
    public FrmServer() {
        setTitle("Universidad Nacional — Panel de Administración");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1040, 740);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        setBackground(BG_MAIN);
        initComponents();
        log("Sistema iniciado. Presione 'Iniciar Servidor' para comenzar.");

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                ReservationPersistence.save(ServerApp.calendar);
                System.out.println(
                        "[CIERRE] Reservas guardadas.");
                System.exit(0);
            }
        });
    }

    /**
     * Orchestrates the GUI layout construction. Builds the root container using
     * a BorderLayout to organize the header, navigation sidebar, and the main
     * content area (dashboard).
     */
    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_MAIN);
        root.add(crearHeader(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 0));
        centro.setBackground(BG_MAIN);
        centro.add(crearSidebar(), BorderLayout.WEST);
        centro.add(createMainBody(), BorderLayout.CENTER);

        root.add(centro, BorderLayout.CENTER);
        add(root);
    }

    /**
     * Creates the top header panel with institutional branding. Includes custom
     * graphics rendering for geometric background accents and a real-time clock
     * synchronization.
     *
     * @return A styled JPanel with the system title and current time.
     */
    private JPanel crearHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(BG_HEADER);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRect(0, getHeight() - 3,
                        getWidth(), 3);

                g2.setColor(new Color(0xFF, 0xFF, 0xFF, 18));
                int[] xp = {getWidth() - 120, getWidth(),
                    getWidth()};
                int[] yp = {0, 0, getHeight()};
                g2.fillPolygon(xp, yp, 3);
                g2.setColor(new Color(0xFF, 0xFF, 0xFF, 10));
                int[] xp2 = {getWidth() - 220, getWidth() - 80,
                    getWidth()};
                int[] yp2 = {0, 0, getHeight()};
                g2.fillPolygon(xp2, yp2, 3);
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 64));
        header.setBackground(BG_HEADER);
        header.setBorder(new EmptyBorder(0, 20, 0, 24));

        JPanel titPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 0, 0));
        titPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("PANEL DE ADMINISTRACIÓN");
        lblTitle.setFont(new Font("Serif", Font.BOLD, 15));
        lblTitle.setForeground(new Color(0xFF, 0xFF, 0xFF, 220));
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        titPanel.add(lblTitle);
        header.add(titPanel, BorderLayout.CENTER);

        JLabel lblClock = new JLabel("");
        lblClock.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblClock.setForeground(new Color(0xFF, 0xFF, 0xFF, 160));
        header.add(lblClock, BorderLayout.EAST);

        Timer clockTimer = new Timer(1000, e -> {
            lblClock.setText(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
        clockTimer.start();

        return header;
    }

    /**
     * Builds the side control panel. Contains real-time resource monitoring
     * cards and system action buttons. Uses a vertical layout to separate
     * header info, metrics, and controls.
     *
     * @return A sidebar JPanel with integrated monitoring and controls.
     */
    private JPanel crearSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(320, 0));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, BORDER_CARD));

        JPanel infoHeader = new JPanel(new BorderLayout(0, 8));
        infoHeader.setBackground(BG_SIDEBAR);
        infoHeader.setBorder(new EmptyBorder(14, 18, 12, 18));

        JPanel labelContainer = new JPanel(new GridLayout(2, 1));
        labelContainer.setBackground(BG_SIDEBAR);
        JLabel lblMainInfo = new JLabel("Reservas de Sala");
        lblMainInfo.setFont(new Font("Serif", Font.BOLD, 14));
        lblMainInfo.setForeground(UNA_RED);
        JLabel lblSubInfo = new JLabel("Monitor de Recursos");
        lblSubInfo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblSubInfo.setForeground(TEXT_MUTED);
        labelContainer.add(lblMainInfo);
        labelContainer.add(lblSubInfo);
        infoHeader.add(labelContainer, BorderLayout.CENTER);

        sidebar.add(infoHeader, BorderLayout.NORTH);

        JPanel cardsContainer = new JPanel(new GridLayout(0, 2, 8, 8));
        cardsContainer.setBackground(BG_SIDEBAR);
        cardsContainer.setBorder(new EmptyBorder(6, 12, 6, 12));

        lblStatusValue = new JLabel("INACTIVO");
        lblReservationsValue = new JLabel("—");
        lblCapacityValue = new JLabel("—");
        lblProjectorValue = new JLabel("—");
        lblMicrophoneValue = new JLabel("—");
        lblSoundValue = new JLabel("—");
        lblFullSetValue = new JLabel("—");

        lblStatusValue.setForeground(COLOR_INACTIVE);

        cardsContainer.add(crearCard("Estado", lblStatusValue, "●"));
        cardsContainer.add(crearCard("Capacidad", lblCapacityValue, "◈"));
        cardsContainer.add(crearCard("Reservas", lblReservationsValue, "◉"));
        cardsContainer.add(crearCard("Proyectores", lblProjectorValue, "▣"));
        cardsContainer.add(crearCard("Micrófonos", lblMicrophoneValue, "♪"));
        cardsContainer.add(crearCard("Sonido", lblSoundValue, "◎"));
        cardsContainer.add(crearCard("Completo", lblFullSetValue, "✦"));
        sidebar.add(cardsContainer, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new GridLayout(5, 1, 0, 6));
        actionPanel.setBackground(BG_SIDEBAR);
        actionPanel.setBorder(new EmptyBorder(10, 12, 18, 12));

        btnStart = createButton(
                "▶  Iniciar Servidor", UNA_BLUE, false);
        btnStop = createButton(
                "■  Detener Servidor", UNA_RED, false);
        btnLog = createButton(
                "↻  Actualizar Vista", UNA_GRAY, true);
        btnEditReservation = createButton(
                "✎  Editar Reserva", COLOR_AMBAR, true);
        btnCancelReservation = createButton(
                "✖  Cancelar Reserva", UNA_RED, true);

        btnStop.setEnabled(false);
        btnEditReservation.setEnabled(false);
        btnCancelReservation.setEnabled(false);

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());
        btnLog.addActionListener(e -> refreshView());
        btnEditReservation.addActionListener(e -> {
            try {
                editSelectedReservation();
            } catch (InterruptedException ex) {
                Logger.getLogger(FrmServer.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        });
        btnCancelReservation.addActionListener(
                e -> cancelSelectedReservation());

        actionPanel.add(btnStart);
        actionPanel.add(btnStop);
        actionPanel.add(btnLog);
        actionPanel.add(btnEditReservation);
        actionPanel.add(btnCancelReservation);
        sidebar.add(actionPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    /**
     * Factory method for creating monitoring cards. Each card displays a
     * specific resource metric with a title, icon, and dynamic value.
     *
     * @param title The descriptive name of the resource.
     * @param valueLabel The JLabel that will hold the dynamic data.
     * @param icon A symbolic character or icon for visual reference.
     * @return A styled JPanel acting as a data card.
     */
    private JPanel crearCard(
            String title, JLabel valueLabel, String icon) {
        JPanel card = new JPanel(new BorderLayout(3, 3));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CARD, 1),
                new EmptyBorder(8, 10, 8, 10)));

        JLabel lblHeader = new JLabel(icon + "  " + title);
        lblHeader.setFont(new Font("SansSerif", Font.PLAIN, 9));
        lblHeader.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font("Serif", Font.BOLD, 13));
        if (valueLabel.getForeground().equals(
                new Color(0, 0, 0))) {
            valueLabel.setForeground(TEXT_DARK);
        }

        card.add(lblHeader, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Factory method for high-fidelity custom buttons. Overrides paintComponent
     * to provide rounded corners, anti-aliased rendering, and dynamic visual
     * feedback for hover, press, and disabled states.
     *
     * @param text The button label.
     * @param color The theme color for the button.
     * @param isSecondary If true, renders a subtle, semi-transparent style.
     * @return A customized JButton with advanced graphics.
     */
    private JButton createButton(
            String text, Color color, boolean isSecondary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(0xE8, 0xE8, 0xE8));
                } else if (getModel().isPressed()) {
                    g2.setColor(color.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(
                            isSecondary
                                    ? new Color(color.getRed(),
                                            color.getGreen(),
                                            color.getBlue(), 30)
                                    : color.brighter());
                } else {
                    g2.setColor(
                            isSecondary
                                    ? new Color(color.getRed(),
                                            color.getGreen(),
                                            color.getBlue(), 15)
                                    : color);
                }
                g2.fillRoundRect(0, 0,
                        getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        if (isSecondary) {
            btn.setForeground(color.darker());
        } else {
            btn.setForeground(UNA_WHITE);
        }
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(color.getRed(), color.getGreen(),
                                color.getBlue(),
                                isSecondary ? 100 : 180), 1),
                new EmptyBorder(7, 10, 7, 10)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Constructs the main body of the dashboard. Organizes the reservation
     * table and the system log area with decorative institutional accents.
     *
     * @return A JPanel containing the central workspace.
     */
    private JPanel createMainBody() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(BG_MAIN);
        body.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel titlePanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        JLabel lblSectionTitle = new JLabel("Calendario de Reservas");
        lblSectionTitle.setFont(new Font("Serif", Font.BOLD, 16));
        lblSectionTitle.setForeground(TEXT_DARK);

        JPanel titleWrapper = new JPanel(new BorderLayout(0, 4));
        titleWrapper.setOpaque(false);
        titleWrapper.setBorder(new EmptyBorder(0, 0, 8, 0));
        titleWrapper.add(lblSectionTitle, BorderLayout.NORTH);
        // Custom painting for the UNA-styled decorative line
        JPanel decorativeLine = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(UNA_RED);
                g.fillRect(0, 0, 40, 3);
                g.setColor(UNA_GRAY);
                g.fillRect(44, 0, 20, 3);
            }
        };
        decorativeLine.setOpaque(false);
        decorativeLine.setPreferredSize(new Dimension(0, 6));
        titleWrapper.add(decorativeLine, BorderLayout.SOUTH);

        body.add(titleWrapper, BorderLayout.NORTH);
        body.add(createTablePanel(), BorderLayout.CENTER);
        body.add(createLogPanel(), BorderLayout.SOUTH);
        return body;
    }

    /**
     * Initializes the reservation table panel. Configures the data model,
     * custom cell rendering for status-based coloring, and selection listeners
     * to manage action button states.
     *
     * @return A JScrollPane containing the styled JTable.
     */
    private JScrollPane createTablePanel() {
        String[] cols = {
            "ID Reserva", "Usuario", "Fecha",
            "Horario", "Estado", "Asistentes",
            "Equipo", "Vigencia"
        };
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        calendarTable = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(
                    TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                boolean selected = isRowSelected(row);
                if (selected) {
                    c.setBackground(
                            new Color(0x03, 0x49, 0x91, 38));
                    c.setForeground(UNA_BLUE);
                } else {
                    c.setBackground(row % 2 == 0
                            ? BG_ROW_EVEN : BG_ROW_ODD);
                    c.setForeground(UNA_BLACK);

                    Object status
                            = tableModel.getValueAt(row, 4);
                    if ("CONFIRMADO".equals(status)) {
                        c.setForeground(COLOR_CONFIRM);
                    } else if ("CANCELADO".equals(status)) {
                        c.setForeground(UNA_GRAY);
                    } else if ("EXPIRADO".equals(status)) {
                        c.setForeground(COLOR_AMBAR);
                    } else if ("RESERVADO_TEMPORAL".equals(status)) {
                        c.setForeground(COLOR_TEMP);
                    }
                }
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(
                            new EmptyBorder(0, 10, 0, 10));
                }
                return c;
            }
        };

        calendarTable.setFont(
                new Font("SansSerif", Font.PLAIN, 12));
        calendarTable.setRowHeight(30);
        calendarTable.setBackground(BG_ROW_EVEN);
        calendarTable.setForeground(TEXT_DARK);
        calendarTable.setGridColor(BORDER_TABLE);
        calendarTable.setShowVerticalLines(false);
        calendarTable.setIntercellSpacing(
                new Dimension(0, 1));
        calendarTable.setSelectionBackground(
                new Color(0x03, 0x49, 0x91, 38));
        calendarTable.setSelectionForeground(UNA_BLUE);

        JTableHeader header = calendarTable.getTableHeader();
        header.setBackground(UNA_RED);
        header.setForeground(UNA_WHITE);
        header.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.setBorder(BorderFactory.createMatteBorder(
                0, 0, 2, 0,
                new Color(0xAA, 0x10, 0x10)));
        header.setPreferredSize(new Dimension(0, 34));

        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                lbl.setBackground(UNA_RED);
                lbl.setForeground(UNA_WHITE);
                lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
                lbl.setOpaque(true);
                return lbl;
            }
        });
        calendarTable.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        boolean hasSelection
                                = calendarTable.getSelectedRow() >= 0;
                        btnEditReservation.setEnabled(
                                hasSelection && isServerRunning);
                        btnCancelReservation.setEnabled(
                                hasSelection && isServerRunning);
                    }
                });
        JScrollPane scrollPane
                = new JScrollPane(calendarTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(
                BORDER_TABLE, 1));
        scrollPane.getViewport().setBackground(BG_ROW_EVEN);

        scrollPane.getVerticalScrollBar()
                .setBackground(BG_MAIN);
        return scrollPane;
    }

    /**
     * Initializes the system log panel. Features a real-time JTextArea for
     * operational monitoring, styled with institutional accents and a
     * specialized scroll pane.
     *
     * @return A JPanel containing the system's log console.
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_MAIN);
        panel.setPreferredSize(new Dimension(0, 190));

        JPanel logHeader = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));
        logHeader.setOpaque(false);
        logHeader.setBorder(new EmptyBorder(0, 0, 4, 0));

        JPanel redAccent = new JPanel();
        redAccent.setBackground(UNA_RED);
        redAccent.setPreferredSize(new Dimension(4, 16));

        JLabel lblLogTitle = new JLabel("  Bitácora en tiempo real");
        lblLogTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblLogTitle.setForeground(TEXT_MEDIUM);
        logHeader.add(redAccent);
        logHeader.add(lblLogTitle);

        panel.add(logHeader, BorderLayout.NORTH);

        txtLogArea = new JTextArea();
        txtLogArea.setEditable(false);
        txtLogArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtLogArea.setBackground(BG_LOG);
        txtLogArea.setForeground(TEXT_LOG);
        txtLogArea.setCaretColor(TEXT_LOG);
        txtLogArea.setLineWrap(true);
        txtLogArea.setWrapStyleWord(true);
        txtLogArea.setBorder(
                new EmptyBorder(10, 14, 10, 14));

        JScrollPane logScroll = new JScrollPane(txtLogArea);
        logScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(0xCD, 0x17, 0x19, 80), 1),
                BorderFactory.createEmptyBorder()));
        panel.add(logScroll, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Starts the server engine. Initializes role validation, restores persisted
     * data, manages the main server socket, and starts the TTL management
     * thread.
     */
    private void startServer() {
        isServerRunning = true;
        lblStatusValue.setText("ACTIVO");
        lblStatusValue.setForeground(COLOR_ACTIVE);
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        log("Servidor INICIADO en puerto 8000.");

        serverThread = new Thread(() -> {
            RoleValidator.load();

            List<Reservation> restoredList
                    = ReservationPersistence.load();
            for (Reservation r : restoredList) {
                ServerApp.calendar
                        .loadRestoredReservation(r);
            }
            SwingUtilities.invokeLater(() -> {
                if (!restoredList.isEmpty()) {
                    log("✔ " + restoredList.size()
                            + " reserva(s) restauradas "
                            + "desde disco.");
                } else {
                    log("No hay reservas previas "
                            + "que restaurar.");
                }
            });

            try {
                activeSocket
                        = new java.net.ServerSocket(8000);

                ReservationTTLThread ttlHandler
                        = new ReservationTTLThread(
                                ServerApp.calendar,
                                ServerApp.resources,
                                ServerApp.ttlQueue,
                                ServerApp.log);
                ttlHandler.setDaemon(true);
                ttlHandler.start();

                System.out.println(
                        "[SERVIDOR] Puerto 8000 abierto, "
                        + "esperando clientes...");

                while (!Thread.currentThread()
                        .isInterrupted()
                        && !activeSocket.isClosed()) {

                    java.net.Socket clientSocket
                            = activeSocket.accept();
                    java.io.DataInputStream inputStream
                            = new java.io.DataInputStream(
                                    new java.io.BufferedInputStream(
                                            clientSocket
                                                    .getInputStream()));
                    String clientData
                            = inputStream.readUTF();
                    System.out.println(
                            "[SERVIDOR] Cliente: "
                            + clientData);

                    ClientHandler handler = new ClientHandler(
                            clientSocket, clientData,
                            ServerApp.calendar,
                            ServerApp.resources,
                            ServerApp.ttlQueue,
                            ServerApp.log);
                    ServerApp.connectedClients.add(handler);
                    handler.start();
                }
            } catch (java.io.IOException e) {
                if (isServerRunning) {
                    log("[ERROR] Servidor: "
                            + e.getMessage());
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        updateTimer
                = new Timer(2000, e -> refreshView());
        updateTimer.start();
        refreshView();
    }

    /**
     * Safely shuts down the server. Stops the UI timer, persists data to disk,
     * notifies and disconnects all active clients, and releases the network
     * port.
     */
    private void stopServer() {
        isServerRunning = false;

        if (updateTimer != null) {
            updateTimer.stop();
        }

        ReservationPersistence.save(
                ServerApp.calendar);
        log("Reservas confirmadas guardadas en disco.");

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler handler
                    : ServerApp.connectedClients) {
                handler.send("ERROR|SERVIDOR_DETENIDO");
                handler.close();
            }
            ServerApp.connectedClients.clear();
        }

        try {
            if (activeSocket != null
                    && !activeSocket.isClosed()) {
                activeSocket.close();
            }
        } catch (java.io.IOException ignored) {
            // Silently ignore during shutdown
        }

        activeSocket = null;

        if (serverThread != null) {
            serverThread.interrupt();
        }

        lblStatusValue.setText("INACTIVO");
        lblStatusValue.setForeground(COLOR_INACTIVE);
        lblReservationsValue.setText("—");
        lblProjectorValue.setText("—");
        lblCapacityValue.setText("—");
        lblMicrophoneValue.setText("—");
        lblSoundValue.setText("—");
        lblFullSetValue.setText("—");

        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        btnEditReservation.setEnabled(false);
        btnCancelReservation.setEnabled(false);

        log("Servidor DETENIDO.");
    }

    /**
     * Synchronizes the UI components with the current server state. Updates
     * dashboard metrics, the real-time log area, and populates the reservation
     * table while maintaining the user's current selection.
     */
    private void refreshView() {
        if (!isServerRunning) {
            return;
        }

        lblReservationsValue.setText(
                ServerApp.calendar.getTotalReservations()
                + " activas");
        lblCapacityValue.setText(
                String.valueOf(
                        ServerApp.manager.getAvailableCapacity()));
        int proyCount
                = ServerApp.manager.getAvailableProjectors();
        int micCount
                = ServerApp.manager.getAvailableMicrophones();
        int soundCount
                = ServerApp.manager.getAvailableSound();

        lblProjectorValue.setText(String.valueOf(proyCount));
        lblMicrophoneValue.setText(String.valueOf(micCount));
        lblSoundValue.setText(String.valueOf(soundCount));

        int fullSets = Math.min(proyCount, Math.min(micCount, soundCount));
        lblFullSetValue.setText(String.valueOf(fullSets));

        List<String> logEntries
                = ServerApp.log.getLast(100);
        txtLogArea.setText("");
        for (String e : logEntries) {
            txtLogArea.append(e + "\n");
        }
        txtLogArea.setCaretPosition(
                txtLogArea.getDocument().getLength());

        String selectedId = null;
        int currentRow = calendarTable.getSelectedRow();
        if (currentRow >= 0) {
            selectedId
                    = (String) tableModel.getValueAt(
                            currentRow, 0);
        }

        tableModel.setRowCount(0);
        List<Reservation> allReservations
                = ServerApp.calendar.getAllReservations();
        int rowToRestore = -1;
        int rowCounter = 0;

        for (Reservation r : allReservations) {
            tableModel.addRow(new Object[]{
                r.getReservationId(),
                r.getClientId(),
                r.getDate(),
                r.getStartTime() + "-" + r.getEndTime(),
                r.getStatus().toString(),
                r.getAttendeeCount(),
                r.getEquipment().toString(),
                r.getStatus()
                == Reservation.Status.RESERVADO_TEMPORAL
                ? r.getRemainingSeconds() + "s"
                : "—"
            });
            if (r.getReservationId().equals(selectedId)) {
                rowToRestore = rowCounter;
            }
            rowCounter++;
        }

        if (rowToRestore >= 0) {
            calendarTable.setRowSelectionInterval(
                    rowToRestore, rowToRestore);
        }
        int finalSelectedRow = calendarTable.getSelectedRow();
        boolean hasSelection = finalSelectedRow >= 0;
        boolean canEdit = hasSelection && isServerRunning
             && !"CANCELADO".equals(tableModel.getValueAt(finalSelectedRow, 4));
        btnEditReservation.setEnabled(canEdit);
        btnCancelReservation.setEnabled(canEdit);
    }

    /**
     * Opens a dialog to edit the selected reservation. Validates business rules
     * for ongoing and future reservations, performs a rollback if the new slot
     * is unavailable, and notifies the client.
     */
    private void editSelectedReservation()
            throws InterruptedException {
        int selectedRow = calendarTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        String resId
                = (String) tableModel.getValueAt(selectedRow, 0);
        String clientId
                = (String) tableModel.getValueAt(selectedRow, 1);
        String currentStrDate
                = (String) tableModel.getValueAt(selectedRow, 2);
        String schedule
                = (String) tableModel.getValueAt(selectedRow, 3);
        String[] hours = schedule.split("-");

        JTextField txtDate = new JTextField(currentStrDate);
        JTextField txtStart = new JTextField(
                hours.length > 0 ? hours[0] : "");
        JTextField txtEnd = new JTextField(
                hours.length > 1 ? hours[1] : "");
        JTextField txtAttendees = new JTextField(
                tableModel.getValueAt(selectedRow, 5).toString());
        JComboBox<String> cbEquipment = new JComboBox<>(
                new String[]{
                    "NINGUNO", "PROYECTOR", "MICROFONO",
                    "SONIDO", "COMPLETO"
                });
        cbEquipment.setSelectedItem(
                tableModel.getValueAt(selectedRow, 6).toString());

        styleFormField(txtDate);
        styleFormField(txtStart);
        styleFormField(txtEnd);
        styleFormField(txtAttendees);

        JPanel formPanel = new JPanel(
                new GridLayout(0, 2, 8, 10));
        formPanel.setBackground(BG_MAIN);
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        formPanel.add(createFormLabel("Fecha (YYYY-MM-DD):"));
        formPanel.add(txtDate);
        formPanel.add(createFormLabel("Hora inicio (HH:mm):"));
        formPanel.add(txtStart);
        formPanel.add(createFormLabel("Hora fin (HH:mm):"));
        formPanel.add(txtEnd);
        formPanel.add(createFormLabel("Asistentes:"));
        formPanel.add(txtAttendees);
        formPanel.add(createFormLabel("Equipo:"));
        formPanel.add(cbEquipment);

        int result = JOptionPane.showConfirmDialog(
                this, formPanel,
                "Editar reserva " + resId
                + "  |  cliente: " + clientId,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String newDateStr = txtDate.getText().trim();
        String newStartStr = txtStart.getText().trim();
        String newEndStr = txtEnd.getText().trim();
        String newAttStr = txtAttendees.getText().trim();
        String newEquipStr = (String) cbEquipment.getSelectedItem();

        if (newDateStr.isEmpty() || newStartStr.isEmpty()
                || newEndStr.isEmpty()
                || newAttStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Complete todos los campos.",
                    "Campos vacíos",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate newDate;
        try {
            newDate = LocalDate.parse(newDateStr);
            if (newDate.getYear() != LocalDate.now().getYear()) {
                JOptionPane.showMessageDialog(this,
                        "Solo se permiten reservas dentro del año actual ("
                        + LocalDate.now().getYear() + ").",
                        "Año no permitido", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (newDate.isBefore(LocalDate.now())) {
                JOptionPane.showMessageDialog(this,
                        "No se pueden hacer reservas en fechas pasadas.",
                        "Fecha inválida", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (HeadlessException e) {
            JOptionPane.showMessageDialog(this,
                    "Formato de fecha inválido. Use YYYY-MM-DD.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalTime newStart, newEnd;
        try {
            newStart = LocalTime.parse(newStartStr);
            newEnd = LocalTime.parse(newEndStr);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Hora inválida. Use HH:mm.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!newStart.isBefore(newEnd)) {
            JOptionPane.showMessageDialog(this,
                    "La hora de inicio debe ser menor a la hora fin.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int attendees;
        try {
            attendees = Integer.parseInt(newAttStr);
            if (attendees < 0 || attendees > 200) {
                JOptionPane.showMessageDialog(this,
                        "Los asistentes deben estar entre 0 y 200.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                    "Asistentes debe ser un número.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Reservation original = ServerApp.calendar.getReservationById(resId);
        if (original == null) {
            JOptionPane.showMessageDialog(this,
                    "Reserva no encontrada.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime newStartFull = LocalDateTime.of(newDate, newStart);
        LocalDateTime newEndFull = LocalDateTime.of(newDate, newEnd);

        LocalDateTime oldStartFull = LocalDateTime.of(
                LocalDate.parse(original.getDate()),
                LocalTime.parse(original.getStartTime()));

        LocalDateTime oldEndFull = LocalDateTime.of(
                LocalDate.parse(original.getDate()),
                LocalTime.parse(original.getEndTime()));

        if (oldEndFull.isBefore(now)) {
            JOptionPane.showMessageDialog(this,
                    "La reserva ya finalizó. Debes crear una nueva.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (oldStartFull.isBefore(now) && oldEndFull.isAfter(now)) {

            if (!newDate.equals(oldStartFull.toLocalDate())) {
                JOptionPane.showMessageDialog(this,
                        "No puedes cambiar la fecha de una reserva en curso.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!newStartFull.equals(oldStartFull)) {
                JOptionPane.showMessageDialog(this,
                        "No puedes modificar la hora de inicio de una reserva "
                                + "en curso.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newEndFull.isBefore(now)) {
                JOptionPane.showMessageDialog(this,
                        "No puedes poner una hora fin que ya pasó.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (newEndFull.isBefore(oldStartFull)) {
                JOptionPane.showMessageDialog(this,
                        "La hora fin no puede ser anterior al inicio original.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (oldStartFull.isAfter(now)) {
            if (newStartFull.isBefore(now)) {
                JOptionPane.showMessageDialog(this,
                        "No puedes usar fechas u horas pasadas.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        ServerApp.calendar.cancelReservation(resId);
        ServerApp.ttlQueue.remove(resId);

        Reservation newRes;
        try {
            newRes = ServerApp.calendar.reserveTemporarily(
                    clientId, newDateStr,
                    newStartStr, newEndStr,
                    Integer.parseInt(newAttStr),
                    Reservation.Equipment.valueOf(newEquipStr),
                    original.getPriority());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Asistentes debe ser un número.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            ServerApp.calendar.reserveTemporarily(
                    clientId, original.getDate(),
                    original.getStartTime(),
                    original.getEndTime(),
                    original.getAttendeeCount(),
                    original.getEquipment(),
                    original.getPriority());
            return;
        }

        if (newRes == null) {
            JOptionPane.showMessageDialog(this,
                    "La nueva franja ya está ocupada.",
                    "Conflicto",
                    JOptionPane.ERROR_MESSAGE);
            Reservation rest
                    = ServerApp.calendar.reserveTemporarily(
                            clientId, original.getDate(),
                            original.getStartTime(),
                            original.getEndTime(),
                            original.getAttendeeCount(),
                            original.getEquipment(),
                            original.getPriority());
            if (rest != null) {
                ServerApp.calendar.confirmReservation(
                        rest.getReservationId());
            }
            return;
        }

        ServerApp.calendar.confirmReservation(
                newRes.getReservationId());
        ReservationPersistence.save(
                ServerApp.calendar);
        ServerApp.log.log("EDICION-SERVIDOR",
                "Servidor editó reserva " + resId
                + " - " + newRes.getReservationId()
                + " | cliente: " + clientId);

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler handler : ServerApp.connectedClients) {
                try {
                    if (handler.getClientId().equals(clientId)) {
                        handler.send("OK|EDITADO|" + resId
                                + "|" + newRes.getReservationId()
                                + "|" + newRes.getDate()
                                + "|" + newRes.getStartTime()
                                + "|" + newRes.getEndTime()
                                + "|" + newRes.getStatus().toString());
                    } else {
                        handler.send("SLOT_LIBRE|" + original.getDate()
                                + "|" + original.getStartTime()
                                + "|" + original.getEndTime());
                        handler.send("SLOT_NUEVO|" + newRes.getDate()
                                + "|" + newRes.getStartTime()
                                + "|" + newRes.getEndTime());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        log("Reserva " + resId + " editada → "
                + newRes.getReservationId()
                + " | cliente: " + clientId);
        refreshView();
    }

    /**
     * Applies a consistent visual style to text input fields. Sets font,
     * background colors, and a compound border for padding.
     *
     * @param field The JTextField to be styled.
     */
    private void styleFormField(JTextField field) {
        field.setFont(
                new Font("SansSerif", Font.PLAIN, 12));
        field.setBackground(BG_FIELD);
        field.setForeground(TEXT_DARK);
        field.setCaretColor(UNA_RED);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        BORDER_CARD, 1),
                new EmptyBorder(4, 8, 4, 8)));
    }

    /**
     * Creates a styled JLabel for form prompts.
     *
     * @param text The label text.
     * @return A JLabel with the system's medium-text styling.
     */
    private JLabel createFormLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(TEXT_MEDIUM);
        return label;
    }

    /**
     * Cancels the selected reservation from the server side. Prompts for
     * confirmation, releases logical resources, removes from TTL queue,
     * persists changes, and notifies the connected client.
     */
    private void cancelSelectedReservation() {
        int selectedRow = calendarTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        String resId = (String) tableModel.getValueAt(selectedRow, 0);
        String clientId = (String) tableModel.getValueAt(selectedRow, 1);

        Reservation res = ServerApp.calendar.getReservationById(resId);
        if (res == null) {
            JOptionPane.showMessageDialog(this, "Reserva no encontrada.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String resDate = res.getDate();
        String resStart = res.getStartTime();
        String resEnd = res.getEndTime();

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Cancelar la reserva " + resId + " del cliente " + clientId + "?",
                "Confirmar cancelación", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        boolean success = ServerApp.calendar.cancelReservation(resId);
        if (!success) {
            JOptionPane.showMessageDialog(this, "No se pudo cancelar la reserva.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ServerApp.ttlQueue.remove(resId);
        ReservationPersistence.save(ServerApp.calendar);
        ServerApp.log.log("CANCELACION-SERVIDOR",
                "Servidor canceló reserva " + resId + " del cliente " + clientId);

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler handler : ServerApp.connectedClients) {
                try {
                    if (handler.getClientId().equals(clientId)) {
                        handler.send("OK|CANCELADO|" + resId);
                    } else {
                        handler.send("SLOT_LIBRE|" + resDate
                                + "|" + resStart + "|" + resEnd);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        log("✖ Reserva " + resId + " cancelada por el servidor.");
        refreshView();
    }

    /**
     * Appends a timestamped message to the server's graphical log console.
     * Ensures thread-safety by using invokeLater for UI updates from background
     * network threads.
     *
     * @param message The message to display.
     */
    public void log(String message) {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            txtLogArea.append(
                    "[" + timestamp + "]  " + message + "\n");
            txtLogArea.setCaretPosition(
                    txtLogArea.getDocument().getLength());
        });
    }
}