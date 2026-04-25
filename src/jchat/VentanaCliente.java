package jchat;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * VentanaCliente — Sistema de Reservas de Sala
 *
 * Integración con API del TSE (apis.gometa.org) para validación
 * de cédulas costarricenses en tiempo real.
 *
 * Dependencia requerida: org.json (agregar al classpath o Maven/Gradle)
 *   Maven: <dependency><groupId>org.json</groupId>
 *           <artifactId>json</artifactId><version>20240303</version></dependency>
 *
 * Si no querés usar org.json, el método parseJsonSimple()
 * incluye un parser manual liviano como alternativa.
 */
public class VentanaCliente extends JFrame {

    // =========================================================
    // PALETA DE COLORES
    // =========================================================
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

    // =========================================================
    // API TSE
    // =========================================================
    private static final String API_CEDULAS_URL = "https://apis.gometa.org/cedulas/";
    // Opcional: agregar tu API key si superás el límite de 20 req/5min
    // private static final String API_KEY = "tu_api_key_aqui";

    // =========================================================
    // CONEXIÓN AL SERVIDOR DE RESERVAS
    // =========================================================
    private Socket           socket;
    private DataInputStream  entrada;
    private DataOutputStream salida;
    private String           ultimaReservaId = null;

    // =========================================================
    // COMPONENTES — LOGIN
    // =========================================================
    private JTextField     txtNombre;         // Solo lectura, auto-rellenado por TSE
    private JTextField     txtDNI;
    private JComboBox<String> cmbRol;
    private JButton        btnConectar;
    private JButton btnSalir;
    private JLabel         lblEstadoConexion;
    private JLabel         lblVerificacion;   // Estado de validación de cédula

    // =========================================================
    // COMPONENTES — FORMULARIO RESERVA
    // =========================================================
    private JTextField     txtFecha;
    private JTextField     txtHora;
    private JTextField     txtHoraFin;
    private JTextField     txtAsistentes;
    private JComboBox<String> cmbEquipamiento;
    private JButton        btnReservar;
    private JButton        btnConfirmar;
    private JButton        btnCancelar;

    // =========================================================
    // COMPONENTES — TABLA Y MENSAJES
    // =========================================================
    private JTable             tablaReservas;
    private DefaultTableModel  modeloReservas;
    private JTextArea          txtMensajes;

