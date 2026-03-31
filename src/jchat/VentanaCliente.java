package jchat;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VentanaCliente extends JFrame {

    // === PALETA ===
    private static final Color BG_DARK      = new Color(13, 17, 28);
    private static final Color BG_PANEL     = new Color(22, 28, 45);
    private static final Color BG_CARD      = new Color(30, 38, 60);
    private static final Color ACCENT_BLUE  = new Color(64, 156, 255);
    private static final Color ACCENT_GREEN = new Color(50, 215, 130);
    private static final Color ACCENT_RED   = new Color(255, 75, 90);
    private static final Color ACCENT_AMBER = new Color(255, 190, 60);
    private static final Color TEXT_PRIMARY = new Color(220, 228, 245);
    private static final Color TEXT_MUTED   = new Color(120, 135, 165);
    private static final Color BORDER_COLOR = new Color(45, 55, 80);

    // === LOGIN ===
    private JTextField txtNombre;
    private JComboBox<String> cmbRol;
    private JButton btnConectar;

    // === FORMULARIO ===
    private JTextField txtFecha;
    private JTextField txtHora;
    private JTextField txtAsistentes;
    private JComboBox<String> cmbEquipamiento;
    private JButton btnReservar;
    private JButton btnConfirmar;
    private JButton btnCancelar;

    // === TABLA ===
    private JTable tablaReservas;
    private DefaultTableModel modeloReservas;

    // === MENSAJES ===
    private JTextArea txtMensajes;

    // === ESTADO ===
    private boolean conectado = false;
    private int contadorReservas = 1;
    private JPanel panelDerecho;

    public VentanaCliente() {
        setTitle("VentanaCliente — Sistema de Reservas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 720);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        setBackground(BG_DARK);
        initComponents();
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.add(crearPanelLogin(), BorderLayout.WEST);

        panelDerecho = new JPanel(new BorderLayout(0, 14));
        panelDerecho.setBackground(BG_DARK);
        panelDerecho.setBorder(new EmptyBorder(20, 20, 20, 20));
        panelDerecho.add(crearPanelFormulario(), BorderLayout.NORTH);
        panelDerecho.add(crearPanelTablaYMensajes(), BorderLayout.CENTER);
        panelDerecho.setVisible(false);

        root.add(panelDerecho, BorderLayout.CENTER);
        add(root);
    }

    // ── LOGIN SIDEBAR ────────────────────────────────────────
    private JPanel crearPanelLogin() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBackground(BG_PANEL);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // Logo
        JPanel logoPanel = new JPanel(new GridLayout(2, 1));
        logoPanel.setBackground(BG_PANEL);
        logoPanel.setBorder(new EmptyBorder(28, 20, 20, 20));
        JLabel ico = new JLabel("⬡  RESERVAS SALA");
        ico.setFont(new Font("Monospaced", Font.BOLD, 14));
        ico.setForeground(ACCENT_BLUE);
        JLabel sub = new JLabel("   Acceso de Usuario");
        sub.setFont(new Font("Monospaced", Font.PLAIN, 11));
        sub.setForeground(TEXT_MUTED);
        logoPanel.add(ico);
        logoPanel.add(sub);
        sidebar.add(logoPanel, BorderLayout.NORTH);

        // Formulario de login
        JPanel formLogin = new JPanel(new GridBagLayout());
        formLogin.setBackground(BG_PANEL);
        formLogin.setBorder(new EmptyBorder(10, 16, 10, 16));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0;
        g.insets = new Insets(6, 0, 3, 0);

        formLogin.add(etiqueta("Nombre"), g);
        g.gridy++;
        txtNombre = crearCampo("Tu nombre completo");
        formLogin.add(txtNombre, g);

        g.gridy++;
        g.insets = new Insets(12, 0, 3, 0);
        formLogin.add(etiqueta("Rol"), g);
        g.gridy++;
        g.insets = new Insets(0, 0, 0, 0);
        cmbRol = new JComboBox<>(new String[]{"Estudiante", "Docente", "Decanatura"});
        estilizarCombo(cmbRol);
        formLogin.add(cmbRol, g);

        g.gridy++;
        g.insets = new Insets(20, 0, 0, 0);
        btnConectar = crearBotonPrimario("▶  Conectar", ACCENT_GREEN);
        btnConectar.addActionListener(e -> conectar());
        formLogin.add(btnConectar, g);

        sidebar.add(formLogin, BorderLayout.CENTER);

        // Pie de estado
        JLabel pie = new JLabel("Sin conexión al servidor", SwingConstants.CENTER);
        pie.setFont(new Font("Monospaced", Font.ITALIC, 10));
        pie.setForeground(TEXT_MUTED);
        pie.setBorder(new EmptyBorder(0, 0, 20, 0));
        sidebar.add(pie, BorderLayout.SOUTH);

        return sidebar;
    }

    // ── FORMULARIO RESERVA ───────────────────────────────────
    private JPanel crearPanelFormulario() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(BG_DARK);

        JLabel header = new JLabel("Nueva Reserva");
        header.setFont(new Font("Monospaced", Font.BOLD, 17));
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        panel.add(header, BorderLayout.NORTH);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(16, 20, 16, 20)));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(5, 8, 5, 8);

        // Fila 0
        g.gridx = 0; g.gridy = 0; g.weightx = 0.3;
        card.add(etiqueta("Fecha (YYYY-MM-DD)"), g);
        g.gridx = 1;
        txtFecha = crearCampo("2025-06-10");
        card.add(txtFecha, g);

        g.gridx = 2;
        card.add(etiqueta("Hora (HH:mm)"), g);
        g.gridx = 3;
        txtHora = crearCampo("09:00");
        card.add(txtHora, g);

        // Fila 1
        g.gridx = 0; g.gridy = 1;
        card.add(etiqueta("N° Asistentes"), g);
        g.gridx = 1;
        txtAsistentes = crearCampo("10");
        card.add(txtAsistentes, g);

        g.gridx = 2;
        card.add(etiqueta("Equipamiento"), g);
        g.gridx = 3;
        cmbEquipamiento = new JComboBox<>(new String[]{"Proyector", "Micrófono", "Sonido", "Proyector + Micrófono", "Todos"});
        estilizarCombo(cmbEquipamiento);
        card.add(cmbEquipamiento, g);

        // Fila 2: botones
        g.gridx = 0; g.gridy = 2; g.insets = new Insets(14, 8, 5, 8);
        btnReservar  = crearBotonPrimario("📅  Reservar",  ACCENT_BLUE);
        btnConfirmar = crearBotonPrimario("✔  Confirmar", ACCENT_GREEN);
        btnCancelar  = crearBotonPrimario("✖  Cancelar",  ACCENT_RED);

        btnReservar.addActionListener(e -> reservar());
        btnConfirmar.addActionListener(e -> confirmarReserva());
        btnCancelar.addActionListener(e -> cancelarReserva());

        card.add(btnReservar, g);
        g.gridx = 1; card.add(btnConfirmar, g);
        g.gridx = 2; card.add(btnCancelar, g);

        panel.add(card, BorderLayout.CENTER);
        activarFormulario(false);
        return panel;
    }

    // ── TABLA + MENSAJES ─────────────────────────────────────
    private JPanel crearPanelTablaYMensajes() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(BG_DARK);

        // Tabla
        JPanel wrapTabla = new JPanel(new BorderLayout(0, 6));
        wrapTabla.setBackground(BG_DARK);
        JLabel lblTabla = new JLabel("▸  Mis Reservas");
        lblTabla.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblTabla.setForeground(TEXT_MUTED);
        wrapTabla.add(lblTabla, BorderLayout.NORTH);

        String[] cols = {"ID", "Fecha", "Hora", "Estado", "TTL"};
        modeloReservas = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tablaReservas = new JTable(modeloReservas) {
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_CARD : BG_PANEL);
                c.setForeground(TEXT_PRIMARY);
                ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                // Color según estado
                Object estado = modeloReservas.getValueAt(row, 3);
                if ("Confirmada".equals(estado)) c.setForeground(ACCENT_GREEN);
                else if ("Cancelada".equals(estado)) c.setForeground(ACCENT_RED);
                else if ("Pendiente".equals(estado)) c.setForeground(ACCENT_AMBER);
                if (isRowSelected(row)) c.setBackground(new Color(64, 156, 255, 45));
                return c;
            }
        };

        tablaReservas.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tablaReservas.setRowHeight(30);
        tablaReservas.setBackground(BG_CARD);
        tablaReservas.setGridColor(BORDER_COLOR);
        tablaReservas.setShowVerticalLines(false);
        tablaReservas.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader th = tablaReservas.getTableHeader();
        th.setBackground(BG_PANEL);
        th.setForeground(ACCENT_BLUE);
        th.setFont(new Font("Monospaced", Font.BOLD, 12));
        th.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_BLUE));
        th.setPreferredSize(new Dimension(0, 34));

        JScrollPane scrollTabla = new JScrollPane(tablaReservas);
        scrollTabla.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        scrollTabla.getViewport().setBackground(BG_CARD);
        wrapTabla.add(scrollTabla, BorderLayout.CENTER);
        panel.add(wrapTabla, BorderLayout.CENTER);

        // Mensajes
        JPanel wrapMsg = new JPanel(new BorderLayout(0, 6));
        wrapMsg.setBackground(BG_DARK);
        wrapMsg.setPreferredSize(new Dimension(0, 180));
        JLabel lblMsg = new JLabel("▸  Mensajes del servidor / Notificaciones");
        lblMsg.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblMsg.setForeground(TEXT_MUTED);
        wrapMsg.add(lblMsg, BorderLayout.NORTH);

        txtMensajes = new JTextArea();
        txtMensajes.setEditable(false);
        txtMensajes.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtMensajes.setBackground(new Color(8, 12, 20));
        txtMensajes.setForeground(new Color(100, 200, 255));
        txtMensajes.setCaretColor(ACCENT_BLUE);
        txtMensajes.setLineWrap(true);
        txtMensajes.setWrapStyleWord(true);
        txtMensajes.setBorder(new EmptyBorder(10, 14, 10, 14));

        JScrollPane scrollMsg = new JScrollPane(txtMensajes);
        scrollMsg.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        wrapMsg.add(scrollMsg, BorderLayout.CENTER);
        panel.add(wrapMsg, BorderLayout.SOUTH);

        return panel;
    }

    // ── ACCIONES ─────────────────────────────────────────────
    private void conectar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese su nombre.", "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }
        conectado = true;
        String rol = (String) cmbRol.getSelectedItem();
        panelDerecho.setVisible(true);
        activarFormulario(true);
        btnConectar.setEnabled(false);
        txtNombre.setEditable(false);
        cmbRol.setEnabled(false);
        notify("✅ Conectado como: " + nombre + " [" + rol + "]");
        notify("Servidor listo. Puede realizar su reserva.");
        setTitle("VentanaCliente — " + nombre + " (" + rol + ")");
    }

    private void reservar() {
        if (!conectado) return;
        String fecha = txtFecha.getText().trim();
        String hora  = txtHora.getText().trim();
        String asis  = txtAsistentes.getText().trim();
        String equipo = (String) cmbEquipamiento.getSelectedItem();
        if (fecha.isEmpty() || hora.isEmpty() || asis.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Complete todos los campos.", "Campos vacíos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = "RES-" + String.format("%03d", contadorReservas++);
        modeloReservas.addRow(new Object[]{id, fecha, hora, "Pendiente", "01:00:00"});
        notify("📅 Reserva " + id + " creada — " + fecha + " " + hora + " | " + equipo + ". Estado: PENDIENTE");
    }

    private void confirmarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) { notify("⚠  Seleccione una reserva para confirmar."); return; }
        modeloReservas.setValueAt("Confirmada", fila, 3);
        notify("✔  Reserva " + modeloReservas.getValueAt(fila, 0) + " CONFIRMADA.");
        tablaReservas.repaint();
    }

    private void cancelarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) { notify("⚠  Seleccione una reserva para cancelar."); return; }
        modeloReservas.setValueAt("Cancelada", fila, 3);
        notify("✖  Reserva " + modeloReservas.getValueAt(fila, 0) + " CANCELADA.");
        tablaReservas.repaint();
    }

    private void notify(String msg) {
        String t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtMensajes.append("[" + t + "]  " + msg + "\n");
        txtMensajes.setCaretPosition(txtMensajes.getDocument().getLength());
    }

    private void activarFormulario(boolean on) {
        txtFecha.setEnabled(on); txtHora.setEnabled(on);
        txtAsistentes.setEnabled(on); cmbEquipamiento.setEnabled(on);
        btnReservar.setEnabled(on); btnConfirmar.setEnabled(on); btnCancelar.setEnabled(on);
    }

    // ── UTILIDADES UI ─────────────────────────────────────────
    private JLabel etiqueta(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        lbl.setForeground(TEXT_MUTED);
        return lbl;
    }

    private JTextField crearCampo(String placeholder) {
        JTextField f = new JTextField(placeholder);
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBackground(BG_DARK);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(ACCENT_BLUE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 10, 6, 10)));
        f.setPreferredSize(new Dimension(0, 34));
        return f;
    }

    private void estilizarCombo(JComboBox<?> combo) {
        combo.setFont(new Font("Monospaced", Font.PLAIN, 12));
        combo.setBackground(BG_DARK);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        combo.setPreferredSize(new Dimension(0, 34));
    }

    private JButton crearBotonPrimario(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Monospaced", Font.BOLD, 12));
        btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        btn.setForeground(color);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90), 1),
                new EmptyBorder(8, 16, 8, 16)));
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new VentanaCliente().setVisible(true);
        });
    }
}
