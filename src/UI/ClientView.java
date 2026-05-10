package UI;

import Notifications.EmailStorage;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Main graphical interface for the Client application.
 *
 * This class handles the complete lifecycle of a client session: 1. User
 * authentication and identity verification via TSE API. 2. Real-time connection
 * management with the Central Server. 3. Dynamic UI switching between Login,
 * Main Menu, and Reservation forms. 4. Asynchronous handling of server
 * responses and TTL (Time-To-Live) updates.
 *
 * Adheres to the UNA (Universidad Nacional) visual identity standards.
 */
public class ClientView extends JFrame {

    // =========================================================
    // COLOR PALETTE - UNA COSTA RICA
    // =========================================================
    private static final Color BG_WHITE = Color.WHITE;
    private static final Color BG_LIGHT = new Color(250, 250, 250);
    private static final Color ACCENT_RED = new Color(220, 38, 38);
    private static final Color ACCENT_RED_DARK = new Color(185, 28, 28);
    private static final Color TEXT_DARK = new Color(55, 65, 81);
    private static final Color TEXT_MUTED = new Color(107, 114, 128);
    private static final Color TEXT_LIGHT = new Color(156, 163, 175);
    private static final Color BORDER_COLOR = new Color(229, 231, 235);
    private static final Color SUCCESS_GREEN = new Color(22, 163, 74);
    private static final Color WARNING_AMBER = new Color(217, 119, 6);
    private final Color BORDER_GRAY = new Color(220, 220, 220);

    // =========================================================
    // TSE API
    // =========================================================
    private static final String TSE_API_URL =
            "https://apis.gometa.org/cedulas/";

    // =========================================================
    // SERVER CONNECTION
    // =========================================================
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private String lastReservationId = null;

    // =========================================================
    // LOGIN COMPONENTS
    // =========================================================
    private JTextField txtClientName;
    private JTextField txtClientId;
    private JComboBox<String> cbUserRole;
    private JButton btnEstablishConnection;
    private JButton btnTerminateSession;
    private JLabel lblStatusIndicator;
    private JLabel lblApiFeedback;
    private JLabel lblUserWelcome;

    // =========================================================
    // FILTER
    // =========================================================
    private JButton hBtnAll, hBtnToday, hBtnWeek, hBtnMonth;
    private JTextField hTxtDate;
    private JComboBox<String> hCmbStatus;
    private JLabel hLblCount;
    private String hActiveFilter = "ALL";

    // =========================================================
    // RESERVATION FORM COMPONENTS
    // =========================================================
    private JTextField txtReservationDate;
    private JComboBox<String> cmbStartTime;
    private JComboBox<String> cmbEndTime;
    private JTextField txtAttendeeCount;
    private JComboBox<String> cbEquipmentType;
    private JSpinner spnEquipQuantity;
    private JButton btnSubmitRequest;
    private JButton btnConfirmSelection;
    private JButton btnAbortReservation;
    private JLabel lblDuration;
    private JButton btnReturnToMenu;

    // =========================================================
    // TABLE & MESSAGE COMPONENTS
    // =========================================================
    private JTable tblClientReservations;
    private DefaultTableModel tblModel;
    private JTextArea txtServerLogs;
    private DefaultTableModel historyModel;
    private final java.util.List<Object[]> allReservationsData
            = new java.util.ArrayList<>();

    // =========================================================
    // APPLICATION STATE
    // =========================================================
    private boolean isConnected = false;
    private boolean isIdVerified = false;
    private String sessionCheckedId = "";
    private JTextField txtEmail;
    private String correoExistente;
    private JPanel pnlMainContainer;
    private JPanel pnlLoginView;
    private JPanel pnlMenuView;
    private JPanel pnlReservationForm;
    private volatile boolean isWorkerRunning = false;
    private final Timer ttlCountdownTimer;
    private final Map<String, List<String[]>> otherUsersSlots
            = new java.util.HashMap<>();

    /**
     * Constructs the ClientView interface. Initializes window properties, UI
     * components, and starts the local TTL countdown timer for temporary
     * reservations.
     */
    public ClientView() {
        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setBackground(BG_WHITE);
        initComponents();
        ttlCountdownTimer = new Timer(1000, e -> updateTTL());
        ttlCountdownTimer.start();
    }

    /**
     * Initializes the root UI structure using a CardLayout to manage navigation
     * between the login, main menu, and reservation screens.
     */
    private void initComponents() {
        pnlMainContainer = new JPanel(new CardLayout());
        pnlMainContainer.setBackground(BG_WHITE);

        pnlLoginView = buildLoginPanel();
        pnlMenuView = buildMenuPanel();
        pnlReservationForm = buildReservationPanel();

        pnlMainContainer.add(pnlLoginView, "LOGIN");
        pnlMainContainer.add(pnlMenuView, "MENU");
        pnlMainContainer.add(pnlReservationForm, "RESERVATION");
        pnlMainContainer.add(buildMyReservationsPanel(), "MY_RESERVATIONS");
        JPanel headerPanel = createHeader();

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_WHITE);
        root.add(headerPanel, BorderLayout.NORTH);
        root.add(pnlMainContainer, BorderLayout.CENTER);
        add(root);

        CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
        cl.show(pnlMainContainer, "LOGIN");
    }

    /**
     * Creates the top navigation bar (Header). Features the university
     * branding, system title, logo, and the dynamic logout button with hover
     * effects.
     *
     * @return A styled JPanel representing the application header.
     */
    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT_RED);
        header.setBorder(new EmptyBorder(15, 25, 15, 25));

        JPanel headerText = new JPanel(new GridLayout(2, 1));
        headerText.setBackground(ACCENT_RED);

        JLabel lblUniversity = new JLabel("UNIVERSIDAD NACIONAL");
        lblUniversity.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblUniversity.setForeground(Color.WHITE);

        JLabel lblSystem = new JLabel("Sistema de Reservas de Salas");
        lblSystem.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblSystem.setForeground(new Color(255, 220, 220));

        headerText.add(lblUniversity);
        headerText.add(lblSystem);
        header.add(headerText, BorderLayout.WEST);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setBackground(ACCENT_RED);

        JLabel logoLabel = loadLogo(160, 55);
        if (logoLabel != null) {
            rightPanel.add(logoLabel);
        }

        btnTerminateSession = new JButton("Cerrar Sesión");
        btnTerminateSession.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnTerminateSession.setForeground(Color.WHITE);
        btnTerminateSession.setFocusPainted(false);
        btnTerminateSession.setContentAreaFilled(false);
        btnTerminateSession.setOpaque(false);
        btnTerminateSession.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.WHITE, 1),
                new EmptyBorder(7, 18, 7, 18)));
        btnTerminateSession.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTerminateSession.setVisible(false);
        btnTerminateSession.addActionListener(e -> logout());
        btnTerminateSession.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnTerminateSession.setForeground(ACCENT_RED);
                btnTerminateSession.setOpaque(true);
                btnTerminateSession.setBackground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnTerminateSession.setForeground(Color.WHITE);
                btnTerminateSession.setOpaque(false);
            }
        });

        rightPanel.add(btnTerminateSession);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    /**
     * Loads and processes the institutional logo from multiple possible
     * resource paths.
     *
     * This method implements a multi-path lookup strategy (Classpath, package
     * root, local resources, and src folders) to ensure the logo is found
     * regardless of the execution environment (IDE vs JAR).
     *
     * Key processing features: 1. Dynamic Transparency: Converts pixel
     * luminance to Alpha values, making darker areas transparent and preserving
     * lighter tones. 2. High-Fidelity Scaling: Uses scaleImage() to preserve
     * sharpness.
     *
     * @param targetW The desired width of the logo.
     * @param targetH The desired height of the logo.
     * @return A JLabel containing the processed ImageIcon, or null if loading
     * fails.
     */
    private JLabel loadLogo(int targetW, int targetH) {
        try {
            InputStream is = getClass().getResourceAsStream(
                    "/resources/LOGO.png");

            if (is == null) {
                System.out.println("Resource not found.");
                return null;
            }

            BufferedImage original = ImageIO.read(is);

            if (original == null) {
                System.out.println("Unable to read image.");
                return null;
            }

            BufferedImage scaled = scaleImage(original, targetW, targetH);

            JLabel lbl = new JLabel(new ImageIcon(scaled));
            lbl.setBorder(new EmptyBorder(0, 0, 0, 10));
            return lbl;

        } catch (IOException e) {
            System.out.println("Error loading logo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Scales an image using high-quality Java2D rendering.
     *
     * @param img source image
     * @param w target width
     * @param h target height
     * @return scaled BufferedImage
     */
    private BufferedImage scaleImage(BufferedImage img, int w, int h) {

        BufferedImage scaled = new BufferedImage(w, h,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = scaled.createGraphics();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        g2.setRenderingHint(RenderingHints.KEY_DITHERING,
                RenderingHints.VALUE_DITHER_ENABLE);

        g2.drawImage(img, 0, 0, w, h, null);

        g2.dispose();

        return scaled;
    }

    /**
     * Constructs the login interface panel using GridBagLayout for precise
     * component alignment.
     *
     * Features included: 1. Identity Verification: Integrates focus and action
     * listeners on the ID field to trigger automated TSE API queries
     * (queryTSE). 2. Data Integrity: The full name field is set to read-only,
     * ensuring users cannot spoof identities retrieved from the official API.
     * 3. Role Selection: Provides a customized JComboBox for university
     * hierarchy classification. 4. UX/UI: Adheres to UNA brand guidelines with
     * hover-ready buttons, custom padding (EmptyBorder), and high-contrast
     * typography for accessibility.
     *
     * @return A styled JPanel containing the login card and authentication
     * controls.
     */
    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_WHITE);

        JPanel loginCard = new JPanel(new GridBagLayout());
        loginCard.setBackground(BG_LIGHT);
        loginCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(30, 40, 30, 40)));
        loginCard.setPreferredSize(new Dimension(450, 550));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 10, 8, 10);

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 25, 10);
        JLabel lblLoginTitle = new JLabel("Inicio de Sesión",
                SwingConstants.CENTER);
        lblLoginTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblLoginTitle.setForeground(ACCENT_RED);
        loginCard.add(lblLoginTitle, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(8, 10, 5, 10);
        JLabel lblDNI = new JLabel("Número de Cédula");
        lblDNI.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDNI.setForeground(TEXT_DARK);
        loginCard.add(lblDNI, gbc);

        lblStatusIndicator = new JLabel(" ");
        lblStatusIndicator.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatusIndicator.setForeground(Color.GRAY);

        gbc.gridy++;
        panel.add(lblStatusIndicator, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 10, 5, 10);
        txtClientId = new JTextField();
        txtClientId.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtClientId.setBackground(BG_WHITE);
        txtClientId.setForeground(TEXT_DARK);
        txtClientId.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));
        txtClientId.setPreferredSize(new Dimension(0, 45));

        txtClientId.addActionListener(e -> {
            String id = txtClientId.getText().trim();
            if (!id.isEmpty() && !id.equals(sessionCheckedId)) {
                queryTSE(id);
            }
        });

        txtClientId.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String id = txtClientId.getText().trim();
                if (!id.isEmpty() && !id.equals(sessionCheckedId)) {
                    queryTSE(id);
                }
            }
        });

        loginCard.add(txtClientId, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(10, 10, 5, 10);

        JLabel lblEmail = new JLabel("Correo institucional");
        lblEmail.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblEmail.setForeground(TEXT_DARK);

        loginCard.add(lblEmail, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 10, 5, 10);

        txtEmail = new JTextField();
        txtEmail.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtEmail.setEnabled(false);

        txtEmail.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));

        txtEmail.setPreferredSize(new Dimension(0, 45));

        loginCard.add(txtEmail, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(5, 10, 5, 10);
        lblApiFeedback = new JLabel("○  Ingrese su cédula para verificar");
        lblApiFeedback.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 11));
        lblApiFeedback.setForeground(TEXT_MUTED);
        loginCard.add(lblApiFeedback, gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(10, 10, 5, 10);
        JLabel lblName = new JLabel("Nombre Completo");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblName.setForeground(TEXT_DARK);
        loginCard.add(lblName, gbc);

        gbc.gridy = 7;
        gbc.insets = new Insets(0, 10, 5, 10);
        txtClientName = new JTextField();
        txtClientName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtClientName.setBackground(new Color(245, 245, 245));
        txtClientName.setForeground(TEXT_DARK);
        txtClientName.setEditable(false);
        txtClientName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));
        txtClientName.setPreferredSize(new Dimension(0, 45));
        loginCard.add(txtClientName, gbc);

        gbc.gridy = 8;
        gbc.insets = new Insets(10, 10, 5, 10);
        JLabel lblRole = new JLabel("Rol en la Universidad");
        lblRole.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRole.setForeground(TEXT_DARK);
        loginCard.add(lblRole, gbc);

        gbc.gridy = 9;
        gbc.insets = new Insets(0, 10, 5, 10);
        cbUserRole = new JComboBox<>(new String[]{"ESTUDIANTE", "DOCENTE",
            "DECANATURA"});
        styleCombo(cbUserRole);
        cbUserRole.setPreferredSize(new Dimension(0, 45));
        loginCard.add(cbUserRole, gbc);

        gbc.gridy = 10;
        gbc.insets = new Insets(25, 10, 10, 10);
        btnEstablishConnection = new JButton("Ingresar al Sistema");
        btnEstablishConnection.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnEstablishConnection.setBackground(ACCENT_RED);
        btnEstablishConnection.setForeground(Color.WHITE);
        btnEstablishConnection.setFocusPainted(false);

        btnEstablishConnection.setContentAreaFilled(true);
        btnEstablishConnection.setOpaque(true);
        btnEstablishConnection.setBorderPainted(false);

        btnEstablishConnection.setBorder(BorderFactory.createEmptyBorder(
                12, 30, 12, 30));
        btnEstablishConnection.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEstablishConnection.addActionListener(e -> connect());
        loginCard.add(btnEstablishConnection, gbc);

        panel.add(loginCard);
        return panel;
    }

    /**
     * Builds the main navigation menu displayed after successful
     * authentication.
     *
     * The panel uses a card-based design pattern: 1. Dynamic Greeting: Updates
     * lblUserWelcome with the authenticated user's information retrieved from
     * the session. 2. Navigation Logic: Implements lambda-based action
     * listeners to trigger CardLayout transitions (e.g., switching to
     * "RESERVATION" view). 3. Modular UI: Leverages a helper method
     * (createMenuCard) to maintain visual consistency across different menu
     * options. 4. Layout: Combines GridBagLayout for centering the main
     * container with GridLayout for the uniform distribution of action cards.
     *
     * @return A styled JPanel containing the primary navigation hub.
     */
    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_LIGHT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JPanel menuCard = new JPanel(new GridBagLayout());
        menuCard.setBackground(BG_WHITE);
        menuCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_GRAY),
                new EmptyBorder(40, 40, 40, 40)
        ));

        GridBagConstraints gbcCard = new GridBagConstraints();
        gbcCard.insets = new Insets(10, 10, 10, 10);
        gbcCard.gridx = 0;
        gbcCard.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Menú Principal", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(ACCENT_RED);
        gbcCard.gridy = 0;
        menuCard.add(title, gbcCard);

        lblUserWelcome = new JLabel("Bienvenido(a)", SwingConstants.CENTER);
        lblUserWelcome.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblUserWelcome.setForeground(SUCCESS_GREEN);
        gbcCard.gridy = 1;
        menuCard.add(lblUserWelcome, gbcCard);

        JPanel cardsPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        cardsPanel.setBackground(BG_LIGHT);

        JPanel cardReserve = createMenuCard(
                "+",
                "Crear Reserva",
                "Nueva reserva de sala",
                () -> {

                    CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
                    cmbStartTime.setSelectedIndex(0);
                    cmbEndTime.setSelectedIndex(0);
                    refreshComboRenderers();
                    cl.show(pnlMainContainer, "RESERVATION");
                }
        );

        JPanel cardMyReservations = createMenuCard(
                "≡",
                "Mis Reservas",
                "Ver historial completo",
                () -> {
                    applyHistoryFilters();

                    CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
                    cl.show(pnlMainContainer, "MY_RESERVATIONS");
                }
        );

        cardsPanel.add(cardReserve);
        cardsPanel.add(cardMyReservations);

        gbcCard.gridy = 2;
        gbcCard.insets = new Insets(30, 10, 10, 10);
        menuCard.add(cardsPanel, gbcCard);

        JButton btnUpdateEmail = new JButton(
                "Actualizar correo institucional");

        btnUpdateEmail.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnUpdateEmail.setForeground(BG_WHITE);
        btnUpdateEmail.setBackground(ACCENT_RED);

        btnUpdateEmail.setOpaque(true);
        btnUpdateEmail.setBorderPainted(false);

        btnUpdateEmail.setFocusPainted(false);
        btnUpdateEmail.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnUpdateEmail.setBorder(new EmptyBorder(10, 18, 10, 18));

        btnUpdateEmail.addActionListener(e -> {
            String actual = EmailStorage.getEmail(sessionCheckedId);

            String nuevo = JOptionPane.showInputDialog(
                    this,
                    "Correo actual: "
                    + (actual != null ? actual : "ninguno")
                    + "\n\nIngrese el nuevo correo institucional:",
                    "Actualizar correo",
                    JOptionPane.PLAIN_MESSAGE
            );

            if (nuevo != null && !nuevo.trim().isEmpty()) {

                nuevo = nuevo.trim();

                if (nuevo.endsWith("@est.una.ac.cr")
                        || nuevo.endsWith("@una.ac.cr")) {

                    EmailStorage.updateEmail(sessionCheckedId, nuevo);

                    JOptionPane.showMessageDialog(
                            this,
                            "Correo actualizado correctamente.",
                            "Listo",
                            JOptionPane.INFORMATION_MESSAGE
                    );

                } else {

                    JOptionPane.showMessageDialog(
                            this,
                            "Solo se permiten correos "
                            + "@est.una.ac.cr o @una.ac.cr",
                            "Correo inválido",
                            JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        });

        gbcCard.gridy = 3;
        gbcCard.insets = new Insets(0, 10, 10, 10);
        gbcCard.anchor = GridBagConstraints.CENTER;

        menuCard.add(btnUpdateEmail, gbcCard);

        panel.add(menuCard, gbc);

        return panel;
    }

    /**
     * Generates a stylized interactive card component for the main menu.
     *
     * Key technical implementations: 1. Functional Interface: Accepts a
     * {@link Runnable} to decouple the UI component from its navigation or
     * business logic. 2. Visual Feedback: Implements a {@link MouseListener} to
     * toggle borders and thickness during hover events, providing clear
     * affordance to the user. 3. Layout Management: Uses {@link BoxLayout} with
     * vertical alignment and struts to ensure consistent spacing between icon
     * and text elements. 4. Custom Styling: Features a compound border and
     * rounded corners for a modern, "card-style" institutional look.
     *
     * @param icon The symbol or character to display as a header.
     * @param title The main text of the card.
     * @param subtitle A descriptive label for the action.
     * @param action The logic to execute when the card is clicked.
     * @return A self-contained, interactive JPanel.
     */
    private JPanel createMenuCard(String icon, String title, String subtitle,
            Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 200), 1,
                        true),
                new EmptyBorder(30, 40, 30, 40)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel lblIcon = new JLabel(icon, SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI", Font.BOLD, 40));
        lblIcon.setForeground(ACCENT_RED);
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(TEXT_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(lblIcon);
        card.add(Box.createVerticalStrut(10));
        card.add(lblTitle);
        card.add(Box.createVerticalStrut(5));
        card.add(lblSub);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ACCENT_RED, 2, true),
                        new EmptyBorder(30, 40, 30, 40)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 200, 200),
                                1, true),
                        new EmptyBorder(30, 40, 30, 40)
                ));
            }
        });

        return card;
    }

    /**
     * Identifies unavailable time slots for a specific date by parsing the
     * current table model.
     *
     * The logic filters out canceled or expired reservations and maps reserved
     * time ranges to discrete slots using the server's 24h format.
     *
     * @param date The target date to check for availability (format:
     * dd/MM/yyyy).
     * @return A Set of strings representing the time slots already occupied.
     */
    private java.util.Set<String> getTakenSlots(String date) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (int i = 0; i < tblModel.getRowCount(); i++) {
            String rowDate = (String) tblModel.getValueAt(i, 1);
            String rowStatus = (String) tblModel.getValueAt(i, 3);
            if (!date.equals(rowDate)) {
                continue;
            }
            if ("CANCELADA".equals(rowStatus) || "CANCELADO".equals(rowStatus)
                    || "EXPIRADA".equals(rowStatus)
                    || "EXPIRADO".equals(rowStatus)) {
                continue;
            }
            String timeRange = (String) tblModel.getValueAt(i, 2);
            if (timeRange == null) {
                continue;
            }
            String[] parts = timeRange.split(" - ");
            if (parts.length < 2) {
                continue;
            }

            String[] allSlots = generateTimeSlots();
            boolean inRange = false;
            for (String slot : allSlots) {
                String s24 = convertToServerFormat(slot);
                if (s24.equals(parts[0])) {
                    inRange = true;
                }
                if (inRange) {
                    taken.add(slot);
                }
                if (s24.equals(parts[1])) {
                    break;
                }
            }
        }
        List<String[]> globalRanges = otherUsersSlots.get(date);
        if (globalRanges != null) {
            String[] allSlots = generateTimeSlots();
            for (String[] range : globalRanges) {
                boolean inRange = false;
                for (String slot : allSlots) {
                    String s24 = convertToServerFormat(slot);
                    if (s24.equals(range[0])) {
                        inRange = true;
                    }
                    if (inRange) {
                        taken.add(slot);
                    }
                    if (s24.equals(range[1])) {
                        break;
                    }
                }
            }
        }
        return taken;
    }

    /**
     * Determines which end-time slots are invalid given a selected start time.
     *
     * An end slot {@code E} is considered invalid if the range [{@code start},
     * {@code E}) overlaps with any already-reserved range [{@code rs},
     * {@code re}) on the given date. Both the current user's own reservations
     * (from the table model) and other users' reservations (stored in
     * {@code otherUsersSlots}) are taken into account.
     *
     * @param date The target date in {@code yyyy-MM-dd} format.
     * @param startSlot The selected start time slot in AM/PM format (e.g.,
     * "8:00 AM").
     * @return A {@code Set} of strings representing the end slots that would
     * cause a scheduling conflict. Returns an empty set if {@code startSlot} is
     * null or equals the placeholder label.
     */
    private java.util.Set<String> getInvalidEndSlots(String date,
            String startSlot) {
        java.util.Set<String> invalid = new java.util.HashSet<>();
        if (startSlot == null || "— Selecciona hora —".equals(startSlot)) {
            return invalid;
        }

        String start24 = convertToServerFormat(startSlot);

        List<String[]> ranges = new java.util.ArrayList<>();
        for (int i = 0; i < tblModel.getRowCount(); i++) {
            String rowDate = (String) tblModel.getValueAt(i, 1);
            String rowStatus = (String) tblModel.getValueAt(i, 3);
            if (!date.equals(rowDate)) {
                continue;
            }
            if ("CANCELADA".equals(rowStatus) || "CANCELADO".equals(rowStatus)
                    || "EXPIRADA".equals(rowStatus)
                    || "EXPIRADO".equals(rowStatus)) {
                continue;
            }
            String timeRange = (String) tblModel.getValueAt(i, 2);
            if (timeRange == null) {
                continue;
            }
            String[] parts = timeRange.split(" - ");
            if (parts.length < 2) {
                continue;
            }
            ranges.add(parts);
        }
        List<String[]> global = otherUsersSlots.get(date);
        if (global != null) {
            for (String[] r : global) {
                ranges.add(new String[]{r[0], r[1]});
            }
        }

        String[] allSlots = generateTimeSlots();
        for (String[] range : ranges) {
            String rs = range[0];
            String re = range[1];
            if (start24.compareTo(re) >= 0) {
                continue;
            }
            for (String slot : allSlots) {
                String e24 = convertToServerFormat(slot);
                if (e24.compareTo(rs) > 0) {
                    invalid.add(slot);
                }
            }
        }
        return invalid;
    }

    /**
     * Updates the visual representation of time selection components based on
     * current availability.
     *
     * This method applies a custom {@link ListCellRenderer} to highlight taken
     * slots with a distinct style (strikethrough-like icon and muted colors)
     * while maintaining the institutional theme for available options.
     *
     * @see #getTakenSlots(String)
     */
    private void refreshComboRenderers() {
        if (tblModel == null) {
            return;
        }
        String date = txtReservationDate.getText().trim();

        java.util.Set<String> takenStart = getTakenSlots(date);

        String selectedStart = (String) cmbStartTime.getSelectedItem();
        java.util.Set<String> takenEnd = 
                getInvalidEndSlots(date, selectedStart);

        cmbStartTime.setRenderer(buildSlotRenderer(takenStart));
        cmbEndTime.setRenderer(buildSlotRenderer(takenEnd));

        applySelectionGuard(cmbStartTime, takenStart);
        applySelectionGuard(cmbEndTime, takenEnd);

        cmbStartTime.repaint();
        cmbEndTime.repaint();
    }

    /**
     * Builds a custom {@link ListCellRenderer} for the time-slot
     * {@link JComboBox} components.
     *
     * The renderer visually distinguishes three types of items: the placeholder
     * option displayed in italic with a muted color, taken slots shown in gray
     * with a lock icon (🔒) to signal unavailability, and available slots
     * styled with an {@code ACCENT_RED} background highlight on selection.
     *
     * @param taken A set of strings representing unavailable slots, obtained
     * from {@link #getTakenSlots(String)} or
     * {@link #getInvalidEndSlots(String, String)}.
     * @return A {@code ListCellRenderer<Object>} ready to be assigned to a
     * {@code JComboBox}.
     */
    private ListCellRenderer<Object> buildSlotRenderer(
            java.util.Set<String> taken) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                setBorder(new EmptyBorder(5, 10, 5, 10));
                if ("— Selecciona hora —".equals(value)) {
                    setFont(new Font("Segoe UI", Font.ITALIC, 13));
                    setForeground(TEXT_MUTED);
                    setBackground(BG_WHITE);
                } else if (taken.contains(value)) {
                    setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
                    setForeground(new Color(180, 180, 180));
                    setBackground(BG_WHITE);
                    setText(value + "  🔒");
                } else {
                    setFont(new Font("Segoe UI", Font.BOLD, 14));
                    setForeground(isSelected && index != -1 ? Color.WHITE
                            : TEXT_DARK);
                    setBackground(isSelected && index != -1 ? ACCENT_RED
                            : BG_WHITE);
                }
                return this;
            }
        };
    }

    /**
     * Registers a {@link SlotGuardListener} on the given {@link JComboBox} to
     * prevent the user from selecting a slot that has been marked as
     * unavailable.
     *
     * Before attaching the new listener, any existing {@code SlotGuardListener}
     * instance is removed to avoid duplicates when renderers are refreshed.
     *
     * @param combo The time-slot combo box to apply the guard to.
     * @param taken The set of invalid or already-occupied slots that must be
     * blocked.
     */
    private void applySelectionGuard(JComboBox<String> combo,
            java.util.Set<String> taken) {
        for (ActionListener al : combo.getActionListeners()) {
            if (al instanceof SlotGuardListener) {
                combo.removeActionListener(al);
            }
        }
        combo.addActionListener(new SlotGuardListener(combo, taken));
    }

    /**
     * An {@link ActionListener} for time-slot {@link JComboBox} components that
     * prevents the selection of occupied or invalid slots.
     *
     * If the user attempts to select a slot contained in the {@code taken} set,
     * the combo box is automatically reverted to index 0 (the "— Selecciona
     * hora —" placeholder), preventing invalid reservation data from being
     * submitted to the server. This listener is registered and managed through
     * {@link #applySelectionGuard}.
     */
    private class SlotGuardListener implements ActionListener {

        private final JComboBox<String> combo;
        private final java.util.Set<String> taken;

        /**
         * Constructs a new guard listener with a reference to the combo box and
         * the set of blocked slots that must not be selectable.
         *
         * @param combo The {@link JComboBox} this listener is attached to.
         * @param taken The set of slots that must not be selectable.
         */
        SlotGuardListener(JComboBox<String> combo, java.util.Set<String> taken){
            this.combo = combo;
            this.taken = taken;
        }

        /**
         * Intercepts the combo box selection event. If the chosen item belongs
         * to the {@code taken} set, it safely resets the selection back to
         * index 0 by temporarily removing and re-adding itself to avoid
         * recursive event firing.
         *
         * @param e The action event triggered by the selection change.
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            Object selected = combo.getSelectedItem();
            if (selected == null) {
                return;
            }
            if (taken.contains(selected.toString())) {
                combo.removeActionListener(this);
                combo.setSelectedIndex(0);
                combo.addActionListener(this);
            }
        }
    }

    /**
     * Assembles the main reservation view by integrating the input form and the
     * data table.
     *
     * The layout is organized as follows: 1. North: User input form section for
     * new reservation requests. 2. Center: Scrollable container for the
     * reservations table and server logs. 3. South: Navigation controls,
     * featuring an animated "Return to Menu" button.
     *
     * Implements smooth scrolling and CardLayout navigation for seamless user
     * experience.
     *
     * @return A composite JPanel serving as the primary reservation workspace.
     */
    private JPanel buildReservationPanel() {
        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setBackground(BG_WHITE);
        content.setBorder(new EmptyBorder(10, 20, 5, 20));

        JPanel formSection = buildFormPanel();
        JPanel bottomSection = buildTableAndMessagesPanel();

        content.add(formSection, BorderLayout.NORTH);
        content.add(bottomSection, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(BG_WHITE);
        buttonPanel.setBorder(new EmptyBorder(2, 15, 8, 15));

        btnReturnToMenu = new JButton("←  Volver al Menú");
        btnReturnToMenu.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnReturnToMenu.setForeground(ACCENT_RED);
        btnReturnToMenu.setFocusPainted(false);
        btnReturnToMenu.setContentAreaFilled(false);
        btnReturnToMenu.setOpaque(false);
        btnReturnToMenu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_RED, 1),
                new EmptyBorder(7, 18, 7, 18)));
        btnReturnToMenu.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnReturnToMenu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnReturnToMenu.setOpaque(true);
                btnReturnToMenu.setBackground(ACCENT_RED);
                btnReturnToMenu.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnReturnToMenu.setOpaque(false);
                btnReturnToMenu.setForeground(ACCENT_RED);
            }
        });
        btnReturnToMenu.addActionListener(e -> {
            CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
            cl.show(pnlMainContainer, "MENU");
        });

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(BG_WHITE);
        outer.add(content, BorderLayout.CENTER);
        buttonPanel.add(btnReturnToMenu);
        outer.add(buttonPanel, BorderLayout.SOUTH);

        return outer;
    }

    /**
     * Builds the "My Reservations" panel, which displays the user's reservation
     * history in a structured table format.
     *
     * This panel is responsible for showing all reservations associated with
     * the logged-in client, including their status, schedule, and other
     * relevant details. It refreshes the table model when the panel is opened
     * and applies visual styling (colors, row formatting, and headers) to
     * improve readability.
     *
     * The table supports different reservation states such as CONFIRMADA,
     * CANCELADA, EXPIRADA, and RESERVADO_TEMPORAL, each rendered with a
     * specific color for quick identification.
     *
     * @return the fully constructed JPanel containing the reservations history
     */
    private JPanel buildMyReservationsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Historial de Reservas");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(ACCENT_RED);

        JPanel topSection = new JPanel(new BorderLayout(0, 6));
        topSection.setBackground(BG_WHITE);

        topSection.add(title, BorderLayout.NORTH);
        topSection.add(buildHistoryFilterPanel(), BorderLayout.CENTER);

        panel.add(topSection, BorderLayout.NORTH);

        String[] cols = {
            "ID Reserva", "Fecha", "Horario", "Estado", "Vigencia"
        };

        historyModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        JTable table = new JTable(historyModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer r,
                    int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                if (isRowSelected(row)) {
                    c.setBackground(new Color(220, 38, 38, 25));
                } else {
                    c.setBackground(row % 2 == 0 ? BG_WHITE : BG_LIGHT);
                }
                Object estadoObj = getValueAt(row, 3);
                String estado = estadoObj != null ? estadoObj.toString() : "";
                switch (estado) {
                    case "CONFIRMADA":
                    case "CONFIRMADO":
                        c.setForeground(SUCCESS_GREEN);
                        break;
                    case "CANCELADA":
                    case "CANCELADO":
                        c.setForeground(TEXT_MUTED);
                        break;
                    case "EXPIRADA":
                    case "EXPIRADO":
                        c.setForeground(WARNING_AMBER);
                        break;
                    case "TEMPORAL":
                    case "RESERVADO_TEMPORAL":
                        c.setForeground(TEXT_DARK);
                        break;
                    default:
                        c.setForeground(TEXT_DARK);
                        break;
                }
                return c;
            }
        };

        table.setRowHeight(28);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.setGridColor(ACCENT_RED);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(new Color(220, 38, 38, 25));
        table.setSelectionForeground(TEXT_DARK);

        JTableHeader header = table.getTableHeader();

        header.setDefaultRenderer(new DefaultTableCellRenderer() {

            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean isSelected,
                    boolean hasFocus,
                    int row,
                    int col) {

                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, col);

                lbl.setBackground(ACCENT_RED);
                lbl.setForeground(Color.WHITE);

                lbl.setFont(new Font("SansSerif", Font.BOLD, 11));

                lbl.setOpaque(true);

                lbl.setBorder(BorderFactory.createMatteBorder(
                        0, 0, 2, 0,
                        ACCENT_RED_DARK
                ));

                return lbl;
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scroll.getViewport().setBackground(BG_WHITE);

        panel.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setBackground(BG_WHITE);

        JButton btnBack = createButton("← Volver", ACCENT_RED, true);

        btnBack.addActionListener(e -> {
            CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
            cl.show(pnlMainContainer, "MENU");
        });

        bottom.add(btnBack);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Creates the UI panel for filtering reservation history, including
     * time-based filters, status dropdown, and reset action. Also shows a
     * dynamic count of filtered results.
     */
    private JPanel buildHistoryFilterPanel() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row.setBackground(BG_LIGHT);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(2, 8, 2, 8)));

        hBtnAll = buildHistoryFilterBtn("Todos", true);
        hBtnToday = buildHistoryFilterBtn("Hoy", false);
        hBtnWeek = buildHistoryFilterBtn("Esta Semana", false);
        hBtnMonth = buildHistoryFilterBtn("Este Mes", false);

        hBtnAll.addActionListener(e -> setHistoryFilter("ALL"));
        hBtnToday.addActionListener(e -> setHistoryFilter("TODAY"));
        hBtnWeek.addActionListener(e -> setHistoryFilter("WEEK"));
        hBtnMonth.addActionListener(e -> setHistoryFilter("MONTH"));

        row.add(new JLabel("Filtro:") {
            {
                setFont(new Font("Segoe UI", Font.PLAIN, 10));
                setForeground(TEXT_MUTED);
            }
        });
        row.add(hBtnAll);
        row.add(hBtnToday);
        row.add(hBtnWeek);
        row.add(hBtnMonth);

        hCmbStatus = new JComboBox<>(new String[]{
            "Todos", "CONFIRMADO", "CANCELADO", "EXPIRADO", "FINALIZADO"
        });
        hCmbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hCmbStatus.setPreferredSize(new Dimension(140, 28));
        hCmbStatus.addActionListener(e -> applyHistoryFilters());
        row.add(new JLabel("Estado:") {
            {
                setFont(new Font("Segoe UI", Font.PLAIN, 10));
                setForeground(TEXT_MUTED);
            }
        });
        row.add(hCmbStatus);

        JButton btnClear = createButton("↺ Limpiar", ACCENT_RED, true);
        btnClear.addActionListener(e -> {
            hActiveFilter = "ALL";
            hBtnAll.putClientProperty("active", true);
            hBtnAll.repaint();
            hBtnToday.putClientProperty("active", false);
            hBtnToday.repaint();
            hBtnWeek.putClientProperty("active", false);
            hBtnWeek.repaint();
            hBtnMonth.putClientProperty("active", false);
            hBtnMonth.repaint();
            hCmbStatus.setSelectedIndex(0);
            applyHistoryFilters();
        });
        row.add(btnClear);

        hLblCount = new JLabel("Mostrando — reservas");
        hLblCount.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hLblCount.setForeground(TEXT_MUTED);

        JPanel wrapper = new JPanel(new BorderLayout(0, 4));
        wrapper.setBackground(BG_WHITE);
        wrapper.add(row, BorderLayout.CENTER);
        wrapper.add(hLblCount, BorderLayout.SOUTH);
        return wrapper;
    }

    /**
     * Builds a custom rounded button for history filtering with support for
     * active state styling and hover rendering.
     */
    private JButton buildHistoryFilterBtn(String text, boolean active) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isActive = Boolean.TRUE.equals(
                        getClientProperty("active"));
                g2.setColor(isActive ? ACCENT_RED
                        : getModel().isRollover()
                        ? new Color(205, 23, 25, 20) : BG_WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(isActive ? ACCENT_RED : BORDER_COLOR);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                FontMetrics fm = g2.getFontMetrics(getFont());
                g2.setColor(isActive ? Color.WHITE : TEXT_DARK);
                g2.setFont(getFont());
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.putClientProperty("active", active);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(5, 14, 5, 14));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Sets the active history filter and updates the UI state of filter
     * buttons. Triggers a refresh of the reservation history table.
     */
    private void setHistoryFilter(String filter) {
        hActiveFilter = filter;
        hBtnAll.putClientProperty("active", "ALL".equals(filter));
        hBtnToday.putClientProperty("active", "TODAY".equals(filter));
        hBtnWeek.putClientProperty("active", "WEEK".equals(filter));
        hBtnMonth.putClientProperty("active", "MONTH".equals(filter));
        hBtnAll.repaint();
        hBtnToday.repaint();
        hBtnWeek.repaint();
        hBtnMonth.repaint();
        applyHistoryFilters();
    }

    /**
     * Applies active filters to the reservation history table. Filters by date
     * range (today, week, month) and reservation status, and updates the table
     * model with the matching results.
     */
    private void applyHistoryFilters() {
        if (historyModel == null) {
            return;
        }
        historyModel.setRowCount(0);

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekEnd = today.plusDays(6);
        java.time.LocalDate monthEnd = today.withDayOfMonth(
                today.lengthOfMonth());
        String statusFilter = (String) hCmbStatus.getSelectedItem();

        int shown = 0;
        for (Object[] row : allReservationsData) {
            String date = row[1].toString();
            String status = row[3].toString();

            // Quick filter
            if (!"ALL".equals(hActiveFilter)) {
                try {
                    java.time.LocalDate d = java.time.LocalDate.parse(date);
                    boolean pass;
                    if ("TODAY".equals(hActiveFilter)) {
                        pass = d.equals(today);
                    } else if ("WEEK".equals(hActiveFilter)) {
                        pass = !d.isBefore(today) && !d.isAfter(weekEnd);
                    } else if ("MONTH".equals(hActiveFilter)) {
                        pass = !d.isBefore(today) && !d.isAfter(monthEnd);
                    } else {
                        pass = true;
                    }
                    if (!pass) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
            }

            if (statusFilter != null && !"Todos".equals(statusFilter)) {
                String normalizado = status
                        .replace("CONFIRMADA", "CONFIRMADO")
                        .replace("CANCELADA", "CANCELADO")
                        .replace("EXPIRADA", "EXPIRADO")
                        .replace("FINALIZADA", "FINALIZADO");
                if (!normalizado.equals(statusFilter)) {
                    continue;
                }
            }

            historyModel.addRow(row);
            shown++;
        }

        if (hLblCount != null) {
            hLblCount.setText("Mostrando " + shown
                    + " de " + allReservationsData.size() + " reservas");
        }
    }

    /**
     * Creates a styled JButton used across the UI with consistent visual
     * design.
     *
     * The button supports primary and secondary styles, allowing different
     * visual behaviors depending on its role in the interface (e.g., main
     * actions vs. navigation or back buttons).
     *
     * @param text the text displayed on the button
     * @param color the base color used for the button background or accent
     * @param isSecondary if true, applies a secondary (less prominent) style;
     * otherwise applies the primary button style
     * @return a fully styled JButton instance ready to be added to the UI
     */
    private JButton createButton(String text, Color color, boolean isSecondary){

        JButton btn = new JButton(text) {

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                ButtonModel model = getModel();

                boolean hover = model.isRollover();
                boolean pressed = model.isPressed();

                Color fill;
                Color textColor;

                if (!isEnabled()) {
                    fill = new Color(230, 230, 230);
                    textColor = Color.GRAY;
                } else if (pressed) {
                    fill = color.darker();
                    textColor = Color.WHITE;
                } else if (hover) {
                    fill = color;
                    textColor = Color.WHITE;
                } else {
                    if (isSecondary) {
                        fill = Color.WHITE;
                        textColor = color;
                    } else {
                        fill = color;
                        textColor = Color.WHITE;
                    }
                }

                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                setForeground(textColor);
                super.paintComponent(g);
            }
        };

        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.setContentAreaFilled(false);
        btn.setOpaque(false);

        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                new EmptyBorder(10, 28, 10, 28)
        ));
        return btn;
    }

    /**
     * Constructs the interactive reservation form panel.
     *
     * Key features: 1. Data Binding: Synchronizes start and end times via event
     * listeners to enforce logical constraints and dynamic duration
     * calculation. 2. Component Integration: Incorporates custom styled combo
     * boxes, a calendar-based date picker, and specialized text fields. 3.
     * State Management: Initially disabled (via setFormEnabled) to prevent
     * input before server handshake or specific action triggers. 4.
     * Multi-column Layout: Uses GridBagLayout with weighted constraints to
     * maintain a responsive, card-like form structure.
     *
     * @return A JPanel representing the high-fidelity reservation input form.
     */
    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_WHITE);

        JLabel header = new JLabel("Nueva Reserva");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setForeground(ACCENT_RED);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        panel.add(header, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(BG_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 20, 12, 20)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);

        String[] timeSlots = generateTimeSlots();

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        JLabel lblDate = new JLabel("📅  Fecha");
        lblDate.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblDate.setForeground(TEXT_DARK);
        card.add(lblDate, gbc);

        gbc.gridx = 1;
        JPanel datePickerPanel = buildCalendarDatePicker();
        card.add(datePickerPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel lblStart = new JLabel("🕒  Hora Inicio");
        lblStart.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblStart.setForeground(TEXT_DARK);
        card.add(lblStart, gbc);

        gbc.gridx = 1;
        cmbStartTime = new JComboBox<>();
        cmbStartTime.addItem("— Selecciona hora —");
        for (int i = 0; i < timeSlots.length - 1; i++) {
            cmbStartTime.addItem(timeSlots[i]);
        }
        styleCombo(cmbStartTime);
        cmbStartTime.addActionListener(e -> {
            updateEndTimeOptions();
            updateDuration();
            refreshComboRenderers();
        });
        card.add(cmbStartTime, gbc);

        gbc.gridx = 2;
        JLabel lblEnd = new JLabel("🕒  Hora de Fin");
        lblEnd.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblEnd.setForeground(TEXT_DARK);
        card.add(lblEnd, gbc);

        gbc.gridx = 3;
        cmbEndTime = new JComboBox<>();
        cmbEndTime.addItem("— Selecciona hora —");
        for (String s : timeSlots) {
            cmbEndTime.addItem(s);
        }
        styleCombo(cmbEndTime);
        cmbEndTime.addActionListener(e -> updateDuration());
        card.add(cmbEndTime, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        lblDuration = new JLabel("Duración: --");
        lblDuration.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDuration.setForeground(SUCCESS_GREEN);
        card.add(lblDuration, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        JLabel lblAttendees = new JLabel("👥  N° Asistentes");
        lblAttendees.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblAttendees.setForeground(TEXT_DARK);
        card.add(lblAttendees, gbc);

        gbc.gridx = 1;
        txtAttendeeCount = buildTextField("0");
        card.add(txtAttendeeCount, gbc);

        gbc.gridx = 2;
        gbc.gridwidth = 1;
        JLabel lblEquipment = new JLabel("Equipamiento");
        lblEquipment.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblEquipment.setForeground(TEXT_DARK);
        card.add(lblEquipment, gbc);

        gbc.gridx = 3;
        cbEquipmentType = new JComboBox<>(new String[]{
            "NINGUNO", "PROYECTOR", "MICROFONO", "SONIDO", "COMPLETO"
        });
        styleCombo(cbEquipmentType);
        cbEquipmentType.addActionListener(e -> {
            String sel = (String) cbEquipmentType.getSelectedItem();
            boolean needsQty = !"NINGUNO".equals(sel) &&
                    !"COMPLETO".equals(sel);
            spnEquipQuantity.setEnabled(needsQty);
        });
        card.add(cbEquipmentType, gbc);

        gbc.gridx = 2;
        gbc.gridy = 4;
        JLabel lblQty = new JLabel("Cantidad");
        lblQty.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblQty.setForeground(TEXT_DARK);
        card.add(lblQty, gbc);

        gbc.gridx = 3;
        spnEquipQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        spnEquipQuantity.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        spnEquipQuantity.setEnabled(false);
        ((JSpinner.DefaultEditor) spnEquipQuantity.getEditor())
                .getTextField().setHorizontalAlignment(JTextField.CENTER);
        card.add(spnEquipQuantity, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(10, 8, 6, 8);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        buttonPanel.setBackground(BG_LIGHT);

        btnSubmitRequest = buildActionButton("💾  Reservar", ACCENT_RED, true);
        btnConfirmSelection = buildActionButton("✓  Confirmar", 
                SUCCESS_GREEN, true);
        btnAbortReservation = buildActionButton("⊗  Cancelar", 
                ACCENT_RED, false);

        btnSubmitRequest.addActionListener(e -> reserve());
        btnConfirmSelection.addActionListener(e -> confirmReservation());
        btnAbortReservation.addActionListener(e -> cancelReservation());

        buttonPanel.add(btnSubmitRequest);
        buttonPanel.add(btnConfirmSelection);
        buttonPanel.add(btnAbortReservation);

        gbc.gridwidth = 4;
        card.add(buttonPanel, gbc);

        panel.add(card, BorderLayout.CENTER);
        setFormEnabled(false);
        return panel;
    }

    /**
     * Builds the feedback section of the UI, containing the reservation history
     * and the system activity log.
     *
     * Technical Highlights: 1. Dynamic Rendering: Overrides prepareRenderer to
     * apply state-based coloring (e.g., Green for CONFIRMADA, Amber for
     * TEMPORAL) and zebra-striping for readability. 2. Non-Editable Model:
     * Implements a custom DefaultTableModel to enforce read-only integrity on
     * the client view. 3. Layout: Uses BoxLayout (Y_AXIS) with vertical struts
     * to separate the reservation table from the server log area. 4. UI
     * Consistency: Styles the JTableHeader with institutional colors and custom
     * matte borders.
     *
     * @return A JPanel providing visual feedback of transactions and system
     * status.
     */
    private JPanel buildTableAndMessagesPanel() {

        JPanel tableWrapper = new JPanel(new BorderLayout(0, 6));
        tableWrapper.setBackground(BG_WHITE);

        JLabel tableLabel = new JLabel("▸  Mis Reservas");
        tableLabel.setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
        tableLabel.setForeground(ACCENT_RED);
        tableWrapper.add(tableLabel, BorderLayout.NORTH);

        String[] columns = {"ID", "Fecha", "Horario", "Estado", "Vigencia"};
        tblModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        tblClientReservations = new JTable(tblModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row,
                    int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_WHITE : BG_LIGHT);
                c.setForeground(TEXT_DARK);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                Object status = tblModel.getValueAt(row, 3);
                if ("CONFIRMADA".equals(status)) {
                    c.setForeground(SUCCESS_GREEN);
                } else if ("CANCELADA".equals(status)) {
                    c.setForeground(ACCENT_RED);
                } else if ("TEMPORAL".equals(status)) {
                    c.setForeground(WARNING_AMBER);
                } else if ("ENVIANDO...".equals(status)) {
                    c.setForeground(TEXT_MUTED);
                } else if ("EXPIRADA".equals(status)) {
                    c.setForeground(ACCENT_RED);
                }
                if (isRowSelected(row)) {
                    c.setBackground(new Color(220, 38, 38, 20));
                }
                return c;
            }
        };

        tblClientReservations.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblClientReservations.setRowHeight(28);
        tblClientReservations.setBackground(BG_WHITE);
        tblClientReservations.setForeground(TEXT_DARK);
        tblClientReservations.setGridColor(BORDER_COLOR);
        tblClientReservations.setShowVerticalLines(false);
        tblClientReservations.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader tableHeader = tblClientReservations.getTableHeader();
        tableHeader.setBackground(ACCENT_RED);
        tableHeader.setForeground(Color.WHITE);
        tableHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0,
                ACCENT_RED));
        tableHeader.setPreferredSize(new Dimension(0, 32));
        tableHeader.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, col);
                lbl.setBackground(ACCENT_RED);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lbl.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1,
                                new Color(200, 30, 45)),
                        new EmptyBorder(0, 8, 0, 8)));
                lbl.setOpaque(true);
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                return lbl;
            }
        });

        JScrollPane tableScroll = new JScrollPane(tblClientReservations);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        tableScroll.getViewport().setBackground(BG_WHITE);
        tableWrapper.add(tableScroll, BorderLayout.CENTER);

        JPanel messagesWrapper = new JPanel(new BorderLayout(0, 6));
        messagesWrapper.setBackground(BG_WHITE);
        messagesWrapper.setBorder(new EmptyBorder(8, 0, 0, 0));

        JLabel messagesLabel = new JLabel("▸  Mensajes del Sistema");
        messagesLabel.setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
        messagesLabel.setForeground(ACCENT_RED);
        messagesWrapper.add(messagesLabel, BorderLayout.NORTH);

        txtServerLogs = new JTextArea();
        txtServerLogs.setEditable(false);
        txtServerLogs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtServerLogs.setBackground(BG_WHITE);
        txtServerLogs.setForeground(TEXT_DARK);
        txtServerLogs.setLineWrap(true);
        txtServerLogs.setWrapStyleWord(true);
        txtServerLogs.setBorder(new EmptyBorder(8, 14, 8, 14));

        JScrollPane messagesScroll = new JScrollPane(txtServerLogs);
        messagesScroll.setBorder(BorderFactory.createLineBorder(
                BORDER_COLOR, 1));
        messagesWrapper.add(messagesScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tableWrapper, messagesWrapper);
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(null);
        splitPane.setDividerSize(6);
        splitPane.setDividerLocation(0.55);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_WHITE);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Creates a custom date picker component consisting of a read-only text
     * field and an interactive calendar trigger.
     *
     * Logic Flow: 1. Initializes the date to {@link LocalDate#now()} with
     * ISO-8601 formatting. 2. Triggers {@link #refreshComboRenderers()} to
     * synchronize available time slots based on the selected date. 3. Provides
     * a custom-painted button that invokes a popup calendar dialog for date
     * selection.
     *
     * @return A styled JPanel acting as a cohesive date input control.
     */
    private JPanel buildCalendarDatePicker() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_WHITE);
        container.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        container.setPreferredSize(new Dimension(0, 45));

        txtReservationDate = new JTextField();
        txtReservationDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtReservationDate.setBackground(BG_WHITE);
        txtReservationDate.setForeground(TEXT_DARK);
        txtReservationDate.setEditable(false);
        txtReservationDate.setBorder(new EmptyBorder(10, 15, 10, 10));
        txtReservationDate.setText(LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        refreshComboRenderers();

        JButton calBtn = new JButton("▼") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        calBtn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 12));
        calBtn.setBackground(ACCENT_RED);
        calBtn.setForeground(Color.WHITE);
        calBtn.setFocusPainted(false);
        calBtn.setContentAreaFilled(false);
        calBtn.setOpaque(false);
        calBtn.setBorder(new EmptyBorder(5, 14, 5, 14));
        calBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        calBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                calBtn.setBackground(ACCENT_RED_DARK);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                calBtn.setBackground(ACCENT_RED);
            }
        });
        calBtn.addActionListener(e -> showCalendarPopup(calBtn));

        container.add(txtReservationDate, BorderLayout.CENTER);
        container.add(calBtn, BorderLayout.EAST);
        return container;
    }

    /**
     * Displays a custom, undecorated modal popup containing an interactive
     * calendar.
     *
     * Technical implementation: 1. Dynamic Grid: Recalculates day positions and
     * month lengths using {@link LocalDate}. 2. State Validation: Disables past
     * dates to prevent invalid reservations. 3. UX Design: Highlights the
     * current system date and manages focus loss to auto-close the popup when
     * clicking outside. 4. Functional Updates: Uses a Runnable to refresh the
     * UI grid during month/year navigation.
     *
     * @param parent The component used as a coordinate reference for popup
     * positioning.
     */
    private void showCalendarPopup(Component parent) {
        JDialog popup = new JDialog((Frame) SwingUtilities.getWindowAncestor(
                parent), false);
        popup.setUndecorated(true);

        final LocalDate[] view = {LocalDate.now()};

        JPanel calPanel = new JPanel(new BorderLayout(0, 8));
        calPanel.setBackground(BG_WHITE);
        calPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 12, 12, 12)));

        JPanel navPanel = new JPanel(new BorderLayout());
        navPanel.setBackground(BG_WHITE);

        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        monthLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        monthLabel.setForeground(TEXT_DARK);

        JButton prevBtn = new JButton("‹");
        JButton nextBtn = new JButton("›");
        for (JButton b : new JButton[]{prevBtn, nextBtn}) {
            b.setFont(new Font("Segoe UI", Font.BOLD, 16));
            b.setBackground(BG_WHITE);
            b.setForeground(ACCENT_RED);
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
        gridPanel.setBackground(BG_WHITE);

        Runnable buildGrid = () -> {
            gridPanel.removeAll();
            monthLabel.setText(view[0].format(DateTimeFormatter.ofPattern(
                    "MMMM yyyy")));

            for (String d : new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr",
                "Sa"}) {
                JLabel lbl = new JLabel(d, SwingConstants.CENTER);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
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
                boolean isPast = date.isBefore(today);

                JButton dayBtn = new JButton(String.valueOf(d)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        if (isToday) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(getBackground());
                            g2.fillRoundRect(0, 0, getWidth(),
                                    getHeight(), 8, 8);
                            g2.dispose();
                        }
                        super.paintComponent(g);
                    }
                };

                dayBtn.setFont(new Font("Segoe UI", isToday ? Font.BOLD
                        : Font.PLAIN, 12));
                dayBtn.setFocusPainted(false);
                dayBtn.setCursor(isPast ? Cursor.getDefaultCursor()
                        : new Cursor(Cursor.HAND_CURSOR));

                if (isPast) {
                    dayBtn.setBackground(BG_LIGHT);
                    dayBtn.setForeground(TEXT_LIGHT);
                    dayBtn.setEnabled(false);
                    dayBtn.setContentAreaFilled(true);
                    dayBtn.setOpaque(true);
                    dayBtn.setBorder(new EmptyBorder(4, 2, 4, 2));
                } else if (isToday) {
                    dayBtn.setBackground(ACCENT_RED);
                    dayBtn.setForeground(Color.WHITE);
                    dayBtn.setContentAreaFilled(false);
                    dayBtn.setOpaque(false);
                    dayBtn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(ACCENT_RED_DARK, 1),
                            new EmptyBorder(3, 1, 3, 1)));
                } else {
                    dayBtn.setBackground(BG_WHITE);
                    dayBtn.setForeground(TEXT_DARK);
                    dayBtn.setContentAreaFilled(true);
                    dayBtn.setOpaque(true);
                    dayBtn.setBorder(new EmptyBorder(4, 2, 4, 2));
                }

                dayBtn.addActionListener(ev -> {
                    txtReservationDate.setText(date.format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd")));
                    refreshComboRenderers();
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
     * Generates an array of time slots from 08:00 AM to 10:00 PM in 30-minute
     * intervals.
     *
     * Technical Details: 1. Time Range: Defined by {@link LocalTime} constants
     * to ensure data integrity. 2. Formatting: Converts 24-hour internal logic
     * into a localized 12-hour AM/PM String format for the UI. 3. Collection
     * Handling: Uses a dynamic List to populate slots before converting to a
     * fixed-size String array for ComboBox compatibility.
     *
     * @return A String array containing the formatted time intervals.
     */
    private String[] generateTimeSlots() {
        List<String> slots = new ArrayList<>();
        LocalTime time = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(20, 0);

        while (!time.isAfter(end)) {
            String ampm = time.getHour() >= 12 ? "PM" : "AM";
            int displayHour = time.getHour() > 12 ? time.getHour() - 12
                    : time.getHour() == 0 ? 12 : time.getHour();
            slots.add(String.format("%d:%02d %s", displayHour, time.getMinute(),
                    ampm));
            time = time.plusMinutes(30);
        }

        return slots.toArray(String[]::new);
    }

    /**
     * Dynamically updates the end-time options based on the selected
     * start-time.
     *
     * Functional Constraints: 1. Mutual Exclusion: Ensures the end-time is
     * always strictly after the start-time to prevent logical booking errors.
     * 2. State Persistence: Attempts to restore the previously selected
     * end-time if it remains valid within the new range. 3. UI Synchronization:
     * Clears and repopulates the cmbEndTime model whenever the start-time
     * selection changes.
     */
    private void updateEndTimeOptions() {
        String selectedStart = (String) cmbStartTime.getSelectedItem();
        if (selectedStart == null) {
            return;
        }

        String currentEnd = (String) cmbEndTime.getSelectedItem();
        cmbEndTime.removeAllItems();
        cmbEndTime.addItem("— Selecciona hora —");

        String[] allSlots = generateTimeSlots();
        boolean startFound = false;

        for (String slot : allSlots) {
            if (!startFound && slot.equals(selectedStart)) {
                startFound = true;
                continue;
            }
            if (startFound) {
                cmbEndTime.addItem(slot);
            }
        }

        if (currentEnd != null) {
            for (int i = 0; i < cmbEndTime.getItemCount(); i++) {
                if (cmbEndTime.getItemAt(i).equals(currentEnd)) {
                    cmbEndTime.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    /**
     * Calculates and displays the elapsed time between the selected start and
     * end points.
     *
     * Key operations: 1. Parsing: Converts UI string selections into
     * {@link LocalTime} objects. 2. Validation: Checks for chronological
     * consistency, updating the label with a warning color (ACCENT_RED) if the
     * range is invalid. 3. Computation: Uses {@link Duration} to compute
     * precise hour and minute intervals for the final output. 4. Visual
     * Feedback: Updates lblDuration with a success state (SUCCESS_GREEN) upon
     * successful calculation.
     */
    private void updateDuration() {
        String startStr = (String) cmbStartTime.getSelectedItem();
        String endStr = (String) cmbEndTime.getSelectedItem();

        if (startStr == null || endStr == null) {
            lblDuration.setText("Duración: --");
            return;
        }

        try {
            LocalTime start = parseTimeString(startStr);
            LocalTime end = parseTimeString(endStr);

            if (end.isBefore(start) || end.equals(start)) {
                lblDuration.setText("⚠  Hora fin debe ser posterior a inicio");
                lblDuration.setForeground(ACCENT_RED);
                return;
            }

            Duration duration = Duration.between(start, end);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();

            String durationText = String.format("Duración: %d h %d min", hours,
                    minutes);
            lblDuration.setText(durationText);
            lblDuration.setForeground(SUCCESS_GREEN);

        } catch (Exception e) {
            lblDuration.setText("Duración: --");
        }
    }

    /**
     * Converts a 12-hour formatted time string (AM/PM) into a {@link LocalTime}
     * object.
     *
     * Conversion Logic: 1. Tokenization: Splits the input to isolate time
     * components and the AM/PM marker. 2. Military Time Calculation: Adjusts
     * the hour based on the marker (e.g., adding 12 for PM, setting 12 AM to
     * 0). 3. Object Mapping: Returns a normalized LocalTime instance for
     * internal computations and duration logic.
     *
     * @param timeStr Formatted string (e.g., "02:30 PM").
     * @return The equivalent LocalTime representation.
     * @throws NumberFormatException if the numerical parts are invalid.
     */
    private LocalTime parseTimeString(String timeStr) {
        String[] parts = timeStr.split(" ");
        String[] timeParts = parts[0].split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        if (parts[1].equals("PM") && hour != 12) {
            hour += 12;
        } else if (parts[1].equals("AM") && hour == 12) {
            hour = 0;
        }

        return LocalTime.of(hour, minute);
    }

    /**
     * Performs an asynchronous identity validation against the TSE (Tribunal
     * Supremo de Elecciones) API.
     *
     * Technical Workflow: 1. Sanitize: Strips non-numeric characters from the
     * input ID. 2. Concurrency: Spawns a daemon thread to prevent UI freezing
     * during network I/O. 3. Network: Executes a GET request with specific
     * timeouts (6s) and User-Agent headers. 4. Error Handling: Manages various
     * HTTP states (200, 404, 429) and network exceptions (Timeout, UnknownHost)
     * with localized user feedback. 5. Thread Safety: Uses
     * SwingUtilities.invokeLater to ensure UI updates happen exclusively on the
     * Event Dispatch Thread (EDT).
     *
     * @param id The raw identification string to verify.
     */
    private void queryTSE(String id) {
        String cleanId = id.replaceAll("[^0-9]", "");
        if (cleanId.isEmpty()) {
            return;
        }

        sessionCheckedId = cleanId;
        lblApiFeedback.setText("⟳  Verificando en TSE...");
        lblApiFeedback.setForeground(WARNING_AMBER);
        txtClientName.setText("");
        isIdVerified = false;
        btnEstablishConnection.setEnabled(false);

        Thread queryThread = new Thread(() -> {
            String urlStr = TSE_API_URL + cleanId;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent",
                        "VentanaCliente-ReservasSala/1.0");

                int status = conn.getResponseCode();

                if (status == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(),
                                    StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    String fullName = parseNameFromJson(sb.toString(), cleanId);

                    if (fullName != null && !fullName.isBlank()) {
                        final String name = fullName;
                        SwingUtilities.invokeLater(() -> {
                            txtClientName.setText(name);
                            lblApiFeedback.setText("✔  Cédula válida - TSE");
                            lblApiFeedback.setForeground(SUCCESS_GREEN);
                            isIdVerified = true;
                            btnEstablishConnection.setEnabled(true);
                            
                            correoExistente = EmailStorage.getEmail(cleanId);
                            if (correoExistente == null) {
                                txtEmail.setText("");
                                txtEmail.setEnabled(true);
                            } else {
                                txtEmail.setText(correoExistente);
                                txtEmail.setEnabled(false);
                            }
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            txtClientName.setText("");
                            lblApiFeedback.setText("✖  Cédula no registrada");
                            lblApiFeedback.setForeground(ACCENT_RED);
                            isIdVerified = false;
                            btnEstablishConnection.setEnabled(false);
                        });
                    }

                } else if (status == 404) {
                    SwingUtilities.invokeLater(() -> {
                        txtClientName.setText("");
                        lblApiFeedback.setText("✖  Cédula no encontrada");
                        lblApiFeedback.setForeground(ACCENT_RED);
                        isIdVerified = false;
                        btnEstablishConnection.setEnabled(false);
                    });
                } else if (status == 429) {
                    SwingUtilities.invokeLater(() -> {
                        lblApiFeedback.setText(
                                "⚠ Límite de consultas - reintente");
                        lblApiFeedback.setForeground(WARNING_AMBER);
                        btnEstablishConnection.setEnabled(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        lblApiFeedback.setText(
                                "⚠  Error TSE (HTTP " + status + ")");
                        lblApiFeedback.setForeground(WARNING_AMBER);
                        btnEstablishConnection.setEnabled(true);
                    });
                }

            } catch (SocketTimeoutException e) {
                SwingUtilities.invokeLater(() -> {
                    lblApiFeedback.setText("⚠  TSE sin respuesta");
                    lblApiFeedback.setForeground(WARNING_AMBER);
                    btnEstablishConnection.setEnabled(true);
                });
            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> {
                    lblApiFeedback.setText("⚠  Sin internet");
                    lblApiFeedback.setForeground(WARNING_AMBER);
                    btnEstablishConnection.setEnabled(true);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lblApiFeedback.setText("⚠  Error de red");
                    lblApiFeedback.setForeground(WARNING_AMBER);
                    btnEstablishConnection.setEnabled(true);
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });

        queryThread.setDaemon(true);
        queryThread.setName("TSE-ID-Lookup");
        queryThread.start();
    }

    /**
     * Extracts and assembles a person's full name from a JSON response string.
     *
     * The process follows these steps: 1. Pre-validation: Quickly identifies
     * empty result sets to avoid unnecessary parsing. 2. Field Extraction:
     * Isolates the first name, first surname, and second surname using a
     * specialized internal string helper. 3. Assembly: Dynamically constructs
     * the full name using {@link StringBuilder}, ensuring proper spacing and
     * trimming of whitespace.
     *
     * @param json The raw JSON string returned by the API.
     * @param id The identification number associated with the query (for
     * logging/context).
     * @return The formatted full name, or {@code null} if the data is missing
     * or malformed.
     */
    private String parseNameFromJson(String json, String id) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            if (json.contains("\"results\":[]") || json.contains(
                    "\"results\": []")) {
                return null;
            }

            String firstName = extractJsonField(json, "nombre");
            String firstSur = extractJsonField(json, "papellido");
            String secondSur = extractJsonField(json, "sapellido");

            if (firstName == null && firstSur == null) {
                return null;
            }

            StringBuilder sb = new StringBuilder();
            if (firstName != null && !firstName.isBlank()) {
                sb.append(firstName.trim());
            }
            if (firstSur != null && !firstSur.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(firstSur.trim());
            }
            if (secondSur != null && !secondSur.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                sb.append(secondSur.trim());
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Manually extracts a specific string field value from a JSON formatted
     * string.
     *
     * Technical Workflow: 1. Token Localization: Searches for the field pattern
     * within the raw string. 2. Delimiter Navigation: Skips colons and
     * whitespace to find the starting quote or null identifier. 3. Escape
     * Character Handling: Properly processes backslashes to include escaped
     * characters in the final value. 4. Null Safety: Explicitly identifies JSON
     * 'null' values to prevent incorrect string captures.
     *
     * @param json The raw JSON text to parse.
     * @param field The key name to search for.
     * @return The extracted string value, or {@code null} if the field is
     * missing or null.
     */
    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }

        int start = idx + pattern.length();
        while (start < json.length() && (json.charAt(start) == ':'
                || json.charAt(start) == ' ')) {
            start++;
        }

        if (start >= json.length()) {
            return null;
        }
        char first = json.charAt(start);

        if (first == '"') {
            start++;
            StringBuilder value = new StringBuilder();
            while (start < json.length()) {
                char c = json.charAt(start);
                if (c == '\\' && start + 1 < json.length()) {
                    start++;
                    value.append(json.charAt(start));
                } else if (c == '"') {
                    break;
                } else {
                    value.append(c);
                }
                start++;
            }
            String v = value.toString().trim();
            return v.isEmpty() ? null : v;
        } else if (first == 'n') {
            return null;
        }
        return null;
    }

    /**
     * Orchestrates the connection process between the client and the central
     * server.
     *
     * Workflow: 1. UI Validation: Checks for mandatory ID and handles TSE
     * verification fallback. 2. Socket Initiation: Attempts to establish a TCP
     * connection to localhost:8000 with a 3-second timeout. 3. Protocol
     * Handshake: Sends client credentials (Name|ID|Role) and waits for server
     * authorization (e.g., "OK|CONECTADO" or "ERROR|ROL_NO_AUTORIZADO"). 4.
     * State Management: Upon success, initializes I/O streams, updates the UI
     * via CardLayout, and spawns the background listener thread. 5. Exception
     * Handling: Provides specific feedback for connection refusals, timeouts,
     * and general I/O failures.
     */
    private void connect() {
        String name = txtClientName.getText().trim();
        String id = txtClientId.getText().trim();
        String role = (String) cbUserRole.getSelectedItem();

        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Ingrese su número de cédula.",
                    "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!isIdVerified && name.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "La cédula no fue verificada contra el TSE.\n"
                    + "¿Desea continuar de todas formas?",
                    "Cédula no verificada",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            name = JOptionPane.showInputDialog(this,
                    "Ingrese su nombre completo:",
                    "Nombre requerido",
                    JOptionPane.PLAIN_MESSAGE);

            if (name == null || name.trim().isEmpty()) {
                return;
            }
            name = name.trim();
            txtClientName.setText(name);
            isIdVerified = true;
        }

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo obtener el nombre desde el TSE.\n"
                    + "Verifique su cédula.",
                    "Nombre no disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String emailIngresado = txtEmail.getText().trim();

        if (correoExistente == null) {

            if (emailIngresado.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Debe ingresar un correo institucional.",
                        "Correo requerido",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean valido = emailIngresado.endsWith("@est.una.ac.cr")
                    || emailIngresado.endsWith("@una.ac.cr");

            if (!valido) {
                JOptionPane.showMessageDialog(this,
                        "Correo institucional inválido.",
                        "Correo inválido",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            EmailStorage.saveEmail(id, emailIngresado);
        }

        Socket tempSocket = null;
        try {
            tempSocket = new Socket();
            tempSocket.connect(new InetSocketAddress("10.47.240.235", 8000),
                    3000);

            DataInputStream tempIn = new DataInputStream(
                    new BufferedInputStream(tempSocket.getInputStream()));
            DataOutputStream tempOut = new DataOutputStream(
                    new BufferedOutputStream(tempSocket.getOutputStream()));

            tempOut.writeUTF(name + "|" + id + "|" + role);
            tempOut.flush();

            tempSocket.setSoTimeout(3000);
            String response = tempIn.readUTF();
            tempSocket.setSoTimeout(0);

            if (response.equals("ERROR|ROL_NO_AUTORIZADO")) {
                JOptionPane.showMessageDialog(this,
                        "Su cédula no está autorizada para el rol: " + role
                        + "\nContacte al administrador.",
                        "Acceso denegado", JOptionPane.ERROR_MESSAGE);
                closeSilently(tempSocket);
                return;
            }

            if (!response.equals("OK|CONECTADO")) {
                logMessage("❌ Servidor rechazó la conexión: " + response);
                closeSilently(tempSocket);
                return;
            }

            socket = tempSocket;
            inputStream = tempIn;
            outputStream = tempOut;

            isConnected = true;
            isWorkerRunning = true;

            if (!emailIngresado.isEmpty() && correoExistente == null) {
                EmailStorage.saveEmail(txtClientId.getText().trim(),
                        emailIngresado);
            }

            lblStatusIndicator.setText("");
            lblUserWelcome.setText("Bienvenido(a), " + name);
            lblUserWelcome.setForeground(SUCCESS_GREEN);

            btnEstablishConnection.setEnabled(false);
            btnTerminateSession.setVisible(true);
            txtClientId.setEditable(false);
            cbUserRole.setEnabled(false);
            setFormEnabled(true);

            logMessage("✅ Conectado como: " + name + " (DNI: " + id + ")");
            logMessage("Servidor listo. Puede realizar su reserva.");
            setTitle("UNIVERSIDAD NACIONAL - " + name);

            CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
            cl.show(pnlMainContainer, "MENU");

            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.setName("Server-Listener");
            listenerThread.start();

        } catch (ConnectException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "No hay servidor activo en el puerto 8000.\n"
                    + "¿Está corriendo el servidor?",
                    "Sin conexión", JOptionPane.ERROR_MESSAGE);
        } catch (SocketTimeoutException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "El servidor no respondió a tiempo.\n"
                    + "Verifique que esté operativo.",
                    "Tiempo de espera agotado", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "Error de conexión: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Terminates the current session and resets the application to its initial
     * state.
     *
     * The logout sequence performs the following: 1. Concurrency Control:
     * Signals background threads to stop by setting {@code isWorkerRunning} to
     * false. 2. Resource Release: Safely closes the network socket and handles
     * potential IOExceptions. 3. State Reset: Clears session-specific
     * identifiers, verification flags, and data models. 4. UI Restoration:
     * Resets input fields (ID, Name, Role), clears logs, and reverts the layout
     * to the "LOGIN" view using {@link CardLayout}.
     */
    private void logout() {
        isWorkerRunning = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        allReservationsData.clear();
        if (tblModel != null) {
            tblModel.setRowCount(0);
        }
        if (historyModel != null) {
            historyModel.setRowCount(0);
        }
        lastReservationId = null;

        isConnected = false;
        isIdVerified = false;

        txtEmail.setText("");
        txtEmail.setEnabled(false);
        correoExistente = null;
        txtClientId.setText("");
        txtClientId.setEditable(true);
        sessionCheckedId = "";
        txtClientName.setText("");
        cbUserRole.setSelectedIndex(0);
        cbUserRole.setEnabled(true);

        lblApiFeedback.setText("○  Ingrese su cédula para verificar");
        lblApiFeedback.setForeground(TEXT_MUTED);

        lblUserWelcome.setText("Bienvenido(a)");
        lblStatusIndicator.setText("");

        btnEstablishConnection.setEnabled(true);
        btnTerminateSession.setVisible(false);

        txtServerLogs.setText("");

        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");

        CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
        cl.show(pnlMainContainer, "LOGIN");
    }

    /**
     * Safely closes a {@link Socket} without throwing checked exceptions.
     *
     * This helper method ensures that resource cleanup does not clutter the
     * main logic. It verifies if the socket is null or already closed before
     * attempting to terminate the connection.
     *
     * @param s The socket instance to be closed, or {@code null}.
     */
    private void closeSilently(Socket s) {
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Continuous background loop that listens for incoming messages from the
     * server.
     *
     * Technical Design: 1. Message Polling: Uses a blocking
     * {@link DataInputStream#readUTF()} call within a dedicated thread to
     * receive server-side updates. 2. Thread Safety: Offloads message
     * processing and disconnection logic to the Event Dispatch Thread (EDT)
     * using {@link SwingUtilities#invokeLater}. 3. Lifecycle Management:
     * Monitors the {@code isWorkerRunning} flag and socket status to ensure
     * graceful termination during logout or connection loss.
     */
    private void listenToServer() {
        try {
            while (isWorkerRunning && !socket.isClosed()) {
                String msg = inputStream.readUTF();
                SwingUtilities.invokeLater(() -> handleServerResponse(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(this::handleDisconnection);
        }
    }

    /**
     * Handles unexpected connection loss and triggers the cleanup sequence.
     *
     * Workflow: 1. Status Guard: Verifies the current connection state to
     * prevent redundant calls. 2. Feedback: Logs a warning message to inform
     * the user about the network failure. 3. Cleanup: Invokes
     * {@link #disconnect()} to reset UI components and release local resources.
     */
    private void handleDisconnection() {
        if (!isConnected) {
            return;
        }
        logMessage("⚠ Conexión con el servidor perdida.");
        disconnect();
    }

    /**
     * Executes the graceful disconnection sequence and UI rollback.
     *
     * Key Actions: 1. Data Integrity: Iterates through the reservation table to
     * remove pending entries (marked as "ENVIANDO...") that failed to
     * synchronize. 2. Resource Management: Safely terminates the network socket
     * using {@link #closeSilently(Socket)}. 3. UI Reset: Restores all input
     * fields, validation flags, and visual indicators to their default values.
     * 4. Navigation: Switches the view back to the "LOGIN" screen via
     * {@link CardLayout}.
     */
    private void disconnect() {
        isConnected = false;

        for (int i = tblModel.getRowCount() - 1; i >= 0; i--) {
            if ("ENVIANDO...".equals(tblModel.getValueAt(i, 3))) {
                tblModel.removeRow(i);
            }
        }

        allReservationsData.clear();
        if (tblModel != null) {
            tblModel.setRowCount(0);
        }
        if (historyModel != null) {
            historyModel.setRowCount(0);
        }
        lastReservationId = null;

        closeSilently(socket);
        setFormEnabled(false);

        btnEstablishConnection.setEnabled(true);
        txtClientId.setEditable(true);
        cbUserRole.setEnabled(true);
        btnTerminateSession.setVisible(false);
        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");

        txtClientName.setText("");
        txtClientId.setText("");
        txtEmail.setText("");
        txtEmail.setEnabled(false);
        correoExistente = null;
        lblApiFeedback.setText("○  Ingrese su cédula para verificar");
        lblApiFeedback.setForeground(TEXT_MUTED);
        isIdVerified = false;
        sessionCheckedId = "";

        CardLayout cl = (CardLayout) pnlMainContainer.getLayout();
        cl.show(pnlMainContainer, "LOGIN");
    }

    /**
     * Central dispatcher for processing and reacting to server-side messages.
     *
     * This method is responsible for interpreting the custom protocol messages
     * received from the server and updating the UI state accordingly. It acts
     * as the main synchronization point between backend events and the client
     * view.
     *
     * Message Handling Logic: HISTORIAL: Reconstructs the reservation history
     * table model using data provided by the server, ensuring no duplicate
     * entries are added.
     *
     * OK | TEMPORAL: Handles creation of temporary reservations, updating both
     * the active table and historical view, and storing TTL values.
     *
     * OK | CONFIRMADO: Updates reservation status to confirmed, clears TTL
     * information, and refreshes UI renderers.
     *
     * OK | CANCELADO: Updates reservation state to cancelled and removes TTL
     * tracking from the interface.
     *
     * ERROR: Handles server-side errors, logs diagnostic information, and
     * removes inconsistent or pending UI entries when necessary.
     *
     * EXPIRACION:Processes TTL expiration events, updates status to expired,
     * clears TTL display, and notifies the user through alerts.
     *
     * This method also ensures UI consistency by preventing duplicate entries
     * in both the active reservations table and the history model.
     *
     * @param msg The raw pipe-delimited protocol string received from the
     * server.
     */
    private void handleServerResponse(String msg) {
        boolean esTecnico = msg.startsWith("HISTORIAL")
                || msg.startsWith("SLOTS_OCUPADOS")
                || msg.startsWith("SLOT_NUEVO")
                || msg.startsWith("SLOT_LIBRE");

        if (!esTecnico) {
            logMessage("← " + msg);
        }

        String[] parts = msg.split("\\|");

        switch (parts[0]) {

            case "HISTORIAL":
                tblModel.setRowCount(0);
                allReservationsData.clear();
                if (historyModel != null) {
                    historyModel.setRowCount(0);
                }

                for (int i = 1; i < parts.length; i++) {
                    String[] fields = parts[i].split(",", 5);
                    if (fields.length < 5) {
                        continue;
                    }

                    String id = fields[0];
                    String date = fields[1];
                    String timeRng = fields[2] + " - " + fields[3];
                    String status = fields[4];

                    allReservationsData.add(new Object[]{id, date,
                        timeRng, status, "—"});

                    if ("RESERVADO_TEMPORAL".equals(status)
                            || "TEMPORAL".equals(status)
                            || "CONFIRMADO".equals(status)
                            || "CONFIRMADA".equals(status)) {
                        tblModel.addRow(new Object[]{id, date, timeRng,
                            status, "—"});
                    }

                    if (historyModel != null) {
                        historyModel.addRow(new Object[]{id, date, timeRng,
                            status, "—"});
                    }
                }
                refreshComboRenderers();
                break;

            case "OK":
                if (parts.length >= 3 && "TEMPORAL".equals(parts[1])) {

                    String id = parts[2];
                    String ttlStr = parts.length >= 4 ? parts[3] : "?";
                    lastReservationId = id;

                    ttlStr = ttlStr.replace("TTL:", "").trim();

                    long ttlValue;
                    try {
                        ttlValue = Long.parseLong(ttlStr);
                    } catch (NumberFormatException e) {
                        ttlValue = -1;
                    }

                    for (int i = 0; i < tblModel.getRowCount(); i++) {

                        if ("ENVIANDO...".equals(tblModel.getValueAt(i, 3))) {

                            String date
                                    = String.valueOf(tblModel.getValueAt(i, 1));
                            String timeRng
                                    = String.valueOf(tblModel.getValueAt(i, 2));

                            tblModel.setValueAt(id, i, 0);
                            tblModel.setValueAt("TEMPORAL", i, 3);
                            tblModel.setValueAt(ttlValue, i, 4);

                            boolean existsInData = false;
                            for (Object[] row : allReservationsData) {
                                if (id.equals(row[0])) {
                                    existsInData = true;
                                    break;
                                }
                            }
                            if (!existsInData) {
                                allReservationsData.add(new Object[]{id, date,
                                    timeRng, "TEMPORAL", "—"});
                            }

                            boolean existsGlobal = false;

                            for (int j = 0; j < tblModel.getRowCount(); j++) {
                                if (id.equals(tblModel.getValueAt(j, 0))) {
                                    existsGlobal = true;
                                    break;
                                }
                            }

                            if (!existsGlobal) {
                                tblModel.addRow(new Object[]{
                                    id, date, timeRng, "TEMPORAL", ttlValue
                                });
                            }

                            if (historyModel != null) {
                                boolean existsHistory = false;

                                for (int j = 0; j < historyModel.getRowCount();
                                        j++) {
                                    if (id.equals(historyModel.getValueAt(
                                            j, 0))) {
                                        existsHistory = true;
                                        break;
                                    }
                                }

                                if (!existsHistory) {
                                    historyModel.addRow(new Object[]{
                                        id, date, timeRng, "TEMPORAL", "N/A"
                                    });
                                }
                            }

                            clearReservationForm();
                            refreshComboRenderers();
                            break;
                        }
                    }
                } else if (parts.length >= 2 && "CONFIRMADO".equals(parts[1])) {

                    String id = parts.length >= 3 ? parts[2] :lastReservationId;

                    updateTableStatus(id, "CONFIRMADA");
                    clearTTL(id);
                    refreshComboRenderers();

                    JOptionPane.showMessageDialog(this,
                            "Reserva confirmada con éxito.\n"
                            + "Consulta en 'Mis Reservas'.",
                            "Confirmación",
                            JOptionPane.INFORMATION_MESSAGE);
                } else if (parts.length >= 2 && "CANCELADO".equals(parts[1])) {

                    String id = parts.length >= 3 ? parts[2] :lastReservationId;

                    updateTableStatus(id, "CANCELADA");
                    clearTTL(id);
                    refreshComboRenderers();
                } else if (parts.length >= 2 && "EDITADO".equals(parts[1])) {
                    if (parts.length >= 7) {
                        String oldId = parts[2];
                        String newId = parts[3];
                        String date = parts[4];
                        String timeRng = parts[5] + " - " + parts[6];
                        String status = parts.length >= 8 ? parts[7]
                                : "CONFIRMADO";

                        for (int i = tblModel.getRowCount() - 1; i >= 0; i--) {
                            if (oldId.equals(tblModel.getValueAt(i, 0))) {
                                tblModel.removeRow(i);
                                break;
                            }
                        }
                        if (historyModel != null) {
                            for (int i = historyModel.getRowCount() - 1;
                                    i >= 0; i--) {
                                if (oldId.equals(historyModel.getValueAt(
                                        i, 0))) {
                                    historyModel.setValueAt("CANCELADA", i, 3);
                                    break;
                                }
                            }
                        }
                        for (Object[] row : allReservationsData) {
                            if (oldId.equals(row[0])) {
                                row[3] = "CANCELADA";
                                break;
                            }
                        }

                        Object[] newRow = {newId, date, timeRng, status, "—"};
                        tblModel.addRow(newRow);
                        allReservationsData.add(newRow.clone());
                        if (historyModel != null) {
                            historyModel.addRow(newRow.clone());
                        }

                        refreshComboRenderers();
                        logMessage("Reserva editada: " + oldId + " → " +newId);
                    }
                }
                break;

            case "ERROR":
                logMessage("❌ Error del servidor: "
                        + (parts.length > 1 ? parts[1] : "desconocido"));

                if (parts.length > 1 && "SERVIDOR_DETENIDO".equals(parts[1])) {
                    handleDisconnection();
                    return;
                }
                
                if (parts.length > 1 && "EDICION_FALLIDA".equals(parts[1])) {
                    String id = parts.length >= 3 ? parts[2] : "";
                    logMessage("⚠ No se pudo completar la edición de " + id);
                }

                for (int i = tblModel.getRowCount() - 1; i >= 0; i--) {
                    if ("ENVIANDO...".equals(tblModel.getValueAt(i, 3))) {
                        tblModel.removeRow(i);
                        break;
                    }
                }
                break;

            case "EXPIRACION":
                if (parts.length >= 2) {

                    String expiredId = parts[1];

                    updateTableStatus(expiredId, "EXPIRADA");
                    clearTTL(expiredId);
                    refreshComboRenderers();
                    logMessage("Reserva " + expiredId + " expiró.");

                    JOptionPane.showMessageDialog(this,
                            "Tu reserva " + expiredId + " expiró por TTL.\n"
                            + "Puede realizar una nueva reserva.",
                            "Reserva expirada",
                            JOptionPane.WARNING_MESSAGE);
                }
                break;

            case "FINALIZADO":
                if (parts.length >= 2) {
                    String finId = parts[1];
                    updateTableStatus(finId, "FINALIZADA");
                    clearTTL(finId);
                    refreshComboRenderers();
                    logMessage("Reserva " + finId + " finalizada.");
                }
                break;

            case "LIMPIAR_HISTORIAL":
                allReservationsData.removeIf(row -> {
                    String st = row[3].toString();
                    return "CANCELADA".equals(st) || "CANCELADO".equals(st)
                            || "EXPIRADA".equals(st) || "EXPIRADO".equals(st)
                            || "FINALIZADA".equals(st) || "FINALIZADO".equals(st);
                });
                if (historyModel != null) {
                    historyModel.setRowCount(0);
                    for (Object[] row : allReservationsData) {
                        historyModel.addRow(row);
                    }
                }
                applyHistoryFilters();
                logMessage("! El administrador limpió el historial. ¡");
                break;

            case "SLOTS_OCUPADOS":
                otherUsersSlots.clear();
                for (int i = 1; i < parts.length; i++) {
                    String[] f = parts[i].split(",");
                    if (f.length < 3) {
                        continue;
                    }
                    otherUsersSlots.computeIfAbsent(f[0],
                            k -> new java.util.ArrayList<>())
                            .add(new String[]{f[1], f[2]});
                }
                refreshComboRenderers();
                break;

            case "SLOT_NUEVO":
                if (parts.length >= 4) {
                    otherUsersSlots.computeIfAbsent(parts[1],
                            k -> new java.util.ArrayList<>())
                            .add(new String[]{parts[2], parts[3]});
                    refreshComboRenderers();
                }
                break;

            case "SLOT_LIBRE":
                if (parts.length >= 4) {
                    List<String[]> ranges = otherUsersSlots.get(parts[1]);
                    if (ranges != null) {
                        ranges.removeIf(r -> r[0].equals(parts[2])
                                && r[1].equals(parts[3]));
                    }
                    refreshComboRenderers();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Resets all input components in the reservation form to their default
     * states.
     *
     * This ensures a clean slate for the next entry by clearing text fields,
     * resetting combo box selections, and updating visual indicators like the
     * duration label and custom renderers.
     */
    private void clearReservationForm() {
        txtAttendeeCount.setText("0");
        cmbStartTime.setSelectedIndex(0);
        cmbEndTime.setSelectedIndex(0);
        cbEquipmentType.setSelectedIndex(0);
        lblDuration.setText("Duración: --");
        refreshComboRenderers();
        cbEquipmentType.setSelectedIndex(0);
        spnEquipQuantity.setValue(1);
        spnEquipQuantity.setEnabled(false);
        txtReservationDate.setText(java.time.LocalDate.now().toString());
    }

    /**
     * Removes the TTL countdown indicator for a specific reservation once it
     * has been confirmed or cancelled.
     *
     * @param id The unique identifier of the reservation to update. If the ID
     * is found, the TTL column is reset to a placeholder ("-") and the table is
     * repainted to reflect the change.
     */
    private void clearTTL(String id) {
        for (int i = 0; i < tblModel.getRowCount(); i++) {
            if (id != null && id.equals(tblModel.getValueAt(i, 0))) {
                tblModel.setValueAt("—", i, 4);
                tblClientReservations.repaint();
                break;
            }
        }

        if (historyModel != null) {
            for (int i = 0; i < historyModel.getRowCount(); i++) {
                if (id != null && id.equals(historyModel.getValueAt(i, 0))) {
                    historyModel.setValueAt("—", i, 4);
                    break;
                }
            }
        }

        for (Object[] row : allReservationsData) {
            if (id != null && id.equals(row[0])) {
                row[4] = "—";
                break;
            }
        }
    }

    /**
     * Updates the status column of a specific reservation in the UI table.
     *
     * This method synchronizes the local view with the server state by locating
     * the row using its reservation ID and updating its status value (e.g.
     * CONFIRMADA, EXPIRADA, CANCELADA). It ensures that the UI remains
     * consistent with backend changes in real time.
     *
     * @param id the identifier of the reservation to be updated
     * @param newStatus the new status value to display in the table
     */
    private void updateTableStatus(String id, String newStatus) {
        for (int i = 0; i < tblModel.getRowCount(); i++) {
            if (id != null && id.equals(tblModel.getValueAt(i, 0))) {
                if ("CANCELADA".equals(newStatus)
                        || "EXPIRADA".equals(newStatus) 
                        || "FINALIZADA".equals(newStatus)) {
                    tblModel.removeRow(i);
                } else {
                    tblModel.setValueAt(newStatus, i, 3);
                }
                tblClientReservations.repaint();
                break;
            }
        }

        for (Object[] row : allReservationsData) {
            if (id != null && id.equals(row[0])) {
                row[3] = newStatus;
                break;
            }
        }

        if (historyModel != null) {
            boolean found = false;
            for (int i = 0; i < historyModel.getRowCount(); i++) {
                if (id != null && id.equals(historyModel.getValueAt(i, 0))) {
                    historyModel.setValueAt(newStatus, i, 3);
                    found = true;
                    break;
                }
            }

            if (!found) {
                for (Object[] row : allReservationsData) {
                    if (id != null && id.equals(row[0])) {
                        historyModel.addRow(new Object[]{
                            row[0], row[1], row[2], newStatus, "—"
                        });
                        break;
                    }
                }
            }
        }
    }

    /**
     * Initiates a new reservation request.
     *
     * Workflow: 1. Validation: Checks connection status and ensures all
     * required fields are filled. 2. Formatting: Converts selected times to a
     * 24-hour server-compliant format. 3. UI Feedback: Adds a placeholder row
     * ("ENVIANDO...") to the table. 4. Transmission: Sends the "RESERVAR"
     * command with the assembled reservation parameters to the server.
     */
    private void reserve() {
        if (!isConnected) {
            return;
        }

        String date = txtReservationDate.getText().trim();
        if (date.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione una fecha.",
                    "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String startTime = convertToServerFormat(
                (String) cmbStartTime.getSelectedItem());
        String endTime = convertToServerFormat(
                (String) cmbEndTime.getSelectedItem());
        String attendees = txtAttendeeCount.getText().trim();
        String role = (String) cbUserRole.getSelectedItem();

        if (attendees.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Complete todos los campos antes de reservar.",
                    "Campos vacíos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String equipType = (String) cbEquipmentType.getSelectedItem();
        int equipQty = (int) spnEquipQuantity.getValue();

        String equipField;
        if ("NINGUNO".equals(equipType)) {
            equipField = "NINGUNO";
        } else if ("COMPLETO".equals(equipType)) {
            equipField = "COMPLETO";
        } else {
            equipField = equipType + ":" + equipQty;
        }

        tblModel.addRow(new Object[]{"...", date,
            startTime + " - " + endTime, "ENVIANDO...", "..."});

        sendMessage("RESERVAR|" + date + "|" + startTime + "|" + endTime
                + "|" + attendees + "|" + equipField + "|" + role);
    }

    /**
     * Normalizes 12-hour (AM/PM) time strings into 24-hour (HH:mm) format.
     *
     * This ensures the server receives a consistent time representation
     * regardless of the UI's display format. Handles edge cases like 12:00
     * AM/PM correctly.
     *
     * @param timeStr The UI time string (e.g., "02:30 PM").
     * @return A formatted 24-hour string (e.g., "14:30").
     */
    private String convertToServerFormat(String timeStr) {
        // Convert "8:00 AM" to "08:00"
        if (timeStr == null) {
            return "";
        }
        try {
            String[] parts = timeStr.split(" ");
            String[] timeParts = parts[0].split(":");
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            if (parts[1].equals("PM") && hour != 12) {
                hour += 12;
            } else if (parts[1].equals("AM") && hour == 12) {
                hour = 0;
            }

            return String.format("%02d:%02d", hour, minute);
        } catch (NumberFormatException e) {
            return timeStr;
        }
    }

    /**
     * Decrements the Time-To-Live (TTL) counter for all pending "TEMPORAL"
     * reservations.
     *
     * Designed to be called by a Swing Timer, this method scans the table
     * model, extracts the current TTL value, and updates the view with the
     * decremented count until it reaches zero or the reservation is confirmed.
     */
    private void updateTTL() {
        for (int i = 0; i < tblModel.getRowCount(); i++) {
            Object statusObj = tblModel.getValueAt(i, 3);
            Object ttlObj = tblModel.getValueAt(i, 4);
            if (statusObj == null || ttlObj == null) {
                continue;
            }
            if (!"TEMPORAL".equals(statusObj.toString())) {
                continue;
            }
            try {
                String ttlStr = ttlObj.toString();
                if (ttlStr.contains(":")) {
                    ttlStr = ttlStr.split(":")[1].trim();
                }
                long ttl = Long.parseLong(ttlStr);
                tblModel.setValueAt(ttl > 0 ? ttl - 1 : 0, i, 4);
            } catch (NumberFormatException e) {
                System.out.println("Invalid TTL: " + ttlObj);
            }
        }
    }

    /**
     * Sends a confirmation request for the currently selected reservation in
     * the table.
     *
     * Guard Clauses: 1. Selection: Verifies that a row is actually selected. 2.
     * State: Ensures the reservation is not in a pending "ENVIANDO" state
     * before sending the "CONFIRMAR" protocol command.
     */
    private void confirmReservation() {
        int row = tblClientReservations.getSelectedRow();
        if (row < 0) {
            logMessage("⚠ Seleccione una reserva para confirmar.");
            return;
        }

        String id = String.valueOf(tblModel.getValueAt(row, 0));
        String status = String.valueOf(tblModel.getValueAt(row, 3));

        if ("ENVIANDO...".equals(status) || "...".equals(id)) {
            logMessage("⚠ Espere la respuesta del servidor.");
            return;
        }

        if (!"TEMPORAL".equals(status)) {
            logMessage("⚠ Solo reservas TEMPORALES pueden confirmarse aquí.");
            return;
        }

        logMessage("Confirmando reserva " + id + "...");
        sendMessage("CONFIRMAR|" + id);
    }

    /**
     * Sends a cancellation request for the currently selected reservation.
     *
     * Similar to confirmation, it verifies the selection and state before
     * transmitting the "CANCELAR" command to release the reserved slot on the
     * server.
     */
    private void cancelReservation() {
        int row = tblClientReservations.getSelectedRow();
        if (row < 0) {
            logMessage("⚠ Seleccione una reserva para cancelar.");
            return;
        }

        String id = String.valueOf(tblModel.getValueAt(row, 0));
        String status = String.valueOf(tblModel.getValueAt(row, 3));

        if ("...".equals(id) || "ENVIANDO...".equals(status)) {
            logMessage("⚠ Espere la respuesta del servidor.");
            return;
        }

        if ("EXPIRADA".equals(status)) {
            logMessage("⚠ Esta reserva ya expiró.");
            return;
        }

        logMessage("Cancelando reserva " + id + "...");
        sendMessage("CANCELAR|" + id);
    }

    /**
     * Sends a UTF-encoded string message to the server via the established
     * output stream.
     *
     * Includes an immediate flush to ensure packet delivery and logs the
     * transaction for local debugging. Catches IOExceptions to prevent UI
     * crashes during network failures.
     *
     * @param message The protocol command or data string to transmit.
     */
    private void sendMessage(String message) {
        try {
            outputStream.writeUTF(message);
            outputStream.flush();
            logMessage(" - " + message);
        } catch (IOException e) {
            logMessage("❌ Error al enviar: " + e.getMessage());
        }
    }

    /**
     * Formats and appends a message to the internal server logs area.
     *
     * Adds a localized HH:mm:ss timestamp and automatically scrolls the
     * JTextArea to the bottom to keep the latest events visible.
     *
     * @param msg The event description or server response to log.
     */
    private void logMessage(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                "HH:mm:ss"));
        txtServerLogs.append("[" + time + "]  " + msg + "\n");
        txtServerLogs.setCaretPosition(txtServerLogs.getDocument().getLength());
    }

    /**
     * Enables or disables all input controls in the reservation form.
     *
     * Used to lock user interaction before a server connection is established,
     * and to restore it once the client is authenticated. The affected
     * components include the date field, start and end time selectors, attendee
     * count field, equipment type selector, and the submit, confirm, and cancel
     * buttons.
     *
     * @param enabled {@code true} to enable the form controls, {@code false} to
     * disable them.
     */
    private void setFormEnabled(boolean enabled) {
        txtReservationDate.setEnabled(enabled);
        cmbStartTime.setEnabled(enabled);
        cmbEndTime.setEnabled(enabled);
        txtAttendeeCount.setEnabled(enabled);
        cbEquipmentType.setEnabled(enabled);
        btnSubmitRequest.setEnabled(enabled);
        btnConfirmSelection.setEnabled(enabled);
        btnAbortReservation.setEnabled(enabled);
        cbEquipmentType.setEnabled(enabled);
        spnEquipQuantity.setEnabled(enabled
                && !"NINGUNO".equals(cbEquipmentType.getSelectedItem())
                && !"COMPLETO".equals(cbEquipmentType.getSelectedItem()));
    }

    /**
     * Constructs a styled JTextField with custom focus-based placeholder logic.
     *
     * Applies Segoe UI typography, internal padding, and a focus listener that
     * toggles placeholder visibility and text color dynamically.
     *
     * @param placeholder The ghost text to display when the field is empty.
     * @return A pre-configured and styled JTextField instance.
     */
    private JTextField buildTextField(String placeholder) {
        JTextField field = new JTextField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBackground(BG_WHITE);
        field.setForeground(TEXT_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));
        field.setPreferredSize(new Dimension(0, 45));
        field.setText(placeholder);

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (field.getText().equals(placeholder)) {
                    field.setText("");
                    field.setForeground(TEXT_DARK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (field.getText().isEmpty()) {
                    field.setText(placeholder);
                    field.setForeground(TEXT_MUTED);
                }
            }
        });
        return field;
    }

    /**
     * Configures a custom visual theme for JComboBox components.
     *
     * It sets typography, colors, and a custom ListCellRenderer to handle
     * selection highlights (using ACCENT_RED) and consistent padding across all
     * dropdown items.
     */
    private void styleCombo(JComboBox<?> combo) {
        combo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        combo.setBackground(BG_WHITE);
        combo.setForeground(TEXT_DARK);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(5, 10, 5, 10)));
        combo.setPreferredSize(new Dimension(0, 45));

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
                setBackground(isSelected ? ACCENT_RED : BG_WHITE);
                setForeground(isSelected ? Color.WHITE : TEXT_DARK);
                setBorder(new EmptyBorder(5, 10, 5, 10));
                return this;
            }
        });
    }

    /**
     * Creates a styled JButton with advanced 2D graphics and hover effects.
     *
     * Implementation details: 1. Geometry: Overrides paintComponent to render
     * smooth rounded corners. 2. Interactivity: Attaches MouseListeners for
     * dynamic background color transitions during hover events. 3. Styling:
     * Supports both primary (filled) and secondary (outlined) visual variants.
     *
     * @param text The label text for the button.
     * @param color The base theme color (applied to background or border).
     * @param isPrimary {@code true} for solid fill, {@code false} for outline
     * style.
     * @return A fully configured and interactive JButton.
     */
    private JButton buildActionButton(String text, Color color,
            boolean isPrimary) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (isPrimary) {
            btn.setBackground(color);
            btn.setForeground(Color.WHITE);
            btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        } else {
            btn.setBackground(BG_WHITE);
            btn.setForeground(color);
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(color, 2),
                    new EmptyBorder(8, 18, 8, 18)));
        }

        Color bgNormal = btn.getBackground();
        Color bgHover = isPrimary ? color.darker()
                : new Color(color.getRed(), color.getGreen(),
                        color.getBlue(), 30);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(bgHover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(bgNormal);
            }
        });

        return btn;
    }

    /**
     * Application entry point.Sets the System Look and Feel to match the OS
     * environment and launches the ClientView on the Event Dispatch Thread
     * (EDT) to ensure thread-safe UI initialization.
     *
     * @param args
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | IllegalAccessException
                    | InstantiationException
                    | UnsupportedLookAndFeelException ignored) {
            }
            new ClientView().setVisible(true);
        });
    }
}