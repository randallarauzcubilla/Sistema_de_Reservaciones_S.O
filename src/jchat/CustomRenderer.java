package jchat;

/**
 * CUSTOMRENDERER - Renderizador de celdas del tablero del servidor
 *
 * Colorea cada número según si fue cantado o no.
 * Usado en la tabla principal del FrmServidor (tablero 1-75).
 */

import java.awt.*;
import java.util.Set;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;

public class CustomRenderer extends DefaultTableCellRenderer {

    private static final Color COLOR_CANTADO = new Color(52,  152, 219); // Azul
    private static final Color COLOR_ULTIMO  = new Color(231, 76,  60);  // Rojo
    private static final Color COLOR_NORMAL  = new Color(236, 240, 241); // Gris

    private final int ultimoNumero;
    private final Set<Integer> cantados;

    public CustomRenderer(int ultimoNumero, Set<Integer> cantados) {
        this.ultimoNumero = ultimoNumero;
        this.cantados     = cantados;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int col) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        setHorizontalAlignment(SwingConstants.CENTER);

        if (value != null) {
            try {
                int num = Integer.parseInt(value.toString());
                if (num == ultimoNumero) {
                    setBackground(COLOR_ULTIMO);
                    setForeground(Color.WHITE);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else if (cantados.contains(num)) {
                    setBackground(COLOR_CANTADO);
                    setForeground(Color.WHITE);
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    setBackground(COLOR_NORMAL);
                    setForeground(new Color(52, 73, 94));
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
            } catch (NumberFormatException e) {
                setBackground(COLOR_NORMAL);
            }
        }
        return this;
    }
}