package jchat;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FrmServidor extends JFrame {

    // === PALETA ===
    private static final Color BG_DARK      = new Color(13, 17, 28);
    private static final Color BG_PANEL     = new Color(22, 28, 45);
    private static final Color BG_CARD      = new Color(30, 38, 60);
    private static final Color ACCENT_BLUE  = new Color(64, 156, 255);
    private static final Color ACCENT_GREEN = new Color(50, 215, 130);
    private static final Color ACCENT_RED   = new Color(255, 75, 90);
    private static final Color TEXT_PRIMARY = new Color(220, 228, 245);
    private static final Color TEXT_MUTED   = new Color(120, 135, 165);
    private static final Color BORDER_COLOR = new Color(45, 55, 80);

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

    // === BITÁCORA ===
    private JTextArea txtBitacora;

    public FrmServidor() {
        setTitle("FrmServidor — Panel de Administración");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 720);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        setBackground(BG_DARK);
        initComponents();
        log("Sistema iniciado. Presione 'Iniciar Servidor' para comenzar.");
          
    // Guardar al cerrar ventana
    addWindowListener(new java.awt.event.WindowAdapter() {
        @Override
        public void windowClosing(java.awt.event.WindowEvent e) {
            PersistenciaReservas.guardar(Servidor.calendario);
            System.out.println("[CIERRE] Reservas guardadas antes de cerrar.");
        }
    });
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.add(crearSidebar(), BorderLayout.WEST);
        root.add(crearCuerpo(), BorderLayout.CENTER);
        add(root);
    }

    private JPanel crearSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(320, 0));
        sidebar.setBackground(BG_PANEL);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        JPanel logoPanel = new JPanel(new GridLayout(2, 1));
        logoPanel.setBackground(BG_PANEL);
        logoPanel.setBorder(new EmptyBorder(28, 20, 20, 20));
        JLabel ico = new JLabel("⬡  RESERVAS SALA");
        ico.setFont(new Font("Monospaced", Font.BOLD, 14));
        ico.setForeground(ACCENT_BLUE);
        JLabel sub = new JLabel("   Panel Administrador");
        sub.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sub.setForeground(TEXT_MUTED);
        logoPanel.add(ico);
        logoPanel.add(sub);
        sidebar.add(logoPanel, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(4, 1, 0, 10));
        cards.setLayout(new GridLayout(0, 2, 8, 8));
        cards.setBackground(BG_PANEL);
        cards.setBorder(new EmptyBorder(10, 12, 10, 12));
     
        lblEstadoValor    = new JLabel("INACTIVO");
        lblReservasValor  = new JLabel("—");
        lblCapacidadValor = new JLabel("—");
        lblEquipoValor    = new JLabel("—");
        lblMicrofonoValor = new JLabel("—");
        lblSonidoValor    = new JLabel("—");
        lblCompletoValor  = new JLabel("—");
        
        lblEstadoValor.setForeground(ACCENT_RED);
        lblReservasValor.setForeground(TEXT_PRIMARY);
        lblEquipoValor.setForeground(TEXT_PRIMARY);
        lblMicrofonoValor.setForeground(TEXT_PRIMARY);
        lblSonidoValor.setForeground(TEXT_PRIMARY);
        lblCompletoValor.setForeground(TEXT_PRIMARY);

        cards.add(crearCard("Estado del<br>servidor", lblEstadoValor, "●"));
        cards.add(crearCard("Capacidad<br>libre",lblCapacidadValor, "◈"));
        cards.add(crearCard("Reservas<br>Activas", lblReservasValor, "◉"));
        cards.add(crearCard("Proyectores<br>libres", lblEquipoValor, "◆"));
        cards.add(crearCard("Micrófonos<br>libres", lblMicrofonoValor, "🎤"));
        cards.add(crearCard("Sonido<br>libre", lblSonidoValor, "🔊"));
        cards.add(crearCard("Completo<br>libre ", lblCompletoValor, "🧩"));
        sidebar.add(cards, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(3, 1, 0, 8));
        btnPanel.setBackground(BG_PANEL);
        btnPanel.setBorder(new EmptyBorder(10, 12, 24, 12));

        btnIniciar  = crearBotonSide("▶   Iniciar Servidor", ACCENT_GREEN);
        btnDetener  = crearBotonSide("■   Detener Servidor", ACCENT_RED);
        btnBitacora = crearBotonSide("≡   Actualizar Vista",  ACCENT_BLUE);

        btnDetener.setEnabled(false);
        btnIniciar.addActionListener(e -> iniciarServidor());
        btnDetener.addActionListener(e -> detenerServidor());
        btnBitacora.addActionListener(e -> actualizarVista());

        btnPanel.add(btnIniciar);
        btnPanel.add(btnDetener);
        btnPanel.add(btnBitacora);
        sidebar.add(btnPanel, BorderLayout.SOUTH);

        return sidebar;
    }

   private JPanel crearCard(String titulo, JLabel valorLabel, String icon) {
    JPanel card = new JPanel(new BorderLayout(4, 4));
    card.setBackground(BG_CARD);
    card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            new EmptyBorder(10, 12, 10, 12)));
    JLabel lblTit = new JLabel("<html>" + icon + " " + titulo + "</html>");
    lblTit.setFont(new Font("Monospaced", Font.PLAIN, 10));
    lblTit.setForeground(TEXT_MUTED);
    valorLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
    card.add(lblTit, BorderLayout.NORTH);
    card.add(valorLabel, BorderLayout.CENTER);

    return card;
}
    
    private JButton crearBotonSide(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Monospaced", Font.BOLD, 12));
        btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        btn.setForeground(color);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80), 1),
                new EmptyBorder(8, 10, 8, 10)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
            }
        });
        return btn;
    }

    private JPanel crearCuerpo() {
        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setBackground(BG_DARK);
        body.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel header = new JLabel("Calendario de Reservas");
        header.setFont(new Font("Monospaced", Font.BOLD, 17));
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        body.add(header, BorderLayout.NORTH);
        body.add(crearPanelTabla(), BorderLayout.CENTER);
        body.add(crearPanelBitacora(), BorderLayout.SOUTH);
        return body;
    }

    private JScrollPane crearPanelTabla() {
        String[] cols = {"ID", "Solicitante", "Fecha", "Hora", "Estado", "Asistentes", "Equipo", "TTL"};
        modeloTabla = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tablaCalendario = new JTable(modeloTabla) {
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_CARD : BG_PANEL);
                c.setForeground(TEXT_PRIMARY);
                ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                Object estado = modeloTabla.getValueAt(row, 4);
                if ("CONFIRMADO".equals(estado))  c.setForeground(ACCENT_GREEN);
                else if ("CANCELADO".equals(estado))  c.setForeground(ACCENT_RED);
                else if ("RESERVADO_TEMPORAL".equals(estado)) c.setForeground(ACCENT_BLUE);
                if (isRowSelected(row)) c.setBackground(new Color(64, 156, 255, 45));
                return c;
            }
        };

        tablaCalendario.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tablaCalendario.setRowHeight(32);
        tablaCalendario.setBackground(BG_CARD);
        tablaCalendario.setForeground(TEXT_PRIMARY);
        tablaCalendario.setGridColor(BORDER_COLOR);
        tablaCalendario.setShowVerticalLines(false);
        tablaCalendario.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader th = tablaCalendario.getTableHeader();
        th.setBackground(BG_PANEL);
        th.setForeground(ACCENT_BLUE);
        th.setFont(new Font("Monospaced", Font.BOLD, 12));
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_BLUE));
        th.setPreferredSize(new Dimension(0, 36));

        JScrollPane scroll = new JScrollPane(tablaCalendario);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scroll.getViewport().setBackground(BG_CARD);
        return scroll;
    }

    private JPanel crearPanelBitacora() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_DARK);
        panel.setPreferredSize(new Dimension(0, 200));

        JLabel lbl = new JLabel("▸  Bitácora en tiempo real");
        lbl.setFont(new Font("Monospaced", Font.BOLD, 12));
        lbl.setForeground(TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(0, 0, 4, 0));
        panel.add(lbl, BorderLayout.NORTH);

        txtBitacora = new JTextArea();
        txtBitacora.setEditable(false);
        txtBitacora.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtBitacora.setBackground(new Color(8, 12, 20));
        txtBitacora.setForeground(ACCENT_GREEN);
        txtBitacora.setCaretColor(ACCENT_GREEN);
        txtBitacora.setLineWrap(true);
        txtBitacora.setWrapStyleWord(true);
        txtBitacora.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane scroll = new JScrollPane(txtBitacora);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ── ACCIONES ─────────────────────────────────────────────
    private void iniciarServidor() {
        servidorActivo = true;
        lblEstadoValor.setText("ACTIVO");
        lblEstadoValor.setForeground(ACCENT_GREEN);
        btnIniciar.setEnabled(false);
        btnDetener.setEnabled(true);
        log("Servidor INICIADO en puerto 8000.");

        hiloServidor = new Thread(() -> {
            // ── Todo dentro del hilo — no bloquea Swing ──

            // 1. Cargar roles
            VerificadorRoles.cargar();

            // 2. Restaurar reservas desde disco
            List<Reserva> restauradas = PersistenciaReservas.cargar();
            for (Reserva r : restauradas) {
                Servidor.calendario.cargarReservaRestaurada(r);
            }
            SwingUtilities.invokeLater(() -> {
                if (!restauradas.isEmpty()) {
                    log("✔ " + restauradas.size() + " reserva(s) restauradas desde disco.");
                } else {
                    log("No hay reservas previas que restaurar.");
                }
            });

            // 3. Abrir socket
            try {
                serverSocketActivo = new java.net.ServerSocket(8000);

                HiloTTL hiloTTL = new HiloTTL(
                    Servidor.calendario, Servidor.recursos,
                    Servidor.colaTTL, Servidor.bitacora
                );
                hiloTTL.setDaemon(true);
                hiloTTL.start();

                System.out.println("[SERVIDOR] Puerto 8000 abierto, esperando clientes...");

                while (!Thread.currentThread().isInterrupted()
                        && !serverSocketActivo.isClosed()) {
                    java.net.Socket clienteSocket = serverSocketActivo.accept();
                    java.io.DataInputStream entrada = new java.io.DataInputStream(
                        new java.io.BufferedInputStream(clienteSocket.getInputStream()));
                    String datosCliente = entrada.readUTF();
                    System.out.println("[SERVIDOR] Cliente: " + datosCliente);

                    HiloReserva hilo = new HiloReserva(
                        clienteSocket, datosCliente,
                        Servidor.calendario, Servidor.recursos,
                        Servidor.colaTTL, Servidor.bitacora
                    );
                    Servidor.clientesConectados.add(hilo);
                    hilo.start();
                }
            } catch (java.io.IOException e) {
                if (servidorActivo) {
                    log("[ERROR] Servidor: " + e.getMessage());
                }
            }
        });
        hiloServidor.setDaemon(true);
        hiloServidor.start();

        timerActualizacion = new Timer(2000, e -> actualizarVista());
        timerActualizacion.start();
        actualizarVista();
    }

    private void detenerServidor() {
        servidorActivo = false;
        if (timerActualizacion != null) timerActualizacion.stop();

        // 1. Guardar reservas confirmadas antes de cerrar
        PersistenciaReservas.guardar(Servidor.calendario);
        log("Reservas confirmadas guardadas en disco.");

        // 2. Notificar a cada cliente que el servidor se detiene
        synchronized (Servidor.clientesConectados) {
            for (HiloReserva hilo : Servidor.clientesConectados) {
                try {
                    hilo.flujoEscritura.writeUTF("ERROR|SERVIDOR_DETENIDO");
                    hilo.flujoEscritura.flush();
                } catch (java.io.IOException ignored) {}
                try {
                    hilo.socket.close();
                } catch (java.io.IOException ignored) {}
            }
            Servidor.clientesConectados.clear();
        }

        // 3. Cerrar el ServerSocket
        try {
            if (serverSocketActivo != null && !serverSocketActivo.isClosed()) {
                serverSocketActivo.close();
            }
        } catch (java.io.IOException ignored) {}
        serverSocketActivo = null;

        // 4. Interrumpir el hilo aceptador
        if (hiloServidor != null) hiloServidor.interrupt();

        lblEstadoValor.setText("INACTIVO");
        lblEstadoValor.setForeground(ACCENT_RED);
        lblReservasValor.setText("—");
        lblEquipoValor.setText("—");
        btnIniciar.setEnabled(true);
        btnDetener.setEnabled(false);
        log("Servidor DETENIDO.");
    }

    private void actualizarVista() {
        if (!servidorActivo) return;
     
        lblReservasValor.setText(Servidor.calendario.totalReservas() + " activas");
        lblCapacidadValor.setText(String.valueOf(Servidor.gestor.getCapacidadMaxima()));
        int proy = Servidor.gestor.proyectoresDisponibles();
        int mic  = Servidor.gestor.microfonosDisponibles();
        int son  = Servidor.gestor.sonidosDisponibles();
        lblEquipoValor.setText(String.valueOf(proy));
        lblMicrofonoValor.setText(String.valueOf(mic));
        lblSonidoValor.setText(String.valueOf(son));
        int completo = Math.min(proy, Math.min(mic, son));
        lblCompletoValor.setText(String.valueOf(completo));

        List<String> entradas = Servidor.bitacora.getUltimas(100);
        txtBitacora.setText("");
        for (String e : entradas) {
            txtBitacora.append(e + "\n");
        }
        txtBitacora.setCaretPosition(txtBitacora.getDocument().getLength());

        modeloTabla.setRowCount(0);
        List<Reserva> todasReservas = Servidor.calendario.getTodasLasReservas();
        for (Reserva r : todasReservas) {
            // En la tabla mostrar solo las no canceladas
            if (r.getEstado() == Reserva.Estado.CANCELADO) continue;
            modeloTabla.addRow(new Object[]{
                r.getIdReserva(),
                r.getIdCliente(),
                r.getFecha(),
                r.getHoraInicio() + "-" + r.getHoraFin(),
                r.getEstado().toString(),
                r.getCantAsistentes(),
                r.getEquipo().toString(),
                r.getTTL() == Long.MAX_VALUE ? "—" : r.segundosRestantes() + "s"
            });
        }
    }

    public void log(String msg) {
        String t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            txtBitacora.append("[" + t + "]  " + msg + "\n");
            txtBitacora.setCaretPosition(txtBitacora.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new FrmServidor().setVisible(true);
        });
    }
}