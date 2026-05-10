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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // === FILTER ===
    private JButton btnFilterAll;
    private JButton btnFilterToday;
    private JButton btnFilterWeek;
    private JButton btnFilterMonth;
    private JTextField txtFilterDate;
    private JComboBox<String> cmbFilterStatus;
    private JLabel lblFilterCount;
    private String activeQuickFilter = "ALL";

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
                System.out.println("[CIERRE] Reservas guardadas.");
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
                g2.fillRect(0, getHeight() - 3, getWidth(), 3);

                g2.setColor(new Color(0xFF, 0xFF, 0xFF, 18));
                int[] xp = {getWidth() - 120, getWidth(), getWidth()};
                int[] yp = {0, 0, getHeight()};
                g2.fillPolygon(xp, yp, 3);
                g2.setColor(new Color(0xFF, 0xFF, 0xFF, 10));
                int[] xp2 = {getWidth() - 220, getWidth() - 80, getWidth()};
                int[] yp2 = {0, 0, getHeight()};
                g2.fillPolygon(xp2, yp2, 3);
                g2.dispose();
            }
        };
        header.setPreferredSize(new Dimension(0, 64));
        header.setBackground(BG_HEADER);
        header.setBorder(new EmptyBorder(0, 20, 0, 24));

        JPanel titPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
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
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
                BORDER_CARD));

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

        btnStart = createButton("▶  Iniciar Servidor", UNA_BLUE, false);
        btnStop = createButton("■  Detener Servidor", UNA_RED, false);
        btnLog = createButton("↻  Actualizar Vista", UNA_GRAY, true);
        btnEditReservation = createButton("✎  Editar Reserva",
                COLOR_AMBAR, true);
        btnCancelReservation = createButton("✖  Cancelar Reserva",
                UNA_RED, true);

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
        btnCancelReservation.addActionListener(e ->cancelSelectedReservation());

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
    private JPanel crearCard(String title, JLabel valueLabel, String icon) {
        JPanel card = new JPanel(new BorderLayout(3, 3));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CARD, 1),
                new EmptyBorder(8, 10, 8, 10)));

        JLabel lblHeader = new JLabel(icon + "  " + title);
        lblHeader.setFont(new Font("SansSerif", Font.PLAIN, 9));
        lblHeader.setForeground(TEXT_MUTED);

        valueLabel.setFont(new Font("Serif", Font.BOLD, 13));
        if (valueLabel.getForeground().equals(new Color(0, 0, 0))) {
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
    private JButton createButton(String text, Color color, boolean isSecondary){
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
                    g2.setColor(isSecondary
                            ? new Color(color.getRed(), color.getGreen(),
                                    color.getBlue(), 30)
                            : color.brighter());
                } else {
                    g2.setColor(isSecondary
                            ? new Color(color.getRed(), color.getGreen(),
                                    color.getBlue(), 15)
                            : color);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
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
                                color.getBlue(), isSecondary ? 100 : 180), 1),
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

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        JLabel lblSectionTitle = new JLabel("Calendario de Reservas");
        lblSectionTitle.setFont(new Font("Serif", Font.BOLD, 16));
        lblSectionTitle.setForeground(TEXT_DARK);

        JPanel titleWrapper = new JPanel(new BorderLayout(0, 4));
        titleWrapper.setOpaque(false);
        titleWrapper.setBorder(new EmptyBorder(0, 0, 8, 0));
        titleWrapper.add(lblSectionTitle, BorderLayout.NORTH);
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

        JPanel contentPanel = new JPanel(new BorderLayout(0, 8));
        contentPanel.setBackground(BG_MAIN);
        contentPanel.add(createFilterPanel(), BorderLayout.NORTH);
        contentPanel.add(createTablePanel(), BorderLayout.CENTER);

        body.add(contentPanel, BorderLayout.CENTER);
        body.add(createLogPanel(), BorderLayout.SOUTH);
        return body;
    }

    /**
     * Builds the filter bar panel with quick-date buttons, a specific-date
     * field, a status dropdown, a clear button, and a result counter. Uses
     * custom-painted rounded controls and the institutional UNA palette.
     *
     * @return A styled JPanel containing all filter controls.
     */
    private JPanel createFilterPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setBackground(BG_MAIN);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row.setBackground(BG_CARD);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CARD, 1),
                new EmptyBorder(2, 8, 2, 8)));

        // --- Quick filter group ---
        JLabel lblQuick = new JLabel("Filtro Rápido");
        lblQuick.setFont(new Font("Dialog", Font.PLAIN, 10));
        lblQuick.setForeground(TEXT_MUTED);
        row.add(lblQuick);

        btnFilterAll = createFilterButton("Todos", true);
        btnFilterToday = createFilterButton("Hoy", false);
        btnFilterWeek = createFilterButton("Esta Semana", false);
        btnFilterMonth = createFilterButton("Este Mes", false);

        btnFilterAll.addActionListener(e -> setQuickFilter("ALL"));
        btnFilterToday.addActionListener(e -> setQuickFilter("TODAY"));
        btnFilterWeek.addActionListener(e -> setQuickFilter("WEEK"));
        btnFilterMonth.addActionListener(e -> setQuickFilter("MONTH"));

        row.add(btnFilterAll);
        row.add(btnFilterToday);
        row.add(btnFilterWeek);
        row.add(btnFilterMonth);

        // --- Separator ---
        JSeparator sep = new JSeparator(JSeparator.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        sep.setForeground(BORDER_CARD);
        row.add(sep);

        // --- Specific date ---
        JLabel lblDateLbl = new JLabel("Fecha Específica");
        lblDateLbl.setFont(new Font("Dialog", Font.PLAIN, 10));
        lblDateLbl.setForeground(TEXT_MUTED);
        row.add(lblDateLbl);

        JPanel datePickerPanel = buildFilterDatePicker();
        row.add(datePickerPanel);

        // --- Separator ---
        JSeparator sep2 = new JSeparator(JSeparator.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 24));
        sep2.setForeground(BORDER_CARD);
        row.add(sep2);

        // --- Status filter ---
        JLabel lblEstadoLbl = new JLabel("Estado");
        lblEstadoLbl.setFont(new Font("Dialog", Font.PLAIN, 10));
        lblEstadoLbl.setForeground(TEXT_MUTED);
        row.add(lblEstadoLbl);

        String[] statuses = {
            "Todos", "CONFIRMADO",
            "CANCELADO", "EXPIRADO", "FINALIZADO"
        };
        cmbFilterStatus = new JComboBox<>(statuses);
        cmbFilterStatus.setFont(new Font("Dialog", Font.PLAIN, 11));
        cmbFilterStatus.setBackground(BG_FIELD);
        cmbFilterStatus.setForeground(TEXT_DARK);
        cmbFilterStatus.setFocusable(false);
        cmbFilterStatus.setPreferredSize(new Dimension(155, 30));
        cmbFilterStatus.setBorder(BorderFactory.createLineBorder(
                BORDER_CARD, 1));
        cmbFilterStatus.addActionListener(e -> applyFilters());
        row.add(cmbFilterStatus);

        // --- Clear button ---
        JButton btnClear = createButton("↺  Limpiar Filtros",
                UNA_RED, false);
        btnClear.setFont(new Font("Dialog", Font.BOLD, 11));
        btnClear.addActionListener(e -> clearFilters());
        row.add(btnClear);

        wrapper.add(row, BorderLayout.CENTER);

        // --- Count label ---
        lblFilterCount = new JLabel("Mostrando — reservas");
        lblFilterCount.setFont(new Font("Dialog", Font.PLAIN, 11));
        lblFilterCount.setForeground(TEXT_MUTED);
        lblFilterCount.setBorder(new EmptyBorder(4, 4, 0, 0));
        wrapper.add(lblFilterCount, BorderLayout.SOUTH);

        return wrapper;
    }

    /**
     * Creates a date picker control for the filter bar, consisting of a
     * read-only text field and a red calendar trigger button. Adapted from the
     * client view's date picker, but allows selecting any date (including past
     * ones) since the server needs to filter historical reservations.
     *
     * @return A styled JPanel acting as a cohesive date input control.
     */
    private JPanel buildFilterDatePicker() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_FIELD);
        container.setBorder(BorderFactory.createLineBorder(BORDER_CARD, 1));
        container.setPreferredSize(new Dimension(160, 30));

        txtFilterDate = new JTextField();
        txtFilterDate.setFont(new Font("Dialog", Font.PLAIN, 11));
        txtFilterDate.setBackground(BG_FIELD);
        txtFilterDate.setForeground(TEXT_MUTED);
        txtFilterDate.setEditable(false);
        txtFilterDate.setBorder(new EmptyBorder(4, 10, 4, 6));
        txtFilterDate.setText("dd/mm/aaaa");

        JButton calBtn = new JButton("▼") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover()
                        ? UNA_RED.darker() : UNA_RED);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        calBtn.setFont(new Font("Dialog", Font.BOLD, 11));
        calBtn.setForeground(UNA_WHITE);
        calBtn.setFocusPainted(false);
        calBtn.setContentAreaFilled(false);
        calBtn.setOpaque(false);
        calBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        calBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        calBtn.addActionListener(e -> showFilterCalendarPopup(calBtn));

        container.add(txtFilterDate, BorderLayout.CENTER);
        container.add(calBtn, BorderLayout.EAST);
        return container;
    }

    /**
     * Displays an undecorated modal popup calendar for the filter date picker.
     * Unlike the client view's calendar, past dates are fully enabled here
     * since the server needs to query historical reservations. Selecting a day
     * writes the date in YYYY-MM-DD format to {@code txtFilterDate} and
     * immediately triggers {@link #applyFilters()}.
     *
     * @param parent The component used as anchor for popup positioning.
     */
    private void showFilterCalendarPopup(Component parent) {
        JDialog popup = new JDialog(
                (java.awt.Frame) SwingUtilities.getWindowAncestor(parent),
                false);
        popup.setUndecorated(true);

        final LocalDate[] view = {LocalDate.now()};

        JPanel calPanel = new JPanel(new BorderLayout(0, 8));
        calPanel.setBackground(BG_CARD);
        calPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CARD, 1),
                new EmptyBorder(12, 12, 12, 12)));

        JPanel navPanel = new JPanel(new BorderLayout());
        navPanel.setBackground(BG_CARD);

        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(new Font("Dialog", Font.BOLD, 13));
        monthLabel.setForeground(TEXT_DARK);

        JButton prevBtn = new JButton("‹");
        JButton nextBtn = new JButton("›");
        for (JButton b : new JButton[]{prevBtn, nextBtn}) {
            b.setFont(new Font("Dialog", Font.BOLD, 16));
            b.setBackground(BG_CARD);
            b.setForeground(UNA_RED);
            b.setFocusPainted(false);
            b.setBorder(new EmptyBorder(2, 10, 2, 10));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setOpaque(true);
            b.setContentAreaFilled(true);
        }

        navPanel.add(prevBtn, BorderLayout.WEST);
        navPanel.add(monthLabel, BorderLayout.CENTER);
        navPanel.add(nextBtn, BorderLayout.EAST);

        JPanel gridPanel = new JPanel(new GridLayout(0, 7, 4, 4));
        gridPanel.setBackground(BG_CARD);

        Runnable buildGrid = () -> {
            gridPanel.removeAll();
            monthLabel.setText(view[0].format(
                    DateTimeFormatter.ofPattern("MMMM yyyy")));

            for (String d : new String[]{
                "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) {
                JLabel lbl = new JLabel(d, SwingConstants.CENTER);
                lbl.setFont(new Font("Dialog", Font.BOLD, 10));
                lbl.setForeground(TEXT_MUTED);
                gridPanel.add(lbl);
            }

            LocalDate firstDay = view[0].withDayOfMonth(1);
            int startCol = firstDay.getDayOfWeek().getValue() % 7;
            LocalDate today = LocalDate.now();

            for (int i = 0; i < startCol; i++) {
                gridPanel.add(new JLabel(""));
            }

            for (int d = 1; d <= view[0].lengthOfMonth(); d++) {
                LocalDate date = view[0].withDayOfMonth(d);
                boolean isToday = date.equals(today);

                JButton dayBtn = new JButton(String.valueOf(d)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        if (isToday) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(
                                    RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(getBackground());
                            g2.fillRoundRect(0, 0,
                                    getWidth(), getHeight(), 8, 8);
                            g2.dispose();
                        }
                        super.paintComponent(g);
                    }
                };

                dayBtn.setFont(new Font("Dialog",
                        isToday ? Font.BOLD : Font.PLAIN, 11));
                dayBtn.setFocusPainted(false);
                dayBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

                if (isToday) {
                    dayBtn.setBackground(UNA_RED);
                    dayBtn.setForeground(UNA_WHITE);
                    dayBtn.setContentAreaFilled(false);
                    dayBtn.setOpaque(false);
                    dayBtn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(
                                    UNA_RED.darker(), 1),
                            new EmptyBorder(3, 1, 3, 1)));
                } else {
                    dayBtn.setBackground(BG_CARD);
                    dayBtn.setForeground(TEXT_DARK);
                    dayBtn.setContentAreaFilled(true);
                    dayBtn.setOpaque(true);
                    dayBtn.setBorder(new EmptyBorder(4, 2, 4, 2));
                }

                dayBtn.addActionListener(ev -> {
                    String formatted = date.format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    txtFilterDate.setText(formatted);
                    txtFilterDate.setForeground(TEXT_DARK);
                    applyFilters();
                    popup.dispose();
                });
                gridPanel.add(dayBtn);
            }

            gridPanel.revalidate();
            gridPanel.repaint();
        };

        prevBtn.addActionListener(e -> {
            view[0] = view[0].minusMonths(1);
            buildGrid.run();
        });
        nextBtn.addActionListener(e -> {
            view[0] = view[0].plusMonths(1);
            buildGrid.run();
        });

        buildGrid.run();

        calPanel.add(navPanel, BorderLayout.NORTH);
        calPanel.add(gridPanel, BorderLayout.CENTER);
        popup.add(calPanel);
        popup.pack();

        Point loc = parent.getLocationOnScreen();
        popup.setLocation(loc.x, loc.y + parent.getHeight());
        popup.setVisible(true);

        popup.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                popup.dispose();
            }
        });
    }

    /**
     * Creates a custom-painted quick-filter toggle button with rounded corners,
     * anti-aliased rendering, and hover/active states. Uses UNA_RED when active
     * to stay consistent with the institutional color palette.
     *
     * @param text Button label.
     * @param active Whether this button starts in its selected state.
     * @return A fully styled, self-painting JButton.
     */
    private JButton createFilterButton(String text, boolean active) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                boolean isActive = Boolean.TRUE.equals(
                        getClientProperty("active"));

                if (isActive) {
                    g2.setColor(UNA_RED);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.setColor(new Color(0, 0, 0, 20));
                    g2.fillRoundRect(0, getHeight() / 2,
                            getWidth(), getHeight() / 2, 20, 20);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(0xCD, 0x17, 0x19, 18));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.setColor(new Color(0xCD, 0x17, 0x19, 60));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawRoundRect(0, 0, getWidth() - 1,
                            getHeight() - 1, 20, 20);
                } else {
                    g2.setColor(BG_CARD);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.setColor(BORDER_CARD);
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.drawRoundRect(0, 0, getWidth() - 1,
                            getHeight() - 1, 20, 20);
                }

                FontMetrics fm = g2.getFontMetrics(getFont());
                String label = getText();
                int tx = (getWidth() - fm.stringWidth(label)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.setColor(isActive ? UNA_WHITE : TEXT_DARK);
                g2.setFont(getFont());
                g2.drawString(label, tx, ty);
                g2.dispose();
            }
        };
        btn.putClientProperty("active", active);
        btn.setFont(new Font("Dialog", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(6, 16, 6, 16));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Applies active or inactive visual state to a quick-filter button by
     * toggling its "active" client property and repainting.
     *
     * @param btn The button to update.
     * @param active True for selected (red fill), false for default.
     */
    private void applyFilterButtonStyle(JButton btn, boolean active) {
        btn.putClientProperty("active", active);
        btn.repaint();
    }

    /**
     * Sets the active quick-filter, updates button styles, and refreshes the
     * table via applyFilters().
     *
     * @param filter One of "ALL", "TODAY", "WEEK", "MONTH".
     */
    private void setQuickFilter(String filter) {
        activeQuickFilter = filter;
        applyFilterButtonStyle(btnFilterAll, "ALL".equals(filter));
        applyFilterButtonStyle(btnFilterToday, "TODAY".equals(filter));
        applyFilterButtonStyle(btnFilterWeek, "WEEK".equals(filter));
        applyFilterButtonStyle(btnFilterMonth, "MONTH".equals(filter));
        applyFilters();
    }

    /**
     * Resets all filters to their default state and refreshes the table.
     */
    private void clearFilters() {
        activeQuickFilter = "ALL";
        applyFilterButtonStyle(btnFilterAll, true);
        applyFilterButtonStyle(btnFilterToday, false);
        applyFilterButtonStyle(btnFilterWeek, false);
        applyFilterButtonStyle(btnFilterMonth, false);
        txtFilterDate.setText("dd/mm/aaaa");
        txtFilterDate.setForeground(TEXT_MUTED);
        cmbFilterStatus.setSelectedIndex(0);
        applyFilters();
    }

    /**
     * Returns the subset of all reservations that match the current filter
     * criteria (quick filter, specific date, and status dropdown).
     *
     * @return Filtered list of reservations.
     */
    private List<Reservation> getFilteredReservations() {
        List<Reservation> all = ServerApp.calendar.getAllReservations();
        List<Reservation> result = new ArrayList<>();

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekEnd = today.plusDays(6);
        java.time.LocalDate monthEnd = today.withDayOfMonth(
                today.lengthOfMonth());

        // Specific date — picker writes yyyy-MM-dd directly
        String specificDate = null;
        String rawDate = txtFilterDate != null ? txtFilterDate.getText() : "";
        if (!rawDate.isBlank() && !rawDate.equals("dd/mm/aaaa")) {
            specificDate = rawDate; // already in yyyy-MM-dd format
        }

        String statusFilter = cmbFilterStatus != null
                ? (String) cmbFilterStatus.getSelectedItem() : "Todos";

        for (Reservation r : all) {
            // --- Quick filter ---
            if (!"ALL".equals(activeQuickFilter)) {
                try {
                    java.time.LocalDate rDate
                            = java.time.LocalDate.parse(r.getDate());
                    boolean passQuick;
                    if ("TODAY".equals(activeQuickFilter)) {
                        passQuick = rDate.equals(today);
                    } else if ("WEEK".equals(activeQuickFilter)) {
                        passQuick = !rDate.isBefore(today)
                                && !rDate.isAfter(weekEnd);
                    } else if ("MONTH".equals(activeQuickFilter)) {
                        passQuick = !rDate.isBefore(today)
                                && !rDate.isAfter(monthEnd);
                    } else {
                        passQuick = true;
                    }
                    if (!passQuick) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
            }

            // --- Specific date ---
            if (specificDate != null && !r.getDate().equals(specificDate)) {
                continue;
            }

            // --- Status ---
            if (statusFilter != null && !"Todos".equals(statusFilter)) {
                if (!r.getStatus().toString().equals(statusFilter)) {
                    continue;
                }
            }

            result.add(r);
        }
        return result;
    }

    /**
     * Re-populates the reservation table using the current filter state.
     * Preserves the user's row selection when possible and updates the
     * result-count label.
     */
    private void applyFilters() {
        if (tableModel == null) {
            return;
        }

        String selectedId = null;
        int currentRow = calendarTable.getSelectedRow();
        if (currentRow >= 0) {
            selectedId = (String) tableModel.getValueAt(currentRow, 0);
        }

        tableModel.setRowCount(0);
        List<Reservation> filtered = getFilteredReservations();
        int totalAll = ServerApp.calendar.getAllReservations().size();
        int rowToRestore = -1;
        int rowCounter = 0;

        for (Reservation r : filtered) {
            tableModel.addRow(new Object[]{
                r.getReservationId(),
                r.getClientId(),
                r.getDate(),
                r.getStartTime() + "-" + r.getEndTime(),
                r.getStatus().toString(),
                r.getAttendeeCount(),
                r.getEquipment().toString(),
                r.getStatus() == Reservation.Status.RESERVADO_TEMPORAL
                ? r.getRemainingSeconds() + "s" : "—"
            });
            if (r.getReservationId().equals(selectedId)) {
                rowToRestore = rowCounter;
            }
            rowCounter++;
        }

        if (rowToRestore >= 0) {
            calendarTable.setRowSelectionInterval(rowToRestore, rowToRestore);
        }

        if (lblFilterCount != null) {
            lblFilterCount.setText("Mostrando " + filtered.size()
                    + " de " + totalAll + " reservas");
        }

        int finalRow = calendarTable.getSelectedRow();
        boolean hasSelection = finalRow >= 0;
        boolean canEdit = hasSelection && isServerRunning
                && !"CANCELADO".equals(tableModel.getValueAt(finalRow, 4));
        if (btnEditReservation != null) {
            btnEditReservation.setEnabled(canEdit);
        }
        if (btnCancelReservation != null) {
            btnCancelReservation.setEnabled(canEdit);
        }
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
                    c.setBackground(new Color(0x03, 0x49, 0x91, 38));
                    c.setForeground(UNA_BLUE);
                } else {
                    c.setBackground(row % 2 == 0 ? BG_ROW_EVEN : BG_ROW_ODD);
                    c.setForeground(UNA_BLACK);

                    Object status = tableModel.getValueAt(row, 4);
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
                    ((JComponent) c).setBorder(new EmptyBorder(0, 10, 0, 10));
                }
                return c;
            }
        };

        calendarTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        calendarTable.setRowHeight(30);
        calendarTable.setBackground(BG_ROW_EVEN);
        calendarTable.setForeground(TEXT_DARK);
        calendarTable.setGridColor(BORDER_TABLE);
        calendarTable.setShowVerticalLines(false);
        calendarTable.setIntercellSpacing(new Dimension(0, 1));
        calendarTable.setSelectionBackground(new Color(0x03, 0x49, 0x91, 38));
        calendarTable.setSelectionForeground(UNA_BLUE);

        JTableHeader header = calendarTable.getTableHeader();
        header.setBackground(UNA_RED);
        header.setForeground(UNA_WHITE);
        header.setFont(new Font("SansSerif", Font.BOLD, 11));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
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

        calendarTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                boolean hasSelection = calendarTable.getSelectedRow() >= 0;
                btnEditReservation.setEnabled(hasSelection && isServerRunning);
                btnCancelReservation.setEnabled(hasSelection &&
                        isServerRunning);
            }
        });

        JScrollPane scrollPane = new JScrollPane(calendarTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_TABLE, 1));
        scrollPane.getViewport().setBackground(BG_ROW_EVEN);
        scrollPane.getVerticalScrollBar().setBackground(BG_MAIN);
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

        JPanel logHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
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
        txtLogArea.setBorder(new EmptyBorder(10, 14, 10, 14));

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

            List<Reservation> restoredList = ReservationPersistence.load();
            for (Reservation r : restoredList) {
                ServerApp.calendar.loadRestoredReservation(r);
            }
            SwingUtilities.invokeLater(() -> {
                if (!restoredList.isEmpty()) {
                    log("✔ " + restoredList.size()
                            + " reserva(s) restauradas desde disco.");
                } else {
                    log("No hay reservas previas que restaurar.");
                }
            });

            try {
                activeSocket = new java.net.ServerSocket(8000);

                ReservationTTLThread ttlHandler = new ReservationTTLThread(
                        ServerApp.calendar,
                        ServerApp.resources,
                        ServerApp.ttlQueue,
                        ServerApp.log);
                ttlHandler.setDaemon(true);
                ttlHandler.start();

                System.out.println("[SERVIDOR] Puerto 8000 abierto, "
                        + "esperando clientes...");

                while (!Thread.currentThread().isInterrupted()
                        && !activeSocket.isClosed()) {

                    java.net.Socket clientSocket = activeSocket.accept();
                    java.io.DataInputStream inputStream
                            = new java.io.DataInputStream(
                                    new java.io.BufferedInputStream(
                                            clientSocket.getInputStream()));
                    String clientData = inputStream.readUTF();
                    System.out.println("[SERVIDOR] Cliente: " + clientData);

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
                    log("[ERROR] Servidor: " + e.getMessage());
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        updateTimer = new Timer(2000, e -> refreshView());
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

        ReservationPersistence.save(ServerApp.calendar);
        log("Reservas confirmadas guardadas en disco.");

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler handler : ServerApp.connectedClients) {
                handler.send("ERROR|SERVIDOR_DETENIDO");
                handler.close();
            }
            ServerApp.connectedClients.clear();
        }

        try {
            if (activeSocket != null && !activeSocket.isClosed()) {
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
                ServerApp.calendar.getTotalReservations() + " activas");
        String nowDate = java.time.LocalDate.now().toString();
        String nowTime = java.time.LocalTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        String nowTimePlus1 = java.time.LocalTime.now()
                .plusMinutes(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        int occupiedNow = ServerApp.calendar.getOccupiedCapacityInRange(
                nowDate, nowTime, nowTimePlus1);
        lblCapacityValue.setText(String.valueOf(
                Math.max(0, ServerApp.manager.getMaxCapacity() - occupiedNow)));

        int usedProy = ServerApp.calendar.getEquipmentInUseNow(
                Core.Reservation.Equipment.PROYECTOR);
        int usedMic = ServerApp.calendar.getEquipmentInUseNow(
                Core.Reservation.Equipment.MICROFONO);
        int usedSound = ServerApp.calendar.getEquipmentInUseNow(
                Core.Reservation.Equipment.SONIDO);

        int proyCount = ServerApp.manager.getTotalProjectors() - usedProy;
        int micCount = ServerApp.manager.getTotalMicrophones() - usedMic;
        int soundCount = ServerApp.manager.getTotalSound() - usedSound;

        lblProjectorValue.setText(String.valueOf(Math.max(0, proyCount)));
        lblMicrophoneValue.setText(String.valueOf(Math.max(0, micCount)));
        lblSoundValue.setText(String.valueOf(Math.max(0, soundCount)));

        int fullSets = Math.min(proyCount, Math.min(micCount, soundCount));
        lblFullSetValue.setText(String.valueOf(Math.max(0, fullSets)));

        List<String> logEntries = ServerApp.log.getLast(100);
        txtLogArea.setText("");
        for (String e : logEntries) {
            txtLogArea.append(e + "\n");
        }
        txtLogArea.setCaretPosition(txtLogArea.getDocument().getLength());

        String selectedId = null;
        int currentRow = calendarTable.getSelectedRow();
        if (currentRow >= 0) {
            selectedId = (String) tableModel.getValueAt(currentRow, 0);
        }

        applyFilters();

        // Restore selection if it still exists after filter
        if (selectedId != null) {
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (selectedId.equals(tableModel.getValueAt(i, 0))) {
                    calendarTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        int finalSelectedRow = calendarTable.getSelectedRow();
        boolean hasSelection = finalSelectedRow >= 0;
        boolean canEdit = hasSelection && isServerRunning
                && !"CANCELADO".equals(tableModel.getValueAt(finalSelectedRow,
                        4));
        btnEditReservation.setEnabled(canEdit);
        btnCancelReservation.setEnabled(canEdit);
    }

    /**
     * Opens a dialog to edit the selected reservation in-place. Validates
     * business rules for ongoing and future reservations, then delegates to
     * ReservationCalendar.editReservation() which mutates the existing
     * Reservation object without changing its ID.
     */
    private void editSelectedReservation() throws InterruptedException {
        int selectedRow = calendarTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }

        String resId = (String) tableModel.getValueAt(selectedRow, 0);
        String clientId = (String) tableModel.getValueAt(selectedRow, 1);
        String currentStrDate = (String) tableModel.getValueAt(selectedRow, 2);
        String schedule = (String) tableModel.getValueAt(selectedRow, 3);
        String[] hours = schedule.split("-");

        JTextField txtDate = new JTextField(currentStrDate);
        JTextField txtStart = new JTextField(hours.length > 0 ? hours[0] : "");
        JTextField txtEnd = new JTextField(hours.length > 1 ? hours[1] : "");
        JTextField txtAttendees = new JTextField(
                tableModel.getValueAt(selectedRow, 5).toString());
        JComboBox<String> cbEquipment = new JComboBox<>(
                new String[]{"NINGUNO", "PROYECTOR", "MICROFONO", "SONIDO",
                    "COMPLETO"});
        cbEquipment.setSelectedItem(tableModel.getValueAt(selectedRow,
                6).toString());

        styleFormField(txtDate);
        styleFormField(txtStart);
        styleFormField(txtEnd);
        styleFormField(txtAttendees);

        JPanel formPanel = new JPanel(new GridLayout(0, 2, 8, 10));
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

        JSpinner spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        spnQty.setFont(new Font("SansSerif", Font.PLAIN, 12));

        cbEquipment.addActionListener(e -> {
            String sel = (String) cbEquipment.getSelectedItem();
            spnQty.setEnabled(!"NINGUNO".equals(sel)
                    && !"COMPLETO".equals(sel));
        });

        String initialSel = (String) cbEquipment.getSelectedItem();
        spnQty.setEnabled(!"NINGUNO".equals(initialSel)
                && !"COMPLETO".equals(initialSel));

        Reservation orig = ServerApp.calendar.getReservationById(resId);
        if (orig != null && !orig.getEquipmentQuantities().isEmpty()) {
            Map.Entry<Reservation.Equipment, Integer> first
                   = orig.getEquipmentQuantities().entrySet().iterator().next();
            spnQty.setValue(first.getValue());
            String currentEquip = tableModel.getValueAt(selectedRow,
                    6).toString();
            spnQty.setEnabled(!"NINGUNO".equals(currentEquip)
                    && !"COMPLETO".equals(currentEquip));
        }

        formPanel.add(createFormLabel("Cantidad equipo:"));
        formPanel.add(spnQty);

        int result = JOptionPane.showConfirmDialog(
                this, formPanel,
                "Editar reserva " + resId + "  |  cliente: " + clientId,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String newDateStr = txtDate.getText().trim();
        String newStartStr = txtStart.getText().trim();
        String newEndStr = txtEnd.getText().trim();
        String newAttStr = txtAttendees.getText().trim();

        if (newDateStr.isEmpty() || newStartStr.isEmpty()
                || newEndStr.isEmpty() || newAttStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Complete todos los campos.",
                    "Campos vacíos", JOptionPane.WARNING_MESSAGE);
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
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Formato de fecha inválido. Use YYYY-MM-DD.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!newStartStr.matches("^\\d{2}:\\d{2}$")
                || !newEndStr.matches("^\\d{2}:\\d{2}$")) {

            JOptionPane.showMessageDialog(this,
                    "La hora debe tener formato HH:mm.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LocalTime newStart;
        LocalTime newEnd;

        try {
            DateTimeFormatter formatter
                    = DateTimeFormatter.ofPattern("HH:mm");

            newStart = LocalTime.parse(newStartStr, formatter);
            newEnd = LocalTime.parse(newEndStr, formatter);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "Hora inválida.",
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

        if (original.getStatus() != Reservation.Status.CONFIRMADO) {
            JOptionPane.showMessageDialog(this,
                    "Solo se pueden editar reservas confirmadas.",
                    "Operación no permitida", JOptionPane.WARNING_MESSAGE);
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
                        "No puedes modificar la hora de inicio"
                        + " de una reserva en curso.",
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

        // Reservation hasn't started yet
        if (oldStartFull.isAfter(now)) {
            if (newStartFull.isBefore(now)) {
                JOptionPane.showMessageDialog(this,
                        "No puedes usar fechas u horas pasadas.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        Map<Reservation.Equipment, Integer> newEquipMap = new LinkedHashMap<>();
        String sel = (String) cbEquipment.getSelectedItem();
        if (!"NINGUNO".equals(sel)) {
            if ("COMPLETO".equals(sel)) {
                newEquipMap.put(Reservation.Equipment.PROYECTOR,
                        ServerApp.manager.getTotalProjectors());
                newEquipMap.put(Reservation.Equipment.MICROFONO,
                        ServerApp.manager.getTotalMicrophones());
                newEquipMap.put(Reservation.Equipment.SONIDO,
                        ServerApp.manager.getTotalSound());
            } else {
                newEquipMap.put(Reservation.Equipment.valueOf(sel),
                        (int) spnQty.getValue());
            }
        }

        boolean edited = ServerApp.calendar.editReservation(
                resId, newDateStr, newStartStr, newEndStr,
                attendees, newEquipMap);

        if (!edited) {
            Reservation check = ServerApp.calendar.getReservationById(resId);
            String motivo;
            if (check == null) {
                motivo = "La reserva no fue encontrada.";
            } else if (check.getStatus() != Reservation.Status.CONFIRMADO) {
                motivo = "Solo se pueden editar reservas confirmadas.";
            } else {
                motivo = "Conflicto detectado:\n"
                        + "• Realice su reserva en otro horario.\n"
                        + "• Sobrepaso el limite de equipamiento disponible.";
            }
            JOptionPane.showMessageDialog(this, motivo,
                    "No se pudo editar", JOptionPane.ERROR_MESSAGE);

            synchronized (ServerApp.connectedClients) {
                for (ClientHandler handler : ServerApp.connectedClients) {
                    if (handler.getClientId().equals(clientId)) {
                        handler.send("ERROR|EDICION_FALLIDA|" + resId);
                        break;
                    }
                }
            }
            return;
        }

        ReservationPersistence.save(ServerApp.calendar);
        ServerApp.log.log("EDICION-SERVIDOR",
                "Servidor editó reserva " + resId + " | cliente: " + clientId);

        // Fetch the updated reservation (same object, same ID)
        Reservation updated = ServerApp.calendar.getReservationById(resId);

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler handler : ServerApp.connectedClients) {
                try {
                    if (handler.getClientId().equals(clientId)) {
                        // Notify the owner: same reservation ID, updated data
                        handler.send("OK|EDITADO|" + resId
                                + "|" + resId // same ID
                                + "|" + updated.getDate()
                                + "|" + updated.getStartTime()
                                + "|" + updated.getEndTime()
                                + "|" + updated.getStatus().toString());
                    } else {
                        // Notify other clients about the freed and new slot
                        handler.send("SLOT_LIBRE|" + original.getDate()
                                + "|" + original.getStartTime()
                                + "|" + original.getEndTime());
                        handler.send("SLOT_NUEVO|" + updated.getDate()
                                + "|" + updated.getStartTime()
                                + "|" + updated.getEndTime());
                    }
                } catch (Exception ignored) {
                }
            }
        }

        log("Reserva " + resId + " editada en sitio | cliente: " + clientId);
        refreshView();
    }

    /**
     * Applies a consistent visual style to text input fields. Sets font,
     * background colors, and a compound border for padding.
     *
     * @param field The JTextField to be styled.
     */
    private void styleFormField(JTextField field) {
        field.setFont(new Font("SansSerif", Font.PLAIN, 12));
        field.setBackground(BG_FIELD);
        field.setForeground(TEXT_DARK);
        field.setCaretColor(UNA_RED);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_CARD, 1),
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

        Reservation.Status st = res.getStatus();

        if (st == Reservation.Status.CANCELADO) {
            JOptionPane.showMessageDialog(this,
                    "La reserva ya está cancelada.",
                    "Operación no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (st == Reservation.Status.EXPIRADO) {
            JOptionPane.showMessageDialog(this,
                    "La reserva ya expiró.",
                    "Operación no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (st == Reservation.Status.FINALIZADO) {
            JOptionPane.showMessageDialog(this,
                    "La reserva ya finalizó y no puede cancelarse.",
                    "Operación no válida", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Cancelar la reserva " + resId + " del cliente "
                + clientId + "?",
                "Confirmar cancelación", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        boolean success = ServerApp.calendar.cancelReservation(resId);
        if (!success) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cancelar la reserva.\n"
                    + "Puede haber sido modificada o ya no existe.",
                    "Operación fallida", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ServerApp.ttlQueue.remove(resId);
        ReservationPersistence.save(ServerApp.calendar);
        ServerApp.log.log("CANCELACION-SERVIDOR",
                "Servidor cancelo reserva " + resId
                + " del cliente " + clientId);

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
            txtLogArea.append("[" + timestamp + "]  " + message + "\n");
            txtLogArea.setCaretPosition(txtLogArea.getDocument().getLength());
        });
    }
}