    // =========================================================
    // ESTADO DE LA APLICACIÓN
    // =========================================================
    private boolean conectado            = false;
    private boolean cedulaVerificada     = false;
    private String  ultimaCedulaConsultada = ""; // evita re-consultar la misma cédula
    private JPanel  panelDerecho;
    private volatile boolean ejecutando = false;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================
    public VentanaCliente() {
        setTitle("VentanaCliente — Sistema de Reservas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 720);
        setMinimumSize(new Dimension(860, 600));
        setLocationRelativeTo(null);
        setBackground(BG_DARK);
        initComponents();
    }

    // =========================================================
    // INICIALIZACIÓN DE COMPONENTES
    // =========================================================
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

    // =========================================================
    // PANEL LOGIN (SIDEBAR IZQUIERDO)
    // =========================================================
    private JPanel crearPanelLogin() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setBackground(BG_PANEL);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR));

        // Logo / Header
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
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.gridx   = 0;
        g.weightx = 1.0;

        // ── Cédula ────────────────────────────────────────────
        g.gridy = 0; g.insets = new Insets(6, 0, 3, 0);
        formLogin.add(etiqueta("DNI / Cédula"), g);

        g.gridy = 1; g.insets = new Insets(0, 0, 0, 0);
        txtDNI = crearCampo("Ej: 123456789");
        // Verificar al perder el foco — solo si la cédula cambió respecto a la última consulta
        txtDNI.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                String dni = txtDNI.getText().trim();
                if (!dni.isEmpty() && !dni.equals("Ej: 123456789")
                        && !dni.equals(ultimaCedulaConsultada)) {
                    consultarCedulaTSE(dni);
                }
            }
        });
        // También verificar al presionar Enter en el campo
        txtDNI.addActionListener(e -> {
            String dni = txtDNI.getText().trim();
            if (!dni.isEmpty() && !dni.equals("Ej: 123456789")
                    && !dni.equals(ultimaCedulaConsultada)) {
                consultarCedulaTSE(dni);
            }
        });
        formLogin.add(txtDNI, g);

        // ── Indicador de estado de verificación ───────────────
        g.gridy = 2; g.insets = new Insets(4, 0, 0, 0);
        lblVerificacion = new JLabel("○  Ingrese su cédula");
        lblVerificacion.setFont(new Font("Monospaced", Font.PLAIN, 10));
        lblVerificacion.setForeground(TEXT_MUTED);
        formLogin.add(lblVerificacion, g);

        // ── Nombre (solo lectura — auto-rellenado por TSE) ────
        g.gridy = 3; g.insets = new Insets(14, 0, 3, 0);
        formLogin.add(etiqueta("Nombre (verificado)"), g);

        g.gridy = 4; g.insets = new Insets(0, 0, 0, 0);
        txtNombre = new JTextField();
        txtNombre.setFont(new Font("Monospaced", Font.BOLD, 11));
        txtNombre.setBackground(new Color(18, 24, 38));
        txtNombre.setForeground(ACCENT_GREEN);
        txtNombre.setEditable(false);
        txtNombre.setCaretColor(ACCENT_BLUE);
        txtNombre.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 10, 6, 10)));
        txtNombre.setPreferredSize(new Dimension(0, 34));
        txtNombre.setToolTipText("Se completa automáticamente desde el registro del TSE");
        formLogin.add(txtNombre, g);

        // ── Rol ───────────────────────────────────────────────
        g.gridy = 5; g.insets = new Insets(14, 0, 3, 0);
        formLogin.add(etiqueta("Rol"), g);

        g.gridy = 6; g.insets = new Insets(0, 0, 0, 0);
        cmbRol = new JComboBox<>(new String[]{"ESTUDIANTE", "DOCENTE", "DECANATURA"});
        estilizarCombo(cmbRol);
        formLogin.add(cmbRol, g);

        // ── Botón Conectar/ingresar ────────────────────────────────────
        g.gridy = 7; g.insets = new Insets(22, 0, 0, 0);
        btnConectar = crearBotonPrimario("▶  Ingresar", ACCENT_GREEN);
        btnConectar.addActionListener(e -> conectar());
        formLogin.add(btnConectar, g);
        
        // - botón de desconectar
        g.gridy = 8; g.insets = new Insets(10, 0, 0, 0);
        btnSalir = crearBotonPrimario("Salir", ACCENT_AMBER);
        btnSalir.addActionListener(e -> salirSistema());
        btnSalir.setEnabled(false);
        formLogin.add(btnSalir, g);

        sidebar.add(formLogin, BorderLayout.CENTER);

        // Label de estado de conexión al servidor
        lblEstadoConexion = new JLabel("● Sin conexión al servidor", SwingConstants.CENTER);
        lblEstadoConexion.setFont(new Font("Monospaced", Font.BOLD, 11));
        lblEstadoConexion.setForeground(ACCENT_RED);
        lblEstadoConexion.setBorder(new EmptyBorder(0, 0, 20, 0));
        sidebar.add(lblEstadoConexion, BorderLayout.SOUTH);

        return sidebar;
    }

    // =========================================================
    // PANEL FORMULARIO RESERVA
    // =========================================================
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
        g.fill    = GridBagConstraints.HORIZONTAL;
        g.insets  = new Insets(5, 8, 5, 8);

        // Fila 0: Fecha | Hora Inicio
        g.gridx = 0; g.gridy = 0; g.weightx = 0.3;
        card.add(etiqueta("Fecha (YYYY-MM-DD)"), g);
        g.gridx = 1;
        txtFecha = crearCampo("YYYY-MM-DD");
        card.add(txtFecha, g);

        g.gridx = 2;
        card.add(etiqueta("Hora Inicio (HH:mm)"), g);
        g.gridx = 3;
        txtHora = crearCampo("HH:mm");
        card.add(txtHora, g);

        // Fila 1: Asistentes | Equipamiento
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
        txtHoraFin = crearCampo("HH:mm");
        card.add(txtHoraFin, g);

        // Fila 3: Botones de acción
        g.gridx = 0; g.gridy = 3; g.insets = new Insets(14, 8, 5, 8);
        btnReservar  = crearBotonPrimario("📅  Reservar",  ACCENT_BLUE);
        btnConfirmar = crearBotonPrimario("✔  Confirmar", ACCENT_GREEN);
        btnCancelar  = crearBotonPrimario("✖  Cancelar",  ACCENT_RED);

        btnReservar.addActionListener(e  -> reservar());
        btnConfirmar.addActionListener(e -> confirmarReserva());
        btnCancelar.addActionListener(e  -> cancelarReserva());

        card.add(btnReservar,  g);
        g.gridx = 1; card.add(btnConfirmar, g);
        g.gridx = 2; card.add(btnCancelar,  g);

        panel.add(card, BorderLayout.CENTER);
        activarFormulario(false);
        return panel;
    }

    // =========================================================
    // PANEL TABLA + MENSAJES
    // =========================================================
    private JPanel crearPanelTablaYMensajes() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(BG_DARK);

        // ── Tabla de reservas ─────────────────────────────────
        JPanel wrapTabla = new JPanel(new BorderLayout(0, 6));
        wrapTabla.setBackground(BG_DARK);
        JLabel lblTabla = new JLabel("▸  Mis Reservas");
        lblTabla.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblTabla.setForeground(TEXT_MUTED);
        wrapTabla.add(lblTabla, BorderLayout.NORTH);

        String[] cols = {"ID", "Fecha", "Horario", "Estado", "TTL"};
        modeloReservas = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tablaReservas = new JTable(modeloReservas) {
            @Override
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? BG_CARD : BG_PANEL);
                c.setForeground(TEXT_PRIMARY);
                if (c instanceof JComponent) {
                    ((JComponent) c).setBorder(new EmptyBorder(0, 8, 0, 8));
                }
                Object estado = modeloReservas.getValueAt(row, 3);
                if      ("CONFIRMADA".equals(estado))  c.setForeground(ACCENT_GREEN);
                else if ("CANCELADA".equals(estado))   c.setForeground(ACCENT_RED);
                else if ("TEMPORAL".equals(estado))    c.setForeground(ACCENT_AMBER);
                else if ("ENVIANDO...".equals(estado)) c.setForeground(TEXT_MUTED);
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
        tablaReservas.setSelectionBackground(new Color(64, 156, 255, 45));

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

        // ── Área de mensajes del servidor ─────────────────────
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

    // =========================================================
    // INTEGRACIÓN TSE — CONSULTA DE CÉDULA
    // =========================================================

    /**
     * Consulta el padrón electoral del TSE a través de apis.gometa.org.
     * Endpoint: GET https://apis.gometa.org/cedulas/{cedula}
     *
     * Respuesta JSON esperada:
     * {
     *   "results": [
     *     {
     *       "cedula":    "123456789",
     *       "nombre":    "JUAN CARLOS",
     *       "papellido": "PEREZ",
     *       "sapellido": "MORA",
     *       "sexo":      "M",
     *       "fechacaduc":"20301231",
     *       "junta":     "1234"
     *     }
     *   ]
     * }
     *
     * Límite gratuito: 20 req / IP / 5 min.
     * API Key: https://apis.gometa.org/cedulas/signup
     *
     * La consulta se ejecuta en un hilo separado para no bloquear la UI.
     */
    private void consultarCedulaTSE(String cedula) {
        // Solo dígitos (limpiar guiones o espacios que el usuario pueda ingresar)
        String cedulaLimpia = cedula.replaceAll("[^0-9]", "");
        if (cedulaLimpia.isEmpty()) return;

        // Registrar esta cédula como "ya consultada" para evitar re-disparos
        // cuando el campo pierde el foco al hacer clic en otros controles
        ultimaCedulaConsultada = cedulaLimpia;

        // Actualizar UI: estado "verificando"
        lblVerificacion.setText("⟳  Verificando en TSE...");
        lblVerificacion.setForeground(ACCENT_AMBER);
        txtNombre.setText("");
        cedulaVerificada = false;
        btnConectar.setEnabled(false);

        // Ejecutar consulta HTTP en hilo separado para no bloquear el Event Dispatch Thread
        Thread hiloConsulta = new Thread(() -> {
            String urlStr = API_CEDULAS_URL + cedulaLimpia;
            HttpURLConnection conn = null;

            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);   // 6 segundos timeout de conexión
                conn.setReadTimeout(6000);       // 6 segundos timeout de lectura
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "VentanaCliente-ReservasSala/1.0");

                int status = conn.getResponseCode();

                if (status == 200) {
                    // Leer respuesta completa
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    String jsonStr = sb.toString();

                    // Parsear JSON (sin dependencia externa)
                    String nombreCompleto = extraerNombreDesdeJson(jsonStr, cedulaLimpia);

                    if (nombreCompleto != null && !nombreCompleto.isBlank()) {
                        final String nombre = nombreCompleto;
                        SwingUtilities.invokeLater(() -> {
                            txtNombre.setText(nombre);
                            lblVerificacion.setText("✔  Cédula válida — TSE");
                            lblVerificacion.setForeground(ACCENT_GREEN);
                            cedulaVerificada = true;
                            btnConectar.setEnabled(true);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            txtNombre.setText("");
                            lblVerificacion.setText("✖  Cédula no registrada");
                            lblVerificacion.setForeground(ACCENT_RED);
                            cedulaVerificada = false;
                            btnConectar.setEnabled(false);
                        });
                    }

                } else if (status == 404) {
                    SwingUtilities.invokeLater(() -> {
                        txtNombre.setText("");
                        lblVerificacion.setText("✖  Cédula no encontrada");
                        lblVerificacion.setForeground(ACCENT_RED);
                        cedulaVerificada = false;
                        btnConectar.setEnabled(false);
                    });

                } else if (status == 429) {
                    // Rate limit superado
                    SwingUtilities.invokeLater(() -> {
                        lblVerificacion.setText("⚠  Límite de consultas — reintente");
                        lblVerificacion.setForeground(ACCENT_AMBER);
                        // En caso de rate limit, permitir conexión con advertencia
                        btnConectar.setEnabled(true);
                    });

                } else {
                    SwingUtilities.invokeLater(() -> {
                        lblVerificacion.setText("⚠  Error TSE (HTTP " + status + ")");
                        lblVerificacion.setForeground(ACCENT_AMBER);
                        btnConectar.setEnabled(true); // Permitir continuar
                    });
                }

            } catch (SocketTimeoutException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerificacion.setText("⚠  TSE sin respuesta — continuando");
                    lblVerificacion.setForeground(ACCENT_AMBER);
                    btnConectar.setEnabled(true); // Permitir si el servicio no responde
                });

            } catch (UnknownHostException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerificacion.setText("⚠  Sin internet — no verificado");
                    lblVerificacion.setForeground(ACCENT_AMBER);
                    btnConectar.setEnabled(true); // Permitir en modo offline
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lblVerificacion.setText("⚠  Error de red: " + e.getMessage());
                    lblVerificacion.setForeground(ACCENT_AMBER);
                    btnConectar.setEnabled(true);
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        });

        hiloConsulta.setDaemon(true);
        hiloConsulta.setName("TSE-Cedula-Lookup");
        hiloConsulta.start();
    }

    /**
     * Extrae el nombre completo del JSON retornado por apis.gometa.org
     * sin usar librerías externas (parser manual liviano).
     *
     * La API puede retornar dos estructuras:
     *
     * Estructura 1 — array "results":
     *   { "results": [ { "nombre": "JUAN", "papellido": "PEREZ", "sapellido": "MORA" } ] }
     *
     * Estructura 2 — campos directos (cuando es búsqueda exacta):
     *   { "nombre": "JUAN", "papellido": "PEREZ", "sapellido": "MORA" }
     */
    private String extraerNombreDesdeJson(String json, String cedula) {
        if (json == null || json.isBlank()) return null;

        try {
            // Verificar si retornó resultados vacíos
            if (json.contains("\"results\":[]") || json.contains("\"results\": []")) {
                return null;
            }

            // Extraer campos nombre, papellido, sapellido
            String nombre    = extraerCampoJson(json, "nombre");
            String papellido = extraerCampoJson(json, "papellido");
            String sapellido = extraerCampoJson(json, "sapellido");

            if (nombre == null && papellido == null) return null;

            StringBuilder sb = new StringBuilder();
            if (nombre    != null && !nombre.isBlank())    sb.append(nombre.trim());
            if (papellido != null && !papellido.isBlank()) { if (sb.length() > 0) sb.append(" "); sb.append(papellido.trim()); }
            if (sapellido != null && !sapellido.isBlank()) { if (sb.length() > 0) sb.append(" "); sb.append(sapellido.trim()); }

            String resultado = sb.toString().trim();
            return resultado.isEmpty() ? null : resultado;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrae el valor de un campo JSON de tipo string.
     * Ejemplo: para "nombre":"JUAN CARLOS" retorna "JUAN CARLOS"
     *
     * Parser minimalista — funciona para los campos simples de la API del TSE.
     * No maneja JSON anidado complejo ni arrays de objetos anidados profundamente.
     */
    private String extraerCampoJson(String json, String campo) {
        // Busca: "campo":"valor" o "campo": "valor"
        String patron = "\"" + campo + "\"";
        int idx = json.indexOf(patron);
        if (idx < 0) return null;

        // Mover al carácter después del nombre del campo
        int start = idx + patron.length();

        // Saltar espacios y el ":"
        while (start < json.length() && (json.charAt(start) == ':' || json.charAt(start) == ' ')) {
            start++;
        }

        if (start >= json.length()) return null;

        char primer = json.charAt(start);

        if (primer == '"') {
            // Valor string: buscar la comilla de cierre (respetando escapes)
            start++; // saltar comilla de apertura
            StringBuilder valor = new StringBuilder();
            while (start < json.length()) {
                char c = json.charAt(start);
                if (c == '\\' && start + 1 < json.length()) {
                    start++; // saltar escape
                    valor.append(json.charAt(start));
                } else if (c == '"') {
                    break; // fin del valor
                } else {
                    valor.append(c);
                }
                start++;
            }
            String v = valor.toString().trim();
            return v.isEmpty() ? null : v;

        } else if (primer == 'n') {
            // null
            return null;
        }

        return null;
    }

    // =========================================================
    // CONEXIÓN AL SERVIDOR DE RESERVAS
    // =========================================================
    private void conectar() {
        String nombre = txtNombre.getText().trim();
        String dni    = txtDNI.getText().trim();

        // Validar campos
        if (dni.isEmpty() || dni.equals("Ej: 123456789")) {
            JOptionPane.showMessageDialog(this,
                "Ingrese su número de cédula.",
                "Campo vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!cedulaVerificada && nombre.isEmpty()) {
            int opcion = JOptionPane.showConfirmDialog(this,
                "La cédula no fue verificada contra el TSE.\n" +
                "¿Desea continuar de todas formas?",
                "Cédula no verificada",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (opcion != JOptionPane.YES_OPTION) return;
            nombre = JOptionPane.showInputDialog(this,
                "Ingrese su nombre completo:",
                "Nombre requerido",
                JOptionPane.PLAIN_MESSAGE);

            if (nombre == null || nombre.trim().isEmpty()) return;
            nombre = nombre.trim();
            txtNombre.setText(nombre);
            cedulaVerificada = true;
        }

        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No se pudo obtener el nombre desde el TSE.\nVerifique su cédula.",
                "Nombre no disponible", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Intentar conectar al servidor de reservas
        Socket socketTemp = null;
        try {
            socketTemp = new Socket();
            socketTemp.connect(new InetSocketAddress("localhost", 8000), 3000);

            DataInputStream  entradaTemp = new DataInputStream(
                    new BufferedInputStream(socketTemp.getInputStream()));
            DataOutputStream salidaTemp  = new DataOutputStream(
                    new BufferedOutputStream(socketTemp.getOutputStream()));

            salidaTemp.writeUTF(nombre + "|" + dni);
            salidaTemp.flush();

            socketTemp.setSoTimeout(3000);
            String respuesta = entradaTemp.readUTF();
            socketTemp.setSoTimeout(0);

            if (!respuesta.equals("OK|CONECTADO")) {
                notifyMsg("❌ Servidor rechazó la conexión: " + respuesta);
                cerrarSilencioso(socketTemp);
                return;
            }

            socket  = socketTemp;
            entrada = entradaTemp;
            salida  = salidaTemp;

            conectado = true;
            ejecutando = true;
            lblEstadoConexion.setText("● " + nombre.split(" ")[0]);
            lblEstadoConexion.setForeground(ACCENT_GREEN);
            panelDerecho.setVisible(true);
            activarFormulario(true);
            btnConectar.setEnabled(false);
            btnSalir.setEnabled(true);
            txtDNI.setEditable(false);
            cmbRol.setEnabled(false);

            notifyMsg("✅ Conectado como: " + nombre + " (DNI: " + dni + ")");
            notifyMsg("Servidor listo. Puede realizar su reserva.");
            setTitle("VentanaCliente — " + nombre.split(" ")[0]);

            Thread hiloEscucha = new Thread(this::escucharServidor);
            hiloEscucha.setDaemon(true);
            hiloEscucha.setName("Servidor-Listener");
            hiloEscucha.start();

        } catch (ConnectException e) {
            cerrarSilencioso(socketTemp);
            JOptionPane.showMessageDialog(this,
                "No hay servidor activo en el puerto 8000.\n¿Está corriendo el servidor?",
                "Sin conexión", JOptionPane.ERROR_MESSAGE);
        } catch (SocketTimeoutException e) {
            cerrarSilencioso(socketTemp);
            JOptionPane.showMessageDialog(this,
                "El servidor no respondió a tiempo.\nVerifique que esté operativo.",
                "Tiempo de espera agotado", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            cerrarSilencioso(socketTemp);
            JOptionPane.showMessageDialog(this,
                "Error de conexión: " + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void salirSistema() {

    ejecutando = false;

    try {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    } catch (IOException ignored) {}

    conectado = false;
    cedulaVerificada = false;
    ultimaReservaId = null;

    txtDNI.setText("");
    txtDNI.setForeground(TEXT_MUTED);
    txtDNI.setEditable(true);
    ultimaCedulaConsultada = "";
    txtNombre.setText("");
    cmbRol.setSelectedIndex(0);
    cmbRol.setEnabled(true);

    lblVerificacion.setText("○  Ingrese su cédula");
    lblVerificacion.setForeground(TEXT_MUTED);

    btnConectar.setEnabled(true);
    btnSalir.setEnabled(false);

    panelDerecho.setVisible(false);

    modeloReservas.setRowCount(0);
    txtMensajes.setText("");

    lblEstadoConexion.setText("● Sin conexión al servidor");
    lblEstadoConexion.setForeground(ACCENT_RED);

    setTitle("VentanaCliente — Sistema de Reservas");

    notifyMsg("Usuario desconectado.");
}

    private void cerrarSilencioso(Socket s) {
        if (s != null && !s.isClosed()) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    // =========================================================
    // ESCUCHAR RESPUESTAS DEL SERVIDOR
    // =========================================================
    private void escucharServidor() {
    try {
        while (ejecutando && !socket.isClosed()) {
            String msg = entrada.readUTF();
            SwingUtilities.invokeLater(() -> procesarRespuesta(msg));
        }
    } catch (IOException e) {
        SwingUtilities.invokeLater(this::manejarDesconexion);
    }
}

    // =========================================================
    // MANEJO DE DESCONEXIÓN DEL SERVIDOR
    // =========================================================
    private void manejarDesconexion() {
        if (!conectado) return;
        notifyMsg("⚠ Conexión con el servidor perdida.");
        desconectar();
    }

    private void desconectar() {
        conectado = false;

        // Eliminar filas en estado transitorio
        for (int i = modeloReservas.getRowCount() - 1; i >= 0; i--) {
            if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                modeloReservas.removeRow(i);
            }
        }

        cerrarSilencioso(socket);
        activarFormulario(false);
        panelDerecho.setVisible(false);

        // Restaurar UI de login
        btnConectar.setEnabled(true);
        txtDNI.setEditable(true);
        cmbRol.setEnabled(true);
        lblEstadoConexion.setText("● Sin conexión al servidor");
        lblEstadoConexion.setForeground(ACCENT_RED);
        setTitle("VentanaCliente — Sistema de Reservas");

        // Limpiar datos del usuario
        txtNombre.setText("");
        txtDNI.setText("Ej: 123456789");
        txtDNI.setForeground(TEXT_MUTED);
        lblVerificacion.setText("○  Ingrese su cédula");
        lblVerificacion.setForeground(TEXT_MUTED);
        cedulaVerificada = false;
        ultimaCedulaConsultada = "";
    }

    // =========================================================
    // PROCESAR RESPUESTAS DEL SERVIDOR
    // =========================================================
    private void procesarRespuesta(String msg) {
        notifyMsg("← " + msg);
        String[] partes = msg.split("\\|");

        switch (partes[0]) {

            case "HISTORIAL":
                modeloReservas.setRowCount(0);
                for (int i = 1; i < partes.length; i++) {
                    String[] campos = partes[i].split(",", 5);
                    if (campos.length < 5) continue;
                    String id      = campos[0];
                    String fecha   = campos[1];
                    String horaRng = campos[2] + " - " + campos[3];
                    String estado  = campos[4];
                    modeloReservas.addRow(new Object[]{id, fecha, horaRng, estado, "—"});
                }
                if (modeloReservas.getRowCount() > 0) {
                    notifyMsg("📋 Historial restaurado: " + modeloReservas.getRowCount() + " reserva(s).");
                }
                break;

            case "OK":
                if (partes.length >= 3 && "TEMPORAL".equals(partes[1])) {
                    String id  = partes[2];
                    String ttl = partes.length >= 4 ? partes[3] : "?";
                    ultimaReservaId = id;
                    // Actualizar la fila que estaba en "ENVIANDO..."
                    for (int i = 0; i < modeloReservas.getRowCount(); i++) {
                        if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                            modeloReservas.setValueAt(id,         i, 0);
                            modeloReservas.setValueAt("TEMPORAL", i, 3);
                            modeloReservas.setValueAt(ttl,        i, 4);
                            break;
                        }
                    }

                } else if (partes.length >= 2 && "CONFIRMADO".equals(partes[1])) {
                    String id = partes.length >= 3 ? partes[2] : ultimaReservaId;
                    actualizarEstadoTabla(id, "CONFIRMADA");

                } else if (partes.length >= 2 && "CANCELADO".equals(partes[1])) {
                    String id = partes.length >= 3 ? partes[2] : ultimaReservaId;
                    actualizarEstadoTabla(id, "CANCELADA");
                }
                break;

            case "ERROR":
                notifyMsg("❌ Error del servidor: " + (partes.length > 1 ? partes[1] : "desconocido"));
                if (partes.length > 1 && "SERVIDOR_DETENIDO".equals(partes[1])) {
                    manejarDesconexion();
                    return;
                }
                // Remover fila transitoria si hay un error
                for (int i = modeloReservas.getRowCount() - 1; i >= 0; i--) {
                    if ("ENVIANDO...".equals(modeloReservas.getValueAt(i, 3))) {
                        modeloReservas.removeRow(i);
                        break;
                    }
                }
                break;

            default:
                // Mensaje no reconocido — ya se imprimió en el log
                break;
        }
    }

    private void actualizarEstadoTabla(String id, String nuevoEstado) {
        for (int i = 0; i < modeloReservas.getRowCount(); i++) {
            if (id != null && id.equals(modeloReservas.getValueAt(i, 0))) {
                modeloReservas.setValueAt(nuevoEstado, i, 3);
                tablaReservas.repaint();
                return;
            }
        }
    }

    // =========================================================
    // ACCIONES DE RESERVA
    // =========================================================
    private void reservar() {
        if (!conectado) return;

        String fecha   = txtFecha.getText().trim();
        String hora    = txtHora.getText().trim();
        String horaFin = txtHoraFin.getText().trim();
        String asis    = txtAsistentes.getText().trim();
        String equipo  = (String) cmbEquipamiento.getSelectedItem();
        String rol     = (String) cmbRol.getSelectedItem();

        // Validar que no sean placeholders
        if (fecha.isEmpty()   || "YYYY-MM-DD".equals(fecha)
         || hora.isEmpty()    || "HH:mm".equals(hora)
         || horaFin.isEmpty() || "HH:mm".equals(horaFin)
         || asis.isEmpty()    || "10".equals(asis)) {
            JOptionPane.showMessageDialog(this,
                "Complete todos los campos antes de reservar.",
                "Campos vacíos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        modeloReservas.addRow(new Object[]{"...", fecha, hora + " - " + horaFin, "ENVIANDO...", "..."});
        enviar("RESERVAR|" + fecha + "|" + hora + "|" + horaFin + "|" + asis + "|" + equipo + "|" + rol);
    }

    private void confirmarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) {
            notifyMsg("⚠ Seleccione una reserva para confirmar.");
            return;
        }
        String id = (String) modeloReservas.getValueAt(fila, 0);
        if ("...".equals(id) || "ENVIANDO...".equals(modeloReservas.getValueAt(fila, 3))) {
            notifyMsg("⚠ Espere la respuesta del servidor.");
            return;
        }
        enviar("CONFIRMAR|" + id);
    }

    private void cancelarReserva() {
        int fila = tablaReservas.getSelectedRow();
        if (fila < 0) {
            notifyMsg("⚠ Seleccione una reserva para cancelar.");
            return;
        }
        String id = (String) modeloReservas.getValueAt(fila, 0);
        if ("...".equals(id)) {
            notifyMsg("⚠ Espere la respuesta del servidor.");
            return;
        }
        enviar("CANCELAR|" + id);
    }

    // =========================================================
    // COMUNICACIÓN CON EL SERVIDOR
    // =========================================================
    private void enviar(String mensaje) {
        try {
            salida.writeUTF(mensaje);
            salida.flush();
            notifyMsg("→ " + mensaje);
        } catch (IOException e) {
            notifyMsg("❌ Error al enviar: " + e.getMessage());
        }
    }

    // =========================================================
    // UTILIDADES DE UI
    // =========================================================

    /** Agrega una línea con timestamp al área de mensajes */
    private void notifyMsg(String msg) {
        String t = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtMensajes.append("[" + t + "]  " + msg + "\n");
        txtMensajes.setCaretPosition(txtMensajes.getDocument().getLength());
    }

    /** Habilita o deshabilita los controles del formulario de reserva */
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

    /** Etiqueta de formulario con estilo consistente */
    private JLabel etiqueta(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        lbl.setForeground(TEXT_MUTED);
        return lbl;
    }

    /** Campo de texto con placeholder y estilo oscuro */
    private JTextField crearCampo(String placeholder) {
        JTextField f = new JTextField();
        f.setFont(new Font("Monospaced", Font.PLAIN, 12));
        f.setBackground(BG_DARK);
        f.setForeground(TEXT_MUTED);
        f.setCaretColor(ACCENT_BLUE);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 10, 6, 10)));
        f.setPreferredSize(new Dimension(0, 34));
        f.setText(placeholder);

        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (f.getText().equals(placeholder)) {
                    f.setText("");
                    f.setForeground(TEXT_PRIMARY);
                }
            }
            @Override
            public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) {
                    f.setText(placeholder);
                    f.setForeground(TEXT_MUTED);
                }
            }
        });
        return f;
    }

    /** Estiliza un JComboBox con la paleta oscura */
    private void estilizarCombo(JComboBox<?> combo) {
        combo.setFont(new Font("Monospaced", Font.BOLD, 12));
        combo.setBackground(BG_CARD);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_BLUE, 1),
                new EmptyBorder(2, 6, 2, 6)));
        combo.setPreferredSize(new Dimension(0, 34));

        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setFont(new Font("Monospaced", Font.BOLD, 12));
                setBackground(isSelected ? ACCENT_BLUE : BG_CARD);
                setForeground(isSelected ? BG_DARK : TEXT_PRIMARY);
                setBorder(new EmptyBorder(4, 10, 4, 10));
                return this;
            }
        });
    }

    /** Crea un botón con borde de color y efecto hover */
    private JButton crearBotonPrimario(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setFont(new Font("Monospaced", Font.BOLD, 12));
        btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        btn.setForeground(color);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 90), 1),
                new EmptyBorder(8, 16, 8, 16)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
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
            } catch (Exception ignored) {}
            new VentanaCliente().setVisible(true);
        });
    }
}