package App;

import UI.ClientView;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientView().setVisible(true);
        });
    }
}