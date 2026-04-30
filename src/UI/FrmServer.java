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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FrmServer extends JFrame {

    // === PALETA ===    
    private static final Color UNA_ROJO = new Color(0xCD, 0x17, 0x19);
    private static final Color UNA_AZUL = new Color(0x03, 0x49, 0x91);
    private static final Color UNA_GRIS = new Color(0xA7, 0xA7, 0xA9);
    private static final Color UNA_BLANCO = new Color(0xFF, 0xFF, 0xFF);
    private static final Color UNA_NEGRO = new Color(0x1A, 0x1A, 0x1A);

    private static final Color BG_PRINCIPAL = new Color(0xF5, 0xF5, 0xF5);
    private static final Color BG_SIDEBAR = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_HEADER = new Color(0xCD, 0x17, 0x19);
    private static final Color BG_CARD = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_TABLA_PAR = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_TABLA_IMP = new Color(0xF0, 0xF4, 0xF9);
    private static final Color BG_BITACORA = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BG_CAMPO = new Color(0xF8, 0xF8, 0xF8);

    // Texto
    private static final Color TEXT_HEADER = new Color(0xFF, 0xFF, 0xFF);
    private static final Color TEXT_OSCURO = new Color(0x1A, 0x1A, 0x1A);
    private static final Color TEXT_MEDIO = new Color(0x55, 0x55, 0x66);
    private static final Color TEXT_MUTED = new Color(0x88, 0x88, 0x99);
    private static final Color TEXT_BITA = new Color(0x8B, 0x00, 0x00);

    // Bordes
    private static final Color BORDE_CARD = new Color(0xE0, 0xE4, 0xEA);
    private static final Color BORDE_TABLA = new Color(0xD0, 0xD8, 0xE8);

    // Acentos funcionales (derivados de la paleta UNA)
    private static final Color COLOR_ACTIVO = new Color(0x03, 0x49, 0x91);
    private static final Color COLOR_INACTIV = new Color(0xCD, 0x17, 0x19);
    private static final Color COLOR_CONFIRM = new Color(0x22, 0x8B, 0x22);
    private static final Color COLOR_TEMP = new Color(0xD4, 0x7B, 0x00);
    private static final Color COLOR_AMBAR = new Color(0xD4, 0x7B, 0x00);

    // === ESTADO ===
    private boolean servidorActivo = false;
    private Thread hiloServidor;
    private Timer timerActualizacion;
    private java.net.ServerSocket serverSocketActivo;

    // === LABELS ===
    private JLabel lblEstadoValor;
    private JLabel lblCapacidadValor;
    private JLabel lblReservasValor;
    private JLabel lblEquipoValor;
    private JLabel lblMicrofonoValor;
    private JLabel lblSonidoValor;
    private JLabel lblCompletoValor;

    // === TABLA ===
    private JTable tablaCalendario;
    private DefaultTableModel modeloTabla;

    // === BOTONES ===
    private JButton btnIniciar;
    private JButton btnDetener;
    private JButton btnBitacora;
    private JButton btnEditarReserva;
    private JButton btnCancelarReserva;

    // === BITÁCORA ===
    private JTextArea txtBitacora;

    public FrmServer() {
        setTitle("Universidad Nacional — Panel de Administración");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1040, 740);
        setMinimumSize(new Dimension(900, 620));
        setLocationRelativeTo(null);
        setBackground(BG_PRINCIPAL);
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

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_PRINCIPAL);
        root.add(crearHeader(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 0));
        centro.setBackground(BG_PRINCIPAL);
        centro.add(crearSidebar(), BorderLayout.WEST);
        centro.add(crearCuerpo(), BorderLayout.CENTER);

        root.add(centro, BorderLayout.CENTER);
        add(root);
    }

    // --- HEADER SUPERIOR CON LOGO UNA ------------
    private JPanel crearHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Fondo rojo UNA
                g2.setColor(BG_HEADER);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Franja gris sutil inferior
                g2.setColor(new Color(0, 0, 0, 30));
                g2.fillRect(0, getHeight() - 3,
                        getWidth(), 3);
                // Motivo triangular decorativo (motivo UNA)
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

        // Título central
        JPanel titPanel = new JPanel(
                new FlowLayout(FlowLayout.CENTER, 0, 0));
        titPanel.setOpaque(false);
        JLabel titulo = new JLabel("PANEL DE ADMINISTRACIÓN");
        titulo.setFont(new Font("Serif", Font.BOLD, 15));
        titulo.setForeground(new Color(0xFF, 0xFF, 0xFF, 220));
        titulo.setHorizontalAlignment(SwingConstants.CENTER);
        titPanel.add(titulo);
        header.add(titPanel, BorderLayout.CENTER);

        // Reloj / info derecha
        JLabel lblHora = new JLabel("");
        lblHora.setFont(new Font("Monospaced", Font.PLAIN, 11));
        lblHora.setForeground(new Color(0xFF, 0xFF, 0xFF, 160));
        header.add(lblHora, BorderLayout.EAST);

        Timer reloj = new Timer(1000, e -> {
            lblHora.setText(LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
        reloj.start();

        return header;
    }

    private JPanel crearSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(320, 0));
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setBorder(BorderFactory.createMatteBorder(
                0, 0, 0, 1, BORDE_CARD));

        // Subtítulo sidebar
        JPanel subPanel = new JPanel(new BorderLayout(0, 8));
        subPanel.setBackground(BG_SIDEBAR);
        subPanel.setBorder(new EmptyBorder(14, 18, 12, 18));

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setBackground(BG_SIDEBAR);
        JLabel lSub1 = new JLabel("Reservas de Sala");
        lSub1.setFont(new Font("Serif", Font.BOLD, 14));
        lSub1.setForeground(UNA_ROJO);
        JLabel lSub2 = new JLabel("Monitor de Recursos");
        lSub2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lSub2.setForeground(TEXT_MUTED);
        textos.add(lSub1);
        textos.add(lSub2);
        subPanel.add(textos, BorderLayout.CENTER);

        sidebar.add(subPanel, BorderLayout.NORTH);

        // Tarjetas de estado
        JPanel cards = new JPanel(new GridLayout(0, 2, 8, 8));
        cards.setBackground(BG_SIDEBAR);
        cards.setBorder(new EmptyBorder(6, 12, 6, 12));

        lblEstadoValor = new JLabel("INACTIVO");
        lblReservasValor = new JLabel("—");
        lblCapacidadValor = new JLabel("—");
        lblEquipoValor = new JLabel("—");
        lblMicrofonoValor = new JLabel("—");
        lblSonidoValor = new JLabel("—");
        lblCompletoValor = new JLabel("—");

        lblEstadoValor.setForeground(COLOR_INACTIV);

        cards.add(crearCard("Estado", lblEstadoValor, "●"));
        cards.add(crearCard("Capacidad", lblCapacidadValor, "◈"));
        cards.add(crearCard("Reservas", lblReservasValor, "◉"));
        cards.add(crearCard("Proyectores", lblEquipoValor, "▣"));
        cards.add(crearCard("Micrófonos", lblMicrofonoValor, "♪"));
        cards.add(crearCard("Sonido", lblSonidoValor, "◎"));
        cards.add(crearCard("Completo", lblCompletoValor, "✦"));
        sidebar.add(cards, BorderLayout.CENTER);

        // Botones de acción
        JPanel btnPanel = new JPanel(new GridLayout(5, 1, 0, 6));
        btnPanel.setBackground(BG_SIDEBAR);
        btnPanel.setBorder(new EmptyBorder(10, 12, 18, 12));

        btnIniciar = crearBoton(
                "▶  Iniciar Servidor", UNA_AZUL, false);
        btnDetener = crearBoton(
                "■  Detener Servidor", UNA_ROJO, false);
        btnBitacora = crearBoton(
                "↻  Actualizar Vista", UNA_GRIS, true);
        btnEditarReserva = crearBoton(
                "✎  Editar Reserva", COLOR_AMBAR, true);
        btnCancelarReserva = crearBoton(
                "✖  Cancelar Reserva", UNA_ROJO, true);

        btnDetener.setEnabled(false);
        btnEditarReserva.setEnabled(false);
        btnCancelarReserva.setEnabled(false);

        btnIniciar.addActionListener(e -> iniciarServidor());
        btnDetener.addActionListener(e -> detenerServidor());
        btnBitacora.addActionListener(e -> actualizarVista());
        btnEditarReserva.addActionListener(e -> {
            try {
                editarReservaSeleccionada();
            } catch (InterruptedException ex) {
                Logger.getLogger(FrmServer.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        });
        btnCancelarReserva.addActionListener(
                e -> cancelarReservaSeleccionada());

        btnPanel.add(btnIniciar);
        btnPanel.add(btnDetener);
        btnPanel.add(btnBitacora);
        btnPanel.add(btnEditarReserva);
        btnPanel.add(btnCancelarReserva);
        sidebar.add(btnPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    private JPanel crearCard(
            String titulo, JLabel valorLabel, String icon) {
        JPanel card = new JPanel(new BorderLayout(3, 3));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE_CARD, 1),
                new EmptyBorder(8, 10, 8, 10)));

        JLabel lblIco = new JLabel(icon + "  " + titulo);
        lblIco.setFont(new Font("SansSerif", Font.PLAIN, 9));
        lblIco.setForeground(TEXT_MUTED);

        valorLabel.setFont(new Font("Serif", Font.BOLD, 13));
        if (valorLabel.getForeground().equals(
                new Color(0, 0, 0))) {
            valorLabel.setForeground(TEXT_OSCURO);
        }

        card.add(lblIco, BorderLayout.NORTH);
        card.add(valorLabel, BorderLayout.CENTER);
        return card;
    }

    private JButton crearBoton(
            String texto, Color color, boolean esSecundario) {
        JButton btn = new JButton(texto) {
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
                            esSecundario
                                    ? new Color(color.getRed(),
                                            color.getGreen(),
                                            color.getBlue(), 30)
                                    : color.brighter());
                } else {
                    g2.setColor(
                            esSecundario
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
        if (esSecundario) {
            btn.setForeground(color.darker());
        } else {
            btn.setForeground(UNA_BLANCO);
        }
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(color.getRed(), color.getGreen(),
                                color.getBlue(),
                                esSecundario ? 100 : 180), 1),
                new EmptyBorder(7, 10, 7, 10)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel crearCuerpo() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(BG_PRINCIPAL);
        body.setBorder(new EmptyBorder(18, 18, 18, 18));

        // Título sección
        JPanel titPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));
        titPanel.setOpaque(false);
        JLabel header = new JLabel("Calendario de Reservas");
        header.setFont(new Font("Serif", Font.BOLD, 16));
        header.setForeground(TEXT_OSCURO);

        // Línea decorativa roja bajo el título
        JPanel titWrap = new JPanel(new BorderLayout(0, 4));
        titWrap.setOpaque(false);
        titWrap.setBorder(new EmptyBorder(0, 0, 8, 0));
        titWrap.add(header, BorderLayout.NORTH);
        JPanel lineaDec = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(UNA_ROJO);
                g.fillRect(0, 0, 40, 3);
                g.setColor(UNA_GRIS);
                g.fillRect(44, 0, 20, 3);
            }
        };
        lineaDec.setOpaque(false);
        lineaDec.setPreferredSize(new Dimension(0, 6));
        titWrap.add(lineaDec, BorderLayout.SOUTH);

        body.add(titWrap, BorderLayout.NORTH);
        body.add(crearPanelTabla(), BorderLayout.CENTER);
        body.add(crearPanelBitacora(), BorderLayout.SOUTH);
        return body;
    }

    private JScrollPane crearPanelTabla() {
        String[] cols = {
            "ID", "Solicitante", "Fecha",
            "Horario", "Estado", "Asistentes",
            "Equipo", "TTL"
        };
        modeloTabla = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        tablaCalendario = new JTable(modeloTabla) {
            @Override
            public Component prepareRenderer(
                    TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                boolean sel = isRowSelected(row);
                if (sel) {
                    c.setBackground(
                            new Color(0x03, 0x49, 0x91, 38));
                    c.setForeground(UNA_AZUL);
                } else {
                    c.setBackground(row % 2 == 0
                            ? BG_TABLA_PAR : BG_TABLA_IMP);
                    c.setForeground(UNA_NEGRO);
                    // Color por estado 
                    Object est
                            = modeloTabla.getValueAt(row, 4);
                    if ("CONFIRMADO".equals(est)) {
                        c.setForeground(COLOR_CONFIRM);
                    } else if ("CANCELADO".equals(est)) {
                        c.setForeground(UNA_GRIS);
                    } else if ("EXPIRADO".equals(est)) {
                        c.setForeground(COLOR_AMBAR);
                    } else if ("RESERVADO_TEMPORAL".equals(est)) {
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

        tablaCalendario.setFont(
                new Font("SansSerif", Font.PLAIN, 12));
        tablaCalendario.setRowHeight(30);
        tablaCalendario.setBackground(BG_TABLA_PAR);
        tablaCalendario.setForeground(TEXT_OSCURO);
        tablaCalendario.setGridColor(BORDE_TABLA);
        tablaCalendario.setShowVerticalLines(false);
        tablaCalendario.setIntercellSpacing(
                new Dimension(0, 1));
        tablaCalendario.setSelectionBackground(
                new Color(0x03, 0x49, 0x91, 38));
        tablaCalendario.setSelectionForeground(UNA_AZUL);

        JTableHeader th = tablaCalendario.getTableHeader();
        th.setBackground(UNA_ROJO);
        th.setForeground(UNA_BLANCO);
        th.setFont(new Font("SansSerif", Font.BOLD, 11));
        th.setBorder(BorderFactory.createMatteBorder(
                0, 0, 2, 0,
                new Color(0xAA, 0x10, 0x10)));
        th.setPreferredSize(new Dimension(0, 34));

        // ← AGREGAR ESTO:
        th.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                lbl.setBackground(UNA_ROJO);
                lbl.setForeground(UNA_BLANCO);
                lbl.setFont(new Font("SansSerif", Font.BOLD, 11));
                lbl.setHorizontalAlignment(SwingConstants.LEFT);
                lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
                lbl.setOpaque(true);
                return lbl;
            }
        });
        // Al seleccionar una fila, habilitar botones de acción
        tablaCalendario.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting()) {
                        boolean hayFila
                                = tablaCalendario.getSelectedRow() >= 0;
                        btnEditarReserva.setEnabled(
                                hayFila && servidorActivo);
                        btnCancelarReserva.setEnabled(
                                hayFila && servidorActivo);
                    }
                });
        JScrollPane scroll
                = new JScrollPane(tablaCalendario);
        scroll.setBorder(BorderFactory.createLineBorder(
                BORDE_TABLA, 1));
        scroll.getViewport().setBackground(BG_TABLA_PAR);

        // Scrollbar styling
        scroll.getVerticalScrollBar()
                .setBackground(BG_PRINCIPAL);
        return scroll;
    }

    private JPanel crearPanelBitacora() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_PRINCIPAL);
        panel.setPreferredSize(new Dimension(0, 190));

        // Cabecera bitácora con acento rojo
        JPanel cabBita = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 0));
        cabBita.setOpaque(false);
        cabBita.setBorder(new EmptyBorder(0, 0, 4, 0));

        JPanel marcaRoja = new JPanel();
        marcaRoja.setBackground(UNA_ROJO);
        marcaRoja.setPreferredSize(new Dimension(4, 16));

        JLabel lblBita = new JLabel("  Bitácora en tiempo real");
        lblBita.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblBita.setForeground(TEXT_MEDIO);
        cabBita.add(marcaRoja);
        cabBita.add(lblBita);

        panel.add(cabBita, BorderLayout.NORTH);

        txtBitacora = new JTextArea();
        txtBitacora.setEditable(false);
        txtBitacora.setFont(new Font("Monospaced", Font.PLAIN, 11));
        txtBitacora.setBackground(BG_BITACORA);
        txtBitacora.setForeground(TEXT_BITA);
        txtBitacora.setCaretColor(TEXT_BITA);
        txtBitacora.setLineWrap(true);
        txtBitacora.setWrapStyleWord(true);
        txtBitacora.setBorder(
                new EmptyBorder(10, 14, 10, 14));

        JScrollPane scroll = new JScrollPane(txtBitacora);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(0xCD, 0x17, 0x19, 80), 1),
                BorderFactory.createEmptyBorder()));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    // ── ACCIONES ─────────────────────────────────────────────
    private void iniciarServidor() {
        servidorActivo = true;
        lblEstadoValor.setText("ACTIVO");
        lblEstadoValor.setForeground(COLOR_ACTIVO);
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(true);
        log("Servidor INICIADO en puerto 8000.");

        hiloServidor = new Thread(() -> {
            RoleValidator.load();

            List<Reservation> restauradas
                    = ReservationPersistence.load();
            for (Reservation r : restauradas) {
                ServerApp.calendar
                        .loadRestoredReservation(r);
            }
            SwingUtilities.invokeLater(() -> {
                if (!restauradas.isEmpty()) {
                    log("✔ " + restauradas.size()
                            + " reserva(s) restauradas "
                            + "desde disco.");
                } else {
                    log("No hay reservas previas "
                            + "que restaurar.");
                }
            });

            try {
                serverSocketActivo
                        = new java.net.ServerSocket(8000);

                ReservationTTLThread hiloTTL
                        = new ReservationTTLThread(
                                ServerApp.calendar,
                                ServerApp.resources,
                                ServerApp.ttlQueue,
                                ServerApp.log);
                hiloTTL.setDaemon(true);
                hiloTTL.start();

                System.out.println(
                        "[SERVIDOR] Puerto 8000 abierto, "
                        + "esperando clientes...");

                while (!Thread.currentThread()
                        .isInterrupted()
                        && !serverSocketActivo.isClosed()) {

                    java.net.Socket clienteSocket
                            = serverSocketActivo.accept();
                    java.io.DataInputStream entrada
                            = new java.io.DataInputStream(
                                    new java.io.BufferedInputStream(
                                            clienteSocket
                                                    .getInputStream()));
                    String datosCliente
                            = entrada.readUTF();
                    System.out.println(
                            "[SERVIDOR] Cliente: "
                            + datosCliente);

                    ClientHandler hilo = new ClientHandler(
                            clienteSocket, datosCliente,
                            ServerApp.calendar,
                            ServerApp.resources,
                            ServerApp.ttlQueue,
                            ServerApp.log);
                    ServerApp.connectedClients.add(hilo);
                    hilo.start();
                }
            } catch (java.io.IOException e) {
                if (servidorActivo) {
                    log("[ERROR] Servidor: "
                            + e.getMessage());
                }
            }
        });
        hiloServidor.setDaemon(true);
        hiloServidor.start();

        timerActualizacion
                = new Timer(2000, e -> actualizarVista());
        timerActualizacion.start();
        actualizarVista();
    }

    private void detenerServidor() {
        servidorActivo = false;

        if (timerActualizacion != null) {
            timerActualizacion.stop();
        }

        ReservationPersistence.save(
                ServerApp.calendar);
        log("Reservas confirmadas guardadas en disco.");

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler hilo
                    : ServerApp.connectedClients) {
                hilo.send("ERROR|SERVIDOR_DETENIDO");
                hilo.close();
            }
            ServerApp.connectedClients.clear();
        }

        try {
            if (serverSocketActivo != null
                    && !serverSocketActivo.isClosed()) {
                serverSocketActivo.close();
            }
        } catch (java.io.IOException ignored) {
        }

        serverSocketActivo = null;

        if (hiloServidor != null) {
            hiloServidor.interrupt();
        }

        lblEstadoValor.setText("INACTIVO");
        lblEstadoValor.setForeground(COLOR_INACTIV);
        lblReservasValor.setText("—");
        lblEquipoValor.setText("—");

        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(false);
        btnEditarReserva.setEnabled(false);
        btnCancelarReserva.setEnabled(false);

        log("Servidor DETENIDO.");
    }

    private void actualizarVista() {
        if (!servidorActivo) {
            return;
        }

        lblReservasValor.setText(
                ServerApp.calendar.getTotalReservations()
                + " activas");
        lblCapacidadValor.setText(
                String.valueOf(
                        ServerApp.manager.getAvailableCapacity()));
        int proy
                = ServerApp.manager.getAvailableProjectors();
        int mic
                = ServerApp.manager.getAvailableMicrophones();
        int son
                = ServerApp.manager.getAvailableSound();
        lblEquipoValor.setText(String.valueOf(proy));
        lblMicrofonoValor.setText(String.valueOf(mic));
        lblSonidoValor.setText(String.valueOf(son));
        int completo = Math.min(proy, Math.min(mic, son));
        lblCompletoValor.setText(String.valueOf(completo));

        List<String> entradas
                = ServerApp.log.getLast(100);
        txtBitacora.setText("");
        for (String e : entradas) {
            txtBitacora.append(e + "\n");
        }
        txtBitacora.setCaretPosition(
                txtBitacora.getDocument().getLength());

        // Preservar fila seleccionada
        String idSeleccionado = null;
        int filaActual = tablaCalendario.getSelectedRow();
        if (filaActual >= 0) {
            idSeleccionado
                    = (String) modeloTabla.getValueAt(
                            filaActual, 0);
        }

        modeloTabla.setRowCount(0);
        List<Reservation> todasReservas
                = ServerApp.calendar.getAllReservations();
        int filaARestaurar = -1;
        int contador = 0;

        for (Reservation r : todasReservas) {
            modeloTabla.addRow(new Object[]{
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
            if (r.getReservationId().equals(idSeleccionado)) {
                filaARestaurar = contador;
            }
            contador++;
        }

        if (filaARestaurar >= 0) {
            tablaCalendario.setRowSelectionInterval(
                    filaARestaurar, filaARestaurar);
        }
        int filaBtn = tablaCalendario.getSelectedRow();
        boolean hayFila = filaBtn >= 0;
        boolean esEditable = hayFila && servidorActivo
                && !"CANCELADO".equals(modeloTabla.getValueAt(filaBtn, 4));
        btnEditarReserva.setEnabled(esEditable);
        btnCancelarReserva.setEnabled(esEditable);
    }

    // ── EDITAR RESERVA DESDE SERVIDOR ────────────────────────
    private void editarReservaSeleccionada()
            throws InterruptedException {
        int fila = tablaCalendario.getSelectedRow();
        if (fila < 0) {
            return;
        }

        String idReserva
                = (String) modeloTabla.getValueAt(fila, 0);
        String idCliente
                = (String) modeloTabla.getValueAt(fila, 1);
        String fechaActual
                = (String) modeloTabla.getValueAt(fila, 2);
        String horario
                = (String) modeloTabla.getValueAt(fila, 3);
        String[] horas = horario.split("-");

        JTextField fFecha = new JTextField(fechaActual);
        JTextField fInicio = new JTextField(
                horas.length > 0 ? horas[0] : "");
        JTextField fFin = new JTextField(
                horas.length > 1 ? horas[1] : "");
        JTextField fAsis = new JTextField(
                modeloTabla.getValueAt(fila, 5).toString());
        JComboBox<String> fEquipo = new JComboBox<>(
                new String[]{
                    "NINGUNO", "PROYECTOR", "MICROFONO",
                    "SONIDO", "COMPLETO"
                });
        fEquipo.setSelectedItem(
                modeloTabla.getValueAt(fila, 6).toString());

        // Estilizar campos del diálogo
        estilizarCampo(fFecha);
        estilizarCampo(fInicio);
        estilizarCampo(fFin);
        estilizarCampo(fAsis);

        JPanel form = new JPanel(
                new GridLayout(0, 2, 8, 10));
        form.setBackground(BG_PRINCIPAL);
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        form.add(crearLabelForm("Fecha (YYYY-MM-DD):"));
        form.add(fFecha);
        form.add(crearLabelForm("Hora inicio (HH:mm):"));
        form.add(fInicio);
        form.add(crearLabelForm("Hora fin (HH:mm):"));
        form.add(fFin);
        form.add(crearLabelForm("Asistentes:"));
        form.add(fAsis);
        form.add(crearLabelForm("Equipo:"));
        form.add(fEquipo);

        int resultado = JOptionPane.showConfirmDialog(
                this, form,
                "Editar reserva " + idReserva
                + "  |  cliente: " + idCliente,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (resultado != JOptionPane.OK_OPTION) {
            return;
        }

        String nuevaFecha = fFecha.getText().trim();
        String nuevaInicio = fInicio.getText().trim();
        String nuevaFin = fFin.getText().trim();
        String nuevaAsis = fAsis.getText().trim();
        String nuevoEquipo
                = (String) fEquipo.getSelectedItem();

        if (nuevaFecha.isEmpty() || nuevaInicio.isEmpty()
                || nuevaFin.isEmpty()
                || nuevaAsis.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Complete todos los campos.",
                    "Campos vacíos",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Reservation original
                = ServerApp.calendar
                        .getReservationById(idReserva);
        if (original == null) {
            JOptionPane.showMessageDialog(this,
                    "Reserva no encontrada.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        ServerApp.calendar.cancelReservation(idReserva);
        ServerApp.ttlQueue.remove(idReserva);

        Reservation nueva;
        try {
            nueva = ServerApp.calendar.reserveTemporarily(
                    idCliente, nuevaFecha,
                    nuevaInicio, nuevaFin,
                    Integer.parseInt(nuevaAsis),
                    Reservation.Equipment.valueOf(nuevoEquipo),
                    original.getPriority());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Asistentes debe ser un número.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            ServerApp.calendar.reserveTemporarily(
                    idCliente, original.getDate(),
                    original.getStartTime(),
                    original.getEndTime(),
                    original.getAttendeeCount(),
                    original.getEquipment(),
                    original.getPriority());
            return;
        }

        if (nueva == null) {
            JOptionPane.showMessageDialog(this,
                    "La nueva franja ya está ocupada.",
                    "Conflicto",
                    JOptionPane.ERROR_MESSAGE);
            Reservation rest
                    = ServerApp.calendar.reserveTemporarily(
                            idCliente, original.getDate(),
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
                nueva.getReservationId());
        ReservationPersistence.save(
                ServerApp.calendar);
        ServerApp.log.log("EDICION-SERVIDOR",
                "Servidor editó reserva " + idReserva
                + " → " + nueva.getReservationId()
                + " | cliente: " + idCliente);

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler hilo
                    : ServerApp.connectedClients) {
                if (hilo.getClientId()
                        .equals(idCliente)) {
                    try {
                        hilo.send("OK|EDITADO|"
                                + nueva.getReservationId());
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }

        log("✎ Reserva " + idReserva + " editada → "
                + nueva.getReservationId()
                + " | cliente: " + idCliente);
        actualizarVista();
    }

    // --- UTILIDADES DE FORMULARIO ----------------
    private void estilizarCampo(JTextField campo) {
        campo.setFont(
                new Font("SansSerif", Font.PLAIN, 12));
        campo.setBackground(BG_CAMPO);
        campo.setForeground(TEXT_OSCURO);
        campo.setCaretColor(UNA_ROJO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        BORDE_CARD, 1),
                new EmptyBorder(4, 8, 4, 8)));
    }

    private JLabel crearLabelForm(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lbl.setForeground(TEXT_MEDIO);
        return lbl;
    }

    // --- CANCELAR RESERVA ------------------------
    private void cancelarReservaSeleccionada() {
        int fila = tablaCalendario.getSelectedRow();
        if (fila < 0) {
            return;
        }

        String idReserva
                = (String) modeloTabla.getValueAt(fila, 0);
        String idCliente
                = (String) modeloTabla.getValueAt(fila, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "¿Cancelar la reserva " + idReserva
                + " del cliente " + idCliente + "?",
                "Confirmar cancelación",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        boolean ok
                = ServerApp.calendar
                        .cancelReservation(idReserva);
        if (!ok) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo cancelar la reserva.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        ServerApp.ttlQueue.remove(idReserva);
        ReservationPersistence.save(
                ServerApp.calendar);
        ServerApp.log.log("CANCELACION-SERVIDOR",
                "Servidor canceló reserva " + idReserva
                + " del cliente " + idCliente);

        synchronized (ServerApp.connectedClients) {
            for (ClientHandler hilo
                    : ServerApp.connectedClients) {
                if (hilo.getClientId()
                        .equals(idCliente)) {
                    try {
                        hilo.send("OK|CANCELADO|"
                                + idReserva);
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }

        log("✖ Reserva " + idReserva
                + " cancelada por el servidor.");
        actualizarVista();
    }

    // --- LOG -------------------------------------
    public void log(String msg) {
        String t = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            txtBitacora.append(
                    "[" + t + "]  " + msg + "\n");
            txtBitacora.setCaretPosition(
                    txtBitacora.getDocument().getLength());
        });
    }
}