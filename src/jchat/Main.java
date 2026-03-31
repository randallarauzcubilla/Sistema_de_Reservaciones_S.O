package jchat;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class Main {

    private static final Color BG_DARK      = new Color(13, 17, 28);
    private static final Color BG_PANEL     = new Color(22, 28, 45);
    private static final Color BG_CARD      = new Color(30, 38, 60);
    private static final Color ACCENT_BLUE  = new Color(64, 156, 255);
    private static final Color ACCENT_GREEN = new Color(50, 215, 130);
    private static final Color TEXT_PRIMARY = new Color(220, 228, 245);
    private static final Color TEXT_MUTED   = new Color(120, 135, 165);
    private static final Color BORDER_COLOR = new Color(45, 55, 80);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            mostrarMenu();
        });
    }

    private static void mostrarMenu() {
        JFrame frame = new JFrame("Sistema de Reservas de Sala");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(440, 380);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.getContentPane().setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(new EmptyBorder(36, 40, 36, 40));

        // Título
        JPanel titulo = new JPanel(new GridLayout(3, 1, 0, 4));
        titulo.setOpaque(false);
        titulo.setBorder(new EmptyBorder(0, 0, 30, 0));

        JLabel ico = new JLabel("⬡  RESERVAS SALA", SwingConstants.CENTER);
        ico.setFont(new Font("Monospaced", Font.BOLD, 20));
        ico.setForeground(ACCENT_BLUE);

        JLabel sep = new JLabel("─────────────────────", SwingConstants.CENTER);
        sep.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sep.setForeground(new Color(45, 55, 80));

        JLabel sub = new JLabel("Seleccione su perfil de acceso", SwingConstants.CENTER);
        sub.setFont(new Font("Monospaced", Font.PLAIN, 12));
        sub.setForeground(TEXT_MUTED);

        titulo.add(ico);
        titulo.add(sep);
        titulo.add(sub);
        root.add(titulo, BorderLayout.NORTH);

        // Botones
        JPanel botones = new JPanel(new GridLayout(2, 1, 0, 14));
        botones.setOpaque(false);

        JButton btnAdmin   = crearCardBtn("🖥   Administrador", "Gestionar servidor y reservas", new Color(64, 156, 255));
        JButton btnCliente = crearCardBtn("👤   Cliente / Usuario", "Realizar o consultar reservas", ACCENT_GREEN);

        btnAdmin.addActionListener(e -> {
            frame.dispose();
            new FrmServidor().setVisible(true);
        });
        btnCliente.addActionListener(e -> {
            frame.dispose();
            new VentanaCliente().setVisible(true);
        });

        botones.add(btnAdmin);
        botones.add(btnCliente);
        root.add(botones, BorderLayout.CENTER);

        JLabel pie = new JLabel("v1.0  —  jchat © 2025", SwingConstants.CENTER);
        pie.setFont(new Font("Monospaced", Font.PLAIN, 10));
        pie.setForeground(new Color(60, 70, 95));
        pie.setBorder(new EmptyBorder(20, 0, 0, 0));
        root.add(pie, BorderLayout.SOUTH);

        frame.add(root);
        frame.setVisible(true);
    }

    private static JButton crearCardBtn(String titulo, String subtitulo, Color color) {
        JButton btn = new JButton(
            "<html><center>" +
            "<span style='font-size:13px; font-family:monospace; color:#" + toHex(color) + "'><b>" + titulo + "</b></span><br>" +
            "<span style='font-size:10px; font-family:monospace; color:#789'>" + subtitulo + "</span>" +
            "</center></html>"
        );
        btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 18));
        btn.setForeground(color);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70), 1),
                new EmptyBorder(14, 20, 14, 20)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 18));
            }
        });
        return btn;
    }

    private static String toHex(Color c) {
        return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
