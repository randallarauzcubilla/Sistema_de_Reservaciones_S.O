package UI;

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
import javax.swing.SpinnerDateModel;
import javax.swing.JSpinner;
import javax.swing.JFormattedTextField;
import java.util.Date;
import javax.imageio.ImageIO;

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
    private static final String TSE_API_URL = "https://apis.gometa.org/cedulas/";

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
    private JTextField txtName;
    private JTextField txtDNI;
    private JComboBox<String> cmbRole;
    private JButton btnConnect;
    private JButton btnLogout;
    private JLabel lblConnectionStatus;
    private JLabel lblVerification;
    private JLabel lblWelcome;

    // =========================================================
    // RESERVATION FORM COMPONENTS
    // =========================================================
    private JTextField txtDate;
    private JComboBox<String> cmbStartTime;
    private JComboBox<String> cmbEndTime;
    private JTextField txtAttendees;
    private JComboBox<String> cmbEquipment;
    private JButton btnReserve;
    private JButton btnConfirm;
    private JButton btnCancel;
    private JLabel lblDuration;
    private JButton btnBackToMenu;
    private JButton btnOpenReservation; // New: "Crear Reserva" button

    // =========================================================
    // TABLE & MESSAGE COMPONENTS
    // =========================================================
    private JTable reservationsTable;
    private DefaultTableModel reservationsModel;
    private JTextArea txtMessages;

    // =========================================================
    // APPLICATION STATE
    // =========================================================
    private boolean isConnected = false;
    private boolean idVerified = false;
    private String lastCheckedId = "";
    private JPanel mainPanel;
    private JPanel loginPanel;
    private JPanel menuPanel;
    private JPanel reservationPanel;
    private volatile boolean running = false;
    private Timer ttlTimer;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================
    public ClientView() {
        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setBackground(BG_WHITE);
        initComponents();
        ttlTimer = new Timer(1000, e -> updateTTL());
        ttlTimer.start();
    }

    // =========================================================
    // COMPONENT INITIALIZATION
    // =========================================================
    private void initComponents() {
        // Root panel with CardLayout for navigation
        mainPanel = new JPanel(new CardLayout());
        mainPanel.setBackground(BG_WHITE);

        // Create different panels
        loginPanel = buildLoginPanel();
        menuPanel = buildMenuPanel();
        reservationPanel = buildReservationPanel();

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(menuPanel, "MENU");
        mainPanel.add(reservationPanel, "RESERVATION");

        // Header
        JPanel headerPanel = createHeader();

        // Main layout
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_WHITE);
        root.add(headerPanel, BorderLayout.NORTH);
        root.add(mainPanel, BorderLayout.CENTER);

        add(root);

        // Show login initially
        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "LOGIN");
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT_RED);
        header.setBorder(new EmptyBorder(15, 25, 15, 25));

        // Left: University name and system title
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

        // Right: logo + botón logout
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setBackground(ACCENT_RED);

        JLabel logoLabel = loadLogo(160, 55);
        if (logoLabel != null) {
            rightPanel.add(logoLabel);
        }

        btnLogout = new JButton("Cerrar Sesión");
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFocusPainted(false);
        btnLogout.setContentAreaFilled(false);
        btnLogout.setOpaque(false);
        btnLogout.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.WHITE, 1),
                new EmptyBorder(7, 18, 7, 18)));
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogout.setVisible(false);
        btnLogout.addActionListener(e -> logout());
        btnLogout.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnLogout.setForeground(ACCENT_RED);
                btnLogout.setOpaque(true);
                btnLogout.setBackground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnLogout.setForeground(Color.WHITE);
                btnLogout.setOpaque(false);
            }
        });

        rightPanel.add(btnLogout);
        header.add(rightPanel, BorderLayout.EAST);

        return header;
    }

    private JLabel loadLogo(int targetW, int targetH) {
        try {
            BufferedImage original = null;

            // Intento 1: classpath raíz
            java.io.InputStream is = getClass().getResourceAsStream("/LogoBlanco.png");
            if (is != null) {
                original = ImageIO.read(is);
            }

            // Intento 2: mismo paquete que la clase
            if (original == null) {
                is = getClass().getResourceAsStream("LogoBlanco.png");
                if (is != null) {
                    original = ImageIO.read(is);
                }
            }

            // Intento 3: carpeta resources relativa al directorio de ejecución
            if (original == null) {
                java.io.File f = new java.io.File("resources/LogoBlanco.png");
                if (f.exists()) {
                    original = ImageIO.read(f);
                }
            }

            // Intento 4: carpeta src/resources
            if (original == null) {
                java.io.File f = new java.io.File("src/resources/LogoBlanco.png");
                if (f.exists()) {
                    original = ImageIO.read(f);
                }
            }

            // Intento 5: ruta absoluta desde el jar/clase
            if (original == null) {
                java.net.URL url = getClass().getClassLoader()
                        .getResource("LogoBlanco.png");
                if (url != null) {
                    original = ImageIO.read(url);
                }
            }

            if (original == null) {
                System.out.println("Logo no encontrado en ninguna ruta.");
                return null;
            }

            BufferedImage transparent = new BufferedImage(
                    original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < original.getHeight(); y++) {
                for (int x = 0; x < original.getWidth(); x++) {
                    int rgb = original.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int lum = (r * 299 + g * 587 + b * 114) / 1000;
                    // Curva de contraste: oscuros → transparente, claros → opaco total
                    int alpha = lum < 40 ? 0
                            : lum > 180 ? 255
                                    : (lum - 40) * 255 / 140;
                    transparent.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }

            // Escalado multi-paso: reducir a la mitad en cada iteración evita
            // la pérdida de detalle de un único salto grande → imagen más nítida.
            BufferedImage scaled = multiStepScale(transparent, targetW, targetH);

            JLabel lbl = new JLabel(new ImageIcon(scaled));
            lbl.setBorder(new EmptyBorder(0, 0, 0, 10));
            return lbl;

        } catch (Exception e) {
            System.out.println("Error cargando logo: " + e.getMessage());
            return null;
        }
    }

    // =========================================================
    // MULTI-STEP SCALING — mantiene nitidez al reducir logos
    // =========================================================
    private BufferedImage multiStepScale(BufferedImage src, int targetW, int targetH) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage current = src;

        // Reducir a la mitad repetidamente mientras sea más del doble del tamaño objetivo
        while (w > targetW * 2 || h > targetH * 2) {
            w = Math.max(w / 2, targetW);
            h = Math.max(h / 2, targetH);
            BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                    RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.drawImage(current, 0, 0, w, h, null);
            g2.dispose();
            current = tmp;
        }

        // Paso final con máxima calidad hacia el tamaño exacto
        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.drawImage(current, 0, 0, targetW, targetH, null);
        g2.dispose();
        return result;
    }

    // =========================================================
    // LOGIN PANEL (STEP 1)
    // =========================================================
    private JPanel buildLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_WHITE);

        JPanel loginCard = new JPanel(new GridBagLayout());
        loginCard.setBackground(BG_LIGHT);
        loginCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(30, 40, 30, 40)));
        loginCard.setPreferredSize(new Dimension(450, 500));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 10, 8, 10);

        // Title
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 25, 10);
        JLabel lblLoginTitle = new JLabel("Inicio de Sesión", SwingConstants.CENTER);
        lblLoginTitle.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblLoginTitle.setForeground(ACCENT_RED);
        loginCard.add(lblLoginTitle, gbc);

        // DNI/Cédula field
        gbc.gridy = 1;
        gbc.insets = new Insets(8, 10, 5, 10);
        JLabel lblDNI = new JLabel("Número de Cédula");
        lblDNI.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDNI.setForeground(TEXT_DARK);
        loginCard.add(lblDNI, gbc);

        lblConnectionStatus = new JLabel(" ");
        lblConnectionStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblConnectionStatus.setForeground(Color.GRAY);

        gbc.gridy++;
        panel.add(lblConnectionStatus, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 10, 5, 10);
        txtDNI = new JTextField();
        txtDNI.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtDNI.setBackground(BG_WHITE);
        txtDNI.setForeground(TEXT_DARK);
        txtDNI.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));
        txtDNI.setPreferredSize(new Dimension(0, 45));

        // AGREGAR ESTO: ActionListener para cuando presiona Enter
        txtDNI.addActionListener(e -> {
            String id = txtDNI.getText().trim();
            if (!id.isEmpty() && !id.equals(lastCheckedId)) {
                queryTSE(id);
            }
        });

        // AGREGAR ESTO: FocusListener para cuando pierde el foco
        txtDNI.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String id = txtDNI.getText().trim();
                if (!id.isEmpty() && !id.equals(lastCheckedId)) {
                    queryTSE(id);
                }
            }
        });

        loginCard.add(txtDNI, gbc);
        // Verification indicator
        gbc.gridy = 3;
        gbc.insets = new Insets(5, 10, 5, 10);
        lblVerification = new JLabel("○  Ingrese su cédula para verificar");
        lblVerification.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 11));
        lblVerification.setForeground(TEXT_MUTED);
        loginCard.add(lblVerification, gbc);

        // Name field (read-only)
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 10, 5, 10);
        JLabel lblName = new JLabel("Nombre Completo");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblName.setForeground(TEXT_DARK);
        loginCard.add(lblName, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(0, 10, 5, 10);
        txtName = new JTextField();
        txtName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        txtName.setBackground(new Color(245, 245, 245));
        txtName.setForeground(TEXT_DARK);
        txtName.setEditable(false);
        txtName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(10, 15, 10, 15)));
        txtName.setPreferredSize(new Dimension(0, 45));
        loginCard.add(txtName, gbc);

        // Role selection
        gbc.gridy = 6;
        gbc.insets = new Insets(10, 10, 5, 10);
        JLabel lblRole = new JLabel("Rol en la Universidad");
        lblRole.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblRole.setForeground(TEXT_DARK);
        loginCard.add(lblRole, gbc);

        gbc.gridy = 7;
        gbc.insets = new Insets(0, 10, 5, 10);
        cmbRole = new JComboBox<>(new String[]{"ESTUDIANTE", "DOCENTE", "DECANATURA"});
        styleCombo(cmbRole);
        cmbRole.setPreferredSize(new Dimension(0, 45));
        loginCard.add(cmbRole, gbc);

        // Connect button
        gbc.gridy = 8;
        gbc.insets = new Insets(25, 10, 10, 10);
        btnConnect = new JButton("Ingresar al Sistema");
        btnConnect.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnConnect.setBackground(ACCENT_RED);
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);

        // IMPORTANTE
        btnConnect.setContentAreaFilled(true);
        btnConnect.setOpaque(true);
        btnConnect.setBorderPainted(false);

        btnConnect.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        btnConnect.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnConnect.addActionListener(e -> connect());
        loginCard.add(btnConnect, gbc);

        panel.add(loginCard);
        return panel;
    }

    // =========================================================
    // MENU PANEL (STEP 2)
    // =========================================================
    private JPanel buildMenuPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_LIGHT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Tarjeta principal
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

        // Título
        JLabel title = new JLabel("Menú Principal", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(ACCENT_RED);
        gbcCard.gridy = 0;
        menuCard.add(title, gbcCard);

        // Bienvenida
        lblWelcome = new JLabel("Bienvenido(a)", SwingConstants.CENTER);
        lblWelcome.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblWelcome.setForeground(SUCCESS_GREEN);
        gbcCard.gridy = 1;
        menuCard.add(lblWelcome, gbcCard);

        // PANEL DE TARJETAS
        JPanel cardsPanel = new JPanel(new GridLayout(1, 2, 30, 0));
        cardsPanel.setBackground(BG_LIGHT);

        // Tarjeta Crear Reserva
        JPanel cardReserve = createMenuCard(
                "+",
                "Crear Reserva",
                "Nueva reserva de sala",
                () -> {

                    CardLayout cl = (CardLayout) mainPanel.getLayout();
                    cl.show(mainPanel, "RESERVATION");
                }
        );

        // Tarjeta Mis Reservas
        JPanel cardMyReservations = createMenuCard(
                "≡",
                "Mis Reservas",
                "Ver reservas activas",
                () -> {
                    JOptionPane.showMessageDialog(this, "Funcionalidad próximamente");
                }
        );

        cardsPanel.add(cardReserve);
        cardsPanel.add(cardMyReservations);

        gbcCard.gridy = 2;
        gbcCard.insets = new Insets(30, 10, 10, 10);
        menuCard.add(cardsPanel, gbcCard);

        panel.add(menuCard, gbc);

        return panel;
    }

    private JPanel createMenuCard(String icon, String title, String subtitle, Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 200, 200), 1, true),
                new EmptyBorder(30, 40, 30, 40)
        ));
        card.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // ICONO
        JLabel lblIcon = new JLabel(icon, SwingConstants.CENTER);
        lblIcon.setFont(new Font("Segoe UI", Font.BOLD, 40));
        lblIcon.setForeground(ACCENT_RED);
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        // TITULO
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(TEXT_DARK);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // SUBTITULO
        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(TEXT_MUTED);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(lblIcon);
        card.add(Box.createVerticalStrut(10));
        card.add(lblTitle);
        card.add(Box.createVerticalStrut(5));
        card.add(lblSub);

        // EVENTOS (CLICK + HOVER)
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
                        BorderFactory.createLineBorder(new Color(255, 200, 200), 1, true),
                        new EmptyBorder(30, 40, 30, 40)
                ));
            }
        });

        return card;
    }

    private java.util.Set<String> getTakenSlots(String date) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (int i = 0; i < reservationsModel.getRowCount(); i++) {
            String rowDate = (String) reservationsModel.getValueAt(i, 1);
            String rowStatus = (String) reservationsModel.getValueAt(i, 3);
            if (!date.equals(rowDate)) {
                continue;
            }
            if ("CANCELADA".equals(rowStatus) || "EXPIRADA".equals(rowStatus)) {
                continue;
            }
            String timeRange = (String) reservationsModel.getValueAt(i, 2);
            if (timeRange == null) {
                continue;
            }
            String[] parts = timeRange.split(" - ");
            if (parts.length < 2) {
                continue;
            }
            // Marcar todos los slots dentro del rango como tomados
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
        return taken;
    }

    private void refreshComboRenderers() {
        if (reservationsModel == null) {
            return;
        }
        String date = txtDate.getText().trim();
        java.util.Set<String> taken = getTakenSlots(date);

        ListCellRenderer<Object> renderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
                setBorder(new EmptyBorder(5, 10, 5, 10));
                if (taken.contains(value)) {
                    setForeground(new Color(180, 180, 180));
                    setBackground(isSelected ? new Color(245, 245, 245) : BG_WHITE);
                    setText(value + "  ✗");
                } else {
                    setForeground(isSelected ? Color.WHITE : TEXT_DARK);
                    setBackground(isSelected ? ACCENT_RED : BG_WHITE);
                }
                return this;
            }
        };

        cmbStartTime.setRenderer(renderer);
        cmbEndTime.setRenderer(renderer);

        cmbStartTime.repaint();
        cmbEndTime.repaint();
    }

    private JPanel buildReservationPanel() {
        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(BG_WHITE);
        content.setBorder(new EmptyBorder(20, 20, 10, 20));

        JPanel formSection = buildFormPanel();
        JPanel bottomSection = buildTableAndMessagesPanel();

        content.add(formSection, BorderLayout.NORTH);
        content.add(bottomSection, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        // Panel externo: scroll arriba, botón "Volver" siempre visible abajo
        JPanel outer = new JPanel(new BorderLayout(0, 5));
        outer.setBackground(BG_WHITE);
        outer.add(scroll, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(BG_WHITE);
        buttonPanel.setBorder(new EmptyBorder(5, 15, 10, 15));

        btnBackToMenu = new JButton("←  Volver al Menú");
        btnBackToMenu.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnBackToMenu.setForeground(ACCENT_RED);
        btnBackToMenu.setFocusPainted(false);
        btnBackToMenu.setContentAreaFilled(false);
        btnBackToMenu.setOpaque(false);
        btnBackToMenu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_RED, 1),
                new EmptyBorder(7, 18, 7, 18)));
        btnBackToMenu.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBackToMenu.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnBackToMenu.setOpaque(true);
                btnBackToMenu.setBackground(ACCENT_RED);
                btnBackToMenu.setForeground(Color.WHITE);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btnBackToMenu.setOpaque(false);
                btnBackToMenu.setForeground(ACCENT_RED);
            }
        });
        btnBackToMenu.addActionListener(e -> {
            CardLayout cl = (CardLayout) mainPanel.getLayout();
            cl.show(mainPanel, "MENU");
        });
        buttonPanel.add(btnBackToMenu);
        outer.add(buttonPanel, BorderLayout.SOUTH);

        return outer;
    }

    // =========================================================
    // RESERVATION FORM PANEL
    // =========================================================
    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_WHITE);

        JLabel header = new JLabel("Nueva Reserva");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setForeground(ACCENT_RED);
        header.setBorder(new EmptyBorder(0, 0, 15, 0));
        panel.add(header, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(BG_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(25, 25, 25, 25)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 10, 8, 10);

        // Initialize time slots
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

        // Row 1: Start Time | End Time
        gbc.gridx = 0;
        gbc.gridy = 1;
        JLabel lblStart = new JLabel("🕒  Hora Inicio");
        lblStart.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblStart.setForeground(TEXT_DARK);
        card.add(lblStart, gbc);

        gbc.gridx = 1;
        cmbStartTime = new JComboBox<>(timeSlots);
        styleCombo(cmbStartTime);
        cmbStartTime.addActionListener(e -> {
            updateEndTimeOptions();
            updateDuration();
        });
        card.add(cmbStartTime, gbc);

        gbc.gridx = 2;
        JLabel lblEnd = new JLabel("🕒  Hora de Fin");
        lblEnd.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblEnd.setForeground(TEXT_DARK);
        card.add(lblEnd, gbc);

        gbc.gridx = 3;
        cmbEndTime = new JComboBox<>(timeSlots);
        styleCombo(cmbEndTime);
        cmbEndTime.addActionListener(e -> updateDuration());
        card.add(cmbEndTime, gbc);

        // Duration display
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        lblDuration = new JLabel("Duración: --");
        lblDuration.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDuration.setForeground(SUCCESS_GREEN);
        card.add(lblDuration, gbc);

        // Row 3: Attendees | Equipment
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        JLabel lblAttendees = new JLabel("👥  N° Asistentes");
        lblAttendees.setFont(new Font("Segoe UI Symbol", Font.BOLD, 13));
        lblAttendees.setForeground(TEXT_DARK);
        card.add(lblAttendees, gbc);

        gbc.gridx = 1;
        txtAttendees = buildTextField("10");
        card.add(txtAttendees, gbc);

        gbc.gridx = 2;
        JLabel lblEquipment = new JLabel("Equipamiento");
        lblEquipment.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblEquipment.setForeground(TEXT_DARK);
        card.add(lblEquipment, gbc);

        gbc.gridx = 3;
        cmbEquipment = new JComboBox<>(new String[]{
            "NINGUNO", "PROYECTOR", "MICROFONO", "SONIDO", "COMPLETO"
        });
        styleCombo(cmbEquipment);
        card.add(cmbEquipment, gbc);

        // Row 4: Action buttons
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(20, 10, 10, 10);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 0));
        buttonPanel.setBackground(BG_LIGHT);

        btnReserve = buildActionButton("💾  Reservar", ACCENT_RED, true);
        btnConfirm = buildActionButton("✓  Confirmar", SUCCESS_GREEN, true);
        btnCancel = buildActionButton("⊗  Cancelar", ACCENT_RED, false);

        btnReserve.addActionListener(e -> reserve());
        btnConfirm.addActionListener(e -> confirmReservation());
        btnCancel.addActionListener(e -> cancelReservation());

        buttonPanel.add(btnReserve);
        buttonPanel.add(btnConfirm);
        buttonPanel.add(btnCancel);

        gbc.gridwidth = 4;
        card.add(buttonPanel, gbc);

        panel.add(card, BorderLayout.CENTER);
        setFormEnabled(false);
        return panel;
    }

    // =========================================================
    // TABLE + MESSAGES PANEL
    // =========================================================
    private JPanel buildTableAndMessagesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_WHITE);

        // ── SECCIÓN TABLA ──────────────────────────────────────
        JPanel tableWrapper = new JPanel(new BorderLayout(0, 6));
        tableWrapper.setBackground(BG_WHITE);
        tableWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tableLabel = new JLabel("▸  Mis Reservas");
        tableLabel.setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
        tableLabel.setForeground(ACCENT_RED);
        tableWrapper.add(tableLabel, BorderLayout.NORTH);

        String[] columns = {"ID", "Fecha", "Horario", "Estado", "TTL"};
        reservationsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        reservationsTable = new JTable(reservationsModel) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_WHITE : BG_LIGHT);
                c.setForeground(TEXT_DARK);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                Object status = reservationsModel.getValueAt(row, 3);
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

        reservationsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reservationsTable.setRowHeight(30);
        reservationsTable.setBackground(BG_WHITE);
        reservationsTable.setForeground(TEXT_DARK);
        reservationsTable.setGridColor(BORDER_COLOR);
        reservationsTable.setShowVerticalLines(false);
        reservationsTable.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader tableHeader = reservationsTable.getTableHeader();
        tableHeader.setBackground(ACCENT_RED);
        tableHeader.setForeground(Color.WHITE);
        tableHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tableHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_RED));
        tableHeader.setPreferredSize(new Dimension(0, 34));
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
                        BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 30, 45)),
                        new EmptyBorder(0, 8, 0, 8)));
                lbl.setOpaque(true);
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                return lbl;
            }
        });

        JScrollPane tableScroll = new JScrollPane(reservationsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        tableScroll.getViewport().setBackground(BG_WHITE);
        tableScroll.setPreferredSize(new Dimension(600, 160));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableWrapper.add(tableScroll, BorderLayout.CENTER);

        // ── SECCIÓN BITÁCORA ───────────────────────────────────
        JPanel messagesWrapper = new JPanel(new BorderLayout(0, 6));
        messagesWrapper.setBackground(BG_WHITE);
        messagesWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesWrapper.setBorder(new EmptyBorder(10, 0, 0, 0));

        JLabel messagesLabel = new JLabel("▸  Mensajes del Sistema");
        messagesLabel.setFont(new Font("Segoe UI Symbol", Font.BOLD, 14));
        messagesLabel.setForeground(ACCENT_RED);
        messagesWrapper.add(messagesLabel, BorderLayout.NORTH);

        txtMessages = new JTextArea();
        txtMessages.setEditable(false);
        txtMessages.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtMessages.setBackground(BG_WHITE);
        txtMessages.setForeground(TEXT_DARK);
        txtMessages.setLineWrap(true);
        txtMessages.setWrapStyleWord(true);
        txtMessages.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane messagesScroll = new JScrollPane(txtMessages);
        messagesScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        messagesScroll.setPreferredSize(new Dimension(600, 130));
        messagesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        messagesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        messagesWrapper.add(messagesScroll, BorderLayout.CENTER);

        // ── ENSAMBLAR ──────────────────────────────────────────
        panel.add(tableWrapper);
        panel.add(Box.createVerticalStrut(8));
        panel.add(messagesWrapper);

        return panel;
    }

    // =========================================================
