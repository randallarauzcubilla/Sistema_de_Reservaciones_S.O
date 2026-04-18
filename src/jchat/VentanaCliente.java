package jchat;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
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

    // === CONEXIÓN ===
    private Socket socket;
    private DataInputStream entrada;
    private DataOutputStream salida;
    private String ultimaReservaId = null;

    // === LOGIN ===
    private JTextField txtNombre;
    private JComboBox<String> cmbRol;
    private JButton btnConectar;

    // === FORMULARIO ===
    private JTextField txtFecha;
    private JTextField txtHora;
    private JTextField txtHoraFin;
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
        cmbRol = new JComboBox<>(new String[]{"ESTUDIANTE", "DOCENTE", "DECANATURA"});
        estilizarCombo(cmbRol);
        formLogin.add(cmbRol, g);

        g.gridy++;
        g.insets = new Insets(20, 0, 0, 0);
        btnConectar = crearBotonPrimario("▶  Conectar", ACCENT_GREEN);
        btnConectar.addActionListener(e -> conectar());
        formLogin.add(btnConectar, g);

        sidebar.add(formLogin, BorderLayout.CENTER);

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

        // Fila 0: Fecha | Hora Inicio
        g.gridx = 0; g.gridy = 0; g.weightx = 0.3;
        card.add(etiqueta("Fecha (YYYY-MM-DD)"), g);
        g.gridx = 1;
        txtFecha = crearCampo("2025-06-10");
        card.add(txtFecha, g);

        g.gridx = 2;
        card.add(etiqueta("Hora Inicio (HH:mm)"), g);
        g.gridx = 3;
        txtHora = crearCampo("09:00");
        card.add(txtHora, g);

        // Fila 1: N° Asistentes | Equipamiento
        g.gridx = 0; g.gridy = 1;
        card.add(etiqueta("N° Asistentes"), g);
        g.gridx = 1;
        txtAsistentes = crearCampo("10");
        card.add(txtAsistentes, g);

        g.gridx = 2;
        card.add(etiqueta("Equipamiento"), g);
        g.gridx = 3;
        cmbEquipamiento = new JComboBox<>(new String[]{
            "NINGUNO", "PROYECTOR", "MICROFONO", "SONIDO", "COMPLETO"
        });
        estilizarCombo(cmbEquipamiento);
        card.add(cmbEquipamiento, g);

        // Fila 2: Hora Fin
        g.gridx = 0; g.gridy = 2;
        card.add(etiqueta("Hora Fin (HH:mm)"), g);
        g.gridx = 1;
        txtHoraFin = crearCampo("10:00");
        card.add(txtHoraFin, g);

        // Fila 3: botones
        g.gridx = 0; g.gridy = 3; g.insets = new Insets(14, 8, 5, 8);
        btnReservar  = crearBotonPrimario("📅  Reservar",  ACCENT_BLUE);
        btnConfirmar = crearBotonPrimario("✔  Confirmar", ACCENT_GREEN);
        btnCancelar  = crearBotonPrimario("✖  Cancelar",  ACCENT_RED);

        btnReservar.addActionListener(e  -> reservar());
        btnConfirmar.addActionListener(e -> confirmarReserva());
        btnCancelar.addActionListener(e  -> cancelarReserva());

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
                Object estado = modeloReservas.getValueAt(row, 3);
                if ("CONFIRMADA".equals(estado))  c.setForeground(ACCENT_GREEN);
                else if ("CANCELADA".equals(estado))  c.setForeground(ACCENT_RED);
                else if ("TEMPORAL".equals(estado))   c.setForeground(ACCENT_AMBER);
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

        JPanel wrapMsg = new JPanel(new BorderLayout(0, 6));
        wrapMsg.setBackground(BG_DARK);
        wrapMsg.setPreferredSize(new Dimension(0, 180));
        JLabel lblMsg = new JLabel("▸  Mensajes del servidor");
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

    // ── CONEXIÓN REAL AL SERVIDOR ────────────────────────────
    private void conectar() {
        String nombre = txtNombre.getText().trim();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese su nombre.", "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            socket  = new Socket("localhost", 8000);
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            salida  = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            salida.writeUTF(nombre);
            salida.flush();

            String respuesta = entrada.readUTF();
            if (!respuesta.startsWith("OK")) {
                notify("❌ Servidor rechazó la conexión: " + respuesta);
                return;
            }

            conectado = true;
            panelDerecho.setVisible(true);
            activarFormulario(true);
            btnConectar.setEnabled(false);
            txtNombre.setEditable(false);
            cmbRol.setEnabled(false);

            notify("✅ Conectado como: " + nombre);
            notify("Servidor listo. Puede realizar su reserva.");
            setTitle("VentanaCliente — " + nombre);

            Thread hiloEscucha = new Thread(this::escucharServidor);
            hiloEscucha.setDaemon(true);
            hiloEscucha.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "No se pudo conectar al servidor.\n¿Está corriendo el servidor?",
                "Error de conexión", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── ESCUCHAR RESPUESTAS DEL SERVIDOR ─────────────────────
    private void escucharServidor() {
        try {
            while (true) {
                String msg = entrada.readUTF();
                SwingUtilities.invokeLater(() -> procesarRespuesta(msg));
            }
        } catch (IOException e) {
            // El servidor se cayó o cerró la conexión
            SwingUtilities.invokeLater(this::manejarDesconexion);
        }
    }

    private void manejarDesconexion() {
        if (!conectado) return;  
        conectado = false;

        for (int i = modeloReservas.getRowCount() - 1; i >= 0; i--) {
            if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                modeloReservas.removeRow(i);
            }
        }

        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        JOptionPane.showMessageDialog(
            this,
            "El servidor ha sido cerrado.\nLa aplicación se cerrará.",
            "Servidor cerrado",
            JOptionPane.ERROR_MESSAGE
        );

        dispose();        
        System.exit(0);     
    }

    private void procesarRespuesta(String msg) {
        notify("← " + msg);
        String[] partes = msg.split("\\|");

        if (partes[0].equals("OK") && partes.length >= 3 && partes[1].equals("TEMPORAL")) {
            String id  = partes[2];
            String ttl = partes.length >= 4 ? partes[3] : "?";
            ultimaReservaId = id;
            for (int i = 0; i < modeloReservas.getRowCount(); i++) {
                if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                    modeloReservas.setValueAt(id,         i, 0);
                    modeloReservas.setValueAt("TEMPORAL", i, 3);
                    modeloReservas.setValueAt(ttl,        i, 4);
                    break;
                }
            }
        } else if (partes[0].equals("OK") && partes.length >= 2 && partes[1].equals("CONFIRMADO")) {
            String id = partes.length >= 3 ? partes[2] : ultimaReservaId;
            actualizarEstadoTabla(id, "CONFIRMADA");
        } else if (partes[0].equals("OK") && partes.length >= 2 && partes[1].equals("CANCELADO")) {
            String id = partes.length >= 3 ? partes[2] : ultimaReservaId;
            actualizarEstadoTabla(id, "CANCELADA");
        } else if (partes[0].equals("ERROR")) {
            notify("❌ Error del servidor: " + (partes.length > 1 ? partes[1] : "desconocido"));

            for (int i = modeloReservas.getRowCount() - 1; i >= 0; i--) {
                if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                    modeloReservas.removeRow(i);
                    break;
                }
            }
        }
    }

    private void actualizarEstadoTabla(String id, String nuevoEstado) {
        for (int i = 0; i < modeloReservas.getRowCount(); i++) {
            if (id.equals(modeloReservas.getValueAt(i, 0))) {
                modeloReservas.setValueAt(nuevoEstado, i, 3);
                tablaReservas.repaint();
                return;
            }
        }
    }

   
    private void reservar() {
        if (!conectado) return;
        String fecha   = txtFecha.getText().trim();
        String hora    = txtHora.getText().trim();
        String horaFin = txtHoraFin.getText().trim();
        String asis    = txtAsistentes.getText().trim();
        String equipo  = (String) cmbEquipamiento.getSelectedItem();
        String rol     = (String) cmbRol.getSelectedItem();

        
        if (fecha.isEmpty() || hora.isEmpty() || horaFin.isEmpty() || asis.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Complete todos los campos antes de reservar.",
                "Campos vacíos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        modeloReservas.addRow(new Object[]{"...", fecha, hora, "ENVIANDO...", "..."});
        enviar("RESERVAR|" + fecha + "|" + hora + "|" + horaFin + "|" + asis + "|" + equipo + "|" + rol);
    }

    private void confirmarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) { notify("⚠ Seleccione una reserva para confirmar."); return; }
        String id = (String) modeloReservas.getValueAt(fila, 0);
        if ("...".equals(id) || "ENVIANDO...".equals(id)) {
            notify("⚠ Espere la respuesta del servidor.");
            return;
        }
        enviar("CONFIRMAR|" + id);
    }

    private void cancelarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) { notify("⚠ Seleccione una reserva para cancelar."); return; }
        String id = (String) modeloReservas.getValueAt(fila, 0);
        if ("...".equals(id)) {
            notify("⚠ Espere la respuesta del servidor.");
            return;
        }
        enviar("CANCELAR|" + id);
    }

    
    private void enviar(String mensaje) {
        try {
            salida.writeUTF(mensaje);
            salida.flush();
            notify("→ " + mensaje);
        } catch (IOException e) {
            notify("❌ Error al enviar: " + e.getMessage());
        }
    }

    
    private void notify(String msg) {
        String t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtMensajes.append("[" + t + "]  " + msg + "\n");
        txtMensajes.setCaretPosition(txtMensajes.getDocument().getLength());
    }

    private void activarFormulario(boolean on) {
        txtFecha.setEnabled(on);
        txtHora.setEnabled(on);
        txtHoraFin.setEnabled(on);
        txtAsistentes.setEnabled(on);
        cmbEquipamiento.setEnabled(on);
        btnReservar.setEnabled(on);
        btnConfirmar.setEnabled(on);
        btnCancelar.setEnabled(on);
    }

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