// CALENDAR DATE PICKER
// =========================================================
    private JPanel buildCalendarDatePicker() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG_WHITE);
        container.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        container.setPreferredSize(new Dimension(0, 45));

        txtDate = new JTextField();
        txtDate.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtDate.setBackground(BG_WHITE);
        txtDate.setForeground(TEXT_DARK);
        txtDate.setEditable(false);
        txtDate.setBorder(new EmptyBorder(10, 15, 10, 10));
        txtDate.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        refreshComboRenderers();

        // ── Mismo patrón que buildActionButton ──
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
        calBtn.setContentAreaFilled(false);   // paintComponent se encarga
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

        container.add(txtDate, BorderLayout.CENTER);
        container.add(calBtn, BorderLayout.EAST);
        return container;
    }

    private void showCalendarPopup(Component parent) {
        JDialog popup = new JDialog((Frame) SwingUtilities.getWindowAncestor(parent), false);
        popup.setUndecorated(true);

        final LocalDate[] view = {LocalDate.now()};

        JPanel calPanel = new JPanel(new BorderLayout(0, 8));
        calPanel.setBackground(BG_WHITE);
        calPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(12, 12, 12, 12)));

        // — Navegación mes/año —
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

        // — Grid días —
        JPanel gridPanel = new JPanel(new GridLayout(0, 7, 4, 4));
        gridPanel.setBackground(BG_WHITE);

        Runnable buildGrid = () -> {
            gridPanel.removeAll();
            monthLabel.setText(view[0].format(DateTimeFormatter.ofPattern("MMMM yyyy")));

            for (String d : new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) {
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
                            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                            g2.dispose();
                        }
                        super.paintComponent(g);
                    }
                };

                dayBtn.setFont(new Font("Segoe UI", isToday ? Font.BOLD : Font.PLAIN, 12));
                dayBtn.setFocusPainted(false);
                dayBtn.setCursor(isPast ? Cursor.getDefaultCursor() : new Cursor(Cursor.HAND_CURSOR));

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
                    txtDate.setText(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
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
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
            }

            public void windowLostFocus(java.awt.event.WindowEvent e) {
                popup.dispose();
            }
        });
    }

    // =========================================================
    // TIME SLOTS GENERATOR
    // =========================================================
    private String[] generateTimeSlots() {
        List<String> slots = new ArrayList<>();
        LocalTime time = LocalTime.of(8, 0); // 8:00 AM
        LocalTime end = LocalTime.of(22, 0); // 10:00 PM

        while (!time.isAfter(end)) {
            String ampm = time.getHour() >= 12 ? "PM" : "AM";
            int displayHour = time.getHour() > 12 ? time.getHour() - 12
                    : time.getHour() == 0 ? 12 : time.getHour();
            slots.add(String.format("%d:%02d %s", displayHour, time.getMinute(), ampm));
            time = time.plusMinutes(30);
        }

        return slots.toArray(new String[0]);
    }

    // =========================================================
    // UPDATE END TIME OPTIONS (only later than start time)
    // =========================================================
    private void updateEndTimeOptions() {
        String selectedStart = (String) cmbStartTime.getSelectedItem();
        if (selectedStart == null) {
            return;
        }

        String currentEnd = (String) cmbEndTime.getSelectedItem();
        cmbEndTime.removeAllItems();

        String[] allSlots = generateTimeSlots();
        boolean startFound = false;

        for (String slot : allSlots) {
            if (!startFound && slot.equals(selectedStart)) {
                startFound = true;
                continue; // Skip the start time itself
            }
            if (startFound) {
                cmbEndTime.addItem(slot);
            }
        }

        // Try to restore previous selection
        if (currentEnd != null) {
            for (int i = 0; i < cmbEndTime.getItemCount(); i++) {
                if (cmbEndTime.getItemAt(i).equals(currentEnd)) {
                    cmbEndTime.setSelectedIndex(i);
                    return;
                }
            }
        }
    }

    // =========================================================
    // UPDATE DURATION CALCULATION
    // =========================================================
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

            String durationText = String.format("Duración: %d h %d min", hours, minutes);
            lblDuration.setText(durationText);
            lblDuration.setForeground(SUCCESS_GREEN);

        } catch (Exception e) {
            lblDuration.setText("Duración: --");
        }
    }

    private LocalTime parseTimeString(String timeStr) {
        // Parse strings like "8:00 AM" or "2:30 PM"
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

    // =========================================================
    // TSE INTEGRATION (unchanged)
    // =========================================================
    private void queryTSE(String id) {
        String cleanId = id.replaceAll("[^0-9]", "");
        if (cleanId.isEmpty()) {
            return;
        }

        lastCheckedId = cleanId;
        lblVerification.setText("⟳  Verificando en TSE...");
        lblVerification.setForeground(WARNING_AMBER);
        txtName.setText("");
        idVerified = false;
        btnConnect.setEnabled(false);

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
                conn.setRequestProperty("User-Agent", "VentanaCliente-ReservasSala/1.0");

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
                            txtName.setText(name);
                            lblVerification.setText("✔  Cédula válida - TSE");
                            lblVerification.setForeground(SUCCESS_GREEN);
                            idVerified = true;
                            btnConnect.setEnabled(true);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            txtName.setText("");
                            lblVerification.setText("✖  Cédula no registrada");
                            lblVerification.setForeground(ACCENT_RED);
                            idVerified = false;
                            btnConnect.setEnabled(false);
                        });
                    }

                } else if (status == 404) {
                    SwingUtilities.invokeLater(() -> {
                        txtName.setText("");
                        lblVerification.setText("✖  Cédula no encontrada");
                        lblVerification.setForeground(ACCENT_RED);
                        idVerified = false;
                        btnConnect.setEnabled(false);
                    });
                } else if (status == 429) {
                    SwingUtilities.invokeLater(() -> {
                        lblVerification.setText("⚠ Límite de consultas - reintente");
                        lblVerification.setForeground(WARNING_AMBER);
                        btnConnect.setEnabled(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        lblVerification.setText("⚠  Error TSE (HTTP " + status + ")");
                        lblVerification.setForeground(WARNING_AMBER);
                        btnConnect.setEnabled(true);
                    });
                }

            } catch (SocketTimeoutException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerification.setText("⚠  TSE sin respuesta");
                    lblVerification.setForeground(WARNING_AMBER);
                    btnConnect.setEnabled(true);
                });
            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerification.setText("⚠  Sin internet");
                    lblVerification.setForeground(WARNING_AMBER);
                    btnConnect.setEnabled(true);
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerification.setText("⚠  Error de red");
                    lblVerification.setForeground(WARNING_AMBER);
                    btnConnect.setEnabled(true);
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

    private String parseNameFromJson(String json, String id) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            if (json.contains("\"results\":[]") || json.contains("\"results\": []")) {
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

    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }

        int start = idx + pattern.length();
        while (start < json.length() && (json.charAt(start) == ':' || json.charAt(start) == ' ')) {
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

    // =========================================================
    // SERVER CONNECTION
    // =========================================================
    private void connect() {
        String name = txtName.getText().trim();
        String id = txtDNI.getText().trim();
        String role = (String) cmbRole.getSelectedItem();

        if (id.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Ingrese su número de cédula.",
                    "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!idVerified && name.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "La cédula no fue verificada contra el TSE.\n¿Desea continuar de todas formas?",
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
            txtName.setText(name);
            idVerified = true;
        }

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo obtener el nombre desde el TSE.\nVerifique su cédula.",
                    "Nombre no disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Socket tempSocket = null;
        try {
            tempSocket = new Socket();
            tempSocket.connect(new InetSocketAddress("localhost", 8000), 3000);

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
                        "Su cédula no está autorizada para el rol: " + role + "\nContacte al administrador.",
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
            running = true;

            // Update connection status for menu
            lblConnectionStatus.setText("");
            lblWelcome.setText("Bienvenido(a), " + name);
            lblWelcome.setForeground(SUCCESS_GREEN);

            btnConnect.setEnabled(false);
            btnLogout.setVisible(true);
            txtDNI.setEditable(false);
            cmbRole.setEnabled(false);
            setFormEnabled(true);

            logMessage("✅ Conectado como: " + name + " (DNI: " + id + ")");
            logMessage("Servidor listo. Puede realizar su reserva.");
            setTitle("UNIVERSIDAD NACIONAL - " + name);

            // Show menu panel after successful login
            CardLayout cl = (CardLayout) mainPanel.getLayout();
            cl.show(mainPanel, "MENU");

            Thread listenerThread = new Thread(this::listenToServer);
            listenerThread.setDaemon(true);
            listenerThread.setName("Server-Listener");
            listenerThread.start();

        } catch (ConnectException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "No hay servidor activo en el puerto 8000.\n¿Está corriendo el servidor?",
                    "Sin conexión", JOptionPane.ERROR_MESSAGE);
        } catch (SocketTimeoutException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "El servidor no respondió a tiempo.\nVerifique que esté operativo.",
                    "Tiempo de espera agotado", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            closeSilently(tempSocket);
            JOptionPane.showMessageDialog(this,
                    "Error de conexión: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logout() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }

        isConnected = false;
        idVerified = false;
        lastReservationId = null;

        txtDNI.setText("");
        txtDNI.setEditable(true);
        lastCheckedId = "";
        txtName.setText("");
        cmbRole.setSelectedIndex(0);
        cmbRole.setEnabled(true);

        lblVerification.setText("○  Ingrese su cédula para verificar");
        lblVerification.setForeground(TEXT_MUTED);

        // LIMPIAR MENSAJES
        lblWelcome.setText("Bienvenido(a)");
        lblConnectionStatus.setText("");

        btnConnect.setEnabled(true);
        btnLogout.setVisible(false);

        reservationsModel.setRowCount(0);
        txtMessages.setText("");

        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");

        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "LOGIN");
    }

    private void closeSilently(Socket s) {
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    // =========================================================
    // LISTEN TO SERVER RESPONSES
    // =========================================================
    private void listenToServer() {
        try {
            while (running && !socket.isClosed()) {
                String msg = inputStream.readUTF();
                SwingUtilities.invokeLater(() -> handleServerResponse(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(this::handleDisconnection);
        }
    }

    // =========================================================
    // DISCONNECTION HANDLING
    // =========================================================
    private void handleDisconnection() {
        if (!isConnected) {
            return;
        }
        logMessage("⚠ Conexión con el servidor perdida.");
        disconnect();
    }

    private void disconnect() {
        isConnected = false;

        for (int i = reservationsModel.getRowCount() - 1; i >= 0; i--) {
            if ("ENVIANDO...".equals(reservationsModel.getValueAt(i, 3))) {
                reservationsModel.removeRow(i);
            }
        }

        closeSilently(socket);
        setFormEnabled(false);

        btnConnect.setEnabled(true);
        txtDNI.setEditable(true);
        cmbRole.setEnabled(true);
        btnLogout.setVisible(false);
        setTitle("UNIVERSIDAD NACIONAL - Sistema de Reservas de Salas");

        txtName.setText("");
        txtDNI.setText("");
        lblVerification.setText("○  Ingrese su cédula para verificar");
        lblVerification.setForeground(TEXT_MUTED);
        idVerified = false;
        lastCheckedId = "";

        CardLayout cl = (CardLayout) mainPanel.getLayout();
        cl.show(mainPanel, "LOGIN");
    }

    // =========================================================
    // HANDLE SERVER RESPONSES
    // =========================================================
    private void handleServerResponse(String msg) {
        logMessage(" - " + msg);
        String[] parts = msg.split("\\|");

        switch (parts[0]) {

            case "HISTORIAL":
                reservationsModel.setRowCount(0);
                for (int i = 1; i < parts.length; i++) {
                    String[] fields = parts[i].split(",", 5);
                    if (fields.length < 5) {
                        continue;
                    }
                    String id = fields[0];
                    String date = fields[1];
                    String timeRng = fields[2] + " - " + fields[3];
                    String status = fields[4];
                    reservationsModel.addRow(new Object[]{id, date, timeRng, status, "—"});
                }
                if (reservationsModel.getRowCount() > 0) {
                    logMessage("Historial restaurado: " + reservationsModel.getRowCount() + " reserva(s).");
                }
                refreshComboRenderers();
                break;

            case "OK":
                if (parts.length >= 3 && "TEMPORAL".equals(parts[1])) {
                    String id = parts[2];
                    String ttl = parts.length >= 4 ? parts[3] : "?";
                    lastReservationId = id;
                    for (int i = 0; i < reservationsModel.getRowCount(); i++) {
                        if ("ENVIANDO...".equals(reservationsModel.getValueAt(i, 3))) {
                            reservationsModel.setValueAt(id, i, 0);
                            reservationsModel.setValueAt("TEMPORAL", i, 3);
                            ttl = ttl.replace("TTL:", "").trim();
                            reservationsModel.setValueAt(Long.valueOf(ttl), i, 4);
                            clearReservationForm();
                            refreshComboRenderers();
                            break;
                        }
                    }
                } else if (parts.length >= 2 && "CONFIRMADO".equals(parts[1])) {
                    String id = parts.length >= 3 ? parts[2] : lastReservationId;
                    updateTableStatus(id, "CONFIRMADA");
                    clearTTL(id);
                    refreshComboRenderers();
                } else if (parts.length >= 2 && "CANCELADO".equals(parts[1])) {
                    String id = parts.length >= 3 ? parts[2] : lastReservationId;
                    updateTableStatus(id, "CANCELADA");
                    clearTTL(id);
                }
                break;

            case "ERROR":
                logMessage("❌ Error del servidor: " + (parts.length > 1 ? parts[1] : "desconocido"));
                if (parts.length > 1 && "SERVIDOR_DETENIDO".equals(parts[1])) {
                    handleDisconnection();
                    return;
                }
                for (int i = reservationsModel.getRowCount() - 1; i >= 0; i--) {
                    if ("ENVIANDO...".equals(reservationsModel.getValueAt(i, 3))) {
                        reservationsModel.removeRow(i);
                        break;
                    }
                }
                break;

            case "EXPIRACION":
                if (parts.length >= 2) {
                    String expiredId = parts[1];
                    updateTableStatus(expiredId, "EXPIRADA");
                    for (int i = 0; i < reservationsModel.getRowCount(); i++) {
                        if (expiredId.equals(reservationsModel.getValueAt(i, 0))) {
                            reservationsModel.setValueAt("—", i, 4);
                            break;
                        }
                    }
                    logMessage("Reserva " + expiredId + " expiró.");
                    JOptionPane.showMessageDialog(this,
                            "Tu reserva " + expiredId + " expiró por TTL.\nPuede realizar una nueva reserva.",
                            "Reserva expirada", JOptionPane.WARNING_MESSAGE);
                }
                break;

            default:
                break;
        }
    }

    private void clearReservationForm() {
        txtAttendees.setText("");
        cmbStartTime.setSelectedIndex(0);
        cmbEndTime.setSelectedIndex(0);
        cmbEquipment.setSelectedIndex(0);
        lblDuration.setText("Duración: --");
        refreshComboRenderers();
    }

    private void clearTTL(String id) {
        for (int i = 0; i < reservationsModel.getRowCount(); i++) {
            if (id != null && id.equals(reservationsModel.getValueAt(i, 0))) {
                reservationsModel.setValueAt("—", i, 4);
                reservationsTable.repaint();
                break;
            }
        }
    }

    private void updateTableStatus(String id, String newStatus) {
        for (int i = 0; i < reservationsModel.getRowCount(); i++) {
            if (id != null && id.equals(reservationsModel.getValueAt(i, 0))) {
                reservationsModel.setValueAt(newStatus, i, 3);
                reservationsTable.repaint();
                return;
            }
        }
    }

    // =========================================================
    // RESERVATION ACTIONS
    // =========================================================
    private void reserve() {
        if (!isConnected) {
            return;
        }

        String date = txtDate.getText().trim();
        if (date.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seleccione una fecha.", "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String startTime = convertToServerFormat((String) cmbStartTime.getSelectedItem());
        String endTime = convertToServerFormat((String) cmbEndTime.getSelectedItem());
        String attendees = txtAttendees.getText().trim();
        String equipment = (String) cmbEquipment.getSelectedItem();
        String role = (String) cmbRole.getSelectedItem();

        if (attendees.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Complete todos los campos antes de reservar.",
                    "Campos vacíos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        reservationsModel.addRow(new Object[]{"...", date,
            startTime + " - " + endTime, "ENVIANDO...", "..."});
        sendMessage("RESERVAR|" + date + "|" + startTime + "|" + endTime + "|"
                + attendees + "|" + equipment + "|" + role);
    }

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
        } catch (Exception e) {
            return timeStr;
        }
    }

    private void updateTTL() {
        for (int i = 0; i < reservationsModel.getRowCount(); i++) {
            Object statusObj = reservationsModel.getValueAt(i, 3);
            Object ttlObj = reservationsModel.getValueAt(i, 4);
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
                reservationsModel.setValueAt(ttl > 0 ? ttl - 1 : 0, i, 4);
            } catch (Exception e) {
                System.out.println("Invalid TTL: " + ttlObj);
            }
        }
    }

    private void confirmReservation() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) {
            logMessage("⚠ Seleccione una reserva para confirmar.");
            return;
        }
        String id = (String) reservationsModel.getValueAt(row, 0);
        if ("...".equals(id) || "ENVIANDO...".equals(reservationsModel.getValueAt(row, 3))) {
            logMessage("⚠ Espere la respuesta del servidor.");
            return;
        }
        sendMessage("CONFIRMAR|" + id);
    }

    private void cancelReservation() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) {
            logMessage("⚠ Seleccione una reserva para cancelar.");
            return;
        }
        String id = (String) reservationsModel.getValueAt(row, 0);
        if ("...".equals(id)) {
            logMessage("⚠ Espere la respuesta del servidor.");
            return;
        }
        sendMessage("CANCELAR|" + id);
    }

    // =========================================================
    // SERVER COMMUNICATION
    // =========================================================
    private void sendMessage(String message) {
        try {
            outputStream.writeUTF(message);
            outputStream.flush();
            logMessage(" - " + message);
        } catch (IOException e) {
            logMessage("❌ Error al enviar: " + e.getMessage());
        }
    }

    // =========================================================
    // UI UTILITIES
    // =========================================================
    private void logMessage(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtMessages.append("[" + time + "]  " + msg + "\n");
        txtMessages.setCaretPosition(txtMessages.getDocument().getLength());
    }

    private void setFormEnabled(boolean enabled) {
        txtDate.setEnabled(enabled);
        cmbStartTime.setEnabled(enabled);
        cmbEndTime.setEnabled(enabled);
        txtAttendees.setEnabled(enabled);
        cmbEquipment.setEnabled(enabled);
        btnReserve.setEnabled(enabled);
        btnConfirm.setEnabled(enabled);
        btnCancel.setEnabled(enabled);
    }

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

    private JSpinner buildDatePicker() {
        SpinnerDateModel model = new SpinnerDateModel();

        Date today = new Date();
        model.setValue(today);
        model.setStart(today);

        JSpinner dateSpinner = new JSpinner(model);

        JSpinner.DateEditor editor
                = new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd");

        dateSpinner.setEditor(editor);

        JFormattedTextField tf = editor.getTextField();
        tf.setEditable(false);
        tf.setBackground(BG_WHITE);
        tf.setForeground(TEXT_DARK);
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        dateSpinner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(5, 10, 5, 10)
        ));

        dateSpinner.setPreferredSize(new Dimension(0, 45));

        return dateSpinner;
    }

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
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font("Segoe UI", Font.BOLD, 14));
                setBackground(isSelected ? ACCENT_RED : BG_WHITE);
                setForeground(isSelected ? Color.WHITE : TEXT_DARK);
                setBorder(new EmptyBorder(5, 10, 5, 10));
                return this;
            }
        });
    }

    private JButton buildActionButton(String text, Color color, boolean isPrimary) {
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
        btn.setContentAreaFilled(false);   // dejamos que paintComponent lo haga
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
                : new Color(color.getRed(), color.getGreen(), color.getBlue(), 30);

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

    // =========================================================
    // MAIN
    // =========================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new ClientView().setVisible(true);
        });
    }
}